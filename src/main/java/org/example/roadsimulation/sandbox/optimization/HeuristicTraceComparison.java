package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.execution.SandboxReplayFactComparator;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.roadsimulation.sandbox.optimization.SphericalComparisonEvidence.id;
import static org.example.roadsimulation.sandbox.optimization.SphericalComparisonEvidence.rows;

/** Compares business identities across dispatch modes, keeping each ledger's technical IDs out of the comparison. */
final class HeuristicTraceComparison {
    private static final Set<String> OBJECTIVES = ObjectiveRegistry.throughput().metricIds();
    private static final JsonNode MISSING = NullNode.getInstance();

    record Difference(int loopIndex, String category, TripKey trip, GlobalPlan.TaskKey task,
                      JsonNode source, JsonNode replay, List<SandboxReplayFactComparator.Difference> fields,
                      JsonNode replayDispatchEvidence) {}
    record Tick(int loopIndex, List<Difference> differences, JsonNode sourceMetrics, JsonNode replayMetrics,
                BigDecimal sourceTerminalTonnes, BigDecimal replayTerminalTonnes) {}
    record Result(String toleranceVersion, boolean matches, Difference firstDifference, List<Tick> ticks) {}

    private final ObjectMapper json;
    private final SandboxReplayFactComparator comparator = new SandboxReplayFactComparator();

    HeuristicTraceComparison(ObjectMapper json) {
        this.json = json;
    }

    Result compare(HeuristicTracePlan.Conversion conversion, ProductionTaskCatalog catalog,
                   ProductionTaskMapper.Mapping sourceMapping, JsonNode source, JsonNode replay) {
        var expectedTicks = source.path("ticks");
        var actualTicks = replay.path("ticks");
        if (!expectedTicks.isArray() || !actualTicks.isArray() || expectedTicks.size() != actualTicks.size()
                || expectedTicks.size() != catalog.totalLoops())
            throw new IllegalArgumentException("Trace horizon mismatch");
        var sourceTrips = conversion.trips().stream().collect(Collectors.toMap(
                HeuristicTracePlan.SourceTrip::assignmentId, HeuristicTracePlan.SourceTrip::trip));
        var tripOrder = sourceTrips.values().stream().sorted().toList();
        var expectedKeys = HeuristicTracePlan.itemKeys(sourceMapping);
        var sinks = catalog.batches().stream().flatMap(batch -> batch.stages().stream())
                .filter(stage -> stage.role() == ProductionTaskCatalog.Role.SINK).map(ProductionTaskCatalog.Stage::key)
                .collect(Collectors.toSet());
        var terminal = catalog.tasks().stream().filter(task -> sinks.contains(
                new ProductionTaskCatalog.StageKey(task.flow().batch(), task.flow().toStageId())))
                .collect(Collectors.toMap(ProductionTaskCatalog.Task::key, ProductionTaskCatalog.Task::weightTonnes));

        var timeline = new ArrayList<Tick>();
        for (int loop = 0; loop < expectedTicks.size(); loop++) {
            var expected = expectedTicks.get(loop);
            var actual = actualTicks.get(loop);
            if (expected.path("tick").path("loopIndex").asInt(-1) != loop
                    || !expected.path("tick").equals(actual.path("tick")))
                throw new IllegalArgumentException("Trace tick clock mismatch at " + loop);
            var plan = actual.path("facts").path("runtime").path("planExecution");
            var state = json.convertValue(plan.path("state"), PlanExecutionState.Snapshot.class);
            var actualKeys = HeuristicTracePlan.itemKeys(json.convertValue(plan.path("mapping"), ProductionTaskMapper.Mapping.class));
            var actualTrips = state.bindings().stream().collect(Collectors.toMap(
                    PlanExecutionState.Binding::assignmentId, PlanExecutionState.Binding::trip));
            if (!sourceTrips.values().containsAll(actualTrips.values()))
                throw new IllegalArgumentException("Replay dispatched a trip outside the converted plan");

            var left = new TickFacts(expected, sourceTrips, expectedKeys);
            var right = new TickFacts(actual, actualTrips, actualKeys);
            var differences = new ArrayList<Difference>();
            for (var trip : tripOrder) {
                var a = left.trip(trip);
                var b = right.trip(trip);
                var result = comparator.compare(a, b, MISSING, MISSING);
                if (!result.factsMatch()) {
                    boolean dispatchMismatch = a.isNull() || b.isNull()
                            || !a.at("/database/assignment/0/start_time").equals(b.at("/database/assignment/0/start_time"));
                    var context = dispatchMismatch
                            ? dispatchEvidence(conversion.plan(), trip, actual, state, right.tasks)
                            : MISSING;
                    differences.add(new Difference(loop, dispatchMismatch ? "DISPATCH" : "TRIP_PROGRESS",
                            trip, null, a, b, result.differences(), context));
                }
            }
            for (var task : catalog.taskKeys()) {
                var a = left.tasks.getOrDefault(task, MISSING);
                var b = right.tasks.getOrDefault(task, MISSING);
                if (!a.equals(b)) differences.add(new Difference(loop, a.isNull() || b.isNull() ? "RELEASE" : "TASK_PROGRESS",
                        null, task, a, b, List.of(), MISSING));
            }
            var aEvents = left.events();
            var bEvents = right.events();
            var eventComparison = comparator.compare(aEvents, bEvents, MISSING, MISSING);
            if (!eventComparison.factsMatch()) differences.add(new Difference(loop, "EVENT_OR_REPLACEMENT", null, null,
                    aEvents, bEvents, eventComparison.differences(), MISSING));
            var aMetrics = metrics(expected);
            var bMetrics = metrics(actual);
            var metricComparison = comparator.compare(MISSING, MISSING, aMetrics, bMetrics);
            if (!metricComparison.factsMatch()) differences.add(new Difference(loop, "OBJECTIVES", null, null,
                    aMetrics, bMetrics, metricComparison.differences(), MISSING));
            timeline.add(new Tick(loop, List.copyOf(differences), aMetrics, bMetrics,
                    deliveredTerminal(left.tasks, terminal), deliveredTerminal(right.tasks, terminal)));
        }
        var first = timeline.stream().flatMap(tick -> tick.differences().stream()).findFirst().orElse(null);
        return new Result(SandboxReplayFactComparator.VERSION, first == null, first, List.copyOf(timeline));
    }

    private static BigDecimal deliveredTerminal(Map<GlobalPlan.TaskKey, JsonNode> tasks, Map<GlobalPlan.TaskKey, BigDecimal> terminal) {
        return terminal.entrySet().stream().filter(entry -> "DELIVERED".equals(tasks.getOrDefault(entry.getKey(), MISSING)
                .path("status").asText())).map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ObjectNode dispatchEvidence(GlobalPlan plan, TripKey trip, JsonNode tick, PlanExecutionState.Snapshot state,
                                        Map<GlobalPlan.TaskKey, JsonNode> tasks) {
        var vehicle = plan.vehicles().stream().filter(v -> v.vehicleId() == trip.logicalVehicleId()).findFirst().orElseThrow();
        var planned = vehicle.trips().get(trip.tripIndex());
        var taskKeys = planned.nodes().stream().filter(n -> n.action() == GlobalPlan.Action.LOAD)
                .map(GlobalPlan.Node::task).toList();
        var queue = state.queues().stream().filter(q -> q.logicalVehicleId() == trip.logicalVehicleId()).findFirst();
        var tickStart = tick.path("tick").path("tickStart").asText();
        var waits = state.events().stream().filter(event -> event.trip().equals(trip) && "WAIT".equals(event.type())
                && event.simTime().equals(java.time.LocalDateTime.parse(tickStart))).toList();
        ObjectNode context = json.createObjectNode();
        context.put("notBeforeLoop", planned.notBeforeLoop());
        context.set("taskKeys", json.valueToTree(taskKeys));
        context.set("unreleasedAtTickEnd", json.valueToTree(taskKeys.stream().filter(key -> !tasks.containsKey(key)).toList()));
        context.set("queueAtTickEnd", json.valueToTree(queue.orElse(null)));
        context.set("recordedDispatchWaits", json.valueToTree(waits));
        context.put("dispatchWaitEvidence", waits.isEmpty() ? "INSUFFICIENT_AT_DISPATCH" : "RECORDED");
        return context;
    }

    private ObjectNode metrics(JsonNode tick) {
        var result = json.createObjectNode();
        var values = result.putObject("metrics");
        var metrics = tick.path("evaluation").path("metrics");
        for (String metric : OBJECTIVES) {
            var value = metrics.path(metric);
            if (!value.isMissingNode()) values.set(metric, fields(value, "status", "unit", "value"));
        }
        return result;
    }

    private final class TickFacts {
        private final JsonNode database;
        private final Map<Long, TripKey> bindings;
        private final Map<Long, GlobalPlan.TaskKey> itemKeys;
        private final Map<TripKey, JsonNode> assignments;
        private final Map<TripKey, List<JsonNode>> nodes, legs, drivers;
        private final Map<GlobalPlan.TaskKey, JsonNode> tasks = new HashMap<>();

        TickFacts(JsonNode tick, Map<Long, TripKey> bindings, Map<Long, GlobalPlan.TaskKey> itemKeys) {
            database = tick.path("facts").path("database");
            this.bindings = bindings;
            this.itemKeys = itemKeys;
            assignments = rows(database, "assignment").values().stream().collect(Collectors.toMap(
                    row -> boundTrip(row, "id"), row -> row));
            nodes = byTrip("assignment_nodes");
            legs = byTrip("assignment_leg");
            drivers = byTrip("assignment_driver_history");
            rows(database, "shipment_item").forEach((id, row) -> {
                var task = itemKeys.get(id);
                if (task == null || tasks.putIfAbsent(task, fields(row, "status", "processing_status",
                        "processing_start_time", "processing_end_time")) != null)
                    throw new IllegalArgumentException("Missing or conflicting stable task in trace: " + id);
            });
        }

        private TripKey boundTrip(JsonNode row, String field) {
            long id = id(row, field);
            var trip = bindings.get(id);
            if (trip == null) throw new IllegalArgumentException("Missing assignment-to-trip trace binding: " + id);
            return trip;
        }

        private Map<TripKey, List<JsonNode>> byTrip(String table) {
            return rows(database, table).values().stream().collect(Collectors.groupingBy(row -> boundTrip(row, "assignment_id")));
        }

        JsonNode trip(TripKey key) {
            var assignment = assignments.get(key);
            if (assignment == null) return MISSING;
            var result = json.createObjectNode();
            var projected = result.putObject("database");
            projected.putArray("assignment").add(fields(assignment, "start_time", "end_time", "vehicle_id", "driver_id",
                    "status", "current_action_index", "current_leg_index"));
            var nodeArray = projected.putArray("assignment_nodes");
            sequenced(nodes, key).forEach(row -> {
                var node = fields(row, "sequence_index", "action_type", "poi_id", "weight_delta", "volume_delta",
                        "is_completed", "actual_arrival_time");
                var task = itemKeys.get(id(row, "shipment_item_id"));
                if (task == null) throw new IllegalArgumentException("Unmapped route node for " + key);
                node.set("task", json.valueToTree(task));
                nodeArray.add(node);
            });
            var legArray = projected.putArray("assignment_leg");
            sequenced(legs, key).forEach(row -> legArray.add(fields(row, "sequence_index", "vehicle_id",
                    "from_poi_id", "to_poi_id", "distance_meters", "driving_seconds", "load_state", "current_load_tonnes",
                    "progress_status", "started_sim_time", "completed_sim_time", "executed_distance_meters",
                    "executed_driving_seconds", "executed_energy_liters", "executed_emission_kg")));
            appendSorted(projected.putArray("assignment_driver_history"), drivers.getOrDefault(key, List.of()).stream()
                    .map(row -> fields(row, "driver_id", "sim_time", "action", "from_status", "to_status")));
            return result;
        }

        JsonNode events() {
            var result = json.createObjectNode();
            var projected = result.putObject("database");
            appendSorted(projected.putArray("transport_random_event"), rows(database, "transport_random_event").values().stream()
                    .map(row -> event(row, "trigger_loop_index", "event_type", "status", "trigger_source", "vehicle_id",
                            "start_time", "planned_end_time", "resolved_time", "delay_seconds", "breakdown_level",
                            "breakdown_phase", "replacement_vehicle_id", "replacement_processed_time", "replacement_outcome")));
            appendSorted(projected.putArray("vehicle_replacement_attempt"), rows(database, "vehicle_replacement_attempt").values().stream()
                    .map(row -> event(row, "original_vehicle_id", "replacement_vehicle_id", "original_driver_id",
                            "replacement_driver_id", "selected_time", "ready_time", "handoff_time", "status", "outcome")));
            return result;
        }

        private ObjectNode event(JsonNode row, String... columns) {
            var result = fields(row, columns);
            result.set("trip", json.valueToTree(boundTrip(row, "assignment_id")));
            return result;
        }
    }

    private static List<JsonNode> sequenced(Map<TripKey, List<JsonNode>> rows, TripKey key) {
        return rows.getOrDefault(key, List.of()).stream()
                .sorted(Comparator.comparingInt(row -> row.path("sequence_index").asInt())).toList();
    }

    private static void appendSorted(ArrayNode target, java.util.stream.Stream<ObjectNode> rows) {
        rows.sorted(Comparator.comparing(JsonNode::toString)).forEach(target::add);
    }

    private ObjectNode fields(JsonNode row, String... names) {
        var selected = json.createObjectNode();
        for (String name : names) if (row.has(name)) selected.set(name, row.get(name));
        return selected;
    }
}
