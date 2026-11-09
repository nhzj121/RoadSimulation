package org.example.roadsimulation.service;

import org.example.roadsimulation.service.production.ProductionRules;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Selects and snapshots POIs for one plan without modifying the chain template. */
@Service
public class ProductionPlanPoiSelector {

    static final int NEAREST_CANDIDATE_COUNT = 5;
    private final POIRepository poiRepository;
    private SandboxRunRuntimeContext sandboxRunRuntimeContext;

    public ProductionPlanPoiSelector(POIRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    @Autowired(required = false)
    void setSandboxRunRuntimeContext(SandboxRunRuntimeContext sandboxRunRuntimeContext) {
        this.sandboxRunRuntimeContext = sandboxRunRuntimeContext;
    }

    public boolean isSandboxControlled() {
        return sandboxRunRuntimeContext != null;
    }

    public Map<ProcessingStage, POI> select(
            List<ProcessingStage> stages,
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            ProcessingStage sink,
            Long randomSeed
    ) {
        long seed = randomSeed == null
                ? ThreadLocalRandom.current().nextLong()
                : randomSeed ^ 0xD6E8FEB86659FD93L;
        Random random = new Random(seed);
        Map<ProcessingStage, POI> selected = new IdentityHashMap<>();
        selected.put(sink, chooseRandom(candidatePois(sink), random));

        List<ProcessingStage> reverseOrder = new ArrayList<>(
                ProcessingChainGraphValidator.topologicalOrder(stages, edges));
        Collections.reverse(reverseOrder);
        for (ProcessingStage stage : reverseOrder) {
            if (stage == sink) {
                continue;
            }
            ProcessingChainGraphValidator.EdgeSpec outgoing = edges.stream()
                    .filter(edge -> edge.fromStage() == stage)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "非末端节点缺少下游节点: " + stage.getStageName()));
            POI downstream = selected.get(outgoing.toStage());
            if (downstream == null) {
                throw new IllegalStateException("下游节点尚未选择 POI: " + outgoing.toStage().getStageName());
            }
            List<POI> nearest = candidatePois(stage).stream()
                    .filter(poi -> !poi.getId().equals(downstream.getId()))
                    .sorted(Comparator
                            .comparingDouble((POI poi) -> distanceKm(poi, downstream))
                            .thenComparing(POI::getId))
                    .limit(NEAREST_CANDIDATE_COUNT)
                    .toList();
            selected.put(stage, chooseRandom(nearest, random));
        }
        return selected;
    }

    public Map<ProcessingStage, POI> selectAutomatic(
            List<ProcessingStage> stages,
            List<ProcessingChainGraphValidator.EdgeSpec> edges,
            ProcessingStage sink,
            Long randomSeed,
            int loopIndex,
            long processingChainId
    ) {
        if (sandboxRunRuntimeContext == null) {
            if (randomSeed == null) {
                throw new IllegalArgumentException("automatic production plan requires a random seed");
            }
            return select(stages, edges, sink, randomSeed);
        }
        return ProductionRules.selectAutomatic(
                stages, edges, sink, loopIndex, processingChainId, this::candidatePois,
                this::distanceKm, sandboxRunRuntimeContext::random);
    }

    private List<POI> candidatePois(ProcessingStage stage) {
        POI template = stage.getProcessingPOI();
        if (template == null || template.getPoiType() == null) {
            throw new IllegalStateException("加工链节点缺少 POI 类型: " + stage.getStageName());
        }
        List<POI> candidates = poiRepository.findByPoiType(template.getPoiType()).stream()
                .filter(poi -> poi != null && poi.getId() != null)
                .filter(this::hasCoordinates)
                .sorted(Comparator.comparing(POI::getId))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "没有可用的同类型 POI: stage=" + stage.getStageName()
                            + ", type=" + template.getPoiType());
        }
        return candidates;
    }

    private POI chooseRandom(List<POI> candidates, Random random) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("POI 候选集合不能为空");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private boolean hasCoordinates(POI poi) {
        return poi.getLatitude() != null && poi.getLongitude() != null;
    }

    private double distanceKm(POI from, POI to) {
        return ProductionRules.distanceKm(from, to);
    }
}
