package org.example.roadsimulation.evaluation;

/**
 * Phase 6B：随每轮快照冻结实际生效的评价阈值。
 *
 * <p>前端不得自行复制默认值，否则运行期配置变化会造成显示口径与后端不一致。</p>
 */
public record EvaluationThresholdSnapshot(
        double lowLoadRatioThreshold,
        double fullLoadRatioThreshold,
        long maxServiceWaitSeconds
) {
}
