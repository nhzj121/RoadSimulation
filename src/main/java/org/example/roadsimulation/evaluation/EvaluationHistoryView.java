package org.example.roadsimulation.evaluation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Phase 9C：评价历史、趋势和最终快照对比的只读 HTTP 投影。 */
public final class EvaluationHistoryView {

    private EvaluationHistoryView() { }

    public record RunSummary(
            String simulationRunId,
            EvaluationRunKind runKind,
            String contractVersion,
            int firstLoopIndex,
            int lastLoopIndex,
            long snapshotCount,
            LocalDateTime firstSimTime,
            LocalDateTime lastSimTime,
            EvaluationSnapshotStatus latestStatus
    ) { }

    public record TrendPoint(
            int loopIndex,
            long snapshotRevision,
            LocalDateTime simTime,
            EvaluationSnapshotStatus snapshotStatus,
            Map<String, EvaluationMetricValue> metrics
    ) { }

    public record Trend(
            RunSummary run,
            List<String> metricIds,
            List<TrendPoint> points
    ) { }

    /** Phase 9C：JSON 导出同时携带运行摘要和未经前端二次解释的完整快照。 */
    public record HistoryExport(
            RunSummary run,
            List<EvaluationSnapshot> snapshots
    ) { }

    public record MetricComparison(
            String metricId,
            String displayName,
            EvaluationMetricCategory category,
            String unit,
            EvaluationMetricValueStatus leftStatus,
            Double leftValue,
            EvaluationMetricValueStatus rightStatus,
            Double rightValue,
            Double rightMinusLeft,
            Double relativeChangeRatio
    ) { }

    public record RunComparison(
            RunSummary leftRun,
            RunSummary rightRun,
            EvaluationSnapshot leftFinalSnapshot,
            EvaluationSnapshot rightFinalSnapshot,
            List<MetricComparison> metrics
    ) { }
}
