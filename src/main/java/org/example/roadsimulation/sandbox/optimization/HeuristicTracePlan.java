package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.roadsimulation.sandbox.optimization.SphericalComparisonEvidence.id;
import static org.example.roadsimulation.sandbox.optimization.SphericalComparisonEvidence.rows;

/** Converts the dispatch facts in a verified source ledger without altering the recorded routes. */
final class HeuristicTracePlan {
    record SourceTrip(long assignmentId, TripKey trip, int dispatchLoop, String dispatchSource, String finalStatus) {}
    record Conversion(GlobalPlan plan, List<SourceTrip> trips, List<GlobalPlan.TaskKey> releasedNotDispatched,
                      List<GlobalPlan.TaskKey> unreleased, int dispatchedTasks, int deliveredTasks) {
        Conversion {
            trips = List.copyOf(trips);
            releasedNotDispatched = List.copyOf(releasedNotDispatched);
            unreleased = List.copyOf(unreleased);
        }
    }
    private record ObservedTrip(long vehicle, int loop, String source, List<GlobalPlan.Node> nodes) {}

    private HeuristicTracePlan() {}

    static Conversion convert(CompiledOptimizationProblem problem, JsonNode export, ProductionTaskMapper.Mapping mapping) {
        return convert(problem, export.path("manifest"), export.path("ticks"), mapping);
    }

    static Conversion convert(CompiledOptimizationProblem problem, JsonNode manifest, JsonNode ticks,
                              ProductionTaskMapper.Mapping mapping) {
        if (!ticks.isArray() || ticks.size() != problem.problem().totalLoops())
            throw new IllegalArgumentException("Incomplete source tick sequence");
        if (!rows(manifest.path("initialDatabaseFacts"), "assignment").isEmpty())
            throw new IllegalArgumentException("Source already had assignments before the first tick");

        var keys = itemKeys(mapping);
        var assignedTasks = new HashSet<GlobalPlan.TaskKey>();
        var observed = new TreeMap<Long, ObservedTrip>();
        Map<Long, JsonNode> assignments = Map.of();
        for (int loop = 0; loop < ticks.size(); loop++) {
            var tick = ticks.get(loop);
            var database = tick.path("facts").path("database");
            var tickStart = LocalDateTime.parse(tick.path("tick").path("tickStart").asText());
            if (loop != tick.path("tick").path("loopIndex").asInt(-1)
                    || !tickStart.equals(problem.problem().simulationStart().plusSeconds(loop * problem.problem().tickSeconds())))
                throw new IllegalArgumentException("Source tick clock differs at loop " + loop);
            assignments = rows(database, "assignment");
            var items = rows(database, "shipment_item");
            var byAssignment = rows(database, "assignment_nodes").values().stream()
                    .collect(Collectors.groupingBy(node -> id(node, "assignment_id")));
            if (!assignments.keySet().containsAll(observed.keySet()) || !assignments.keySet().containsAll(byAssignment.keySet()))
                throw new IllegalArgumentException("Missing source assignment at loop " + loop);
            for (var entry : assignments.entrySet()) {
                long assignment = entry.getKey();
                var row = entry.getValue();
                long vehicle = id(row, "vehicle_id");
                var nodes = nodes(assignment, byAssignment.getOrDefault(assignment, List.of()), keys, items);
                var previous = observed.get(assignment);
                if (previous != null) {
                    if (previous.vehicle() != vehicle)
                        throw invalid(assignment, "vehicle changed at loop " + loop);
                    if (!previous.nodes().equals(nodes))
                        throw invalid(assignment, "route changed at loop " + loop);
                    continue;
                }
                if (loop == 0 || loop % 3 != 0
                        || !sameTime(row, "created_time", tickStart) || !sameTime(row, "start_time", tickStart))
                    throw invalid(assignment, "illegal dispatch evidence at loop " + loop);
                String source = row.path("updated_by").asText();
                if (!Set.of("MultiOrderGA", "TAIL_FALLBACK_HEURISTIC").contains(source))
                    throw invalid(assignment, "unknown dispatch source " + source);
                for (var node : nodes) {
                    if (node.action() == GlobalPlan.Action.LOAD && !assignedTasks.add(node.task()))
                        throw invalid(assignment, "task dispatched more than once: " + node.task());
                }
                observed.put(assignment, new ObservedTrip(vehicle, loop, source, nodes));
            }
        }

        var queues = new TreeMap<Long, List<GlobalPlan.Trip>>();
        var sources = new ArrayList<SourceTrip>();
        var ordered = observed.entrySet().stream().sorted(Comparator
                .<Map.Entry<Long, ObservedTrip>>comparingInt(entry -> entry.getValue().loop())
                .thenComparingLong(Map.Entry::getKey)).toList();
        for (var entry : ordered) {
            var trip = entry.getValue();
            var queue = queues.computeIfAbsent(trip.vehicle(), ignored -> new ArrayList<>());
            if (!queue.isEmpty() && queue.get(queue.size() - 1).notBeforeLoop() == trip.loop())
                throw invalid(entry.getKey(), "same vehicle dispatched twice at loop " + trip.loop());
            sources.add(new SourceTrip(entry.getKey(), new TripKey(trip.vehicle(), queue.size()),
                    trip.loop(), trip.source(), assignments.get(entry.getKey()).path("status").asText()));
            queue.add(new GlobalPlan.Trip(trip.loop(), trip.nodes()));
        }

        var catalog = problem.catalog().taskKeys();
        var released = new HashSet<>(keys.values());
        var deferred = catalog.stream().filter(task -> !assignedTasks.contains(task)).toList();
        var vehicles = queues.entrySet().stream().map(entry -> new GlobalPlan.VehiclePlan(entry.getKey(), entry.getValue())).toList();
        var plan = new GlobalPlan(GlobalPlan.VERSION, problem.problem(), catalog, vehicles, deferred);
        var last = ticks.get(ticks.size() - 1).path("facts").path("database");
        var delivered = rows(last, "shipment_item").entrySet().stream()
                .filter(entry -> "DELIVERED".equals(entry.getValue().path("status").asText()))
                .map(entry -> keys.get(entry.getKey())).toList();
        if (delivered.stream().anyMatch(task -> !assignedTasks.contains(task)))
            throw new IllegalArgumentException("Delivered source task has no recorded dispatch");
        return new Conversion(plan, sources, deferred.stream().filter(released::contains).toList(),
                deferred.stream().filter(task -> !released.contains(task)).toList(), assignedTasks.size(), delivered.size());
    }

    static Map<Long, GlobalPlan.TaskKey> itemKeys(ProductionTaskMapper.Mapping mapping) {
        var result = new HashMap<Long, GlobalPlan.TaskKey>();
        for (var binding : mapping.tasks())
            if (result.putIfAbsent(binding.shipmentItemId(), binding.key()) != null)
                throw new IllegalArgumentException("Duplicate mapped shipment item " + binding.shipmentItemId());
        return Map.copyOf(result);
    }

    private static List<GlobalPlan.Node> nodes(long assignment, List<JsonNode> observed,
                                               Map<Long, GlobalPlan.TaskKey> keys, Map<Long, JsonNode> items) {
        var ordered = observed.stream().sorted(Comparator.comparingInt(row -> row.path("sequence_index").asInt(-1))).toList();
        if (ordered.isEmpty()) throw invalid(assignment, "missing route nodes");
        var nodes = new ArrayList<GlobalPlan.Node>();
        var loads = new HashSet<GlobalPlan.TaskKey>();
        var unloads = new HashSet<GlobalPlan.TaskKey>();
        for (var row : ordered) {
            var sequence = row.path("sequence_index");
            if (!sequence.isIntegralNumber() || !sequence.canConvertToInt() || sequence.intValue() != nodes.size())
                throw invalid(assignment, "missing or duplicate node sequence");
            var action = switch (row.path("action_type").asText()) {
                case "LOAD" -> GlobalPlan.Action.LOAD;
                case "UNLOAD" -> GlobalPlan.Action.UNLOAD;
                default -> throw invalid(assignment, "unsupported node action " + row.path("action_type").asText());
            };
            long item = id(row, "shipment_item_id");
            var task = keys.get(item);
            if (task == null || !items.containsKey(item))
                throw invalid(assignment, "missing released task mapping for ShipmentItem " + item);
            boolean paired = action == GlobalPlan.Action.LOAD ? loads.add(task) : loads.contains(task) && unloads.add(task);
            if (!paired) throw invalid(assignment, "duplicate or out-of-order cargo operation for " + task);
            nodes.add(new GlobalPlan.Node(task, action, id(row, "poi_id")));
        }
        if (!loads.equals(unloads)) throw invalid(assignment, "missing unload operation");
        return List.copyOf(nodes);
    }

    private static boolean sameTime(JsonNode row, String field, LocalDateTime expected) {
        var value = row.path(field);
        return value.isTextual() && expected.equals(LocalDateTime.parse(value.asText()));
    }

    private static IllegalArgumentException invalid(long assignment, String reason) {
        return new IllegalArgumentException("Source Assignment " + assignment + ": " + reason);
    }
}
