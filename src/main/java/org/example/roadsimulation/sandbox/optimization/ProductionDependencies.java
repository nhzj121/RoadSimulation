package org.example.roadsimulation.sandbox.optimization;

import java.util.*;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;

/** Edges are stage -> outgoing task -> target stage; inbound lists are ALL prerequisites. */
public record ProductionDependencies(String artifactVersion, String catalogFingerprint, List<Node> stages) {
    public static final String VERSION = "sandbox-production-dependencies/v1";
    public record Node(StageKey stage, Role role, long processingSeconds,
                       List<GlobalPlan.TaskKey> inbound, List<GlobalPlan.TaskKey> outbound) {
        public Node {
            Objects.requireNonNull(stage); Objects.requireNonNull(role);
            inbound = sorted(inbound, "inbound tasks", t -> t);
            outbound = sorted(outbound, "outbound tasks", t -> t);
            if (processingSeconds < 0 || (role != Role.PROCESSING && processingSeconds != 0)
                    || (role == Role.SOURCE && (!inbound.isEmpty() || outbound.isEmpty()))
                    || (role == Role.SINK && (inbound.isEmpty() || !outbound.isEmpty()))
                    || (role == Role.PROCESSING && (inbound.isEmpty() || outbound.isEmpty())))
                throw new IllegalArgumentException("Invalid dependency role/timing");
        }
    }
    public ProductionDependencies {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        catalogFingerprint = ContractChecks.hash(catalogFingerprint, "catalogFingerprint");
        stages = sorted(stages, "dependency stages", Node::stage);
        var from = new HashMap<GlobalPlan.TaskKey, StageKey>();
        var to = new HashMap<GlobalPlan.TaskKey, StageKey>();
        for (var node : stages) {
            for (var task : node.outbound) {
                if (!node.stage.equals(new StageKey(new BatchKey(task.generationLoop(), task.chainId()), task.fromStageId()))
                        || from.put(task, node.stage) != null) throw new IllegalArgumentException("Invalid outbound dependency");
            }
            for (var task : node.inbound) {
                if (!node.stage.equals(new StageKey(new BatchKey(task.generationLoop(), task.chainId()), task.toStageId()))
                        || to.put(task, node.stage) != null) throw new IllegalArgumentException("Invalid inbound dependency");
            }
        }
        if (!from.keySet().equals(to.keySet())) throw new IllegalArgumentException("Unpaired dependency tasks");
        var indegree = new HashMap<StageKey, Integer>();
        var successors = new HashMap<StageKey, List<StageKey>>();
        stages.forEach(node -> indegree.put(node.stage, 0));
        from.forEach((task, source) -> {
            var target = to.get(task);
            indegree.merge(target, 1, Integer::sum);
            successors.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
        });
        var ready = new ArrayDeque<StageKey>();
        stages.forEach(node -> { if (indegree.get(node.stage) == 0) ready.add(node.stage); });
        int visited = 0;
        while (!ready.isEmpty()) {
            var source = ready.remove(); visited++;
            for (var target : successors.getOrDefault(source, List.of())) {
                if (indegree.merge(target, -1, Integer::sum) == 0) ready.add(target);
            }
        }
        if (visited != stages.size()) throw new IllegalArgumentException("Cyclic production dependencies");
    }
    public static ProductionDependencies from(ProductionTaskCatalog catalog) {
        var nodes = new ArrayList<Node>();
        var incoming = new HashMap<StageKey, List<GlobalPlan.TaskKey>>();
        var outgoing = new HashMap<StageKey, List<GlobalPlan.TaskKey>>();
        for (var task : catalog.tasks()) {
            incoming.computeIfAbsent(new StageKey(task.flow().batch(), task.flow().toStageId()), ignored -> new ArrayList<>()).add(task.key());
            outgoing.computeIfAbsent(new StageKey(task.flow().batch(), task.flow().fromStageId()), ignored -> new ArrayList<>()).add(task.key());
        }
        for (var batch : catalog.batches()) for (var stage : batch.stages()) {
            nodes.add(new Node(stage.key(), stage.role(), stage.processingSeconds(),
                    incoming.getOrDefault(stage.key(), List.of()), outgoing.getOrDefault(stage.key(), List.of())));
        }
        return new ProductionDependencies(VERSION, catalog.fingerprint(), nodes);
    }
    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
