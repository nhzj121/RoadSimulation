package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ProductionPlanPoiSelectorTest {

    @Test
    void fixedSeedSelectsSameSinkAndRandomUpstreamFromNearestFive() {
        POIRepository poiRepository = mock(POIRepository.class);
        ProductionPlanPoiSelector selector = new ProductionPlanPoiSelector(poiRepository);
        ProcessingStage source = stage(1L, 1, "Source", POI.POIType.IRON_MINE, "RAW", "RAW");
        ProcessingStage sink = stage(2L, 2, "Sink", POI.POIType.AUTO_ASSEMBLY_PLANT, "RAW", "FINAL");
        List<ProcessingStage> stages = List.of(source, sink);
        List<ProcessingChainGraphValidator.EdgeSpec> edges =
                ProcessingChainGraphValidator.normalizedEdges(stages, List.of());

        POI sink1 = poi(201L, "Sink 1", POI.POIType.AUTO_ASSEMBLY_PLANT, 0.0, 0.0);
        POI sink2 = poi(202L, "Sink 2", POI.POIType.AUTO_ASSEMBLY_PLANT, 0.01, 0.0);
        List<POI> sourceCandidates = List.of(
                poi(101L, "Mine 1", POI.POIType.IRON_MINE, 0.02, 0.0),
                poi(102L, "Mine 2", POI.POIType.IRON_MINE, 0.03, 0.0),
                poi(103L, "Mine 3", POI.POIType.IRON_MINE, 0.04, 0.0),
                poi(104L, "Mine 4", POI.POIType.IRON_MINE, 0.05, 0.0),
                poi(105L, "Mine 5", POI.POIType.IRON_MINE, 0.06, 0.0),
                poi(106L, "Far mine", POI.POIType.IRON_MINE, 50.0, 0.0)
        );
        when(poiRepository.findByPoiType(POI.POIType.AUTO_ASSEMBLY_PLANT))
                .thenReturn(List.of(sink1, sink2));
        when(poiRepository.findByPoiType(POI.POIType.IRON_MINE))
                .thenReturn(sourceCandidates);

        Map<ProcessingStage, POI> first = selector.select(stages, edges, sink, 42L);
        Map<ProcessingStage, POI> second = selector.select(stages, edges, sink, 42L);

        assertThat(first.get(sink).getId()).isEqualTo(second.get(sink).getId());
        assertThat(first.get(source).getId()).isEqualTo(second.get(source).getId());
        assertThat(first.get(source).getId()).isNotEqualTo(106L);
        assertThat(first.get(source).getPoiType()).isEqualTo(POI.POIType.IRON_MINE);
        assertThat(first.get(sink).getPoiType()).isEqualTo(POI.POIType.AUTO_ASSEMBLY_PLANT);
    }

    @Test
    void sandboxAutomaticSelectionUsesIndependentDomainStreamsAndStableCandidateOrder() {
        POIRepository poiRepository = mock(POIRepository.class);
        SandboxRunRuntimeContext runtime = mock(SandboxRunRuntimeContext.class);
        SandboxRandomProtocol protocol = new SandboxRandomProtocol(new ObjectMapper());
        when(runtime.random(any(), any())).thenAnswer(invocation -> protocol.random(
                "20260927", invocation.getArgument(0), invocation.getArgument(1)));
        ProductionPlanPoiSelector selector = new ProductionPlanPoiSelector(poiRepository);
        selector.setSandboxRunRuntimeContext(runtime);
        ProcessingStage source = stage(1L, 1, "Source", POI.POIType.IRON_MINE, "RAW", "RAW");
        ProcessingStage sink = stage(2L, 2, "Sink", POI.POIType.AUTO_ASSEMBLY_PLANT, "RAW", "FINAL");
        List<ProcessingStage> stages = List.of(source, sink);
        List<ProcessingChainGraphValidator.EdgeSpec> edges =
                ProcessingChainGraphValidator.normalizedEdges(stages, List.of());
        POI sink1 = poi(201L, "Sink 1", POI.POIType.AUTO_ASSEMBLY_PLANT, 0.0, 0.0);
        POI sink2 = poi(202L, "Sink 2", POI.POIType.AUTO_ASSEMBLY_PLANT, 0.01, 0.0);
        POI source1 = poi(101L, "Mine 1", POI.POIType.IRON_MINE, 0.02, 0.0);
        POI source2 = poi(102L, "Mine 2", POI.POIType.IRON_MINE, 0.03, 0.0);
        when(poiRepository.findByPoiType(POI.POIType.AUTO_ASSEMBLY_PLANT))
                .thenReturn(List.of(sink2, sink1), List.of(sink1, sink2));
        when(poiRepository.findByPoiType(POI.POIType.IRON_MINE))
                .thenReturn(List.of(source2, source1), List.of(source1, source2));

        Map<ProcessingStage, POI> first = selector.selectAutomatic(
                stages, edges, sink, 123L, 6, 9L);
        Map<ProcessingStage, POI> second = selector.selectAutomatic(
                stages, edges, sink, 123L, 6, 9L);

        assertThat(first.get(sink).getId()).isEqualTo(second.get(sink).getId());
        assertThat(first.get(source).getId()).isEqualTo(second.get(source).getId());
    }

    private ProcessingStage stage(
            Long id,
            int order,
            String name,
            POI.POIType type,
            String inputSku,
            String outputSku
    ) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setStageName(name);
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoodsSku(outputSku);
        stage.setOutputWeightRatio(1.0);
        stage.setProcessingTimeMinutes(60);
        stage.setProcessingPOI(poi(id + 1000L, name + " template", type, 0.0, 0.0));
        return stage;
    }

    private POI poi(
            Long id,
            String name,
            POI.POIType type,
            double longitude,
            double latitude
    ) {
        POI poi = new POI(
                name,
                BigDecimal.valueOf(longitude),
                BigDecimal.valueOf(latitude),
                type
        );
        poi.setId(id);
        return poi;
    }
}
