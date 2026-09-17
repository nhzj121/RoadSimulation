package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase 9B-2：隔离交付 SLA 事实投影故障，确保评价侧异常不会回滚运输业务。
 */
@Component
public class DeliverySlaLedgerProjector {

    private static final Logger log = LoggerFactory.getLogger(DeliverySlaLedgerProjector.class);

    private final DeliverySlaLedgerWriter writer;
    private final DeliverySlaLedgerHealth health;

    public DeliverySlaLedgerProjector(DeliverySlaLedgerWriter writer, DeliverySlaLedgerHealth health) {
        this.writer = writer;
        this.health = health;
    }

    public void project(String simulationRunId, SimulationTick tick) {
        try {
            // Phase 9B-2：独立 writer Bean 上的 REQUIRES_NEW 保证观察写入不加入运输事务。
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
                    "[Phase9B-2 DeliverySlaLedger] projection failed: runId={}, loop={}",
                    simulationRunId,
                    tick == null ? null : tick.loopIndex(),
                    ex
            );
        }
    }
}
