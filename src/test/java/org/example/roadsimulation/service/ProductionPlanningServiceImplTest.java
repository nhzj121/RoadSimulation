package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.impl.ProductionPlanningServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionPlanningServiceImplTest {

    @Test
    void createRandomPlanPropagatesDemandBackwardThroughStageRatios() {
        ProcessingChainRepository chainRepository = mock(ProcessingChainRepository.class);
        ProductionPlanRepository planRepository = mock(ProductionPlanRepository.class);
        ProductionPlanNodeRepository nodeRepository = mock(ProductionPlanNodeRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        POIRepository poiRepository = mock(POIRepository.class);
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);

        ProductionPlanningServiceImpl service = new ProductionPlanningServiceImpl(
                chainRepository,
                planRepository,
                nodeRepository,
                batchRepository,
                executionRepository,
                poiRepository,
                enrollmentRepository,
                transportDemandService
        );

        POI source = poi(1L, "SOURCE");
        POI first = poi(2L, "FIRST");
        POI second = poi(3L, "SECOND");
        POI third = poi(4L, "THIRD");

        ProcessingStage stage1 = stage(1L, 1, first, "RAW", "SEMI_1", 0.8);
        ProcessingStage stage2 = stage(2L, 2, second, "SEMI_1", "SEMI_2", 0.875);
        ProcessingStage stage3 = stage(3L, 3, third, "SEMI_2", "FINAL", 0.9);

        ProcessingChain chain = new ProcessingChain();
        chain.setId(10L);
        chain.setChainCode("CHAIN");
        chain.setChainName("Test chain");
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);
        chain.setStages(new java.util.ArrayList<>(List.of(stage1, stage2, stage3)));
        stage1.setProcessingChain(chain);
        stage2.setProcessingChain(chain);
        stage3.setProcessingChain(chain);

        when(chainRepository.findById(10L)).thenReturn(Optional.of(chain));
        when(poiRepository.findById(1L)).thenReturn(Optional.of(source));
        when(planRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> {
            ProductionPlan plan = invocation.getArgument(0);
            plan.setId(100L);
            return plan;
        });
        when(nodeRepository.saveAll(any())).thenAnswer(invocation -> {
            List<org.example.roadsimulation.entity.ProductionPlanNode> nodes = invocation.getArgument(0);
            for (int i = 0; i < nodes.size(); i++) {
                nodes.get(i).setId(200L + i);
            }
            return nodes;
        });

        ProductionPlanResponse response = service.createRandomPlan(new CreateProductionPlanRequest(
                10L,
                63.0,
                63.0,
                null,
                123L,
                1L,
                "test"
        ));

        assertThat(response.finalDemandWeight()).isEqualTo(63.0);
        assertThat(response.nodes()).hasSize(3);
        assertThat(response.nodes().get(0).plannedInputWeight()).isEqualTo(100.0);
        assertThat(response.nodes().get(0).plannedOutputWeight()).isEqualTo(80.0);
        assertThat(response.nodes().get(1).plannedInputWeight()).isEqualTo(80.0);
        assertThat(response.nodes().get(1).plannedOutputWeight()).isEqualTo(70.0);
        assertThat(response.nodes().get(2).plannedInputWeight()).isEqualTo(70.0);
        assertThat(response.nodes().get(2).plannedOutputWeight()).isEqualTo(63.0);
    }

    private POI poi(Long id, String name) {
        POI poi = new POI(name, BigDecimal.ZERO, BigDecimal.ZERO, POI.POIType.WAREHOUSE);
        poi.setId(id);
        return poi;
    }

    private ProcessingStage stage(
            Long id,
            Integer order,
            POI poi,
            String inputSku,
            String outputSku,
            double outputRatio
    ) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setStageName("Stage " + order);
        stage.setProcessingPOI(poi);
        stage.setInputGoods(new Goods(inputSku, inputSku));
        stage.setOutputGoods(new Goods(outputSku, outputSku));
        stage.setOutputWeightRatio(outputRatio);
        stage.setProcessingTimeMinutes(60);
        return stage;
    }
}
