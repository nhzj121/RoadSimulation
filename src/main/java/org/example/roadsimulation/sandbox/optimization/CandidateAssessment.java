package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.evaluation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Evidence supplied by a future trusted evaluator, not a substitute for verifying an execution journal. */
public record CandidateAssessment(OptimizationProblemRef problem, String planFingerprint,
                                  boolean hardConstraintsSatisfied, Completion completion,
                                  Map<String, MetricReading> metrics, Map<String, BigDecimal> auditData,
                                  List<String> diagnostics) {
    public enum ExecutionStatus { COMPLETED, INCOMPLETE, FAILED }

    public record Completion(ExecutionStatus executionStatus, int completedLoops, int finalLoopIndex,
                             LocalDateTime finalSimTime, EvaluationSnapshotStatus snapshotStatus,
                             String evaluationContractVersion, int transportFailureCount) {
        public Completion {
            Objects.requireNonNull(executionStatus, "executionStatus");
            if (completedLoops < 0 || finalLoopIndex < -1 || transportFailureCount < 0)
                throw new IllegalArgumentException("Invalid completion counters");
            Objects.requireNonNull(finalSimTime, "finalSimTime");
            Objects.requireNonNull(snapshotStatus, "snapshotStatus");
            evaluationContractVersion = ContractChecks.text(evaluationContractVersion, "evaluationContractVersion");
        }
    }

    public record MetricReading(String metricId, String unit, EvaluationMetricValueStatus status,
                                BigDecimal value, String reason) {
        public MetricReading {
            metricId = ContractChecks.text(metricId, "metricId");
            unit = ContractChecks.text(unit, "unit");
            Objects.requireNonNull(status, "status");
            if (status == EvaluationMetricValueStatus.AVAILABLE) {
                value = ContractChecks.decimal(value, "metric value");
                reason = null;
            } else {
                if (value != null) throw new IllegalArgumentException("Unavailable metric must have null value");
                reason = ContractChecks.text(reason, "reason");
            }
        }

        public static MetricReading from(EvaluationMetricValue metric) {
            return new MetricReading(metric.metricId(), metric.unit(), metric.status(),
                    metric.value() == null ? null : BigDecimal.valueOf(metric.value()), metric.reason());
        }
    }

    public CandidateAssessment {
        Objects.requireNonNull(problem, "problem");
        planFingerprint = ContractChecks.hash(planFingerprint, "planFingerprint");
        Objects.requireNonNull(completion, "completion");
        if (metrics == null) throw new IllegalArgumentException("metrics are required");
        var copy = new TreeMap<String, MetricReading>();
        metrics.forEach((key, metric) -> {
            if (metric == null || !metric.metricId().equals(key))
                throw new IllegalArgumentException("Metric key does not match reading");
            copy.put(key, metric);
        });
        metrics = Collections.unmodifiableMap(copy);
        auditData = ContractChecks.decimals(auditData, "auditData");
        diagnostics = ContractChecks.list(diagnostics, "diagnostics");
        diagnostics.forEach(d -> ContractChecks.text(d, "diagnostic"));
    }

    /** Inspects final metadata, not only whether a snapshot happens to contain four numbers. */
    public List<String> executionIssues() {
        var issues = new ArrayList<String>();
        if (!hardConstraintsSatisfied) issues.add("HARD_CONSTRAINTS_NOT_SATISFIED");
        if (completion.executionStatus() != ExecutionStatus.COMPLETED) issues.add("EXECUTION_NOT_COMPLETED");
        if (completion.completedLoops() != problem.totalLoops()
                || completion.finalLoopIndex() != problem.totalLoops() - 1
                || !completion.finalSimTime().equals(problem.deadline())) issues.add("HORIZON_MISMATCH");
        if (!problem.evaluationContractVersion().equals(completion.evaluationContractVersion()))
            issues.add("EVALUATION_CONTRACT_MISMATCH");
        if (completion.snapshotStatus() == EvaluationSnapshotStatus.FAILED) issues.add("SNAPSHOT_FAILED");
        if (completion.transportFailureCount() != 0) issues.add("TRANSPORT_PROGRESS_FAILED");
        return List.copyOf(issues);
    }
}
