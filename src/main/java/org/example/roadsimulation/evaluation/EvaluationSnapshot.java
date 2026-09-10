package org.example.roadsimulation.evaluation;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 6B：一轮业务推进完成后的不可变评价快照。
 *
 * <p>所有指标共享相同的 run、revision、loopIndex 与 simTime；HTTP 读取只返回该对象，
 * 不在请求线程重新扫描数据库。</p>
 */
public record EvaluationSnapshot(
        String contractVersion,
        String simulationRunId,
        EvaluationRunKind runKind,
        int loopIndex,
        long snapshotRevision,
        LocalDateTime simTime,
        EvaluationSnapshotStatus snapshotStatus,
        int processedAssignmentCount,
        int failedAssignmentCount,
        List<String> errorCodes,
        EvaluationThresholdSnapshot thresholds,
        Map<String, EvaluationMetricValue> metrics
) {
    public EvaluationSnapshot {
        contractVersion = requireText(contractVersion, "contractVersion");
        simulationRunId = requireText(simulationRunId, "simulationRunId");
        Objects.requireNonNull(runKind, "runKind is required");
        if (loopIndex < 0) {
            throw new IllegalArgumentException("loopIndex must be non-negative");
        }
        if (snapshotRevision <= 0L) {
            throw new IllegalArgumentException("snapshotRevision must be positive");
        }
        Objects.requireNonNull(simTime, "simTime is required");
        Objects.requireNonNull(snapshotStatus, "snapshotStatus is required");
        if (processedAssignmentCount < 0 || failedAssignmentCount < 0
                || failedAssignmentCount > processedAssignmentCount) {
            throw new IllegalArgumentException("invalid assignment processing counters");
        }
        errorCodes = List.copyOf(Objects.requireNonNull(errorCodes, "errorCodes are required"));
        if (errorCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("errorCodes must not contain blank values");
        }
        Objects.requireNonNull(thresholds, "thresholds are required");

        // Phase 6B：复制并冻结有序映射，确保同一 revision 被多次读取时不会发生内容漂移。
        LinkedHashMap<String, EvaluationMetricValue> copiedMetrics =
                new LinkedHashMap<>(Objects.requireNonNull(metrics, "metrics are required"));
        // Phase 6B：每个快照必须完整携带 69 项契约，缺项不能伪装成可供前端消费的快照。
        if (copiedMetrics.size() != EvaluationMetricId.values().length
                || copiedMetrics.entrySet().stream().anyMatch(entry -> entry.getValue() == null
                || !entry.getKey().equals(entry.getValue().metricId()))) {
            throw new IllegalArgumentException("metrics must contain every contract item with matching keys");
        }
        metrics = Collections.unmodifiableMap(copiedMetrics);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
