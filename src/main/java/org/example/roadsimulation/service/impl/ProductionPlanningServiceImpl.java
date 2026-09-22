package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanFlowRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.ProcessingChainGraphValidator;
import org.example.roadsimulation.service.ProcessingChainSkuValidator;
import org.example.roadsimulation.service.ProductionPlanningService;
import org.example.roadsimulation.service.ProductionPlanPoiSelector;
import org.example.roadsimulation.service.TransportDemandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Demand-driven production planning service for linear and Y-shape DAG chains.
 */
@Service
@Transactional
public class ProductionPlanningServiceImpl implements ProductionPlanningService {

    private static final DateTimeFormatter PLAN_NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ProcessingChainRepository chainRepository;
    private final ProductionPlanRepository planRepository;
    private final ProductionPlanNodeRepository nodeRepository;
    private final ProductionPlanFlowRepository planFlowRepository;
    private final ProductionBatchRepository batchRepository;
    private final ProcessingStageExecutionRepository executionRepository;
    private final ProcessingExecutionFlowRepository executionFlowRepository;
    private final TransportDemandService transportDemandService;
    private final ProductionPlanPoiSelector poiSelector;

    public ProductionPlanningServiceImpl(
            ProcessingChainRepository chainRepository,
            ProductionPlanRepository planRepository,
            ProductionPlanNodeRepository nodeRepository,
            ProductionPlanFlowRepository planFlowRepository,
            ProductionBatchRepository batchRepository,
            ProcessingStageExecutionRepository executionRepository,
            ProcessingExecutionFlowRepository executionFlowRepository,
            TransportDemandService transportDemandService,
            ProductionPlanPoiSelector poiSelector
    ) {
        this.chainRepository = chainRepository;
        this.planRepository = planRepository;
        this.nodeRepository = nodeRepository;
        this.planFlowRepository = planFlowRepository;
        this.batchRepository = batchRepository;
        this.executionRepository = executionRepository;
        this.executionFlowRepository = executionFlowRepository;
        this.transportDemandService = transportDemandService;
        this.poiSelector = poiSelector;
    }

    @Override
    public ProductionPlanResponse createRandomPlan(CreateProductionPlanRequest request) {
        return createPlan(request, null, null);
    }

    @Override
    public ProductionPlanResponse createAutomaticPlan(
            CreateProductionPlanRequest request,
            String simulationRunId,
            int generationRound
    ) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId is required");
        }
        if (generationRound < 0) {
            throw new IllegalArgumentException("generationRound must not be negative");
        }
        return createPlan(request, simulationRunId, generationRound);
    }

    private ProductionPlanResponse createPlan(
            CreateProductionPlanRequest request,
            String simulationRunId,
            Integer generationRound
    ) {
        if (request == null || request.chainId() == null) {
            throw new IllegalArgumentException("chainId is required");
        }

        ProcessingChain chain = chainRepository.findById(request.chainId())
                .orElseThrow(() -> new IllegalArgumentException("加工链不存在: " + request.chainId()));
        requireActiveChain(chain);

        List<ProcessingStage> stages = sortedStages(chain);
        List<ProcessingChainGraphValidator.EdgeSpec> edges =
                ProcessingChainGraphValidator.normalizedEdges(stages, chain.getEdges());
        ProcessingChainGraphValidator.validate(stages, chain.getEdges());

        List<ProcessingStage> topologicalOrder =
                ProcessingChainGraphValidator.topologicalOrder(stages, edges);
        ProcessingStage sink = findSink(stages, edges);
        double finalDemand = randomFinalWeight(request);

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanNo(generatePlanNo());
        plan.setChain(chain);
        plan.setFinalSku(resolveFinalDemandSku(sink, edges));
        plan.setFinalDemandWeight(finalDemand);
        plan.setRandomSeed(request.randomSeed());
        plan.setSimulationRunId(simulationRunId);
        plan.setGenerationRound(generationRound);
        plan.setStatus(ProductionPlan.PlanStatus.CALCULATED);
        plan.setUpdatedAt(LocalDateTime.now());
        ProductionPlan savedPlan = planRepository.save(plan);

        PlanCalculation calculation = calculatePlan(stages, edges, topologicalOrder, sink, finalDemand);
        Map<ProcessingStage, POI> selectedPois = poiSelector.select(
                stages, edges, sink, request.randomSeed());
        List<ProductionPlanNode> nodes = createPlanNodes(
                savedPlan, stages, edges, sink, calculation, selectedPois);
        List<ProductionPlanFlow> flows = createPlanFlows(
                savedPlan,
                edges,
                calculation,
                nodes
        );

        savedPlan.getNodes().addAll(nodes);
        savedPlan.getFlows().addAll(flows);
        savedPlan.setSourcePOI(firstSourcePOI(nodes));
        return mapPlan(savedPlan, nodes, flows);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionPlanResponse getPlan(Long planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("生产计划不存在: " + planId));
        List<ProductionPlanNode> nodes =
                nodeRepository.findByPlanIdOrderByStageOrderAsc(plan.getId());
        List<ProductionPlanFlow> flows = planFlowRepository.findByPlanId(plan.getId());
        return mapPlan(plan, nodes, flows);
    }

    @Override
    public ProductionBatchResponse releasePlan(Long planId, String actor) {
        if (planId == null) {
            throw new IllegalArgumentException("生产计划 ID 不能为空");
        }
        int updated = planRepository.updateStatusIfCurrent(
                planId,
                ProductionPlan.PlanStatus.CALCULATED,
                ProductionPlan.PlanStatus.RELEASED
        );
        if (updated == 0) {
            throw new IllegalStateException("只有 CALCULATED 状态且未发布过的生产计划可以发布");
        }

        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("生产计划不存在: " + planId));
        requireActiveChain(plan.getChain());
        if (batchRepository.existsByPlanId(planId)) {
            throw new IllegalStateException("生产计划已存在批次，不能重复发布");
        }

        List<ProductionPlanNode> nodes =
                nodeRepository.findByPlanIdOrderByStageOrderAsc(planId);
        List<ProductionPlanFlow> planFlows = planFlowRepository.findByPlanId(planId);
        if (nodes.isEmpty() || planFlows.isEmpty()) {
            throw new IllegalStateException("生产计划缺少工序节点或物料流");
        }

        ProductionBatch batch = new ProductionBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setPlan(plan);
        batch.setChain(plan.getChain());
        batch.setPlannedFinalOutputWeight(plan.getFinalDemandWeight());
        batch.setStatus(ProductionBatch.BatchStatus.WAITING_MATERIAL);
        batch.setUpdatedAt(LocalDateTime.now());
        ProductionBatch savedBatch = batchRepository.save(batch);

        Map<Long, ProcessingStageExecution> executionsByNode = new HashMap<>();
        List<ProcessingStageExecution> executions = new ArrayList<>();
        for (ProductionPlanNode node : nodes) {
            ProcessingStageExecution execution = new ProcessingStageExecution();
            execution.setBatch(savedBatch);
            execution.setPlanNode(node);
            execution.setStage(node.getStage());
            execution.setStageOrder(node.getStageOrder());
            execution.setProcessingPOI(node.getSelectedPOI() == null
                    ? node.getStage().getProcessingPOI()
                    : node.getSelectedPOI());
            if (node.getNodeRole() == ProductionPlanNode.NodeRole.SOURCE) {
                execution.setStatus(ProcessingStageExecution.ExecutionStatus.COMPLETED);
                execution.setActualInputWeight(0.0);
                execution.setActualOutputWeight(node.getPlannedOutputWeight());
                execution.setProgressPercent(100);
                node.setStatus(ProductionPlanNode.NodeStatus.COMPLETED);
            } else {
                execution.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
            }
            ProcessingStageExecution saved = executionRepository.save(execution);
            executions.add(saved);
            executionsByNode.put(node.getId(), saved);
        }
        savedBatch.getExecutions().addAll(executions);

        List<ProcessingExecutionFlow> executionFlows = new ArrayList<>();
        for (ProductionPlanFlow planFlow : planFlows) {
            ProcessingExecutionFlow flow = new ProcessingExecutionFlow();
            flow.setBatch(savedBatch);
            flow.setPlanFlow(planFlow);
            if (planFlow.getFromNode() == null) {
                throw new IllegalStateException("R2 生产计划不允许外部入厂物料流");
            }
            ProcessingStageExecution fromExecution = executionsByNode.get(planFlow.getFromNode().getId());
            ProcessingStageExecution toExecution = executionsByNode.get(planFlow.getToNode().getId());
            if (fromExecution == null || toExecution == null) {
                throw new IllegalStateException("生产计划物料流引用了不存在的执行节点: flowId="
                        + planFlow.getId());
            }
            flow.setFromExecution(fromExecution);
            flow.setToExecution(toExecution);
            flow.setInputKey(planFlow.getInputKey());
            flow.setSku(planFlow.getSku());
            flow.setPlannedWeight(planFlow.getPlannedWeight());
            flow.setStatus(ProcessingExecutionFlow.FlowStatus.PLANNED);
            executionFlows.add(executionFlowRepository.save(flow));
        }
        savedBatch.getFlows().addAll(executionFlows);

        int initialTransportCount = 0;
        for (ProcessingExecutionFlow flow : executionFlows) {
            if (flow.getFromExecution().getPlanNode().getNodeRole()
                    == ProductionPlanNode.NodeRole.SOURCE) {
                flow.setActualWeight(flow.getPlannedWeight());
                executionFlowRepository.save(flow);
                transportDemandService.createTransport(flow, null, actor);
                initialTransportCount++;
            }
        }
        if (initialTransportCount == 0) {
            throw new IllegalStateException("生产计划没有可发布的起点运输流");
        }

        plan.setUpdatedAt(LocalDateTime.now());
        return mapBatch(savedBatch, executions, executionFlows);
    }

    private PlanCalculation calculatePlan(
            List<ProcessingStage> stages,
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            List<ProcessingStage> topologicalOrder,
            ProcessingStage sink,
            double finalDemand
    ) {
        Map<ProcessingStage, Double> outputs = new IdentityHashMap<>();
        Map<ProcessingStage, Map<String, Double>> inputWeights = new IdentityHashMap<>();
        outputs.put(sink, finalDemand);

        List<ProcessingChainGraphValidator.InputSpec> sinkInputs =
                ProcessingChainGraphValidator.inputs(sink);
        if (sinkInputs.size() != 1) {
            throw new IllegalArgumentException("末端接收节点必须且只能接收一种最终阶段货物");
        }
        ProcessingChainGraphValidator.InputSpec sinkInput = sinkInputs.get(0);
        ProcessingChainGraphValidator.EdgeSpec finalEdge = requireIncomingEdge(
                edges, sink, sinkInput);
        inputWeights.put(sink, Map.of(sinkInput.inputKey(), finalDemand));
        outputs.merge(finalEdge.fromStage(), finalDemand, Double::sum);

        for (int i = topologicalOrder.size() - 1; i >= 0; i--) {
            ProcessingStage stage = topologicalOrder.get(i);
            if (stage == sink) {
                continue;
            }
            double output = outputs.getOrDefault(stage, 0.0);
            if (output <= 0 || !Double.isFinite(output)) {
                throw new IllegalStateException("工序需求量无效: " + stage.getStageName());
            }

            if (isSourceStage(stage, edges)) {
                validateCapacity(stage, output);
                inputWeights.put(stage, Map.of());
                continue;
            }

            double ratio = outputRatio(stage);
            double totalInput = round(output / ratio);
            validateCapacity(stage, totalInput);

            Map<String, Double> weights = new LinkedHashMap<>();
            for (ProcessingChainGraphValidator.InputSpec input :
                    ProcessingChainGraphValidator.inputs(stage)) {
                double weight = round(totalInput * input.inputShare());
                if (weight <= 0 || !Double.isFinite(weight)) {
                    throw new IllegalStateException("工序输入量无效: " + stage.getStageName());
                }
                weights.put(input.inputKey(), weight);
                ProcessingChainGraphValidator.EdgeSpec incoming =
                        requireIncomingEdge(edges, stage, input);
                outputs.merge(incoming.fromStage(), weight, Double::sum);
            }
            inputWeights.put(stage, weights);
        }
        return new PlanCalculation(outputs, inputWeights);
    }

    private List<ProductionPlanNode> createPlanNodes(
            ProductionPlan plan,
            List<ProcessingStage> stages,
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            ProcessingStage sink,
            PlanCalculation calculation,
            Map<ProcessingStage, POI> selectedPois
    ) {
        List<ProductionPlanNode> nodes = new ArrayList<>();
        for (ProcessingStage stage : stages) {
            List<ProcessingChainGraphValidator.InputSpec> inputs =
                    ProcessingChainGraphValidator.inputs(stage);
            boolean source = isSourceStage(stage, edges);
            boolean terminal = stage == sink;
            double plannedInput = source ? 0.0 : calculation.inputWeights()
                    .get(stage)
                    .values()
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            ProductionPlanNode node = new ProductionPlanNode();
            node.setPlan(plan);
            node.setStage(stage);
            POI selectedPOI = selectedPois.get(stage);
            if (selectedPOI == null) {
                throw new IllegalStateException("计划节点缺少 POI 快照: " + stage.getStageName());
            }
            node.setSelectedPOI(selectedPOI);
            node.setStageOrder(stage.getStageOrder());
            node.setInputSku(source ? null : inputs.size() == 1
                    ? inputs.get(0).sku()
                    : String.join(",", inputs.stream().map(
                            ProcessingChainGraphValidator.InputSpec::sku).toList()));
            node.setOutputSku(terminal
                    ? inputs.get(0).sku()
                    : ProcessingChainSkuValidator.resolveOutputSku(stage));
            node.setPlannedInputWeight(round(plannedInput));
            node.setPlannedOutputWeight(round(calculation.outputs().getOrDefault(stage, 0.0)));
            node.setNodeRole(source
                    ? ProductionPlanNode.NodeRole.SOURCE
                    : terminal
                    ? ProductionPlanNode.NodeRole.SINK
                    : ProductionPlanNode.NodeRole.PROCESSING);
            node.setStatus(ProductionPlanNode.NodeStatus.PLANNED);
            nodes.add(node);
        }
        return nodeRepository.saveAll(nodes);
    }

    private List<ProductionPlanFlow> createPlanFlows(
            ProductionPlan plan,
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            PlanCalculation calculation,
            List<ProductionPlanNode> nodes
    ) {
        Map<ProcessingStage, ProductionPlanNode> nodesByStage = new IdentityHashMap<>();
        nodes.forEach(node -> nodesByStage.put(node.getStage(), node));

        List<ProductionPlanFlow> flows = new ArrayList<>();
        for (ProcessingChainGraphValidator.EdgeSpec edge : edges) {
            ProcessingChainGraphValidator.InputSpec input = edge.input();
            ProductionPlanFlow flow = new ProductionPlanFlow();
            flow.setPlan(plan);
            flow.setFromNode(nodesByStage.get(edge.fromStage()));
            flow.setToNode(nodesByStage.get(edge.toStage()));
            flow.setStageInput(input.persistentInput());
            flow.setInputKey(input.inputKey());
            flow.setSku(input.sku());
            flow.setPlannedWeight(calculation.inputWeights()
                    .get(edge.toStage())
                    .get(input.inputKey()));
            flows.add(flow);
        }
        return planFlowRepository.saveAll(flows);
    }

    private void requireActiveChain(ProcessingChain chain) {
        if (chain.getStatus() != ProcessingChain.ChainStatus.ACTIVE) {
            throw new IllegalStateException("加工链未激活: " + chain.getChainName());
        }
    }

    private List<ProcessingStage> sortedStages(ProcessingChain chain) {
        if (chain.getStages() == null) {
            return List.of();
        }
        return chain.getStages().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProcessingStage::getStageOrder))
                .toList();
    }

    private ProcessingStage findSink(
            List<ProcessingStage> stages,
            List<ProcessingChainGraphValidator.EdgeSpec> edges
    ) {
        List<ProcessingStage> sinks = stages.stream()
                .filter(stage -> edges.stream().noneMatch(edge -> edge.fromStage() == stage))
                .toList();
        if (sinks.size() != 1) {
            throw new IllegalArgumentException("Y 型加工链必须且只能有一个最终工序");
        }
        return sinks.get(0);
    }

    private String resolveFinalDemandSku(
            ProcessingStage sink,
            List<ProcessingChainGraphValidator.EdgeSpec> edges
    ) {
        List<ProcessingChainGraphValidator.InputSpec> inputs =
                ProcessingChainGraphValidator.inputs(sink);
        if (inputs.size() != 1) {
            throw new IllegalArgumentException("末端接收节点必须且只能接收一种最终阶段货物");
        }
        requireIncomingEdge(edges, sink, inputs.get(0));
        return inputs.get(0).sku();
    }

    private ProcessingChainGraphValidator.EdgeSpec requireIncomingEdge(
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            ProcessingStage stage,
            ProcessingChainGraphValidator.InputSpec input
    ) {
        return edges.stream()
                .filter(edge -> edge.toStage() == stage
                        && edge.input().inputKey().equals(input.inputKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "非起点节点输入缺少上游阶段: stage=" + stage.getStageName()
                                + ", input=" + input.inputKey()
                ));
    }

    private boolean isSourceStage(
            ProcessingStage stage,
            List<ProcessingChainGraphValidator.EdgeSpec> edges
    ) {
        return edges.stream().noneMatch(edge -> edge.toStage() == stage);
    }

    private void validateCapacity(ProcessingStage stage, double inputWeight) {
        if (stage.getMinBatchSize() != null && inputWeight < stage.getMinBatchSize()) {
            throw new IllegalStateException(
                    "反向计算输入量低于工序最小批量: stage=" + stage.getStageName()
                            + ", required=" + inputWeight
                            + ", minBatchSize=" + stage.getMinBatchSize()
            );
        }
        if (stage.getMaxCapacityPerCycle() != null && inputWeight > stage.getMaxCapacityPerCycle()) {
            throw new IllegalStateException(
                    "反向计算输入量超过工序单次产能: stage=" + stage.getStageName()
                            + ", required=" + inputWeight
                            + ", maxCapacityPerCycle=" + stage.getMaxCapacityPerCycle()
            );
        }
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

    private double outputRatio(ProcessingStage stage) {
        return stage.getOutputWeightRatio() == null ? 1.0 : stage.getOutputWeightRatio();
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

    private POI firstSourcePOI(List<ProductionPlanNode> nodes) {
        return nodes.stream()
                .filter(node -> node.getNodeRole() == ProductionPlanNode.NodeRole.SOURCE)
                .map(ProductionPlanNode::getSelectedPOI)
                .findFirst()
                .orElse(null);
    }

    private ProductionPlanResponse mapPlan(
            ProductionPlan plan,
            List<ProductionPlanNode> nodes,
            List<ProductionPlanFlow> flows
    ) {
        List<ProductionPlanResponse.NodeResponse> nodeResponses = nodes.stream()
                .map(node -> new ProductionPlanResponse.NodeResponse(
                        node.getId(),
                        node.getStage().getId(),
                        node.getStageOrder(),
                        node.getStage().getStageName(),
                        node.getSelectedPOI() == null ? null : node.getSelectedPOI().getId(),
                        node.getSelectedPOI() == null ? null : node.getSelectedPOI().getName(),
                        node.getInputSku(),
                        node.getOutputSku(),
                        node.getPlannedInputWeight(),
                        node.getPlannedOutputWeight(),
                        node.getNodeRole().name(),
                        node.getStatus().name()
                ))
                .toList();

        List<ProductionPlanResponse.FlowResponse> flowResponses = flows.stream()
                .map(flow -> new ProductionPlanResponse.FlowResponse(
                        flow.getId(),
                        flow.getFromNode() == null ? null : flow.getFromNode().getId(),
                        flow.getToNode().getId(),
                        flow.getInputKey(),
                        flow.getSku(),
                        flow.getPlannedWeight(),
                        flow.getSourcePOI() == null ? null : flow.getSourcePOI().getId(),
                        flow.getSourcePOI() == null ? null : flow.getSourcePOI().getName()
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
                plan.getSimulationRunId(),
                plan.getGenerationRound(),
                plan.getCreatedAt(),
                nodeResponses,
                flowResponses
        );
    }

    private ProductionBatchResponse mapBatch(
            ProductionBatch batch,
            List<ProcessingStageExecution> executions,
            List<ProcessingExecutionFlow> flows
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
                        flows.stream()
                                .filter(flow -> execution.getId().equals(flow.getToExecution().getId()))
                                .map(flow -> flow.getShipment() == null ? null : flow.getShipment().getId())
                                .filter(Objects::nonNull)
                                .findFirst().orElse(null),
                        flows.stream()
                                .filter(flow -> flow.getFromExecution() != null
                                        && execution.getId().equals(flow.getFromExecution().getId()))
                                .map(flow -> flow.getShipment() == null ? null : flow.getShipment().getId())
                                .filter(Objects::nonNull)
                                .findFirst().orElse(null)
                ))
                .toList();

        List<ProductionBatchResponse.FlowResponse> flowResponses = flows.stream()
                .map(flow -> new ProductionBatchResponse.FlowResponse(
                        flow.getId(),
                        flow.getPlanFlow().getId(),
                        flow.getFromExecution() == null ? null : flow.getFromExecution().getId(),
                        flow.getToExecution().getId(),
                        flow.getInputKey(),
                        flow.getSku(),
                        flow.getPlannedWeight(),
                        flow.getActualWeight(),
                        flow.getShipment() == null ? null : flow.getShipment().getId(),
                        flow.getStatus().name()
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
                executionResponses,
                flowResponses
        );
    }

    private record PlanCalculation(
            Map<ProcessingStage, Double> outputs,
            Map<ProcessingStage, Map<String, Double>> inputWeights
    ) {}
}
