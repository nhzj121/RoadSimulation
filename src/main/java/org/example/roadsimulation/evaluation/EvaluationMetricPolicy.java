package org.example.roadsimulation.evaluation;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 6A：经确认的可配置评价阈值。
 *
 * <p>这些阈值必须随 Phase 6B 快照一并返回，保证同一数值可以追溯到计算口径。</p>
 */
@Component
@ConfigurationProperties(prefix = "simulation.evaluation")
public class EvaluationMetricPolicy {

    /** Phase 6A：当前有载车辆载重率低于 50% 时定义为低载。 */
    private double lowLoadRatioThreshold = 0.50;

    /** Phase 6A：当前有载车辆载重率达到 90% 时定义为满载。 */
    private double fullLoadRatioThreshold = 0.90;

    /** Phase 6A：P95 服务等待约束默认采用 120 个仿真分钟。 */
    private long maxServiceWaitSeconds = 120L * 60L;

    public double getLowLoadRatioThreshold() {
        return lowLoadRatioThreshold;
    }

    public void setLowLoadRatioThreshold(double lowLoadRatioThreshold) {
        validateRatio(lowLoadRatioThreshold, "lowLoadRatioThreshold");
        this.lowLoadRatioThreshold = lowLoadRatioThreshold;
    }

    public double getFullLoadRatioThreshold() {
        return fullLoadRatioThreshold;
    }

    public void setFullLoadRatioThreshold(double fullLoadRatioThreshold) {
        validateRatio(fullLoadRatioThreshold, "fullLoadRatioThreshold");
        this.fullLoadRatioThreshold = fullLoadRatioThreshold;
    }

    public long getMaxServiceWaitSeconds() {
        return maxServiceWaitSeconds;
    }

    public void setMaxServiceWaitSeconds(long maxServiceWaitSeconds) {
        if (maxServiceWaitSeconds <= 0L) {
            throw new IllegalArgumentException("maxServiceWaitSeconds must be positive");
        }
        this.maxServiceWaitSeconds = maxServiceWaitSeconds;
    }

    /**
     * Phase 6A：属性绑定完成后再校验两个阈值的相对关系，避免 Spring setter 调用顺序影响合法配置。
     */
    @PostConstruct
    public void validatePolicy() {
        if (lowLoadRatioThreshold >= fullLoadRatioThreshold) {
            throw new IllegalArgumentException("low-load threshold must be lower than full-load threshold");
        }
    }

    private void validateRatio(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be a finite ratio in [0, 1]");
        }
    }
}
