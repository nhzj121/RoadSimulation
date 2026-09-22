package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageEdge;
import org.example.roadsimulation.entity.ProcessingStageInput;
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
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionPlanningServiceImplYShapeTest {

    @Test
    void calculatesYShapeDemandBackwardThroughInputShares() {
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
                chainRepository, planRepository, nodeRepository, planFlowRepository,
                batchRepository, executionRepository, executionFlowRepository,
                transportDemandService, poiSelector
        );

        ProcessingChain chain = yChain();
        when(chainRepository.findById(10L)).thenReturn(Optional.of(chain));
        when(poiSelector.select(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<ProcessingStage> selectedStages = invocation.getArgument(0);
            IdentityHashMap<ProcessingStage, POI> result = new IdentityHashMap<>();
            selectedStages.forEach(stage -> result.put(stage, stage.getProcessingPOI()));
            return result;
        });
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

        ProductionPlanResponse response = service.createRandomPlan(new CreateProductionPlanRequest(
                10L, 95.0, 95.0, null, 1L, 1L, "test", Map.of()
        ));

        assertThat(response.finalDemandWeight()).isEqualTo(95.0);
        assertThat(response.nodes()).hasSize(4);

        ProductionPlanResponse.NodeResponse steel = response.nodes().get(0);
        ProductionPlanResponse.NodeResponse wood = response.nodes().get(1);
        ProductionPlanResponse.NodeResponse merge = response.nodes().get(2);
        ProductionPlanResponse.NodeResponse sink = response.nodes().get(3);
        assertThat(steel.nodeRole()).isEqualTo("SOURCE");
        assertThat(steel.plannedInputWeight()).isZero();
        assertThat(steel.plannedOutputWeight()).isEqualTo(80.0);
        assertThat(wood.nodeRole()).isEqualTo("SOURCE");
        assertThat(wood.plannedInputWeight()).isZero();
        assertThat(wood.plannedOutputWeight()).isEqualTo(20.0);
        assertThat(merge.nodeRole()).isEqualTo("PROCESSING");
        assertThat(merge.plannedInputWeight()).isEqualTo(100.0);
        assertThat(merge.plannedOutputWeight()).isEqualTo(95.0);
        assertThat(sink.nodeRole()).isEqualTo("SINK");
        assertThat(sink.plannedInputWeight()).isEqualTo(95.0);
        assertThat(sink.plannedOutputWeight()).isEqualTo(95.0);

        assertThat(response.flows()).hasSize(3);
        assertThat(response.flows())
                .filteredOn(flow -> flow.inputKey().equals("steel"))
                .allSatisfy(flow -> assertThat(flow.plannedWeight()).isEqualTo(80.0));
        assertThat(response.flows())
                .filteredOn(flow -> flow.inputKey().equals("wood"))
                .allSatisfy(flow -> assertThat(flow.plannedWeight()).isEqualTo(20.0));
    }

    private ProcessingChain yChain() {
        ProcessingChain chain = new ProcessingChain();
        chain.setId(10L);
        chain.setChainCode("Y");
        chain.setChainName("Y chain");
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);

        POI steelPoi = poi(2L, "Steel factory");
        POI woodPoi = poi(3L, "Wood factory");
        POI mergePoi = poi(4L, "Merge factory");

        ProcessingStage steel = stage(1L, 1, "steel", steelPoi, "ORE", "STEEL", 1.0);
        ProcessingStage wood = stage(2L, 2, "wood", woodPoi, "TIMBER", "WOOD", 1.0);
        ProcessingStage merge = stage(3L, 3, "merge", mergePoi, null, "FINAL", 0.95);
        ProcessingStage sink = stage(4L, 4, "sink", poi(5L, "Demand receiver"), "FINAL", "RECEIVED", 0.5);
        ProcessingStageInput steelInput = new ProcessingStageInput(merge, "steel", "STEEL", 0.8);
        ProcessingStageInput woodInput = new ProcessingStageInput(merge, "wood", "WOOD", 0.2);
        ProcessingStageInput finalInput = new ProcessingStageInput(sink, "final", "FINAL", 1.0);
        merge.setInputs(new ArrayList<>(List.of(steelInput, woodInput)));
        sink.setInputs(new ArrayList<>(List.of(finalInput)));

        List.of(steel, wood, merge, sink).forEach(stage -> stage.setProcessingChain(chain));
        chain.setStages(new ArrayList<>(List.of(steel, wood, merge, sink)));
        chain.setEdges(new ArrayList<>(List.of(
                new ProcessingStageEdge(chain, steel, merge, steelInput),
                new ProcessingStageEdge(chain, wood, merge, woodInput),
                new ProcessingStageEdge(chain, merge, sink, finalInput)
        )));
        return chain;
    }

    private ProcessingStage stage(
            Long id,
            int order,
            String name,
            POI poi,
            String inputSku,
            String outputSku,
            double outputRatio
    ) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setStageKey(name);
        stage.setStageName(name);
        stage.setProcessingPOI(poi);
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setOutputWeightRatio(outputRatio);
        stage.setProcessingTimeMinutes(60);
        stage.setInputs(new ArrayList<>());
        return stage;
    }

    private POI poi(Long id, String name) {
        POI poi = new POI(name, BigDecimal.ZERO, BigDecimal.ZERO, POI.POIType.WAREHOUSE);
        poi.setId(id);
        return poi;
    }
}
