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
    // Phase 7E-R：当前版本主动不提供该能力，后续只有新契约才能重新启用。
    NOT_SUPPORTED,
    NOT_APPLICABLE
}
