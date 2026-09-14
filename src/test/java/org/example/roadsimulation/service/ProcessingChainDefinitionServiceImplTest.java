package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingStageEdgeRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProcessingStageInputRepository;
import org.example.roadsimulation.repository.ProcessingStageRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.impl.ProcessingChainDefinitionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessingChainDefinitionServiceImplTest {

    private ProcessingChainRepository chainRepository;
    private ProcessingStageRepository stageRepository;
    private ProcessingChainDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        chainRepository = mock(ProcessingChainRepository.class);
        stageRepository = mock(ProcessingStageRepository.class);
        ProcessingStageInputRepository stageInputRepository =
                mock(ProcessingStageInputRepository.class);
        ProcessingStageEdgeRepository stageEdgeRepository =
                mock(ProcessingStageEdgeRepository.class);
        ProductionPlanRepository planRepository = mock(ProductionPlanRepository.class);
        ProductionPlanNodeRepository planNodeRepository = mock(ProductionPlanNodeRepository.class);
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        POIRepository poiRepository = mock(POIRepository.class);
        GoodsRepository goodsRepository = mock(GoodsRepository.class);

        service = new ProcessingChainDefinitionServiceImpl(
                chainRepository,
                stageRepository,
                stageInputRepository,
                stageEdgeRepository,
                planRepository,
                planNodeRepository,
                executionRepository,
                poiRepository,
                goodsRepository
        );
    }

    @Test
    void createStageShouldRejectAdjacentSkuMismatch() {
        ProcessingChain chain = chain(1L, stage(1L, 1, "RAW", "SEMI"));
        when(chainRepository.findById(1L)).thenReturn(Optional.of(chain));

        ProcessingStage newStage = stage(null, 2, "OTHER", "FINAL");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createStage(1L, newStage))
                .withMessageContaining("SKU 不一致");

        verify(stageRepository, never()).save(any(ProcessingStage.class));
    }

    @Test
    void updateStageShouldRejectBreakingAdjacentSkuConsistency() {
        ProcessingStage first = stage(1L, 1, "RAW", "SEMI");
        ProcessingStage second = stage(2L, 2, "SEMI", "FINAL");
        ProcessingChain chain = chain(1L, first, second);
        first.setProcessingChain(chain);
        second.setProcessingChain(chain);
        when(stageRepository.findById(1L)).thenReturn(Optional.of(first));

        ProcessingStage details = stage(null, 1, "RAW", "WRONG");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateStage(1L, details))
                .withMessageContaining("SKU 不一致");

        verify(stageRepository, never()).save(any(ProcessingStage.class));
    }

    private ProcessingChain chain(Long id, ProcessingStage... stages) {
        ProcessingChain chain = new ProcessingChain();
        chain.setId(id);
        chain.setChainCode("CHAIN-" + id);
        chain.setChainName("Test chain");
        chain.setStages(new ArrayList<>(List.of(stages)));
        return chain;
    }

    private ProcessingStage stage(Long id, int order, String inputSku, String outputSku) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setStageName("Stage " + order);
        stage.setProcessingPOI(new POI());
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setProcessingTimeMinutes(60);
        return stage;
    }
}
