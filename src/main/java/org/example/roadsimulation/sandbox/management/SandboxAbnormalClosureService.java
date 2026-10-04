package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.workspace.*;
import java.sql.*;
import java.util.*;

/** Explicit maintenance only: never starts a worker, repairs facts, or resumes an execution. */
public final class SandboxAbnormalClosureService {
    private final SandboxManagementSettings settings;
    private final ObjectMapper json;
    private final SandboxManagementJobStore jobs;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    public SandboxAbnormalClosureService(SandboxManagementSettings settings, ObjectMapper json) {
        this.settings = settings; this.json = json; this.jobs = new SandboxManagementJobStore(settings);
    }

    public Map<String, Object> inspect(String id) {
        SandboxManagementJobStore.uuid(id);
        try (var connection = jobs.open()) {
            safety.acquirePreparationLock(connection);
            try {
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                try { var report = inspect(connection, id); connection.commit(); return report; }
                catch (Exception failure) { connection.rollback(); throw failure; }
            }
            finally { safety.releasePreparationLock(connection); }
        } catch (SQLException failure) {
            throw new SandboxWorkspaceException("INSPECTION_DATABASE_FAILED", "Cannot inspect sandbox job", failure);
        }
    }

    public Map<String, Object> close(String id, String confirmedFingerprint) {
        SandboxManagementJobStore.uuid(id);
        if (confirmedFingerprint == null || !confirmedFingerprint.matches("[0-9a-f]{64}"))
            throw new SandboxWorkspaceException("INSPECTION_CONFIRMATION_REQUIRED", "Supply the inspection fingerprint explicitly");
        try (var connection = jobs.open()) {
            safety.acquirePreparationLock(connection);
            try {
                connection.setAutoCommit(false);
                try {
                    var report = inspect(connection, id);
                    require(confirmedFingerprint.equals(report.get("inspectionSha256")), "STALE_INSPECTION");
                    require(Boolean.TRUE.equals(report.get("eligibleForClosure")), "ABNORMAL_CLOSURE_NOT_SAFE");
                    @SuppressWarnings("unchecked") var job = (Map<String, Object>) report.get("job");
                    String executionId = (String) job.get("execution_id");
                    String terminal = "ABORTED";
                    String code = "INSPECTED_WORKER_EXIT";
                    String workspace = "FAILED";
                    String failurePhase = "INTERRUPTION";
                    if (executionId != null) {
                        @SuppressWarnings("unchecked") var execution = (Map<String, Object>) report.get("execution");
                        String status = execution.get("execution_status").toString();
                        terminal = switch (status) {
                            case "COMPLETED" -> "SUCCEEDED"; case "FAILED" -> "FAILED";
                            case "CANCELLED" -> "CANCELLED"; default -> "ABORTED";
                        };
                        workspace = "COMPLETED".equals(status) ? "EXECUTION_COMPLETED"
                                : "CANCELLED".equals(status) ? "EXECUTION_CANCELLED" : "FAILED";
                        failurePhase = "FAILED".equals(status) ? (String) execution.get("failure_phase")
                                : "ABORTED".equals(terminal) ? "INTERRUPTION" : null;
                        if (Set.of("CREATED", "RUNNING").contains(status)) {
                            try (var update = connection.prepareStatement("UPDATE sandbox_execution SET execution_status='INTERRUPTED',failed_loop_index=NULL,failure_phase='INTERRUPTION',failure_code=?,failure_message='Explicitly closed after worker and journal inspection',finished_at=CURRENT_TIMESTAMP(6) WHERE execution_id=? AND execution_status IN('CREATED','RUNNING')")) {
                                update.setString(1, code); update.setString(2, executionId); require(update.executeUpdate() == 1, "CLOSURE_STATE_CHANGED");
                            }
                        } else code = (String) execution.get("failure_code");
                    }
                    try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status=?,failure_code=?,closure_report_json=?,closed_at=CURRENT_TIMESTAMP(6),finished_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=?")) {
                        update.setString(1, terminal); update.setString(2, code);
                        update.setString(3, json.writeValueAsString(report)); update.setString(4, id); update.executeUpdate();
                    }
                    try (var update = connection.prepareStatement("UPDATE sandbox_workspace_marker SET workspace_state=?,active_job_id=NULL,failure_code=?,failure_message=NULL,failure_phase=? WHERE marker_id=1 AND active_job_id=?")) {
                        update.setString(1, workspace); update.setString(2, code);
                        update.setString(3, failurePhase); update.setString(4, id);
                        require(update.executeUpdate() == 1, "CLOSURE_STATE_CHANGED");
                    }
                    connection.commit();
                    return Map.of("jobId", id, "status", terminal, "inspectionSha256", confirmedFingerprint, "reservationReleased", true);
                } catch (Exception failure) {
                    connection.rollback();
                    if (failure instanceof SandboxWorkspaceException workspace) throw workspace;
                    throw new SandboxWorkspaceException("ABNORMAL_CLOSURE_FAILED", "Cannot persist inspected closure", failure);
                }
            } finally { safety.releasePreparationLock(connection); }
        } catch (SQLException failure) {
            throw new SandboxWorkspaceException("CLOSURE_DATABASE_FAILED", "Cannot close sandbox job", failure);
        }
    }

    private Map<String, Object> inspect(Connection connection, String id) {
        try {
            safety.requirePreparationLockHeld(connection);
            var job = one(connection, "SELECT job_id,run_spec_key,run_spec_revision,job_status,execution_id,worker_pid,worker_started_at,updated_at FROM sandbox_management_job WHERE job_id=? FOR UPDATE", id);
            var marker = one(connection, "SELECT workspace_state,baseline_id,run_spec_key,run_spec_revision,active_job_id,active_execution_id,prepared_run_facts_sha256 FROM sandbox_workspace_marker WHERE marker_id=1 FOR UPDATE");
            List<String> blockers = new ArrayList<>();
            if (!id.equals(marker.get("active_job_id"))) blockers.add("NOT_RESERVATION_OWNER");
            if (!Set.of("ACCEPTED", "PREPARING", "RUNNING", "CANCEL_REQUESTED", "FINALIZING", "INTERRUPTED").contains(job.get("job_status"))) blockers.add("JOB_ALREADY_CLOSED");
            String observation = workerObservation(job);
            if (Set.of("ALIVE", "IDENTITY_UNKNOWN").contains(observation)) blockers.add("WORKER_NOT_CONFIRMED_DEAD");
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("artifactVersion", "sandbox-abnormal-inspection/v1"); report.put("job", job);
            report.put("workspace", marker); report.put("workerObservation", observation);
            String executionId = (String) job.get("execution_id");
            if (executionId != null) {
                var execution = one(connection, "SELECT execution_id,run_spec_key,run_spec_revision,baseline_id,prepared_run_facts_sha256,execution_status,completed_loops,failure_code,failure_phase FROM sandbox_execution WHERE execution_id=? FOR UPDATE", executionId);
                report.put("execution", execution);
                if (!Objects.equals(job.get("run_spec_key"), execution.get("run_spec_key"))
                        || !Objects.equals(job.get("run_spec_revision"), execution.get("run_spec_revision"))
                        || !Objects.equals(marker.get("run_spec_key"), execution.get("run_spec_key"))
                        || !Objects.equals(marker.get("run_spec_revision"), execution.get("run_spec_revision"))
                        || !Objects.equals(marker.get("baseline_id"), execution.get("baseline_id"))
                        || !Objects.equals(marker.get("prepared_run_facts_sha256"), execution.get("prepared_run_facts_sha256"))
                        || !(executionId.equals(marker.get("active_execution_id"))
                        || (marker.get("active_execution_id") == null && "CREATED".equals(execution.get("execution_status")) && "RUN_SPEC_READY".equals(marker.get("workspace_state")))))
                    blockers.add("EXECUTION_ASSOCIATION_MISMATCH");
                try { report.put("journal", new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json).verify(connection, executionId)); }
                catch (Exception corrupt) { blockers.add("JOURNAL_NOT_VERIFIED"); }
            } else {
                if ("EXECUTION_RUNNING".equals(marker.get("workspace_state"))) blockers.add("UNBOUND_RUNNING_EXECUTION");
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM sandbox_execution e LEFT JOIN sandbox_management_job j ON j.execution_id=e.execution_id WHERE e.execution_status IN('CREATED','RUNNING') AND j.job_id IS NULL")) {
                    if (rows.next() && rows.getInt(1) > 0) blockers.add("UNBOUND_EXECUTION_REQUIRES_INSPECTION");
                }
            }
            report.put("blockers", blockers); report.put("eligibleForClosure", blockers.isEmpty());
            report.put("inspectionSha256", new LexicographicJsonSha256(json).hashObject(report));
            return report;
        } catch (SandboxWorkspaceException failure) { throw failure; }
        catch (Exception failure) { throw new SandboxWorkspaceException("INSPECTION_FAILED", "Cannot verify job associations", failure); }
    }

    private static String workerObservation(Map<String, Object> job) {
        if (job.get("worker_pid") == null) return "ACCEPTED".equals(job.get("job_status")) || "INTERRUPTED".equals(job.get("job_status")) ? "NOT_STARTED" : "IDENTITY_UNKNOWN";
        var handle = ProcessHandle.of(((Number) job.get("worker_pid")).longValue());
        if (handle.isEmpty()) return "NOT_PRESENT";
        var started = handle.get().info().startInstant();
        if (started.isEmpty() || job.get("worker_started_at") == null) return "IDENTITY_UNKNOWN";
        return started.get().toString().equals(job.get("worker_started_at")) ? "ALIVE" : "DIFFERENT_PROCESS";
    }
    private static Map<String, Object> one(Connection connection, String sql, Object... values) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) query.setObject(index + 1, values[index]);
            try (var rows = query.executeQuery()) { require(rows.next(), "INSPECTION_RECORD_NOT_FOUND"); return SandboxManagementJobStore.row(rows); }
        }
    }
    private static void require(boolean condition, String code) {
        if (!condition) throw new SandboxWorkspaceException(code, "Inspected closure safety check failed");
    }
}
