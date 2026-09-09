package org.example.roadsimulation.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Phase 7B：在运输事务成功提交后，把观察事件投影成持久化节点服务账本。
 *
 * <p>{@code AFTER_COMMIT} 配合写入器的 {@code REQUIRES_NEW} 保证账本不参与车辆状态决策，也不会让账本写入
 * 先于业务事务落库。监听器故障只记录评价事实缺口，不尝试回写或补推进运输生命周期。</p>
 */
@Component
public class NodeServiceLedgerProjector {

    private static final Logger log = LoggerFactory.getLogger(NodeServiceLedgerProjector.class);

    private final NodeServiceLedgerWriter writer;
    private final NodeServiceLedgerHealth health;

    public NodeServiceLedgerProjector(NodeServiceLedgerWriter writer, NodeServiceLedgerHealth health) {
        this.writer = writer;
        this.health = health;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void project(NodeServiceObservation observation) {
        try {
            // Phase 7B：事务代理位于独立 writer Bean，异常可在新事务结束后由此处可靠隔离。
            writer.project(observation);
        } catch (RuntimeException ex) {
            // Phase 7B：评价账本是观察侧；运输事务此时已经提交，禁止反向补偿业务状态。
            health.recordProjectionFailure();
            log.error(
                    "[Phase7B NodeServiceLedger] projection failed: type={}, assignmentId={}, nodeId={}",
                    observation.type(), observation.assignmentId(), observation.assignmentNodeId(), ex
            );
        }
    }

}
