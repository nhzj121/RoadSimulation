package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.ProcessingChainGraphRequest;
import org.example.roadsimulation.dto.ProcessingChainGraphResponse;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageEdge;
import org.example.roadsimulation.entity.ProcessingStageInput;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingStageEdgeRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProcessingStageInputRepository;
import org.example.roadsimulation.repository.ProcessingStageRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.ProcessingChainDefinitionService;
import org.example.roadsimulation.service.ProcessingChainGraphValidator;
import org.example.roadsimulation.service.ProcessingChainSkuValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ProcessingChainDefinitionServiceImpl implements ProcessingChainDefinitionService {

    private final ProcessingChainRepository processingChainRepository;
    private final ProcessingStageRepository processingStageRepository;
    private final ProcessingStageInputRepository stageInputRepository;
    private final ProcessingStageEdgeRepository stageEdgeRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final ProductionPlanNodeRepository productionPlanNodeRepository;
    private final ProcessingStageExecutionRepository processingStageExecutionRepository;
    private final POIRepository poiRepository;
    private final GoodsRepository goodsRepository;

    public ProcessingChainDefinitionServiceImpl(
            ProcessingChainRepository processingChainRepository,
            ProcessingStageRepository processingStageRepository,
            ProcessingStageInputRepository stageInputRepository,
            ProcessingStageEdgeRepository stageEdgeRepository,
            ProductionPlanRepository productionPlanRepository,
            ProductionPlanNodeRepository productionPlanNodeRepository,
            ProcessingStageExecutionRepository processingStageExecutionRepository,
            POIRepository poiRepository,
            GoodsRepository goodsRepository
    ) {
        this.processingChainRepository = processingChainRepository;
        this.processingStageRepository = processingStageRepository;
        this.stageInputRepository = stageInputRepository;
        this.stageEdgeRepository = stageEdgeRepository;
        this.productionPlanRepository = productionPlanRepository;
        this.productionPlanNodeRepository = productionPlanNodeRepository;
        this.processingStageExecutionRepository = processingStageExecutionRepository;
        this.poiRepository = poiRepository;
        this.goodsRepository = goodsRepository;
    }

    @Override
    public ProcessingChain createChain(ProcessingChain chain) {
        requireChainDefinition(chain);
        if (processingChainRepository.existsByChainCode(chain.getChainCode())) {
            throw new IllegalArgumentException("加工链编码已存在：" + chain.getChainCode());
        }

        if (chain.getStages() != null) {
            chain.getStages().forEach(stage -> {
                validateStage(stage, null);
                stage.setProcessingChain(chain);
                if (stage.getInputs() != null) {
                    stage.getInputs().forEach(input -> input.setStage(stage));
                }
            });
            ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges());
        }
        return processingChainRepository.save(chain);
    }

    @Override
    public ProcessingChainGraphResponse createGraph(ProcessingChainGraphRequest request) {
        requireGraphRequest(request);
        if (processingChainRepository.existsByChainCode(request.chainCode())) {
            throw new IllegalArgumentException("加工链编码已存在：" + request.chainCode());
        }

        ProcessingChain chain = new ProcessingChain();
        chain.setChainCode(request.chainCode());
        chain.setChainName(request.chainName());
        chain.setDescription(request.description());

        Map<String, ProcessingStage> stagesByKey = new HashMap<>();
        Map<String, Map<String, ProcessingStageInput>> inputsByStageAndKey = new HashMap<>();
        for (ProcessingChainGraphRequest.StageRequest stageRequest : request.stages()) {
            ProcessingStage stage = createStageEntity(chain, stageRequest);
            chain.getStages().add(stage);
            stagesByKey.put(stageRequest.stageKey(), stage);

            Map<String, ProcessingStageInput> inputsByKey = new HashMap<>();
            for (ProcessingStageInput input : stage.getInputs()) {
                inputsByKey.put(input.getInputKey(), input);
            }
            inputsByStageAndKey.put(stageRequest.stageKey(), inputsByKey);
        }

        for (ProcessingChainGraphRequest.EdgeRequest edgeRequest : request.edges()) {
            ProcessingStage fromStage = stagesByKey.get(edgeRequest.fromStageKey());
            ProcessingStage toStage = stagesByKey.get(edgeRequest.toStageKey());
            Map<String, ProcessingStageInput> inputs = inputsByStageAndKey.get(edgeRequest.toStageKey());
            if (fromStage == null || toStage == null || inputs == null) {
                throw new IllegalArgumentException("加工链边引用了不存在的工序或输入");
            }
            ProcessingStageInput targetInput = inputs.get(edgeRequest.toInputKey());
            if (targetInput == null) {
                throw new IllegalArgumentException("工序输入不存在: " + edgeRequest.toInputKey());
            }
            chain.getEdges().add(new ProcessingStageEdge(chain, fromStage, toStage, targetInput));
        }

        ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges());
        ProcessingChain saved = processingChainRepository.save(chain);
        return mapGraph(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingChainGraphResponse getGraph(Long chainId) {
        return mapGraph(requireExistingChain(chainId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessingChain> getChainById(Long id) {
        return processingChainRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessingChain> getAllChains() {
        return processingChainRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessingStage> getStages(Long chainId) {
        requireExistingChain(chainId);
        return processingStageRepository.findByProcessingChainIdOrderByStageOrderAsc(chainId);
    }

    @Override
    public ProcessingChain updateChainStatus(Long id, ProcessingChain.ChainStatus status) {
        ProcessingChain chain = requireExistingChain(id);
        if (status == null) {
            throw new IllegalArgumentException("加工链状态不能为空");
        }
        chain.setStatus(status);
        return processingChainRepository.save(chain);
    }

    @Override
    public void deleteChain(Long id) {
        requireExistingChain(id);
        if (productionPlanRepository.existsByChainId(id)) {
            throw new IllegalStateException("加工链已有生产计划，不能删除");
        }
        processingChainRepository.deleteById(id);
    }

    @Override
    public ProcessingStage createStage(Long chainId, ProcessingStage stage) {
        ProcessingChain chain = requireExistingChain(chainId);
        validateStage(stage, null);
        if (hasStageOrderConflict(chain, stage.getStageOrder(), null)) {
            throw new IllegalArgumentException("工序顺序已存在：" + stage.getStageOrder());
        }

        stage.setProcessingChain(chain);
        if (stage.getInputs() != null) {
            stage.getInputs().forEach(input -> input.setStage(stage));
        }
        List<ProcessingStage> candidateStages = new ArrayList<>(chain.getStages());
        candidateStages.add(stage);
        ProcessingChainGraphValidator.validate(candidateStages, chain.getEdges());
        return processingStageRepository.save(stage);
    }

    @Override
    public ProcessingStage updateStage(Long stageId, ProcessingStage details) {
        ProcessingStage stage = processingStageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("工序不存在：" + stageId));
        if (details == null) {
            throw new IllegalArgumentException("工序更新内容不能为空");
        }

        if (details.getStageOrder() != null) {
            if (details.getStageOrder() <= 0) {
                throw new IllegalArgumentException("工序顺序必须大于 0");
            }
            if (hasStageOrderConflict(stage.getProcessingChain(), details.getStageOrder(), stageId)) {
                throw new IllegalArgumentException("工序顺序已存在：" + details.getStageOrder());
            }
            stage.setStageOrder(details.getStageOrder());
        }
        if (details.getStageKey() != null) stage.setStageKey(details.getStageKey());
        if (details.getStageName() != null) stage.setStageName(details.getStageName());
        if (details.getDescription() != null) stage.setDescription(details.getDescription());
        if (details.getProcessingPOI() != null) stage.setProcessingPOI(details.getProcessingPOI());
        if (details.getInputGoods() != null) stage.setInputGoods(details.getInputGoods());
        if (details.getInputGoodsSku() != null) stage.setInputGoodsSku(details.getInputGoodsSku());
        if (details.getInputWeightRatio() != null) {
            requirePositiveRatio(details.getInputWeightRatio(), "输入产出系数");
            stage.setInputWeightRatio(details.getInputWeightRatio());
        }
        if (details.getOutputGoods() != null) stage.setOutputGoods(details.getOutputGoods());
        if (details.getOutputGoodsSku() != null) stage.setOutputGoodsSku(details.getOutputGoodsSku());
        if (details.getOutputWeightRatio() != null) {
            requirePositiveRatio(details.getOutputWeightRatio(), "产出率");
            stage.setOutputWeightRatio(details.getOutputWeightRatio());
        }
        if (details.getProcessingTimeMinutes() != null) {
            if (details.getProcessingTimeMinutes() <= 0) {
                throw new IllegalArgumentException("加工时间必须大于 0 分钟");
            }
            stage.setProcessingTimeMinutes(details.getProcessingTimeMinutes());
        }
        if (details.getMaxCapacityPerCycle() != null) stage.setMaxCapacityPerCycle(details.getMaxCapacityPerCycle());
        if (details.getMinBatchSize() != null) stage.setMinBatchSize(details.getMinBatchSize());
        if (details.getInputs() != null) {
            stage.getInputs().clear();
            details.getInputs().forEach(input -> {
                input.setStage(stage);
                stage.getInputs().add(input);
            });
        }

        ProcessingChain chain = stage.getProcessingChain();
        ProcessingChainGraphValidator.validate(chain.getStages(), chain.getEdges());
        return processingStageRepository.save(stage);
    }

    @Override
    public void deleteStage(Long stageId) {
        if (!processingStageRepository.existsById(stageId)) {
            throw new IllegalArgumentException("工序不存在：" + stageId);
        }
        if (productionPlanNodeRepository.existsByStageId(stageId)) {
            throw new IllegalStateException("工序已有生产计划节点，不能删除");
        }
        if (processingStageExecutionRepository.existsByStageId(stageId)) {
            throw new IllegalStateException("工序已有执行记录，不能删除");
        }
        if (stageEdgeRepository.existsByFromStageId(stageId)
                || stageEdgeRepository.existsByToStageId(stageId)) {
            throw new IllegalStateException("工序已被加工链边引用，不能删除");
        }
        processingStageRepository.deleteById(stageId);
    }

    private ProcessingStage createStageEntity(
            ProcessingChain chain,
            ProcessingChainGraphRequest.StageRequest request
    ) {
        POI poi = poiRepository.findById(request.processingPoiId())
                .orElseThrow(() -> new IllegalArgumentException("加工 POI 不存在: " + request.processingPoiId()));
        ProcessingStage stage = new ProcessingStage();
        stage.setProcessingChain(chain);
        stage.setStageOrder(request.stageOrder());
        stage.setStageKey(request.stageKey());
        stage.setStageName(request.stageName());
        stage.setDescription(request.description());
        stage.setProcessingPOI(poi);
        stage.setOutputGoodsSku(request.outputGoodsSku());
        stage.setOutputWeightRatio(request.outputWeightRatio() == null ? 1.0 : request.outputWeightRatio());
        stage.setProcessingTimeMinutes(request.processingTimeMinutes());
        stage.setMinBatchSize(request.minBatchSize());
        stage.setMaxCapacityPerCycle(request.maxCapacityPerCycle());

        if (request.inputs() == null || request.inputs().isEmpty()) {
            throw new IllegalArgumentException("图加工链工序必须显式定义输入: " + request.stageName());
        }
        for (ProcessingChainGraphRequest.InputRequest inputRequest : request.inputs()) {
            ProcessingStageInput input = new ProcessingStageInput(
                    stage,
                    inputRequest.inputKey(),
                    inputRequest.sku(),
                    inputRequest.inputShare()
            );
            goodsRepository.findBySku(inputRequest.sku()).ifPresent(input::setGoods);
            stage.getInputs().add(input);
        }
        return stage;
    }

    private void requireGraphRequest(ProcessingChainGraphRequest request) {
        if (request == null
                || request.chainCode() == null || request.chainCode().isBlank()
                || request.chainName() == null || request.chainName().isBlank()) {
            throw new IllegalArgumentException("加工链编码和名称不能为空");
        }
        if (request.stages() == null || request.stages().isEmpty()) {
            throw new IllegalArgumentException("加工链至少需要一道工序");
        }
        for (ProcessingChainGraphRequest.StageRequest stage : request.stages()) {
            if (stage == null || stage.stageKey() == null || stage.stageKey().isBlank()) {
                throw new IllegalArgumentException("图加工链工序标识不能为空");
            }
        }
        if (request.edges() == null || request.edges().isEmpty()) {
            throw new IllegalArgumentException("图加工链至少需要一条边");
        }
    }

    private ProcessingChain requireExistingChain(Long chainId) {
        if (chainId == null) {
            throw new IllegalArgumentException("加工链 ID 不能为空");
        }
        return processingChainRepository.findById(chainId)
                .orElseThrow(() -> new IllegalArgumentException("加工链不存在：" + chainId));
    }

    private void requireChainDefinition(ProcessingChain chain) {
        if (chain == null
                || chain.getChainCode() == null || chain.getChainCode().isBlank()
                || chain.getChainName() == null || chain.getChainName().isBlank()) {
            throw new IllegalArgumentException("加工链编码和名称不能为空");
        }
    }

    private void validateStage(ProcessingStage stage, Long excludedStageId) {
        if (stage == null) {
            throw new IllegalArgumentException("工序不能为空");
        }
        if (stage.getStageOrder() == null || stage.getStageOrder() <= 0) {
            throw new IllegalArgumentException("工序顺序必须大于 0");
        }
        if (stage.getStageName() == null || stage.getStageName().isBlank()) {
            throw new IllegalArgumentException("工序名称不能为空");
        }
        if (stage.getProcessingPOI() == null) {
            throw new IllegalArgumentException("加工 POI 不能为空");
        }
        if (stage.getProcessingTimeMinutes() == null || stage.getProcessingTimeMinutes() <= 0) {
            throw new IllegalArgumentException("加工时间必须大于 0 分钟");
        }
        if (stage.getOutputWeightRatio() != null) {
            requirePositiveRatio(stage.getOutputWeightRatio(), "产出率");
        }
        if (stage.getInputWeightRatio() != null) {
            requirePositiveRatio(stage.getInputWeightRatio(), "输入产出系数");
        }
        if (stage.getInputs() == null || stage.getInputs().isEmpty()) {
            ProcessingChainSkuValidator.validateStage(stage);
        }
    }

    private boolean hasStageOrderConflict(ProcessingChain chain, Integer stageOrder, Long excludedStageId) {
        return chain.getStages().stream()
                .filter(Objects::nonNull)
                .anyMatch(stage -> !Objects.equals(stage.getId(), excludedStageId)
                        && Objects.equals(stage.getStageOrder(), stageOrder));
    }

    private void requirePositiveRatio(Double value, String name) {
        if (value == null || value <= 0 || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + "必须大于 0");
        }
    }

    private ProcessingChainGraphResponse mapGraph(ProcessingChain chain) {
        List<ProcessingChainGraphResponse.StageResponse> stages = chain.getStages().stream()
                .map(stage -> new ProcessingChainGraphResponse.StageResponse(
                        stage.getId(),
                        stage.getStageOrder(),
                        stage.getStageKey(),
                        stage.getStageName(),
                        stage.getDescription(),
                        stage.getProcessingPOI().getId(),
                        stage.getProcessingPOI().getName(),
                        ProcessingChainSkuValidator.resolveOutputSku(stage),
                        stage.getOutputWeightRatio(),
                        stage.getProcessingTimeMinutes(),
                        stage.getMinBatchSize(),
                        stage.getMaxCapacityPerCycle(),
                        stage.getInputs().stream()
                                .map(input -> new ProcessingChainGraphResponse.InputResponse(
                                        input.getId(),
                                        input.getInputKey(),
                                        input.getSku(),
                                        input.getInputShare()
                                ))
                                .toList()
                ))
                .toList();

        List<ProcessingChainGraphResponse.EdgeResponse> edges = chain.getEdges().stream()
                .map(edge -> new ProcessingChainGraphResponse.EdgeResponse(
                        edge.getId(),
                        edge.getFromStage().getId(),
                        edge.getToStage().getId(),
                        edge.getToStageInput().getId(),
                        edge.getFromStage().getStageKey(),
                        edge.getToStage().getStageKey(),
                        edge.getToStageInput().getInputKey()
                ))
                .toList();

        return new ProcessingChainGraphResponse(
                chain.getId(),
                chain.getChainCode(),
                chain.getChainName(),
                chain.getStatus().name(),
                chain.getDescription(),
                stages,
                edges
        );
    }
}
