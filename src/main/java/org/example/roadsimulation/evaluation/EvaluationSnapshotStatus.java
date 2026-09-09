package org.example.roadsimulation.evaluation;

/**
 * Phase 6B：一次评价快照的整体完成状态。
 *
 * <p>该状态描述本轮评价采集是否完整，不替代单个指标自己的
 * {@link EvaluationMetricValueStatus}。</p>
 */
public enum EvaluationSnapshotStatus {
    COMPLETE,
    PARTIAL,
    FAILED
}
