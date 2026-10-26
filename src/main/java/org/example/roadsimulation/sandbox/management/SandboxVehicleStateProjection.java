package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.*;

/** Display-only state presence. Never counts samples, transitions or service completions. */
public final class SandboxVehicleStateProjection {
    public static final List<String> GROUPS = List.of("empty", "loaded", "loading", "unloading", "waiting", "abnormal", "other");
    private final ObjectMapper json;
    private final LocalDateTime lower, upper;
    private final Set<Long> expectedVehicles;
    private final Map<String, Set<Long>> presence = new LinkedHashMap<>();
    private final Set<Long> uncertainReservations = new HashSet<>();
    private String uncertainty;

    public SandboxVehicleStateProjection(ObjectMapper json, JsonNode initial, LocalDateTime lower, LocalDateTime upper) {
        this.json = json; this.lower = lower; this.upper = upper;
        expectedVehicles = rows(initial, "vehicle").keySet();
        GROUPS.forEach(group -> presence.put(group, new HashSet<>()));
    }

    public void observe(JsonNode database, LocalDateTime observedAt, boolean boundary) {
        var vehicles = vehicles(database);
        for (var entry : vehicles.entrySet()) {
            JsonNode row = entry.getValue(); String status = text(row, "current_status");
            if ("other".equals(group(status))) uncertainty = "历史记录包含无法解释的车辆状态，不能确认完整窗口统计";
            if (!boundary) add(group(status), entry.getKey());
            else if ("WAITING".equals(status)) {
                // A PASS_BY may finish inside the first window tick. Its frozen
                // action duration, not today's configured duration, owns that interval.
                JsonNode started = row.path("status_start_time"), duration = row.path("status_duration_seconds");
                if (started.isTextual() && duration.isIntegralNumber() && duration.canConvertToLong() && duration.asLong() > 0) {
                    LocalDateTime from = time(started);
                    require(!from.isAfter(observedAt));
                    if (from.plusSeconds(duration.asLong()).isAfter(lower)) add("waiting", entry.getKey());
                } else uncertainty = "窗口起点的等待状态缺少可确认的持续时间";
            }
        }
    }

    public ObjectNode current(JsonNode database) {
        var vehicles = vehicles(database);
        ObjectNode result = json.createObjectNode(), current = result.putObject("current");
        Map<String, Set<Long>> now = new LinkedHashMap<>(); GROUPS.forEach(g -> now.put(g, new HashSet<>()));
        for (var entry : vehicles.entrySet()) {
            String group = group(text(entry.getValue(), "current_status"));
            if (group != null) now.get(group).add(entry.getKey());
        }
        current.put("status", "AVAILABLE"); current.putNull("reason"); current.put("totalVehicles", vehicles.size());
        current.put("nonIdleVehicles", now.values().stream().mapToInt(Set::size).sum()); current.set("counts", counts(now));
        var window = result.putObject("window"); window.put("status", "NOT_REQUESTED"); window.putNull("reason");
        window.putNull("counts"); window.putNull("distinctNonIdleVehicles"); return result;
    }

    public ObjectNode finish(JsonNode database, JsonNode evaluation, LocalDateTime runStart) {
        ObjectNode result = current(database), window = (ObjectNode) result.path("window");
        var vehicles = vehicles(database);
        var service = new SandboxServiceActivityProjection(json).project(database, evaluation, runStart, lower, upper, lower.equals(runStart));
        if (!"AVAILABLE".equals(service.path("status").asText()))
            return unavailable(result, window, service.path("status").asText(), service.path("reason").asText());
        for (String table : List.of("transport_execution_segment", "transport_random_event", "vehicle_replacement_attempt", "assignment_leg"))
            if (!database.path(table).isArray()) return unavailable(result, window, "NOT_AVAILABLE", "历史冻结记录缺少" + table + "，不能确认状态覆盖");

        // Cumulative service intervals capture short LOAD/UNLOAD phases even
        // when neither is visible in an end-tick vehicle snapshot.
        for (JsonNode episode : database.path("node_service_episode")) {
            LocalDateTime started = time(episode.path("service_started_at"));
            LocalDateTime completed = optionalTime(episode.path("service_completed_at"));
            if (completed == null) {
                String required = "LOAD".equals(text(episode, "action_type")) ? "LOADING" : "UNLOADING";
                if (!required.equals(vehicles.get(vehicle(episode.path("vehicle_id"))).path("current_status").asText()))
                    return unavailable(result, window, "NOT_AVAILABLE", "未闭合装卸记录与本轮车辆状态不一致，不能推测其结束时间");
            }
            if (overlaps(started, completed)) add("LOAD".equals(text(episode, "action_type")) ? "loading" : "unloading", vehicle(episode.path("vehicle_id")));
        }
        var legs = rows(database, "assignment_leg");
        for (JsonNode segment : rows(database, "transport_execution_segment").values()) {
            long id = positive(segment.path("leg_id")); require(legs.containsKey(id));
            require(Objects.equals(segment.get("assignment_id"), legs.get(id).get("assignment_id")));
            LocalDateTime from = time(segment.path("from_sim_time")), to = time(segment.path("to_sim_time"));
            require(!to.isBefore(from) && !from.isBefore(runStart) && !to.isAfter(upper));
            String load = text(segment, "load_state"); require(Set.of("EMPTY", "LOADED").contains(load));
            if (overlaps(from, to)) add("EMPTY".equals(load) ? "empty" : "loaded", vehicle(segment.path("vehicle_id")));
        }
        for (JsonNode leg : legs.values()) {
            LocalDateTime started = optionalTime(leg.path("started_sim_time"));
            if (started != null && inside(started)) {
                String load = text(leg, "load_state"); require(Set.of("EMPTY", "LOADED").contains(load));
                // This is only the actual start owner. Never attribute an entire
                // replacement leg's interval to its immutable planning owner.
                add("EMPTY".equals(load) ? "empty" : "loaded", vehicle(leg.path("vehicle_id")));
            }
        }
        for (JsonNode node : database.path("assignment_nodes")) {
            if (!"PASS_BY".equals(node.path("action_type").asText())) continue;
            LocalDateTime arrived = optionalTime(node.path("actual_arrival_time"));
            if (arrived == null || !inside(arrived)) continue;
            for (JsonNode leg : legs.values()) if (Objects.equals(leg.get("to_node_id"), node.get("id"))) add("waiting", vehicle(leg.path("vehicle_id")));
        }
        for (JsonNode event : rows(database, "transport_random_event").values()) {
            if (!"VEHICLE_BREAKDOWN".equals(event.path("event_type").asText())) continue;
            LocalDateTime started = time(event.path("start_time")), ended = optionalTime(event.path("resolved_time"));
            if (overlaps(started, ended)) add("abnormal", vehicle(event.path("vehicle_id")));
        }
        for (JsonNode attempt : rows(database, "vehicle_replacement_attempt").values()) {
            LocalDateTime selected = time(attempt.path("selected_time")), handoff = optionalTime(attempt.path("handoff_time"));
            long id = vehicle(attempt.path("replacement_vehicle_id"));
            if ("CANCELLED".equals(text(attempt, "status")) && handoff == null) {
                // Legacy cancellations do not retain an end instant. A
                // selection within this window is still positive evidence;
                // an older cancelled interval cannot be guessed as ongoing.
                if (inside(selected)) add("abnormal", id);
                else if (!selected.isAfter(upper)) uncertainReservations.add(id);
            } else if (overlaps(selected, handoff)) add("abnormal", id);
        }
        uncertainReservations.removeAll(presence.get("abnormal"));
        if (!uncertainReservations.isEmpty()) uncertainty = "历史替换预留取消记录缺少结束时刻，无法确认窗口内异常车辆总数";
        if (uncertainty != null) return unavailable(result, window, "NOT_AVAILABLE", uncertainty);
        window.put("status", "AVAILABLE"); window.putNull("reason"); window.set("counts", counts(presence));
        window.put("distinctNonIdleVehicles", presence.values().stream().flatMap(Set::stream).distinct().count());
        return result;
    }

    private ObjectNode unavailable(ObjectNode result, ObjectNode window, String status, String reason) {
        window.put("status", status); window.put("reason", reason); window.putNull("counts"); window.putNull("distinctNonIdleVehicles"); return result;
    }
    private ObjectNode counts(Map<String, Set<Long>> data) {
        ObjectNode result = json.createObjectNode(); GROUPS.forEach(group -> result.put(group, data.get(group).size())); return result;
    }
    private Map<Long, JsonNode> vehicles(JsonNode db) {
        var result = rows(db, "vehicle"); require(result.keySet().equals(expectedVehicles)); return result;
    }
    private Map<Long, JsonNode> rows(JsonNode db, String table) {
        require(db.path(table).isArray()); Map<Long, JsonNode> result = new TreeMap<>();
        for (JsonNode row : db.path(table)) require(row.isObject() && result.put(positive(row.path("id")), row) == null);
        return result;
    }
    private void add(String group, long id) { if (group != null) presence.get(group).add(id); }
    private long vehicle(JsonNode value) { long id = positive(value); require(expectedVehicles.contains(id)); return id; }
    private long positive(JsonNode value) { require(value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0); return value.asLong(); }
    private String text(JsonNode row, String field) { require(row.path(field).isTextual() && !row.path(field).asText().isBlank()); return row.path(field).asText(); }
    private String group(String status) {
        return switch (status) {
            case "IDLE" -> null; case "ORDER_DRIVING" -> "empty"; case "TRANSPORT_DRIVING" -> "loaded";
            case "LOADING" -> "loading"; case "UNLOADING" -> "unloading"; case "WAITING" -> "waiting";
            case "BREAKDOWN", "SCRAPPED", "RESERVED_REPLACEMENT" -> "abnormal"; default -> "other";
        };
    }
    private boolean inside(LocalDateTime at) { return !at.isBefore(lower) && !at.isAfter(upper); }
    private boolean overlaps(LocalDateTime from, LocalDateTime to) {
        require(!from.isAfter(upper) && (to == null || !to.isBefore(from) && !to.isAfter(upper)));
        return !from.isAfter(upper) && (to == null || to.isAfter(lower) || from.equals(to) && inside(from));
    }
    private LocalDateTime optionalTime(JsonNode node) { return node.isMissingNode() || node.isNull() ? null : time(node); }
    private LocalDateTime time(JsonNode node) {
        require(node.isTextual()); try { return LocalDateTime.parse(node.asText()); } catch (RuntimeException e) { throw SandboxResultReader.corrupt(); }
    }
    private void require(boolean ok) { SandboxResultReader.require(ok, "EXECUTION_RECORD_CORRUPT"); }
}
