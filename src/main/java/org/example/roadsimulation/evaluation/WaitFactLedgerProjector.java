package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase 9A-1：在一轮业务推进完成后触发等待事实投影，并隔离观察侧故障。
 *
 * <p>本组件不抛出 writer 异常，因此评价账本故障不会改变运输任务的提交结果；
 * 同时通过 health 锁存失败，防止后续评价静默少算。</p>
 */
@Component
public class WaitFactLedgerProjector {

    private static final Logger log = LoggerFactory.getLogger(WaitFactLedgerProjector.class);

    private final WaitFactLedgerWriter writer;
    private final WaitFactLedgerHealth health;

    public WaitFactLedgerProjector(WaitFactLedgerWriter writer, WaitFactLedgerHealth health) {
        this.writer = writer;
        this.health = health;
    }

    public void project(String simulationRunId, SimulationTick tick) {
        try {
            // Phase 9A-1：独立 writer Bean 上的 REQUIRES_NEW 事务在代理边界生效。
            writer.project(simulationRunId, tick);
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            String reason = ex.getClass().getSimpleName()
                    + (message == null || message.isBlank() ? "" : ": " + message);
            health.recordProjectionFailure(
                    simulationRunId,
                    tick == null ? null : tick.loopIndex(),
                    tick == null ? null : tick.tickEnd(),
                    "PROJECTION",
                    reason
            );
            log.error(
                    "[Phase9A WaitFactLedger] projection failed: runId={}, loop={}",
                    simulationRunId,
                    tick == null ? null : tick.loopIndex(),
                    ex
            );
        }
    }
}
