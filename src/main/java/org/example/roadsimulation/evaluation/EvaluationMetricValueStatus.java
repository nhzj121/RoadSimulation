package org.example.roadsimulation.evaluation;

/**
 * Phase 6A：单个快照指标值的统一可用性状态。
 *
 * <p>Phase 6B 必须使用这些状态表达缺失或异常，禁止以数值 0 伪装不可计算结果。</p>
 */
public enum EvaluationMetricValueStatus {
    AVAILABLE,
    NOT_AVAILABLE,
    NOT_APPLICABLE,
    INVALID
}
