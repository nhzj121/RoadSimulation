package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import java.math.BigDecimal;
import java.util.*;

/** Pure adapter. Cumulative execution failures must be collected by G3 across all ticks. */
public final class EvaluationMetricAdapter {
    private EvaluationMetricAdapter() {}

    public static CandidateAssessment fromSnapshot(OptimizationProblemRef problem, String planFingerprint,
            boolean hardConstraintsSatisfied, CandidateAssessment.ExecutionStatus executionStatus,
            int completedLoops, int cumulativeTransportFailures, EvaluationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var readings = new TreeMap<String, CandidateAssessment.MetricReading>();
        snapshot.metrics().forEach((id, value) -> readings.put(id, CandidateAssessment.MetricReading.from(value)));
        var audit = new TreeMap<String, BigDecimal>();
        for (String id : List.of("cargoRequiredTonnes", "cargoDeliveredTonnes")) {
            var metric = readings.get(id);
            if (metric != null && metric.value() != null) audit.put(id, metric.value());
        }
        if (cumulativeTransportFailures < snapshot.failedAssignmentCount())
            throw new IllegalArgumentException("Cumulative failures cannot be less than final tick failures");
        var completion = new CandidateAssessment.Completion(executionStatus, completedLoops, snapshot.loopIndex(),
                snapshot.simTime(), snapshot.snapshotStatus(), snapshot.contractVersion(), cumulativeTransportFailures);
        return new CandidateAssessment(problem, planFingerprint, hardConstraintsSatisfied, completion,
                readings, audit, snapshot.errorCodes());
    }
}
