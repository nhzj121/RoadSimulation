package org.example.roadsimulation.evaluation;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 7B：记录当前运行中无法持久化的节点服务观察事件。
 *
 * <p>观察侧故障不能回滚运输业务，但也不能被评价层静默解释为零事件；一旦发生投影失败，
 * 节点服务指标保持 INVALID，直到完整仿真 reset 同时清空账本和本状态。</p>
 */
@Component
public class NodeServiceLedgerHealth {

    private final AtomicLong projectionFailureCount = new AtomicLong();

    public void recordProjectionFailure() {
        projectionFailureCount.incrementAndGet();
    }

    public boolean hasProjectionFailures() {
        return projectionFailureCount.get() > 0L;
    }

    public long getProjectionFailureCount() {
        return projectionFailureCount.get();
    }

    public void reset() {
        projectionFailureCount.set(0L);
    }
}
