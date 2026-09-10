package org.example.roadsimulation.evaluation;

/**
 * Phase 6A：单个快照指标值的统一可用性状态。
 *
 * <p>Phase 6B 必须使用这些状态表达缺失或异常，禁止以数值 0 伪装不可计算结果。</p>
 */
public enum EvaluationMetricValueStatus {
    AVAILABLE,
    NOT_AVAILABLE,
    // Phase 7E-R：指标被当前版本明确排除，不再与“等待事实接入”混为一谈。
    NOT_SUPPORTED,
    NOT_APPLICABLE,
    INVALID
}
