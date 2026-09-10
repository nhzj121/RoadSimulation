package org.example.roadsimulation.evaluation;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase 7C：单个 simulation tick 使用的不可变外部环境快照。
 *
 * <p>Phase 7D 起该对象同时支持 Shadow 与进度影响模式。即使处于进度影响模式，也只能改变
 * 当轮可完成的基准行驶工作量，不得改变计划路线、路线几何和前端动画协议。</p>
 */
public record EnvironmentScenarioSnapshot(
        String scenarioId,
        String scenarioVersion,
        long seed,
        int loopIndex,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        EnvironmentScenarioPhase phase,
        EnvironmentApplicationMode applicationMode,
        int modeledRoadCount,
        int closedRoadCount,
        int abnormalEventCount,
        int weatherRiskLevel,
        double normalNetworkSpeedKph,
        double networkAverageSpeedKph,
        double congestionIndex,
        double roadPassabilityRatio,
        double travelTimeFactor
) {

    public EnvironmentScenarioSnapshot {
        // Phase 7C：快照在进入指标计算前集中拒绝缺失标识和非法仿真窗口。
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }
        if (scenarioVersion == null || scenarioVersion.isBlank()) {
            throw new IllegalArgumentException("scenarioVersion must not be blank");
        }
        if (loopIndex < 0) {
            throw new IllegalArgumentException("loopIndex must be non-negative");
        }
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(validTo, "validTo must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(applicationMode, "applicationMode must not be null");
        if (!validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo must be after validFrom");
        }

        // Phase 7C：逻辑路网只用于场景统计，封闭数不得超过建模总数。
        if (modeledRoadCount <= 0 || closedRoadCount < 0 || closedRoadCount > modeledRoadCount) {
            throw new IllegalArgumentException("invalid modeled/closed road count");
        }
        if (abnormalEventCount < 0 || weatherRiskLevel < 0 || weatherRiskLevel > 3) {
            throw new IllegalArgumentException("invalid event count or weather risk level");
        }
        if (!isPositiveFinite(normalNetworkSpeedKph)
                || !isPositiveFinite(networkAverageSpeedKph)
                || !isPositiveFinite(congestionIndex)
                || !isPositiveFinite(travelTimeFactor)
                || !Double.isFinite(roadPassabilityRatio)
                || roadPassabilityRatio < 0.0
                || roadPassabilityRatio > 1.0) {
            throw new IllegalArgumentException("invalid environment metric value");
        }
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    /** Phase 7D：兼容 Phase 7C 测试和诊断，同时以显式模式作为唯一真实字段。 */
    public boolean shadowMode() {
        return applicationMode == EnvironmentApplicationMode.SHADOW;
    }

    /** Phase 7D：运输进度服务只接受明确启用的环境快照，不从因子数值猜测模式。 */
    public boolean progressInfluenceEnabled() {
        return applicationMode == EnvironmentApplicationMode.PROGRESS_AFFECTING;
    }
}
