package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
import java.math.BigDecimal;
import java.util.*;

/** No database/runtime IDs: the graph includes whole-trip start gates and delivery prerequisites. */
public final class GlobalPlanValidator {
    public record Peak(BigDecimal weight, BigDecimal volume) {}
    public record Validation(Map<TripKey, Peak> peaks, Set<TripKey> upstreamDeferred) {
        public Validation { peaks = Collections.unmodifiableMap(new TreeMap<>(peaks)); upstreamDeferred = Set.copyOf(upstreamDeferred); }
    }
    private static final BigDecimal EPSILON = new BigDecimal("0.000001");
    public Validation validate(PlanExecutionTask execution, SandboxBaselinePackageV1.Data data) {
        return validatePlan(execution.compiledProblem(), execution.plan(), data);
    }

    public Validation validatePlan(CompiledOptimizationProblem problem, GlobalPlan plan, SandboxBaselinePackageV1.Data data) {
        if (!problem.problem().equals(plan.problem())) throw new IllegalArgumentException("Plan/problem mismatch");
        var catalog = problem.catalog();
        plan.validateCatalog(catalog.taskKeys());
        var tasks = new HashMap<GlobalPlan.TaskKey, ProductionTaskCatalog.Task>();
        catalog.tasks().forEach(t -> tasks.put(t.key(), t));
        var flows = new HashMap<ProductionTaskCatalog.FlowKey, ProductionTaskCatalog.Flow>();
        catalog.flows().forEach(f -> flows.put(f.key(), f));
        var vehicles = new HashMap<Long, SandboxBaselinePackageV1.Vehicle>(); data.vehicles().forEach(v -> vehicles.put(v.id(), v));
        var stages = new HashMap<ProductionTaskCatalog.StageKey, ProductionDependencies.Node>();
        problem.dependencies().stages().forEach(s -> stages.put(s.stage(), s));
        var taskOwners = new HashMap<GlobalPlan.TaskKey, TripKey>(); var peaks = new TreeMap<TripKey, Peak>();
        var graph = new HashMap<String, Set<String>>();
        for (var vehiclePlan : plan.vehicles()) {
            var vehicle = vehicles.get(vehiclePlan.vehicleId());
            if (vehicle == null || data.driverVehicleBindings().stream().noneMatch(b -> b.vehicleId() == vehicle.id()))
                throw new IllegalArgumentException("Missing vehicle or associated driver: " + vehiclePlan.vehicleId());
            for (int i = 0; i < vehiclePlan.trips().size(); i++) {
                var key = new TripKey(vehicle.id(), i); var trip = vehiclePlan.trips().get(i);
                var stack = new ArrayDeque<GlobalPlan.TaskKey>();
                BigDecimal weight = BigDecimal.ZERO, volume = BigDecimal.ZERO, maxWeight = weight, maxVolume = volume;
                for (var node : trip.nodes()) {
                    var task = tasks.get(node.task());
                    if (task == null) throw new IllegalArgumentException("Unknown catalog task");
                    var flow = flows.get(task.flow()); boolean load = node.action() == GlobalPlan.Action.LOAD;
                    if (node.poiId() != (load ? flow.originPoiId() : flow.destinationPoiId()))
                        throw new IllegalArgumentException("Wrong cargo operation POI: " + key);
                    if (load) { stack.push(node.task()); taskOwners.put(node.task(), key); }
                    else if (stack.isEmpty() || !stack.pop().equals(node.task())) throw new IllegalArgumentException("LIFO violation: " + key);
                    weight = load ? weight.add(task.weightTonnes()) : weight.subtract(task.weightTonnes());
                    volume = load ? volume.add(task.volumeCubicMeters()) : volume.subtract(task.volumeCubicMeters());
                    maxWeight = maxWeight.max(weight); maxVolume = maxVolume.max(volume);
                    if (weight.signum() < 0 || volume.signum() < 0
                            || !fits(vehicle, weight, volume))
                        throw new IllegalArgumentException("Capacity/volume violation: " + key);
                }
                if (!stack.isEmpty() || weight.signum() != 0 || volume.signum() != 0) throw new IllegalArgumentException("Unbalanced trip");
                peaks.put(key, new Peak(maxWeight, maxVolume));
                vertex(graph, trip(key)); if (i > 0) edge(graph, trip(new TripKey(vehicle.id(), i - 1)), trip(key));
            }
        }
        for (var task : catalog.tasks()) {
            var producer = new ProductionTaskCatalog.StageKey(task.flow().batch(), task.flow().fromStageId());
            var consumer = new ProductionTaskCatalog.StageKey(task.flow().batch(), task.flow().toStageId());
            edge(graph, stage(producer), cargo(task.key())); edge(graph, cargo(task.key()), stage(consumer));
            var owner = taskOwners.get(task.key());
            if (owner != null) { edge(graph, stage(producer), trip(owner)); edge(graph, trip(owner), cargo(task.key())); }
        }
        var degrees = new HashMap<String,Integer>(); graph.keySet().forEach(k -> degrees.put(k, 0));
        graph.values().forEach(next -> next.forEach(k -> degrees.merge(k, 1, Integer::sum)));
        var ready = new ArrayDeque<String>(); degrees.forEach((k,v) -> { if (v == 0) ready.add(k); }); int visited = 0;
        while (!ready.isEmpty()) { var key = ready.remove(); visited++; for (var next : graph.get(key)) if (degrees.merge(next,-1,Integer::sum) == 0) ready.add(next); }
        if (visited != graph.size()) throw new IllegalArgumentException("DEPENDENCY_CYCLE: whole-trip readiness/vehicle order");
        var blockedNodes = new HashSet<String>(); var pending = new ArrayDeque<String>();
        plan.deferredTasks().forEach(t -> pending.add(cargo(t)));
        while (!pending.isEmpty()) { var key = pending.remove(); if (blockedNodes.add(key)) pending.addAll(graph.getOrDefault(key, Set.of())); }
        var deferred = new HashSet<TripKey>(); peaks.keySet().forEach(k -> { if (blockedNodes.contains(trip(k))) deferred.add(k); });
        return new Validation(peaks, deferred);
    }
    static boolean fits(SandboxBaselinePackageV1.Vehicle vehicle, BigDecimal weight, BigDecimal volume) {
        return weight.compareTo(vehicle.maxLoadCapacityTonnes().add(EPSILON)) <= 0
                && volume.compareTo(vehicle.cargoVolumeCubicMeters().add(EPSILON)) <= 0;
    }
    private static String trip(TripKey k) { return "trip:" + OptimizationJson.write(k); }
    private static String stage(ProductionTaskCatalog.StageKey k) { return "stage:" + OptimizationJson.write(k); }
    private static String cargo(GlobalPlan.TaskKey k) { return "cargo:" + OptimizationJson.write(k); }
    private static void vertex(Map<String,Set<String>> graph, String k) { graph.computeIfAbsent(k, ignored -> new HashSet<>()); }
    private static void edge(Map<String,Set<String>> graph, String from, String to) { vertex(graph,from); vertex(graph,to); graph.get(from).add(to); }
}
