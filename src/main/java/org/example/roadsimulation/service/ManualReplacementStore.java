package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Ordinary-run workflow state. Reservation ownership uses the existing event ID namespace. */
@Repository
@Profile("!sandbox-runtime")
public class ManualReplacementStore {
    public enum Phase { WAITING, EN_ROUTE, HANDED_OFF, VISUAL_ONLY, FINISHED, TERMINATED }

    record State(long eventId, String runId, String requestId, long originalVehicleId, long assignmentId,
                 double longitude, double latitude, int nextStopIndex, Phase phase, Long attemptId,
                 Double spareLongitude, Double spareLatitude, boolean restored, String reason) {
        State with(Phase phase, Long attempt, Double longitude, Double latitude, String reason) {
            return new State(eventId, runId, requestId, originalVehicleId, assignmentId, this.longitude,
                    this.latitude, nextStopIndex, phase, attempt, longitude, latitude, restored, reason);
        }
    }

    private static final RowMapper<State> ROW = (row, index) -> new State(
            row.getLong("event_id"), row.getString("run_id"), row.getString("request_id"),
            row.getLong("original_vehicle_id"), row.getLong("assignment_id"), row.getDouble("longitude"),
            row.getDouble("latitude"), row.getInt("next_stop_index"), Phase.valueOf(row.getString("phase")),
            row.getObject("attempt_id", Long.class), row.getObject("spare_longitude", Double.class),
            row.getObject("spare_latitude", Double.class), row.getBoolean("restored"), row.getString("reason"));

    private final JdbcTemplate jdbc;
    private final SimulationContext clock;
    private volatile boolean installed;
    private volatile long nextSchemaCheck;

    public ManualReplacementStore(JdbcTemplate jdbc, SimulationContext clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean installed() {
        if (installed) return true;
        long now = System.currentTimeMillis();
        if (now < nextSchemaCheck) return false;
        synchronized (this) {
            if (installed) return true;
            if (now < nextSchemaCheck) return false;
            installed = Integer.valueOf(1).equals(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ordinary_manual_replacement'
                    """, Integer.class));
            nextSchemaCheck = now + 10_000;
            return installed;
        }
    }

    Optional<State> request(String run, String request) {
        return jdbc.query("SELECT * FROM ordinary_manual_replacement WHERE run_id = ? AND request_id = ?",
                ROW, run, request).stream().findFirst();
    }

    Optional<State> lock(String run, long event) {
        return jdbc.query("SELECT * FROM ordinary_manual_replacement WHERE run_id = ? AND event_id = ? FOR UPDATE",
                ROW, run, event).stream().findFirst();
    }

    Optional<State> find(String run, long event) {
        return jdbc.query("SELECT * FROM ordinary_manual_replacement WHERE run_id = ? AND event_id = ?",
                ROW, run, event).stream().findFirst();
    }

    List<State> open(String run) {
        return jdbc.query("""
                SELECT * FROM ordinary_manual_replacement
                WHERE run_id = ? AND (restored = FALSE OR phase = 'HANDED_OFF') ORDER BY event_id
                """, ROW, run);
    }

    List<State> waiting(String run) {
        return jdbc.query("""
                SELECT * FROM ordinary_manual_replacement
                WHERE run_id = ? AND phase = 'WAITING' ORDER BY event_id
                """, ROW, run);
    }

    void insert(State state) {
        jdbc.update("""
                INSERT INTO ordinary_manual_replacement
                (event_id, run_id, request_id, original_vehicle_id, assignment_id, longitude, latitude,
                 next_stop_index, phase) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, state.eventId(), state.runId(), state.requestId(), state.originalVehicleId(), state.assignmentId(),
                state.longitude(), state.latitude(), state.nextStopIndex(), state.phase().name());
    }

    State update(State state) {
        jdbc.update("""
                UPDATE ordinary_manual_replacement SET phase = ?, attempt_id = ?, spare_longitude = ?,
                 spare_latitude = ?, reason = ? WHERE event_id = ?
                """, state.phase().name(), state.attemptId(), state.spareLongitude(), state.spareLatitude(),
                state.reason(), state.eventId());
        return state;
    }

    void restore(long event) {
        jdbc.update("UPDATE ordinary_manual_replacement SET restored = TRUE WHERE event_id = ?", event);
    }

    public Set<Long> heldVehicleIds() {
        String run = clock.getSimulationRunId().orElse(null);
        if (run == null || clock.isDeterministicSandboxRun() || !installed()) return Set.of();
        return new HashSet<>(jdbc.queryForList("""
                SELECT original_vehicle_id FROM ordinary_manual_replacement
                WHERE run_id = ? AND restored = FALSE
                """, Long.class, run));
    }

    public List<Vehicle> filterDispatchable(List<Vehicle> candidates) {
        var held = heldVehicleIds();
        return held.isEmpty() ? candidates : candidates.stream().filter(vehicle -> !held.contains(vehicle.getId())).toList();
    }

    void requireDispatchClaim(long vehicle, Long assignment) {
        String run = clock.getSimulationRunId().orElse(null);
        if (run == null || clock.isDeterministicSandboxRun() || !installed()) return;
        var statuses = jdbc.queryForList("SELECT current_status FROM vehicle WHERE id = ? FOR UPDATE", String.class, vehicle);
        if (statuses.contains("RESERVED_REPLACEMENT")) throw new IllegalStateException("车辆已预留换车");
        var tasks = jdbc.queryForList("""
                SELECT assignment_id FROM ordinary_manual_replacement
                WHERE run_id = ? AND restored = FALSE AND original_vehicle_id = ? FOR UPDATE
                """, Long.class, run, vehicle);
        if (!tasks.isEmpty() && !tasks.contains(assignment)) throw new IllegalStateException("故障车辆暂停接单");
    }

    public void clearOrdinaryRun() {
        if (!clock.isDeterministicSandboxRun() && installed())
            jdbc.update("DELETE FROM ordinary_manual_replacement");
    }
}
