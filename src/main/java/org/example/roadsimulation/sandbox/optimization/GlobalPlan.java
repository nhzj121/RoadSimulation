package org.example.roadsimulation.sandbox.optimization;

import java.util.*;

/** G0 validates structure only. Production dependencies, resources and capacities belong to G2. */
public record GlobalPlan(String artifactVersion, OptimizationProblemRef problem,
                         List<TaskKey> catalogTaskKeys, List<VehiclePlan> vehicles,
                         List<TaskKey> deferredTasks) {
    public static final String VERSION = "sandbox-global-plan/v1";
    private static final Comparator<TaskKey> TASK_ORDER = Comparator.comparing(OptimizationJson::write);

    public record TaskKey(int generationLoop, long chainId, long fromStageId, long toStageId,
                          String inputKey, int partIndex) {
        public TaskKey {
            if (generationLoop < 0 || chainId <= 0 || fromStageId <= 0 || toStageId <= 0 || partIndex < 0)
                throw new IllegalArgumentException("Invalid production task key");
            inputKey = ContractChecks.text(inputKey, "inputKey");
        }
    }
    public enum Action { LOAD, UNLOAD }
    public record Node(TaskKey task, Action action, long poiId) {
        public Node {
            if (task == null || action == null) throw new IllegalArgumentException("Task and action are required");
            if (poiId <= 0) throw new IllegalArgumentException("Invalid poiId");
        }
    }
    public record Trip(int notBeforeLoop, List<Node> nodes) {
        public Trip {
            if (notBeforeLoop < 0) throw new IllegalArgumentException("Invalid notBeforeLoop");
            nodes = ContractChecks.list(nodes, "nodes");
            if (nodes.isEmpty()) throw new IllegalArgumentException("Empty trip");
        }
    }
    public record VehiclePlan(long vehicleId, List<Trip> trips) {
        public VehiclePlan {
            if (vehicleId <= 0) throw new IllegalArgumentException("Invalid vehicleId");
            trips = ContractChecks.list(trips, "trips");
            if (trips.isEmpty()) throw new IllegalArgumentException("Empty vehicle plan");
        }
    }

    public GlobalPlan {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        Objects.requireNonNull(problem, "problem");
        catalogTaskKeys = uniqueTasks(catalogTaskKeys, "catalogTaskKeys");
        deferredTasks = uniqueTasks(deferredTasks, "deferredTasks");
        vehicles = ContractChecks.list(vehicles, "vehicles").stream()
                .sorted(Comparator.comparingLong(VehiclePlan::vehicleId)).toList();
        var vehicleIds = new HashSet<Long>();
        var served = new HashSet<TaskKey>();
        for (var vehicle : vehicles) {
            if (!vehicleIds.add(vehicle.vehicleId())) throw new IllegalArgumentException("Duplicate vehicle");
            for (var trip : vehicle.trips()) {
                if (trip.notBeforeLoop() >= problem.totalLoops())
                    throw new IllegalArgumentException("Departure is outside horizon; defer task explicitly");
                var loaded = new HashSet<TaskKey>();
                var unloaded = new HashSet<TaskKey>();
                for (var node : trip.nodes()) {
                    if (node.action() == Action.LOAD) {
                        if (!loaded.add(node.task()) || !served.add(node.task()))
                            throw new IllegalArgumentException("Duplicate task load");
                    } else if (!loaded.contains(node.task()) || !unloaded.add(node.task())) {
                        throw new IllegalArgumentException("Unload must follow its unique load in the same trip");
                    }
                }
                if (!loaded.equals(unloaded)) throw new IllegalArgumentException("Unpaired load/unload");
            }
        }
        var accounted = new HashSet<>(served);
        for (var task : deferredTasks) {
            if (!accounted.add(task)) throw new IllegalArgumentException("Task both served and deferred");
        }
        if (!accounted.equals(new HashSet<>(catalogTaskKeys)))
            throw new IllegalArgumentException("Plan does not cover its task catalog exactly");
        if (catalogTaskKeys.stream().anyMatch(t -> t.generationLoop() >= problem.totalLoops()))
            throw new IllegalArgumentException("Task generation is outside horizon");
    }

    private static List<TaskKey> uniqueTasks(List<TaskKey> tasks, String field) {
        tasks = ContractChecks.list(tasks, field);
        if (new HashSet<>(tasks).size() != tasks.size()) throw new IllegalArgumentException("Duplicate " + field);
        return tasks.stream().sorted(TASK_ORDER).toList();
    }

    /** G1 supplies the trusted catalog; embedded keys alone are not proof of catalog authenticity. */
    public void validateCatalog(Collection<TaskKey> expected) {
        if (!catalogTaskKeys.equals(uniqueTasks(ContractChecks.list(expected, "expected catalog"), "expected catalog")))
            throw new IllegalArgumentException("Plan catalog differs from frozen task catalog");
    }

    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
