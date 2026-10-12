package org.example.roadsimulation.sandbox.management;

import org.example.roadsimulation.sandbox.workspace.*;
import java.sql.*;
import java.util.*;

/** Durable reservation precedes process launch. Only verified terminal workers release it. */
public final class SandboxManagementJobStore {
    private final SandboxManagementSettings settings;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    public SandboxManagementJobStore(SandboxManagementSettings settings) { this.settings = settings; }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(settings.url(), settings.user(), settings.password());
        try {
            safety.requireSafeTarget(settings.url(), connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
                if (!rows.next() || !Set.of("sandbox-control-schema/v8", "sandbox-control-schema/v9").contains(rows.getString(1)))
                    throw new SandboxWorkspaceException("CONTROL_SCHEMA_NOT_READY", "Administrator must provision control schema v8");
            }
            return connection;
        } catch (Exception failure) { connection.close(); throw failure; }
    }

    public Map<String, Object> reserve(String key, int revision) {
        return reserveInternal(key, revision, null).job();
    }

    public record Reservation(Map<String, Object> job, boolean created) {}
    public record StartReceipt(String jobId, String clientRequestId, String runSpecKey, int revision, boolean reused) {}
    public Reservation reserveRequest(String key, int revision, String requestId) {
        uuid(requestId);
        return reserveInternal(key, revision, requestId);
    }
    public static StartReceipt receipt(Reservation reservation, String requestId) {
        var row = reservation.job();
        return new StartReceipt(row.get("job_id").toString(), requestId, row.get("run_spec_key").toString(),
                ((Number) row.get("run_spec_revision")).intValue(), !reservation.created());
    }
    public StartReceipt findRequest(String requestId) {
        uuid(requestId);
        try (var connection = open()) {
            requireRequestSchema(connection);
            var existing = requestRow(connection, requestId);
            require(existing != null, "START_REQUEST_NOT_FOUND");
            return receipt(new Reservation(existing, false), requestId);
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }
    private Reservation reserveInternal(String key, int revision, String requestId) {
        key(key); positive(revision);
        try (var connection = open()) {
            if (requestId != null) {
                requireRequestSchema(connection);
                var replay = replay(connection, key, revision, requestId);
                if (replay != null) return replay;
            }
            try { safety.acquirePreparationLock(connection); }
            catch (SandboxWorkspaceException busy) {
                if (requestId != null && "WORKSPACE_BUSY".equals(busy.errorCode())) {
                    var replay = replay(connection, key, revision, requestId);
                    if (replay != null) return replay;
                }
                throw busy;
            }
            try {
                if (requestId != null) {
                    var replay = replay(connection, key, revision, requestId);
                    if (replay != null) return replay;
                }
                safety.requireNoUnfinishedExecution(connection);
                requireNoUnboundExecution(connection);
                try (var query = connection.prepareStatement("SELECT artifact_version FROM sandbox_run_spec_revision WHERE run_spec_key=? AND revision_no=? AND archived=b'0'")) {
                    query.setString(1, key); query.setInt(2, revision);
                    try (var rows = query.executeQuery()) {
                        if (!rows.next()) throw new SandboxWorkspaceException("RUN_SPEC_REVISION_NOT_FOUND", "Publish the requested revision first");
                        if (!"sandbox-run-specification-revision/v2".equals(rows.getString(1)))
                            throw new SandboxWorkspaceException("UNSUPPORTED_RUN_SPEC_VERSION", "Management executes v2 revisions only");
                    }
                }
                String id = UUID.randomUUID().toString();
                connection.setAutoCommit(false);
                try {
                    String insertSql = requestId == null
                            ? "INSERT INTO sandbox_management_job(job_id,run_spec_key,run_spec_revision,job_status) VALUES(?,?,?,'ACCEPTED')"
                            : "INSERT INTO sandbox_management_job(job_id,run_spec_key,run_spec_revision,job_status,client_request_id) VALUES(?,?,?,'ACCEPTED',?)";
                    try (var insert = connection.prepareStatement(insertSql)) {
                        insert.setString(1, id); insert.setString(2, key); insert.setInt(3, revision);
                        if (requestId != null) insert.setString(4, requestId);
                        insert.executeUpdate();
                    }
                    try (var update = connection.prepareStatement("UPDATE sandbox_workspace_marker SET active_job_id=? WHERE marker_id=1 AND active_job_id IS NULL")) {
                        update.setString(1, id); require(update.executeUpdate() == 1, "WORKSPACE_BUSY");
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
                return new Reservation(Map.of("job_id", id, "run_spec_key", key, "run_spec_revision", revision,
                        "job_status", "ACCEPTED", "workerObservation", "NOT_STARTED", "attentionRequired", false), true);
            } finally { safety.releasePreparationLock(connection); }
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    private Reservation replay(Connection connection, String key, int revision, String requestId) throws SQLException {
        var row = requestRow(connection, requestId);
        if (row == null) return null;
        require(key.equals(row.get("run_spec_key")) && revision == ((Number) row.get("run_spec_revision")).intValue(), "START_REQUEST_CONFLICT");
        return new Reservation(row, false);
    }
    private Map<String, Object> requestRow(Connection connection, String requestId) throws SQLException {
        try (var query = connection.prepareStatement("SELECT job_id,run_spec_key,run_spec_revision FROM sandbox_management_job WHERE client_request_id=?")) {
            query.setString(1, requestId);
            try (var rows = query.executeQuery()) { return rows.next() ? row(rows) : null; }
        }
    }
    private void requireRequestSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
            require(rows.next() && "sandbox-control-schema/v9".equals(rows.getString(1)), "START_CONTROL_SCHEMA_NOT_READY");
        }
    }

    public void claim(String id) {
        uuid(id);
        try (var connection = open()) {
            safety.acquirePreparationLock(connection);
            try {
                safety.requireNoUnfinishedExecution(connection, id);
                try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status='PREPARING',worker_pid=?,worker_started_at=?,updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND job_status='ACCEPTED'")) {
                    update.setLong(1, ProcessHandle.current().pid());
                    update.setString(2, ProcessHandle.current().info().startInstant().orElseThrow().toString());
                    update.setString(3, id); require(update.executeUpdate() == 1, "JOB_ALREADY_CLAIMED");
                }
            } finally { safety.releasePreparationLock(connection); }
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    /** Cancellation changes control state only. Row locking serializes claim and finalization. */
    public Map<String, Object> cancel(String id) {
        uuid(id);
        try (var connection = open()) {
            connection.setAutoCommit(false);
            try {
                String status;
                try (var query = connection.prepareStatement("SELECT job_status FROM sandbox_management_job WHERE job_id=? FOR UPDATE")) {
                    query.setString(1, id);
                    try (var rows = query.executeQuery()) {
                        require(rows.next(), "JOB_NOT_FOUND"); status = rows.getString(1);
                    }
                }
                if ("CANCELLED".equals(status)) {
                    connection.commit(); return Map.of("jobId", id, "status", status);
                }
                require(Set.of("ACCEPTED", "RUNNING", "CANCEL_REQUESTED").contains(status), "CANCEL_NOT_ALLOWED");
                safety.requireManagementJobOwner(connection, id);
                if (!"CANCEL_REQUESTED".equals(status)) {
                    try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET cancel_requested_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),job_status=? WHERE job_id=?")) {
                        update.setString(1, "ACCEPTED".equals(status) ? "CANCELLED" : "CANCEL_REQUESTED");
                        update.setString(2, id); update.executeUpdate();
                    }
                    if ("ACCEPTED".equals(status)) {
                        try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET finished_at=CURRENT_TIMESTAMP(6),failure_code='USER_CANCELLED' WHERE job_id=?")) {
                            update.setString(1, id); update.executeUpdate();
                        }
                        releaseReservation(connection, id);
                    }
                }
                connection.commit();
                return Map.of("jobId", id, "status", "ACCEPTED".equals(status) ? "CANCELLED" : "CANCEL_REQUESTED");
            } catch (Exception failure) { connection.rollback(); throw failure; }
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    /** Shares the lease transaction with execution/workspace terminal writes. */
    public static void finishWithLease(Connection connection, String id, String executionId,
                                       String status, String code) throws SQLException {
        var safety = new SandboxWorkspaceSafety();
        safety.requirePreparationLockHeld(connection); safety.requireManagementJobOwner(connection, id);
        require(!connection.getAutoCommit(), "JOB_TRANSACTION_REQUIRED");
        String expectedExecution = switch (status) {
            case "SUCCEEDED" -> "COMPLETED"; case "FAILED" -> "FAILED"; case "CANCELLED" -> "CANCELLED";
            default -> throw new SandboxWorkspaceException("INVALID_JOB_TERMINAL", "Unsupported terminal state");
        };
        try (var query = connection.prepareStatement("SELECT j.job_status,j.cancel_requested_at,e.execution_status FROM sandbox_management_job j JOIN sandbox_execution e ON e.execution_id=j.execution_id WHERE j.job_id=? AND e.execution_id=? FOR UPDATE")) {
            query.setString(1, id); query.setString(2, executionId);
            try (var rows = query.executeQuery()) {
                require(rows.next() && expectedExecution.equals(rows.getString(3)), "JOB_EXECUTION_NOT_TERMINAL");
                if (!"FAILED".equals(status)) require("FINALIZING".equals(rows.getString(1)), "JOB_NOT_FINALIZING");
                if ("CANCELLED".equals(status)) require(rows.getTimestamp(2) != null, "CANCELLATION_NOT_REQUESTED");
            }
        }
        try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status=?,failure_code=?,finished_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND execution_id=? AND job_status IN('PREPARING','RUNNING','CANCEL_REQUESTED','FINALIZING')")) {
            update.setString(1, status); update.setString(2, code); update.setString(3, id); update.setString(4, executionId);
            require(update.executeUpdate() == 1, "JOB_STATE_MISMATCH");
        }
        releaseReservation(connection, id);
    }

    private static void releaseReservation(Connection connection, String id) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE sandbox_workspace_marker SET active_job_id=NULL WHERE marker_id=1 AND active_job_id=?")) {
            update.setString(1, id); require(update.executeUpdate() == 1, "JOB_STATE_MISMATCH");
        }
    }

    /** Launch/preparation failure may release only when no execution was created and the lock is free. */
    public void failBeforeExecution(String id, String errorCode) {
        try (var connection = open()) {
            safety.acquirePreparationLock(connection);
            try {
                safety.requireNoUnfinishedExecution(connection, id);
                connection.setAutoCommit(false);
                try {
                    try (var query = connection.prepareStatement("SELECT execution_id FROM sandbox_management_job WHERE job_id=? FOR UPDATE")) {
                        query.setString(1, id);
                        try (var rows = query.executeQuery()) { require(rows.next() && rows.getString(1) == null, "JOB_REQUIRES_INSPECTION"); }
                    }
                    requireNoUnboundExecution(connection);
                    terminal(connection, id, "FAILED", errorCode, true); connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            } finally { safety.releasePreparationLock(connection); }
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    /** Unexpected process exit is not a completed/failed simulation. Keep the reservation and facts. */
    public void interrupted(String id) {
        try (var connection = open(); var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status='INTERRUPTED',failure_code='WORKER_EXITED',finished_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND job_status IN('ACCEPTED','PREPARING','RUNNING','CANCEL_REQUESTED','FINALIZING')")) {
            update.setString(1, id); update.executeUpdate();
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    private void terminal(Connection connection, String id, String status, String code, boolean release) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status=?,failure_code=?,finished_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND job_status IN('ACCEPTED','PREPARING','RUNNING')")) {
            update.setString(1, status); update.setString(2, code); update.setString(3, id); require(update.executeUpdate() == 1, "JOB_STATE_MISMATCH");
        }
        if (release) try (var update = connection.prepareStatement("UPDATE sandbox_workspace_marker SET active_job_id=NULL WHERE marker_id=1 AND active_job_id=?")) {
            update.setString(1, id); require(update.executeUpdate() == 1, "JOB_STATE_MISMATCH");
        }
    }

    public Map<String, Object> get(String id) {
        uuid(id);
        try (var connection = open(); var query = connection.prepareStatement("SELECT j.*,e.execution_status,e.requested_loops,e.completed_loops,e.failure_phase,TIMESTAMPDIFF(SECOND,j.created_at,CURRENT_TIMESTAMP)>=60 AS stale_acceptance FROM sandbox_management_job j LEFT JOIN sandbox_execution e ON e.execution_id=j.execution_id WHERE j.job_id=?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new SandboxWorkspaceException("JOB_NOT_FOUND", "Unknown sandbox job");
                Map<String, Object> result = row(rows);
                result.remove("closure_report_json"); // Audit report is not a polling payload.
                boolean active = Set.of("ACCEPTED", "PREPARING", "RUNNING", "CANCEL_REQUESTED", "FINALIZING", "INTERRUPTED").contains(rows.getString("job_status"));
                long pid = rows.getLong("worker_pid"); String started = rows.getString("worker_started_at");
                boolean alive = started != null && ProcessHandle.of(pid).flatMap(handle -> handle.info().startInstant())
                        .map(value -> value.toString().equals(started)).orElse(false);
                boolean waiting = "ACCEPTED".equals(rows.getString("job_status"));
                result.remove("stale_acceptance");
                result.put("workerObservation", !active ? "TERMINAL" : alive ? "ALIVE" : waiting ? "NOT_STARTED" : "NOT_OBSERVED");
                result.put("attentionRequired", active && !alive && (!waiting || rows.getBoolean("stale_acceptance"))); return result;
            }
        } catch (SQLException failure) { throw databaseFailure(failure); }
    }

    public static Map<String, Object> row(ResultSet rows) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        var metadata = rows.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            Object value = rows.getObject(i);
            if (value instanceof Timestamp timestamp) value = timestamp.toLocalDateTime().toString();
            if (value instanceof byte[]) value = rows.getBoolean(i);
            result.put(metadata.getColumnLabel(i), value);
        }
        return result;
    }
    private static void requireNoUnboundExecution(Connection connection) throws SQLException {
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM sandbox_execution e LEFT JOIN sandbox_management_job j ON j.execution_id=e.execution_id WHERE e.execution_status IN('CREATED','RUNNING') AND j.job_id IS NULL")) {
            require(rows.next() && rows.getInt(1) == 0, "JOB_REQUIRES_INSPECTION");
        }
    }
    public static void key(String key) { if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) throw new SandboxWorkspaceException("INVALID_KEY", "Invalid sandbox key"); }
    public static void uuid(String id) { try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); } catch (Exception failure) { throw new SandboxWorkspaceException("INVALID_ID", "Expected canonical UUID"); } }
    public static void positive(int value) { if (value <= 0) throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Revision must be positive"); }
    private static void require(boolean condition, String code) { if (!condition) throw new SandboxWorkspaceException(code, "Sandbox job state check failed"); }
    private static SandboxWorkspaceException databaseFailure(SQLException failure) { return new SandboxWorkspaceException("MANAGEMENT_DATABASE_FAILED", "Sandbox management database operation failed", failure); }
}
