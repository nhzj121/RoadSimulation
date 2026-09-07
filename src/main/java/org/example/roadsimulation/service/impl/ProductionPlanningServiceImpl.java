package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.Enrollment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.ProductionPlanningService;
import org.example.roadsimulation.service.TransportDemandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Demand-driven production planning service.
 *
 * <p>The final demand is generated at the end of the chain, then quantities are
 * propagated backward through stage output ratios.</p>
 */
@Service
@Transactional
public class ProductionPlanningServiceImpl implements ProductionPlanningService {

    private static final DateTimeFormatter PLAN_NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ProcessingChainRepository chainRepository;
    private final ProductionPlanRepository planRepository;
    private final ProductionPlanNodeRepository nodeRepository;
    private final ProductionBatchRepository batchRepository;
    private final ProcessingStageExecutionRepository executionRepository;
    private final POIRepository poiRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TransportDemandService transportDemandService;

    public ProductionPlanningServiceImpl(
            ProcessingChainRepository chainRepository,
            ProductionPlanRepository planRepository,
            ProductionPlanNodeRepository nodeRepository,
            ProductionBatchRepository batchRepository,
            ProcessingStageExecutionRepository executionRepository,
            POIRepository poiRepository,
            EnrollmentRepository enrollmentRepository,
            TransportDemandService transportDemandService
    ) {
        this.chainRepository = chainRepository;
        this.planRepository = planRepository;
        this.nodeRepository = nodeRepository;
        this.batchRepository = batchRepository;
        this.executionRepository = executionRepository;
        this.poiRepository = poiRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.transportDemandService = transportDemandService;
    }

    @Override
    public ProductionPlanResponse createRandomPlan(CreateProductionPlanRequest request) {
        if (request == null || request.chainId() == null) {
            throw new IllegalArgumentException("chainId is required");
        }

        ProcessingChain chain = chainRepository.findById(request.chainId())
                .orElseThrow(() -> new IllegalArgumentException("加工链不存在: " + request.chainId()));
        if (chain.getStatus() != ProcessingChain.ChainStatus.ACTIVE) {
            throw new IllegalStateException("加工链未激活: " + chain.getChainName());
        }

        List<ProcessingStage> stages = sortedStages(chain);
        validateStages(stages);
        double finalDemand = randomFinalWeight(request);
        POI sourcePOI = resolveSourcePOI(request.sourcePoiId(), stages.get(0));

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanNo(generatePlanNo());
        plan.setChain(chain);
        plan.setFinalSku(resolveOutputSku(stages.get(stages.size() - 1)));
        plan.setFinalDemandWeight(finalDemand);
        plan.setSourcePOI(sourcePOI);
        plan.setRandomSeed(request.randomSeed());
        plan.setStatus(ProductionPlan.PlanStatus.CALCULATED);
        plan.setUpdatedAt(LocalDateTime.now());
        ProductionPlan savedPlan = planRepository.save(plan);

        List<ProductionPlanNode> nodes = buildPlanNodes(savedPlan, stages, finalDemand);
        List<ProductionPlanNode> savedNodes = nodeRepository.saveAll(nodes);
        savedPlan.getNodes().addAll(savedNodes);

        return mapPlan(savedPlan, savedNodes);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionPlanResponse getPlan(Long planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("生产计划不存在: " + planId));
        List<ProductionPlanNode> nodes =
                nodeRepository.findByPlanIdOrderByStageOrderAsc(plan.getId());
        return mapPlan(plan, nodes);
    }

    @Override
    public ProductionBatchResponse releasePlan(Long planId, String actor) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("生产计划不存在: " + planId));
        if (plan.getStatus() != ProductionPlan.PlanStatus.CALCULATED) {
            throw new IllegalStateException("只有 CALCULATED 状态的生产计划可以发布");
        }

        List<ProductionPlanNode> nodes =
                nodeRepository.findByPlanIdOrderByStageOrderAsc(plan.getId());
        if (nodes.isEmpty()) {
            throw new IllegalStateException("生产计划没有工序节点");
        }

        ProductionBatch batch = new ProductionBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setPlan(plan);
        batch.setChain(plan.getChain());
        batch.setPlannedFinalOutputWeight(nodes.get(nodes.size() - 1).getPlannedOutputWeight());
        batch.setStatus(ProductionBatch.BatchStatus.WAITING_MATERIAL);
        batch.setUpdatedAt(LocalDateTime.now());
        ProductionBatch savedBatch = batchRepository.save(batch);

        List<ProcessingStageExecution> executions = new ArrayList<>();
        for (ProductionPlanNode node : nodes) {
            ProcessingStageExecution execution = new ProcessingStageExecution();
            execution.setBatch(savedBatch);
            execution.setPlanNode(node);
            execution.setStage(node.getStage());
            execution.setStageOrder(node.getStageOrder());
            execution.setProcessingPOI(node.getStage().getProcessingPOI());
            execution.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
            executions.add(execution);
        }
        List<ProcessingStageExecution> savedExecutions = executionRepository.saveAll(executions);
        savedBatch.getExecutions().addAll(savedExecutions);

        ProcessingStageExecution firstExecution = savedExecutions.get(0);
        transportDemandService.createInboundTransport(firstExecution, plan.getSourcePOI(), actor);

        plan.setStatus(ProductionPlan.PlanStatus.RELEASED);
        planRepository.save(plan);

        return mapBatch(savedBatch, savedExecutions);
    }

    private List<ProductionPlanNode> buildPlanNodes(
            ProductionPlan plan,
            List<ProcessingStage> stages,
            double finalDemand
    ) {
        int size = stages.size();
        double[] inputs = new double[size];
        double[] outputs = new double[size];
        double currentOutput = finalDemand;

        for (int i = size - 1; i >= 0; i--) {
            ProcessingStage stage = stages.get(i);
            double ratio = outputRatio(stage);
            outputs[i] = currentOutput;
            inputs[i] = round(currentOutput / ratio);

            if (stage.getMinBatchSize() != null && inputs[i] < stage.getMinBatchSize()) {
                throw new IllegalStateException(
                        "反向计算输入量低于工序最小批量: stage=" + stage.getStageName()
                                + ", required=" + inputs[i]
                                + ", minBatchSize=" + stage.getMinBatchSize()
                );
            }
            if (stage.getMaxCapacityPerCycle() != null && inputs[i] > stage.getMaxCapacityPerCycle()) {
                throw new IllegalStateException(
                        "反向计算输入量超过工序单次产能: stage=" + stage.getStageName()
                                + ", required=" + inputs[i]
                                + ", maxCapacityPerCycle=" + stage.getMaxCapacityPerCycle()
                );
            }
            if (i > 0) {
                currentOutput = inputs[i];
            }
        }

        List<ProductionPlanNode> nodes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ProcessingStage stage = stages.get(i);
            ProductionPlanNode node = new ProductionPlanNode();
            node.setPlan(plan);
            node.setStage(stage);
            node.setStageOrder(stage.getStageOrder());
            node.setInputSku(resolveInputSku(stage));
            node.setOutputSku(resolveOutputSku(stage));
            node.setPlannedInputWeight(round(inputs[i]));
            node.setPlannedOutputWeight(round(outputs[i]));
            node.setStatus(ProductionPlanNode.NodeStatus.PLANNED);
            nodes.add(node);
        }
        return nodes;
    }

    private double randomFinalWeight(CreateProductionPlanRequest request) {
        Double min = request.minFinalWeight();
        Double max = request.maxFinalWeight();
        if (min == null || max == null || min <= 0 || max < min
                || !Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("最终产品需求量范围无效");
        }

        Random random = request.randomSeed() == null
                ? new Random()
                : new Random(request.randomSeed());
        double weight = min + random.nextDouble() * (max - min);

        Double lotSize = request.lotSize();
        if (lotSize != null && lotSize > 0) {
            weight = Math.ceil(weight / lotSize) * lotSize;
        }
        if (weight > max) {
            throw new IllegalStateException("随机需求量按批量取整后超过最大值");
        }
        return round(weight);
    }

    private POI resolveSourcePOI(Long sourcePoiId, ProcessingStage firstStage) {
        if (sourcePoiId != null) {
            return poiRepository.findById(sourcePoiId)
                    .orElseThrow(() -> new IllegalArgumentException("来源 POI 不存在: " + sourcePoiId));
        }

        String inputSku = resolveInputSku(firstStage);
        if (inputSku == null || inputSku.isBlank()) {
            throw new IllegalStateException("第一道工序缺少输入 SKU，无法推断原材料来源 POI");
        }
        return enrollmentRepository.findByGoodsSku(inputSku).stream()
                .filter(enrollment -> enrollment.getPoi() != null)
                .findFirst()
                .map(Enrollment::getPoi)
                .orElseThrow(() -> new IllegalStateException(
                        "无法根据 SKU 找到原材料来源 POI: " + inputSku
                ));
    }

    private List<ProcessingStage> sortedStages(ProcessingChain chain) {
        if (chain.getStages() == null) {
            return List.of();
        }
        return chain.getStages().stream()
                .filter(stage -> stage.getStageOrder() != null)
                .sorted(Comparator.comparing(ProcessingStage::getStageOrder))
                .toList();
    }

    private void validateStages(List<ProcessingStage> stages) {
        if (stages.isEmpty()) {
            throw new IllegalStateException("加工链没有工序");
        }
        Integer previous = null;
        for (ProcessingStage stage : stages) {
            if (stage.getStageOrder() == null || stage.getStageOrder() < 1) {
                throw new IllegalArgumentException("工序顺序必须从 1 开始");
            }
            if (previous != null && stage.getStageOrder() != previous + 1) {
                throw new IllegalArgumentException("工序顺序必须连续");
            }
            if (stage.getProcessingPOI() == null) {
                throw new IllegalArgumentException("工序缺少加工 POI: " + stage.getStageName());
            }
            if (stage.getProcessingTimeMinutes() == null || stage.getProcessingTimeMinutes() < 0) {
                throw new IllegalArgumentException("工序加工时长无效: " + stage.getStageName());
            }
            double ratio = outputRatio(stage);
            if (ratio <= 0 || !Double.isFinite(ratio)) {
                throw new IllegalArgumentException("工序产出率必须大于 0: " + stage.getStageName());
            }
            previous = stage.getStageOrder();
        }
    }

    private double outputRatio(ProcessingStage stage) {
        return stage.getOutputWeightRatio() == null ? 1.0 : stage.getOutputWeightRatio();
    }

    private String resolveInputSku(ProcessingStage stage) {
        if (stage.getInputGoods() != null && stage.getInputGoods().getSku() != null) {
            return stage.getInputGoods().getSku();
        }
        return stage.getInputGoodsSku();
    }

    private String resolveOutputSku(ProcessingStage stage) {
        if (stage.getOutputGoods() != null && stage.getOutputGoods().getSku() != null) {
            return stage.getOutputGoods().getSku();
        }
        return stage.getOutputGoodsSku();
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String generatePlanNo() {
        return "PP-" + LocalDateTime.now().format(PLAN_NO_FORMAT)
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateBatchNo() {
        return "PB-" + LocalDateTime.now().format(PLAN_NO_FORMAT)
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ProductionPlanResponse mapPlan(
            ProductionPlan plan,
            List<ProductionPlanNode> nodes
    ) {
        List<ProductionPlanResponse.NodeResponse> nodeResponses = nodes.stream()
                .map(node -> new ProductionPlanResponse.NodeResponse(
                        node.getId(),
                        node.getStage().getId(),
                        node.getStageOrder(),
                        node.getStage().getStageName(),
                        node.getInputSku(),
                        node.getOutputSku(),
                        node.getPlannedInputWeight(),
                        node.getPlannedOutputWeight(),
                        node.getStatus().name()
                ))
                .toList();

        return new ProductionPlanResponse(
                plan.getId(),
                plan.getPlanNo(),
                plan.getChain().getId(),
                plan.getChain().getChainCode(),
                plan.getChain().getChainName(),
                plan.getFinalSku(),
                plan.getFinalDemandWeight(),
                plan.getSourcePOI() == null ? null : plan.getSourcePOI().getId(),
                plan.getSourcePOI() == null ? null : plan.getSourcePOI().getName(),
                plan.getStatus().name(),
                plan.getRandomSeed(),
                plan.getCreatedAt(),
                nodeResponses
        );
    }

    private ProductionBatchResponse mapBatch(
            ProductionBatch batch,
            List<ProcessingStageExecution> executions
    ) {
        List<ProductionBatchResponse.ExecutionResponse> executionResponses = executions.stream()
                .map(execution -> new ProductionBatchResponse.ExecutionResponse(
                        execution.getId(),
                        execution.getPlanNode().getId(),
                        execution.getStage().getId(),
                        execution.getStageOrder(),
                        execution.getStage().getStageName(),
                        execution.getProcessingPOI().getId(),
                        execution.getProcessingPOI().getName(),
                        execution.getStatus().name(),
                        execution.getActualInputWeight(),
                        execution.getActualOutputWeight(),
                        execution.getProgressPercent(),
                        execution.getStartedAt(),
                        execution.getCompletedAt(),
                        execution.getInboundShipment() == null ? null : execution.getInboundShipment().getId(),
                        execution.getOutboundShipment() == null ? null : execution.getOutboundShipment().getId()
                ))
                .toList();

        return new ProductionBatchResponse(
                batch.getId(),
                batch.getBatchNo(),
                batch.getPlan().getId(),
                batch.getPlan().getPlanNo(),
                batch.getChain().getId(),
                batch.getChain().getChainCode(),
                batch.getStatus().name(),
                batch.getPlannedFinalOutputWeight(),
                batch.getActualFinalOutputWeight(),
                batch.getStartedAt(),
                batch.getCompletedAt(),
                executionResponses
        );
    }
}
