package org.example.roadsimulation.evaluation;

/**
 * Phase 7D：外部环境快照对运输执行的应用模式。
 *
 * <p>SHADOW 只生成评价事实；PROGRESS_AFFECTING 允许旅行时间因子影响实际路段推进，
 * 但两种模式都不允许改变计划路线或路线几何。</p>
 */
public enum EnvironmentApplicationMode {
    SHADOW,
    PROGRESS_AFFECTING
}
