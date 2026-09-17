package org.example.roadsimulation.evaluation;

/**
 * Phase 6A：指标对应的逻辑时间范围。
 */
public enum EvaluationMetricTimeScope {
    /** 本轮业务推进全部完成后的瞬时状态。 */
    TICK_END_INSTANT,
    /** 当前仿真运行开始至本轮结束的累计事实。 */
    CURRENT_RUN_CUMULATIVE,
    /** 当前运行内已经闭合的等待或运输事件样本。 */
    CURRENT_RUN_COMPLETED_EPISODES,
    /** 同时组合本轮瞬时状态和当前运行累计事实。 */
    TICK_END_AND_CURRENT_RUN
}
