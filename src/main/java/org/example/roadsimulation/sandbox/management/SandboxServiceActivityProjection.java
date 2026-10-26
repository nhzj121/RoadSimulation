package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.*;

/** Counts recorded node services, NOT vehicle-state transitions or map callbacks. */
public final class SandboxServiceActivityProjection {
    private final ObjectMapper json;
    public SandboxServiceActivityProjection(ObjectMapper json) { this.json = json; }

    public ObjectNode project(JsonNode database, JsonNode evaluation, LocalDateTime runStart,
                              LocalDateTime lower, LocalDateTime upper, boolean includeInitialBoundary) {
        ObjectNode result = json.createObjectNode();
        result.put("source", "RECORDED_NODE_SERVICE_EPISODES");
        result.put("boundaryPolicy", includeInitialBoundary ? "INITIAL_START_INCLUSIVE_END_INCLUSIVE" : "START_EXCLUSIVE_END_INCLUSIVE");
        if (!"1.9".equals(evaluation.path("contractVersion").asText()))
            return unavailable(result, "NOT_SUPPORTED", "历史评价契约未知，不能确认装卸观察账本口径");
        if (!database.path("node_service_episode").isArray())
            return unavailable(result, "NOT_AVAILABLE", "该历史轮次未记录装卸服务账本");
        for (String id : List.of("environmentNodeAverageServiceSeconds", "environmentNodeThroughputTonnes")) {
            if ("INVALID".equals(evaluation.path("metrics").path(id).path("status").asText()))
                return unavailable(result, "NOT_AVAILABLE", "本轮节点服务观察指标无效，无法确认次数完整性");
        }
        Map<Long, JsonNode> vehicles = rows(database, "vehicle"), assignments = rows(database, "assignment"),
                nodes = rows(database, "assignment_nodes"), pois = rows(database, "poi");
        Set<Long> ids = new HashSet<>(), completedNodes = new HashSet<>();
        Set<String> keys = new HashSet<>(), activeServices = new HashSet<>();
        long loadStarted = 0, loadCompleted = 0, unloadStarted = 0, unloadCompleted = 0;
        for (JsonNode episode : database.path("node_service_episode")) {
            long id = positive(episode.path("id")), vehicle = positive(episode.path("vehicle_id")), assignment = positive(episode.path("assignment_id"));
            String action = episode.path("action_type").asText(), status = episode.path("status").asText(), key = episode.path("event_key").asText();
            require(episode.isObject() && ids.add(id) && !key.isBlank() && keys.add(key));
            require(Set.of("LOAD", "UNLOAD").contains(action) && Set.of("IN_SERVICE", "COMPLETED").contains(status));
            require(vehicles.containsKey(vehicle) && assignments.containsKey(assignment) && pois.containsKey(positive(episode.path("poi_id"))));
            JsonNode nodeId = episode.path("assignment_node_id");
            if (!nodeId.isMissingNode() && !nodeId.isNull()) {
                long node = positive(nodeId); JsonNode fact = nodes.get(node);
                require(fact != null && positive(fact.path("assignment_id")) == assignment
                        && action.equals(fact.path("action_type").asText()) && key.equals("node:" + node));
                if ("COMPLETED".equals(status)) completedNodes.add(node);
            } else {
                JsonNode sequence = episode.path("leg_sequence_index");
                require(sequence.isIntegralNumber() && sequence.canConvertToInt() && sequence.asInt() >= 0
                        && key.equals("assignment:" + assignment + ":leg:" + sequence.asInt() + ":" + action));
            }
            LocalDateTime started = time(episode.path("service_started_at"));
            require(!started.isBefore(runStart) && !started.isAfter(upper));
            LocalDateTime completed = null;
            if ("COMPLETED".equals(status)) {
                completed = time(episode.path("service_completed_at"));
                require(!completed.isBefore(started) && !completed.isAfter(upper));
            } else {
                require(episode.path("service_completed_at").isNull() || episode.path("service_completed_at").isMissingNode());
                activeServices.add(vehicle + ":" + action);
            }
            if (inside(started, lower, upper, includeInitialBoundary)) {
                if ("LOAD".equals(action)) loadStarted++; else unloadStarted++;
            }
            if (completed != null && inside(completed, lower, upper, includeInitialBoundary)) {
                if ("LOAD".equals(action)) loadCompleted++; else unloadCompleted++;
            }
        }
        // A completion without a matching start is not a trustworthy zero. The
        // lifecycle can commit even when its observation publisher rejected it.
        for (var entry : nodes.entrySet()) {
            JsonNode node = entry.getValue();
            if (Set.of("LOAD", "UNLOAD").contains(node.path("action_type").asText())
                    && node.path("is_completed").asBoolean() && !completedNodes.contains(entry.getKey()))
                return unavailable(result, "NOT_AVAILABLE", "已完成装卸节点缺少匹配的完整服务记录");
        }
        for (var entry : vehicles.entrySet()) {
            String status = entry.getValue().path("current_status").asText();
            String action = "LOADING".equals(status) ? "LOAD" : "UNLOADING".equals(status) ? "UNLOAD" : null;
            if (action != null && !activeServices.contains(entry.getKey() + ":" + action))
                return unavailable(result, "NOT_AVAILABLE", "正在装卸的车辆缺少匹配的服务开始记录");
        }
        result.put("status", "AVAILABLE"); result.putNull("reason");
        var counts = result.putObject("counts");
        counts.put("loadStarted", loadStarted); counts.put("loadCompleted", loadCompleted);
        counts.put("unloadStarted", unloadStarted); counts.put("unloadCompleted", unloadCompleted);
        result.put("coverage", "RECORDED_EPISODES_WITH_COMPLETED_NODE_AND_ACTIVE_VEHICLE_CROSSCHECK");
        return result;
    }
    private ObjectNode unavailable(ObjectNode result, String status, String reason) {
        result.put("status", status); result.put("reason", reason); result.putNull("counts"); return result;
    }
    private boolean inside(LocalDateTime event, LocalDateTime lower, LocalDateTime upper, boolean include) {
        return !event.isAfter(upper) && (event.isAfter(lower) || include && event.equals(lower));
    }
    private Map<Long, JsonNode> rows(JsonNode database, String table) {
        require(database.path(table).isArray()); Map<Long, JsonNode> result = new HashMap<>();
        for (JsonNode row : database.path(table)) require(row.isObject() && result.put(positive(row.path("id")), row) == null);
        return result;
    }
    private long positive(JsonNode value) { require(value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0); return value.asLong(); }
    private LocalDateTime time(JsonNode value) {
        require(value.isTextual()); try { return LocalDateTime.parse(value.asText()); } catch (RuntimeException ex) { throw SandboxResultReader.corrupt(); }
    }
    private void require(boolean condition) { SandboxResultReader.require(condition, "EXECUTION_RECORD_CORRUPT"); }
}
