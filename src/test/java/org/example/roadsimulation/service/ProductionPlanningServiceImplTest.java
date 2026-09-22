package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanFlowRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.impl.ProductionPlanningServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
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
        ProductionPlanFlowRepository planFlowRepository = mock(ProductionPlanFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionPlanPoiSelector poiSelector = mock(ProductionPlanPoiSelector.class);

        ProductionPlanningServiceImpl service = new ProductionPlanningServiceImpl(
                chainRepository,
                planRepository,
                nodeRepository,
                planFlowRepository,
                batchRepository,
                executionRepository,
                executionFlowRepository,
                transportDemandService,
                poiSelector
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

        when(poiSelector.select(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<ProcessingStage> selectedStages = invocation.getArgument(0);
            IdentityHashMap<ProcessingStage, POI> result = new IdentityHashMap<>();
            selectedStages.forEach(stage -> result.put(stage, stage.getProcessingPOI()));
            return result;
        });

        when(chainRepository.findById(10L)).thenReturn(Optional.of(chain));
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
        when(planFlowRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ProductionPlanFlow> flows = invocation.getArgument(0);
            for (int i = 0; i < flows.size(); i++) {
                flows.get(i).setId(300L + i);
            }
            return flows;
        });

        ProductionPlanResponse response = service.createAutomaticPlan(new CreateProductionPlanRequest(
                10L,
                63.0,
                63.0,
                null,
                123L,
                1L,
                "test",
                Map.of()
        ), "run-1", 6);

        assertThat(response.finalDemandWeight()).isEqualTo(63.0);
        assertThat(response.simulationRunId()).isEqualTo("run-1");
        assertThat(response.generationRound()).isEqualTo(6);
        assertThat(response.finalSku()).isEqualTo("SEMI_2");
        assertThat(response.nodes()).hasSize(3);
        assertThat(response.nodes()).allSatisfy(node -> assertThat(node.selectedPoiId()).isNotNull());
        assertThat(response.nodes().get(0).nodeRole()).isEqualTo("SOURCE");
        assertThat(response.nodes().get(0).plannedInputWeight()).isZero();
        assertThat(response.nodes().get(0).plannedOutputWeight()).isEqualTo(72.0);
        assertThat(response.nodes().get(1).nodeRole()).isEqualTo("PROCESSING");
        assertThat(response.nodes().get(1).plannedInputWeight()).isEqualTo(72.0);
        assertThat(response.nodes().get(1).plannedOutputWeight()).isEqualTo(63.0);
        assertThat(response.nodes().get(2).nodeRole()).isEqualTo("SINK");
        assertThat(response.nodes().get(2).plannedInputWeight()).isEqualTo(63.0);
        assertThat(response.nodes().get(2).plannedOutputWeight()).isEqualTo(63.0);
        assertThat(response.flows()).hasSize(2);
        assertThat(response.flows()).allSatisfy(flow -> {
            assertThat(flow.fromNodeId()).isNotNull();
            assertThat(flow.sourcePoiId()).isNull();
        });
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
