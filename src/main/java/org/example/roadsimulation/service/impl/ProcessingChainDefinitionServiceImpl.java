package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProcessingStageRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.ProcessingChainDefinitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ProcessingChainDefinitionServiceImpl implements ProcessingChainDefinitionService {

    private final ProcessingChainRepository processingChainRepository;
    private final ProcessingStageRepository processingStageRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final ProductionPlanNodeRepository productionPlanNodeRepository;
    private final ProcessingStageExecutionRepository processingStageExecutionRepository;

    public ProcessingChainDefinitionServiceImpl(
            ProcessingChainRepository processingChainRepository,
            ProcessingStageRepository processingStageRepository,
            ProductionPlanRepository productionPlanRepository,
            ProductionPlanNodeRepository productionPlanNodeRepository,
            ProcessingStageExecutionRepository processingStageExecutionRepository
    ) {
        this.processingChainRepository = processingChainRepository;
        this.processingStageRepository = processingStageRepository;
        this.productionPlanRepository = productionPlanRepository;
        this.productionPlanNodeRepository = productionPlanNodeRepository;
        this.processingStageExecutionRepository = processingStageExecutionRepository;
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
            });
        }
        return processingChainRepository.save(chain);
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
        processingStageRepository.deleteById(stageId);
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
    }

    private boolean hasStageOrderConflict(ProcessingChain chain, Integer stageOrder, Long excludedStageId) {
        return chain.getStages().stream()
                .filter(Objects::nonNull)
                .anyMatch(stage -> !Objects.equals(stage.getId(), excludedStageId)
                        && Objects.equals(stage.getStageOrder(), stageOrder));
    }

    private void requirePositiveRatio(Double value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + "必须大于 0");
        }
    }
}
