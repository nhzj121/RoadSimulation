package org.example.roadsimulation.service;

/**
 * Phase 4：单个 Assignment 在一次后端权威 tick 中的路段推进结果。
 *
 * <p>{@code remainingSeconds} 继续遵守用户确认的方案 A：它只用于诊断，本轮结束时作废，
 * 不传递给下一路段或装卸状态。</p>
 */
public record TransportProgressResult(
        Long assignmentId,
        Long legId,
        Integer legSequenceIndex,
        int loopIndex,
        Outcome outcome,
        long consumedSeconds,
        long remainingSeconds,
        boolean legCompleted,
        // Phase 4：表示“本次 tick 新产生”全部路段完成事件，不是任务终态快照。
        boolean allLegsCompleted,
        String failureReason
) {

    // Phase 4：legCompleted/allLegsCompleted 都是本次 tick 事件，重复 tick 不得重放。

    /** Phase 4：区分真实推进、路段完成、非行驶暂停、重复调用和无需推进。 */
    public enum Outcome {
        ADVANCED,
        LEG_COMPLETED,
        // Phase 4：装货、卸货、等待或故障期间是正常暂停，不记为推进失败。
        WAITING_FOR_DRIVING_STATE,
        DUPLICATE_TICK_SKIPPED,
        ALL_LEGS_COMPLETED,
        ASSIGNMENT_NOT_ELIGIBLE,
        // Phase 4：单任务失败继续隔离，不回滚同一 tick 中其他任务的已提交进度。
        FAILED
    }
}
