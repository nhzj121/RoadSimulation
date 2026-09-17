package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanFlowRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.impl.ProductionPlanningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionPlanningServiceImplSkuValidationTest {

    private ProcessingChainRepository chainRepository;
    private ProductionPlanningServiceImpl service;

    @BeforeEach
    void setUp() {
        chainRepository = mock(ProcessingChainRepository.class);
        ProductionPlanRepository planRepository = mock(ProductionPlanRepository.class);
        ProductionPlanNodeRepository nodeRepository = mock(ProductionPlanNodeRepository.class);
        ProductionPlanFlowRepository planFlowRepository = mock(ProductionPlanFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        POIRepository poiRepository = mock(POIRepository.class);
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);

        service = new ProductionPlanningServiceImpl(
                chainRepository,
                planRepository,
                nodeRepository,
                planFlowRepository,
                batchRepository,
                executionRepository,
                executionFlowRepository,
                poiRepository,
                enrollmentRepository,
                transportDemandService
        );
    }

    @Test
    void createRandomPlanShouldRejectChainWithInconsistentSkus() {
        ProcessingChain chain = new ProcessingChain();
        chain.setId(1L);
        chain.setChainCode("BAD_CHAIN");
        chain.setChainName("Bad chain");
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);
        chain.setStages(new ArrayList<>(List.of(
                stage(1, "RAW", "SEMI_1"),
                stage(2, "SEMI_2", "FINAL")
        )));
        when(chainRepository.findById(1L)).thenReturn(Optional.of(chain));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createRandomPlan(new CreateProductionPlanRequest(
                        1L, 10.0, 20.0, null, null, null, "test"
                )))
                .withMessageContaining("SKU 不一致");
    }

    private ProcessingStage stage(int order, String inputSku, String outputSku) {
        ProcessingStage stage = new ProcessingStage();
        stage.setStageOrder(order);
        stage.setStageName("Stage " + order);
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setProcessingPOI(new POI(
                "Stage POI",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                POI.POIType.WAREHOUSE
        ));
        stage.setProcessingTimeMinutes(60);
        return stage;
    }
}
