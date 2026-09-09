package org.example.roadsimulation.evaluation;

/**
 * Phase 6A：指标相对于当前项目事实层的实现准备度。
 *
 * <p>该枚举描述“现在为什么能算或不能算”；它不是某一轮快照的运行时状态。</p>
 */
public enum EvaluationMetricReadiness {
    READY,
    REQUIRES_FACT_CAPTURE,
    REQUIRES_ENVIRONMENT,
    REQUIRES_ENERGY_MODEL,
    NOT_APPLICABLE
}
