package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

import static org.example.roadsimulation.sandbox.optimization.GlobalPlan.*;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;
import static org.example.roadsimulation.sandbox.optimization.PreferenceScorer.PRECISION;

/** Nominal, round-aligned forecast for ranking proposals before sandbox execution. */
final class PlanTimingProjection {
    record Timing(TaskKey task, Integer releaseLoop, Integer dispatchLoop, Integer unloadLoop, String blocked) {}
    record Projection(BigDecimal emptyRatio, BigDecimal emptyKm, BigDecimal deliveredTonnes,
                      BigDecimal terminalTonnes, List<Timing> tasks) {}
    private record TripTiming(int dispatch, int end) {}
    private record Delivery(int unload, int tripEnd) {}

    private final CompiledOptimizationProblem problem;
    private final Map<TaskKey, Task> tasks = new LinkedHashMap<>();
    private final Map<TaskKey, ProductionDependencies.Node> producers = new HashMap<>();
    private final Map<FlowKey, List<TaskKey>> parts = new HashMap<>();
    private final Set<TaskKey> terminal = new HashSet<>();
    private final Map<List<String>, SphericalPathModel.Leg> legs = new HashMap<>();
    private final int horizon;
    private final long tick;

    PlanTimingProjection(CompiledOptimizationProblem problem) {
        this.problem = problem;
        horizon = problem.problem().totalLoops();
        tick = problem.problem().tickSeconds();
        for (var task : problem.catalog().tasks()) {
            tasks.put(task.key(), task);
            parts.computeIfAbsent(task.flow(), key -> new ArrayList<>()).add(task.key());
        }
        for (var stage : problem.dependencies().stages()) {
            stage.outbound().forEach(key -> producers.put(key, stage));
            if (stage.role() == Role.SINK) terminal.addAll(stage.inbound());
        }
    }

    Projection project(GlobalPlan plan, GlobalPlanValidator.Validation validation) {
        var simulation = new Forecast(plan, validation);
        return simulation.run();
    }

    private final class Forecast {
        private final GlobalPlan plan;
        private final GlobalPlanValidator.Validation validation;
        private final Map<TripKey, TripTiming> trips = new HashMap<>();
        private final Map<TaskKey, Delivery> deliveries = new HashMap<>();
        private final Map<TaskKey, Integer> releases = new HashMap<>();
        private final Map<TaskKey, TripKey> owners = new HashMap<>();
        private BigDecimal emptyMeters = BigDecimal.ZERO;
        private BigDecimal totalMeters = BigDecimal.ZERO;

        Forecast(GlobalPlan plan, GlobalPlanValidator.Validation validation) {
            this.plan = plan;
            this.validation = validation;
        }

        Projection run() {
            var pending = new ArrayList<TripKey>();
            var vehicles = new HashMap<Long, VehiclePlan>();
            for (var vehicle : plan.vehicles()) {
                vehicles.put(vehicle.vehicleId(), vehicle);
                for (int index = 0; index < vehicle.trips().size(); index++) {
                    var key = new TripKey(vehicle.vehicleId(), index);
                    vehicle.trips().get(index).nodes().forEach(node -> owners.put(node.task(), key));
                    if (!validation.upstreamDeferred().contains(key)) pending.add(key);
                }
            }
            // The validator already checked the combined vehicle and production graph for cycles.
            while (!pending.isEmpty()) {
                int count = pending.size();
                pending.removeIf(key -> tryTrip(key, vehicles.get(key.logicalVehicleId())));
                if (count == pending.size()) throw new IllegalStateException("Validated plan has an unresolved dependency");
            }
            BigDecimal delivered = BigDecimal.ZERO;
            BigDecimal finalDelivered = BigDecimal.ZERO;
            var timings = new ArrayList<Timing>();
            for (var task : tasks.values()) {
                var key = task.key();
                var owner = owners.get(key);
                var trip = trips.get(owner);
                var delivery = deliveries.get(key);
                Integer release = release(key);
                boolean complete = delivery != null && delivery.unload() < horizon;
                if (complete) {
                    delivered = delivered.add(task.weightTonnes());
                    if (terminal.contains(key)) finalDelivered = finalDelivered.add(task.weightTonnes());
                }
                String blocked = owner == null ? "DEFERRED" : trip == null ? "UPSTREAM_DEFERRED"
                        : trip.dispatch() >= horizon ? "DISPATCH_AFTER_CUTOFF" : complete ? null : "UNLOAD_AFTER_CUTOFF";
                timings.add(new Timing(key, release, trip == null ? null : trip.dispatch(),
                        delivery == null ? null : delivery.unload(), blocked));
            }
            BigDecimal ratio = totalMeters.signum() == 0 ? null : emptyMeters.divide(totalMeters, PRECISION);
            return new Projection(ratio, emptyMeters.movePointLeft(3), delivered, finalDelivered, List.copyOf(timings));
        }

        private boolean tryTrip(TripKey key, VehiclePlan vehicle) {
            int index = key.tripIndex();
            var trip = vehicle.trips().get(index);
            var previous = index == 0 ? null : trips.get(new TripKey(vehicle.vehicleId(), index - 1));
            if (index > 0 && previous == null) return false;
            // Vehicle completion and processed-goods release happen after this round's dispatch.
            int ready = previous == null ? 0 : previous.end() + 1;
            for (var node : trip.nodes()) {
                if (node.action() != Action.LOAD) continue;
                Integer release = release(node.task());
                if (release == null) return false;
                ready = Math.max(ready, release + (producers.get(node.task()).role() == Role.SOURCE ? 0 : 1));
            }
            int dispatch = Math.max(3, (Math.max(trip.notBeforeLoop(), ready) + 2) / 3 * 3);
            String from = index == 0 ? "VEHICLE_START:" + vehicle.vehicleId()
                    : poi(vehicle.trips().get(index - 1).nodes().get(vehicle.trips().get(index - 1).nodes().size() - 1).poiId());
            int loop = dispatch;
            int carried = 0;
            var unloaded = new HashMap<TaskKey, Integer>();
            for (var node : trip.nodes()) {
                String to = poi(node.poiId());
                String origin = from;
                var leg = legs.computeIfAbsent(List.of(origin, to), path -> problem.paths().between(origin, to));
                int travelLoops = Math.max(1, rounds(leg.drivingSeconds()));
                long simulatedSeconds = Math.max(0L, horizon - (long) loop) * tick;
                BigDecimal distance = leg.distanceMeters();
                if (leg.drivingSeconds() > simulatedSeconds) {
                    distance = distance.multiply(BigDecimal.valueOf(simulatedSeconds), PRECISION)
                            .divide(BigDecimal.valueOf(leg.drivingSeconds()), PRECISION);
                }
                totalMeters = totalMeters.add(distance);
                if (carried == 0) emptyMeters = emptyMeters.add(distance);
                loop += travelLoops + 1;
                if (node.action() == Action.LOAD) carried++;
                else {
                    carried--;
                    unloaded.put(node.task(), loop);
                }
                from = to;
            }
            int end = loop;
            trips.put(key, new TripTiming(dispatch, end));
            unloaded.forEach((task, at) -> deliveries.put(task, new Delivery(at, end)));
            return true;
        }

        private Integer release(TaskKey key) {
            if (releases.containsKey(key)) return releases.get(key);
            var stage = producers.get(key);
            if (stage.role() == Role.SOURCE) return key.generationLoop();
            int ready = 0;
            var inboundFlows = new HashSet<FlowKey>();
            for (var inbound : stage.inbound()) inboundFlows.add(tasks.get(inbound).flow());
            for (var flow : inboundFlows) {
                int lastUnload = -1;
                boolean settled = false;
                for (var part : parts.get(flow)) {
                    var delivery = deliveries.get(part);
                    if (delivery == null) return null;
                    if (delivery.unload() > lastUnload) {
                        lastUnload = delivery.unload();
                        settled = false;
                    }
                    if (delivery.unload() == lastUnload && delivery.tripEnd() == lastUnload) settled = true;
                }
                // A completed assignment publishes delivery; otherwise the next production scan reconciles it.
                ready = Math.max(ready, lastUnload + (settled ? 0 : 1));
            }
            int allInputsReady = ready;
            int release = ready + rounds(stage.processingSeconds());
            if (stage.processingSeconds() == 0 && stage.inbound().stream()
                    .anyMatch(task -> deliveries.get(task).tripEnd() == allInputsReady && deliveries.get(task).unload() == allInputsReady)) {
                release++;
            }
            int at = release;
            stage.outbound().forEach(task -> releases.put(task, at));
            return release;
        }
    }

    private int rounds(long seconds) { return Math.toIntExact((seconds + tick - 1) / tick); }
    static String poi(long id) { return "POI:" + id; }
}
