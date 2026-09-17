package org.example.roadsimulation.evaluation;

/**
 * Phase 7C：可复现外部环境场景的离散阶段。
 *
 * <p>阶段只描述评价侧事实，不代表路线服务、道路实体或车辆状态发生了变化。</p>
 */
public enum EnvironmentScenarioPhase {
    NORMAL,
    RAIN,
    PEAK_CONGESTION,
    INCIDENT,
    RECOVERY
}
