package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

/** Complete potential tasks, including flows that never become ready before the deadline. */
public record ProductionTaskCatalog(String artifactVersion, String inputFingerprint, int totalLoops,
                                    List<Batch> batches, List<Flow> flows, List<Task> tasks) {
    public static final String VERSION = "sandbox-production-task-catalog/v1";
    public record BatchKey(int generationLoop, long chainId) {
        public BatchKey { if (generationLoop < 0 || chainId <= 0) throw new IllegalArgumentException("Invalid batch key"); }
    }
    public record StageKey(BatchKey batch, long stageId) {
        public StageKey { Objects.requireNonNull(batch); if (stageId <= 0) throw new IllegalArgumentException("Invalid stage"); }
    }
    public record FlowKey(BatchKey batch, long fromStageId, long toStageId, String inputKey) {
        public FlowKey {
            Objects.requireNonNull(batch);
            if (fromStageId <= 0 || toStageId <= 0 || fromStageId == toStageId)
                throw new IllegalArgumentException("Invalid flow stages");
            inputKey = ContractChecks.text(inputKey, "inputKey");
        }
    }
    public enum Role { SOURCE, PROCESSING, SINK }
    public record Stage(StageKey key, Role role, long poiId, long processingSeconds,
                        BigDecimal plannedInput, BigDecimal plannedOutput,
                        BigDecimal transportInput, BigDecimal transportOutput) {
        public Stage {
            Objects.requireNonNull(key); Objects.requireNonNull(role);
            if (poiId <= 0 || processingSeconds < 0 || (role != Role.PROCESSING && processingSeconds != 0))
                throw new IllegalArgumentException("Invalid stage timing/POI");
            plannedInput = ContractChecks.nonNegative(plannedInput, "plannedInput");
            plannedOutput = ContractChecks.positive(plannedOutput, "plannedOutput");
            transportInput = ContractChecks.nonNegative(transportInput, "transportInput");
            transportOutput = ContractChecks.positive(transportOutput, "transportOutput");
        }
    }
    public record Batch(BatchKey key, int finalQuantity, BigDecimal finalDemandWeight, List<Stage> stages) {
        public Batch {
            Objects.requireNonNull(key);
            if (finalQuantity < 10 || finalQuantity > 34) throw new IllegalArgumentException("Invalid automatic quantity");
            finalDemandWeight = ContractChecks.positive(finalDemandWeight, "finalDemandWeight");
            stages = sorted(stages, "stages", Stage::key);
            if (stages.isEmpty() || stages.stream().anyMatch(s -> !key.equals(s.key.batch)))
                throw new IllegalArgumentException("Invalid batch stages");
        }
    }
    public record Flow(FlowKey key, String sku, long originPoiId, long destinationPoiId,
                       BigDecimal plannedWeight, BigDecimal transportWeight, BigDecimal transportVolume) {
        public Flow {
            Objects.requireNonNull(key); sku = ContractChecks.text(sku, "sku");
            if (originPoiId <= 0 || destinationPoiId <= 0) throw new IllegalArgumentException("Invalid flow POI");
            plannedWeight = ContractChecks.positive(plannedWeight, "plannedWeight");
            transportWeight = ContractChecks.positive(transportWeight, "transportWeight");
            transportVolume = ContractChecks.nonNegative(transportVolume, "transportVolume");
        }
    }
    public record Task(GlobalPlan.TaskKey key, FlowKey flow, int quantity,
                       BigDecimal weightTonnes, BigDecimal volumeCubicMeters) {
        public Task {
            Objects.requireNonNull(key); Objects.requireNonNull(flow);
            if (!key.equals(new GlobalPlan.TaskKey(flow.batch.generationLoop, flow.batch.chainId,
                    flow.fromStageId, flow.toStageId, flow.inputKey, key.partIndex())) || quantity <= 0)
                throw new IllegalArgumentException("Invalid task identity/quantity");
            weightTonnes = ContractChecks.positive(weightTonnes, "weightTonnes");
            volumeCubicMeters = ContractChecks.nonNegative(volumeCubicMeters, "volumeCubicMeters");
        }
    }
    public ProductionTaskCatalog {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        inputFingerprint = ContractChecks.hash(inputFingerprint, "inputFingerprint");
        if (totalLoops <= 0) throw new IllegalArgumentException("Invalid horizon");
        batches = sorted(batches, "batches", Batch::key);
        flows = sorted(flows, "flows", Flow::key);
        tasks = sorted(tasks, "tasks", Task::key);
        var stages = new HashMap<StageKey, Stage>();
        for (var batch : batches) {
            if (batch.key.generationLoop >= totalLoops || batch.key.generationLoop % 6 != 0)
                throw new IllegalArgumentException("Batch outside production schedule");
            batch.stages.forEach(s -> stages.put(s.key, s));
        }
        for (var flow : flows) {
            Stage from = stages.get(new StageKey(flow.key.batch, flow.key.fromStageId));
            Stage to = stages.get(new StageKey(flow.key.batch, flow.key.toStageId));
            if (from == null || to == null || from.poiId != flow.originPoiId || to.poiId != flow.destinationPoiId)
                throw new IllegalArgumentException("Unresolved flow stage/POI");
        }
        var flowKeys = new HashSet<>(flows.stream().map(Flow::key).toList());
        var tasksByFlow = new HashMap<FlowKey, List<Task>>();
        for (var task : tasks) {
            if (!flowKeys.contains(task.flow)) throw new IllegalArgumentException("Unknown task flow");
            tasksByFlow.computeIfAbsent(task.flow, ignored -> new ArrayList<>()).add(task);
        }
        for (var flow : flows) {
            var parts = tasksByFlow.getOrDefault(flow.key, List.of()).stream()
                    .sorted(Comparator.comparingInt(t -> t.key.partIndex())).toList();
            if (parts.isEmpty()) throw new IllegalArgumentException("Flow has no tasks");
            for (int i = 0; i < parts.size(); i++) if (parts.get(i).key.partIndex() != i)
                throw new IllegalArgumentException("Noncontiguous task parts");
            // Preserve the existing double summation contract, including final-part residue.
            double weight = parts.stream().mapToDouble(t -> t.weightTonnes.doubleValue()).sum();
            double volume = parts.stream().mapToDouble(t -> t.volumeCubicMeters.doubleValue()).sum();
            if (Math.abs(weight - flow.transportWeight.doubleValue()) > 1e-9
                    || BigDecimal.valueOf(volume).compareTo(flow.transportVolume) != 0)
                throw new IllegalArgumentException("Flow/task mass or volume mismatch");
        }
    }
    static <T, K> List<T> sorted(List<T> items, String name, java.util.function.Function<T, K> key) {
        var result = ContractChecks.list(items, name).stream()
                .sorted(Comparator.comparing(t -> OptimizationJson.write(key.apply(t)))).toList();
        if (result.stream().map(key).distinct().count() != result.size())
            throw new IllegalArgumentException("Duplicate " + name);
        return result;
    }
    public List<GlobalPlan.TaskKey> taskKeys() { return tasks.stream().map(Task::key).toList(); }
    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
