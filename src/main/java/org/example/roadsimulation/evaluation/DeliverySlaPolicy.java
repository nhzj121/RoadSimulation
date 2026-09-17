package org.example.roadsimulation.evaluation;

import jakarta.annotation.PostConstruct;
import org.example.roadsimulation.core.SimulationContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Phase 9B-1：交付时限的纯评价契约。
 *
 * <p>本策略只把需求起终点的静态几何距离转换为可复现 SLA，不读取车辆、任务分配、
 * 实际路线、环境快照或前端动画。Phase 9B-1 不调用本类写入任何业务实体；后续事实
 * 接入阶段只能在既有需求创建完成后使用它冻结交付截止时间。</p>
 */
@Component
@ConfigurationProperties(prefix = "simulation.evaluation.delivery-sla")
public class DeliverySlaPolicy {

    /** Phase 9B-1：任何公式或默认参数变化都必须提升模型标识。 */
    public static final String DEFAULT_MODEL_ID = "DETERMINISTIC_DISTANCE_SLA_V1";

    /** Phase 9B-1：当前后端装卸动作和评价截止时间共同使用一个规范仿真 tick。 */
    private static final long TICK_SECONDS = SimulationContext.TICK_DURATION.getSeconds();

    /** Phase 9B-1：版本化 SLA 模型标识，供后续事实追溯。 */
    private String modelId = DEFAULT_MODEL_ID;

    /**
     * Phase 9B-1：Haversine 直线距离到静态参考道路距离的折算系数。
     *
     * <p>该值已经确认为 1.30；它不随 tick 或环境变化，不得解释为 Phase 7D 的动态
     * 距离修正系数，也不得改写实际路线。</p>
     */
    private double roadDistanceFactor = 1.30;

    /** Phase 9B-1：SLA 基准行驶速度，默认与正常路网速度 60 km/h 对齐。 */
    private double referenceSpeedKph = 60.0;

    /**
     * Phase 9B-1：计算从需求生成到交付的最大允许秒数。
     *
     * <p>原始值 = 已冻结的等待服务额度 + 静态参考行驶秒数 + 一个卸货 tick；最终
     * 向上对齐到完整 tick，避免 30 分钟离散推进把数学边界误判为逾期。</p>
     */
    public long calculateSlaSeconds(double haversineDistanceKm, long maxServiceWaitSeconds) {
        requireNonNegativeFinite(haversineDistanceKm, "haversineDistanceKm");
        if (maxServiceWaitSeconds <= 0L) {
            throw new IllegalArgumentException("maxServiceWaitSeconds must be positive");
        }

        double referenceTravelSeconds = haversineDistanceKm
                * roadDistanceFactor
                / referenceSpeedKph
                * 3_600.0;
        if (!Double.isFinite(referenceTravelSeconds) || referenceTravelSeconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("reference travel seconds exceed supported range");
        }

        long roundedTravelSeconds = (long) Math.ceil(referenceTravelSeconds);
        long rawSeconds = Math.addExact(maxServiceWaitSeconds, roundedTravelSeconds);
        // Phase 9B-1：等待额度已经覆盖“需求生成至首次有载推进”，这里只再加入卸货窗口。
        rawSeconds = Math.addExact(rawSeconds, TICK_SECONDS);
        return alignUpToTick(rawSeconds);
    }

    /** Phase 9B-1：截止时间必须锚定后端需求生成仿真时间，不得读取系统墙钟。 */
    public LocalDateTime calculateDeadline(
            LocalDateTime demandCreatedSimTime,
            double haversineDistanceKm,
            long maxServiceWaitSeconds
    ) {
        if (demandCreatedSimTime == null) {
            throw new IllegalArgumentException("demandCreatedSimTime must not be null");
        }
        return demandCreatedSimTime.plusSeconds(
                calculateSlaSeconds(haversineDistanceKm, maxServiceWaitSeconds)
        );
    }

    /** Phase 9B-1：恰好在截止时刻完成卸货仍属于准时。 */
    public boolean isCompletedOnTime(LocalDateTime deliveredSimTime, LocalDateTime deadlineSimTime) {
        if (deliveredSimTime == null || deadlineSimTime == null) {
            throw new IllegalArgumentException("delivery time and deadline must not be null");
        }
        return !deliveredSimTime.isAfter(deadlineSimTime);
    }

    /** Phase 9B-1：未交付任务只有在观察时刻严格晚于截止时刻后才属于当前逾期。 */
    public boolean isPastDeadline(LocalDateTime observedSimTime, LocalDateTime deadlineSimTime) {
        if (observedSimTime == null || deadlineSimTime == null) {
            throw new IllegalArgumentException("observation time and deadline must not be null");
        }
        return observedSimTime.isAfter(deadlineSimTime);
    }

    @PostConstruct
    public void validatePolicy() {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("delivery SLA modelId must not be blank");
        }
        requirePositiveFinite(roadDistanceFactor, "roadDistanceFactor");
        requirePositiveFinite(referenceSpeedKph, "referenceSpeedKph");
    }

    private long alignUpToTick(long seconds) {
        long ticks = Math.addExact(seconds, TICK_SECONDS - 1L) / TICK_SECONDS;
        return Math.multiplyExact(ticks, TICK_SECONDS);
    }

    private void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public double getRoadDistanceFactor() {
        return roadDistanceFactor;
    }

    public void setRoadDistanceFactor(double roadDistanceFactor) {
        requirePositiveFinite(roadDistanceFactor, "roadDistanceFactor");
        this.roadDistanceFactor = roadDistanceFactor;
    }

    public double getReferenceSpeedKph() {
        return referenceSpeedKph;
    }

    public void setReferenceSpeedKph(double referenceSpeedKph) {
        requirePositiveFinite(referenceSpeedKph, "referenceSpeedKph");
        this.referenceSpeedKph = referenceSpeedKph;
    }

    public long getUnloadingServiceSeconds() {
        return TICK_SECONDS;
    }

    public long getDeadlineAlignmentSeconds() {
        return TICK_SECONDS;
    }
}
