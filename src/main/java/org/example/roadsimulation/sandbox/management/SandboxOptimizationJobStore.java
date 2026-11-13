package org.example.roadsimulation.sandbox.management;

import org.example.roadsimulation.sandbox.optimization.SandboxOptimizationProfile;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety;

import java.sql.*;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** A parent owns the workspace across separate execution leases. Only a verified archive becomes its result. */
public final class SandboxOptimizationJobStore {
    public record Child(String jobId, String kind) {
        public Child {
            SandboxManagementJobStore.uuid(jobId);
            if (!Set.of("HEURISTIC_SOURCE", "BASELINE", "HEURISTIC_SEED", "CANDIDATE").contains(kind))
                throw new IllegalArgumentException("Invalid optimization attempt kind");
        }
    }

    private final SandboxManagementJobStore jobs;
    private final SandboxManagementSettings settings;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    private final String jobId;

    public SandboxOptimizationJobStore(SandboxManagementSettings settings, String jobId) {
        SandboxManagementJobStore.uuid(jobId);
        this.settings = settings;
        this.jobs = new SandboxManagementJobStore(settings);
        this.jobId = jobId;
    }

    public static boolean available(Connection connection) throws SQLException {
        return "sandbox-control-schema/v10".equals(value(connection,
                "SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1"));
    }

    public static boolean isOptimization(Connection connection, String id) throws SQLException {
        return available(connection) && id.equals(value(connection,
                "SELECT job_id FROM sandbox_optimization_job WHERE job_id=?", id));
    }

    static void create(Connection connection, String id) throws SQLException {
        update(connection, "INSERT INTO sandbox_optimization_job(job_id,configuration_sha256) VALUES(?,?)",
                id, SandboxOptimizationProfile.identity().parameters().get("configurationSha256"));
    }

    static void recordStopIfManaged(Connection connection, String id, String reason) throws SQLException {
        if (!available(connection)) return;
        try (var update = connection.prepareStatement("UPDATE sandbox_optimization_job SET stop_reason=? WHERE job_id=?")) {
            update.setString(1, reason);
            update.setString(2, id);
            update.executeUpdate();
        }
    }

    public String jobId() { return jobId; }

    public void freezeProblem(String fingerprint) throws SQLException {
        try (var connection = jobs.open()) {
            update(connection, "UPDATE sandbox_optimization_job SET problem_sha256=? WHERE job_id=?",
                    fingerprint, jobId);
        }
    }

    public boolean cancelled() throws SQLException {
        try (var connection = jobs.open()) {
            return "CANCEL_REQUESTED".equals(value(connection,
                    "SELECT job_status FROM sandbox_management_job WHERE job_id=?", jobId));
        }
    }

    public static void requireChildStart(Connection connection, Child child, String key, int revision) throws SQLException {
        requireChildPhase(connection, child);
        if (!"HEURISTIC_SOURCE".equals(child.kind())) {
            require(child.jobId().equals(value(connection, """
                    SELECT job_id FROM sandbox_management_job WHERE job_id=? AND run_spec_key=? AND run_spec_revision=?
                    """, child.jobId(), key, revision)), "OPTIMIZATION_REVISION_MISMATCH");
        }
    }

    private static void requireChildPhase(Connection connection, Child child) throws SQLException {
        String status = parentStatus(connection, child.jobId());
        if ("CANCEL_REQUESTED".equals(status)) throw new SandboxWorkspaceException("USER_CANCELLED", "Optimization cancelled");
        boolean searching = "CANDIDATE".equals(child.kind());
        require((searching ? "RUNNING" : "PREPARING").equals(status), "OPTIMIZATION_PHASE_MISMATCH");
    }

    public static void bind(Connection connection, Child child, String executionId, String manifestHash) throws SQLException {
        requireChildPhase(connection, child);
        update(connection, "INSERT INTO sandbox_optimization_attempt(execution_id,job_id,attempt_kind,manifest_sha256) VALUES(?,?,?,?)",
                executionId, child.jobId(), child.kind(), manifestHash);
    }

    public static boolean stopChild(Connection connection, Child child) throws SQLException {
        String status = parentStatus(connection, child.jobId());
        require(Set.of("PREPARING", "RUNNING", "CANCEL_REQUESTED").contains(status), "OPTIMIZATION_PARENT_STOPPED");
        return "CANCEL_REQUESTED".equals(status);
    }

    public static void recordChildEnd(Connection connection, Child child, String executionId) throws SQLException {
        update(connection, """
                UPDATE sandbox_optimization_attempt SET last_tick_sha256=
                  (SELECT tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? ORDER BY loop_index DESC LIMIT 1)
                WHERE job_id=? AND execution_id=?
                """, executionId, child.jobId(), executionId);
    }

    /** The caller has verified the complete export and authenticated it against the evaluation request. */
    public void verified(String executionId, String manifestHash, String lastTickHash) throws SQLException {
        try (var connection = jobs.open()) {
            update(connection, """
                    UPDATE sandbox_optimization_attempt a JOIN sandbox_execution e ON e.execution_id=a.execution_id
                    SET a.verified=TRUE
                    WHERE a.job_id=? AND a.execution_id=? AND a.manifest_sha256=? AND a.last_tick_sha256=?
                      AND e.execution_status='COMPLETED'
                    """, jobId, executionId, manifestHash, lastTickHash);
        }
    }

    public boolean checkpoint(String executionId, boolean prepared) throws SQLException {
        try (var connection = jobs.open()) {
            connection.setAutoCommit(false);
            try {
                String status = parentStatus(connection, jobId);
                if ("CANCEL_REQUESTED".equals(status)) { connection.rollback(); return false; }
                require((prepared ? "PREPARING" : "RUNNING").equals(status), "OPTIMIZATION_PHASE_MISMATCH");
                require(executionId.equals(value(connection, """
                        SELECT execution_id FROM sandbox_optimization_attempt
                        WHERE job_id=? AND execution_id=? AND verified=TRUE AND attempt_kind<>'HEURISTIC_SOURCE'
                        """, jobId, executionId)), "OPTIMIZATION_RESULT_UNVERIFIED");
                if (prepared) {
                    update(connection, "UPDATE sandbox_optimization_job SET preparation_complete=TRUE,best_execution_id=? WHERE job_id=?",
                            executionId, jobId);
                    update(connection, "UPDATE sandbox_management_job SET job_status='RUNNING',updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=?", jobId);
                } else {
                    update(connection, "UPDATE sandbox_optimization_job SET best_execution_id=? WHERE job_id=?", executionId, jobId);
                }
                connection.commit();
                return true;
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
    }

    public void finish(String requestedStatus, String reason) throws SQLException {
        try (var connection = jobs.open()) {
            safety.acquirePreparationLock(connection);
            try {
                safety.requireManagementJobOwner(connection, jobId);
                connection.setAutoCommit(false);
                try {
                    String unfinished = unfinishedChild(connection, jobId);
                    if (unfinished != null) closeExitedChild(connection, unfinished, reason);
                    safety.requireNoUnfinishedExecution(connection, jobId);
                    String state = parentStatus(connection, jobId);
                    require(Set.of("PREPARING", "RUNNING", "CANCEL_REQUESTED").contains(state), "JOB_STATE_MISMATCH");
                    String status = "CANCEL_REQUESTED".equals(state) ? "CANCELLED" : requestedStatus;
                    String stop = "CANCELLED".equals(status) ? "USER_CANCELLED" : reason;
                    update(connection, "UPDATE sandbox_optimization_job SET stop_reason=? WHERE job_id=?", stop, jobId);
                    update(connection, """
                            UPDATE sandbox_management_job j JOIN sandbox_optimization_job o ON o.job_id=j.job_id
                            SET j.job_status=?,j.failure_code=?,j.execution_id=o.best_execution_id,
                                j.finished_at=CURRENT_TIMESTAMP(6),j.updated_at=CURRENT_TIMESTAMP(6)
                            WHERE j.job_id=?
                            """, status, "SUCCEEDED".equals(status) && !"NO_DEMAND".equals(stop) ? null : stop, jobId);
                    update(connection, "UPDATE sandbox_workspace_marker SET active_job_id=NULL WHERE marker_id=1 AND active_job_id=?", jobId);
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            } finally { safety.releasePreparationLock(connection); }
        }
    }

    static String unfinishedChild(Connection connection, String id) throws SQLException {
        return value(connection, """
                SELECT a.execution_id FROM sandbox_optimization_attempt a JOIN sandbox_execution e USING(execution_id)
                WHERE a.job_id=? AND e.execution_status IN ('CREATED','RUNNING') LIMIT 1
                """, id);
    }

    private void closeExitedChild(Connection connection, String execution, String reason) throws SQLException {
        // The managed launcher waits for its child process to exit before returning to finish.
        require(execution.equals(value(connection, """
                SELECT e.execution_id FROM sandbox_execution e JOIN sandbox_workspace_marker m
                  ON m.marker_id=1 AND m.run_spec_key=e.run_spec_key AND m.run_spec_revision=e.run_spec_revision
                WHERE e.execution_id=? AND (m.active_execution_id=e.execution_id OR
                  (m.active_execution_id IS NULL AND e.execution_status='CREATED' AND m.workspace_state='RUN_SPEC_READY'))
                """, execution)), "EXECUTION_ASSOCIATION_MISMATCH");
        var json = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json).verify(connection, execution);
        } catch (Exception failure) {
            throw new SandboxWorkspaceException("JOURNAL_NOT_VERIFIED", "Interrupted execution requires inspection", failure);
        }
        update(connection, """
                UPDATE sandbox_execution SET execution_status='INTERRUPTED',failed_loop_index=NULL,
                  failure_phase='INTERRUPTION',failure_code=?,finished_at=CURRENT_TIMESTAMP(6)
                WHERE execution_id=? AND execution_status IN ('CREATED','RUNNING')
                """, reason, execution);
        recordChildEnd(connection, new Child(jobId, "CANDIDATE"), execution);
        update(connection, """
                UPDATE sandbox_workspace_marker SET workspace_state='FAILED',active_execution_id=?,
                  failure_phase='INTERRUPTION',failure_code=? WHERE marker_id=1 AND active_job_id=?
                """, execution, reason, jobId);
    }

    private static String parentStatus(Connection connection, String id) throws SQLException {
        String status = value(connection, """
                SELECT j.job_status FROM sandbox_management_job j JOIN sandbox_optimization_job o ON o.job_id=j.job_id
                WHERE j.job_id=? FOR UPDATE
                """, id);
        require(status != null, "OPTIMIZATION_JOB_NOT_FOUND");
        return status;
    }

    static String value(Connection connection, String sql, Object... args) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            for (int index = 0; index < args.length; index++) query.setObject(index + 1, args[index]);
            try (var rows = query.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    static void update(Connection connection, String sql, Object... args) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            for (int index = 0; index < args.length; index++) query.setObject(index + 1, args[index]);
            require(query.executeUpdate() == 1, "OPTIMIZATION_STATE_MISMATCH");
        }
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new SandboxWorkspaceException(code, "Optimization job state check failed");
    }
}
