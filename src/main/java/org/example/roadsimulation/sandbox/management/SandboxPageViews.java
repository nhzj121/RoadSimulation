package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;

/** Additive, camel-case page contracts; no database or business execution in this mapper. */
public final class SandboxPageViews {
    private SandboxPageViews() {}
    public record Progress(Integer requestedLoops, Integer recordedLoops, Double percent) {}
    public record Actions(boolean canCancel, String cancelDisabledReason,
                          boolean canPause, boolean canResume) {}
    public record JobView(String artifactVersion, String jobId, String runSpecKey, int revision,
                          String status, String phase, String executionId, String workerObservation,
                          boolean attentionRequired, boolean cancellationRequested,
                          Progress progress, Actions actions, String failureCode, String failurePhase) {}
    public record ExecutionSummary(String artifactVersion, String executionId, String runSpecKey,
                                   int revision, String baselineId, String status, String resultKind,
                                   Progress progress, Integer lastRecordedLoopIndex, Integer failedLoopIndex,
                                   String failurePhase, String failureCode, String manifestSha256,
                                   String lastTickSha256, JsonNode lastEvaluation,
                                   String evaluationIntegrityScope, String ledgerIntegrity) {}

    public static JobView job(Map<String, Object> row) {
        String status = text(row, "job_status");
        boolean attention = Boolean.TRUE.equals(row.get("attentionRequired"));
        boolean cancel = !attention && Set.of("ACCEPTED", "RUNNING").contains(status);
        String reason = cancel ? null : attention ? "INSPECTION_REQUIRED"
                : "CANCEL_REQUESTED".equals(status) ? "REQUEST_ALREADY_ACCEPTED"
                : Set.of("PREPARING", "FINALIZING").contains(status) ? "PHASE_NOT_CANCELLABLE"
                : "TASK_TERMINAL";
        return new JobView("sandbox-job-view/v1", text(row, "job_id"), text(row, "run_spec_key"),
                integer(row, "run_spec_revision"), status, switch (status) {
                    case "ACCEPTED" -> "ACCEPTED"; case "PREPARING" -> "PREPARING";
                    case "RUNNING", "CANCEL_REQUESTED" -> "RUNNING";
                    case "FINALIZING" -> "FINALIZING";
                    case "INTERRUPTED" -> "ATTENTION_REQUIRED"; default -> "TERMINAL";
                }, text(row, "execution_id"), text(row, "workerObservation"), attention,
                row.get("cancel_requested_at") != null,
                progress(row), new Actions(cancel, reason, false, false),
                text(row, "failure_code"), text(row, "failure_phase"));
    }

    public static ExecutionSummary summary(Map<String, Object> row, JsonNode evaluation) {
        String status = text(row, "execution_status");
        int recorded = integer(row, "completed_loops");
        return new ExecutionSummary("sandbox-execution-summary/v1", text(row, "execution_id"),
                text(row, "run_spec_key"), integer(row, "run_spec_revision"), text(row, "baseline_id"),
                status, switch (status) {
                    case "COMPLETED" -> "FULL_COMPLETION"; case "CANCELLED" -> "CANCELLED_PREFIX";
                    case "FAILED" -> "FAILED_PREFIX"; case "INTERRUPTED" -> "INTERRUPTED_PREFIX";
                    default -> "IN_PROGRESS_PREFIX";
                }, progress(row), recorded == 0 ? null : recorded - 1,
                integer(row, "failed_loop_index"), text(row, "failure_phase"), text(row, "failure_code"),
                text(row, "manifest_sha256"), text(row, "tick_sha256"), evaluation,
                evaluation == null ? "NONE" : "PAYLOAD_ONLY", "NOT_CHECKED");
    }

    static Progress progress(Map<String, Object> row) {
        Integer requested = integer(row, "requested_loops"), recorded = integer(row, "completed_loops");
        return new Progress(requested, recorded,
                requested == null || requested <= 0 || recorded == null ? null : 100.0 * recorded / requested);
    }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? null : value.toString();
    }
    private static Integer integer(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? null : ((Number) value).intValue();
    }
}
