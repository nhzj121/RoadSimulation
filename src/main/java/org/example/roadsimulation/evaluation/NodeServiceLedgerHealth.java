package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 7B：记录当前运行中无法持久化的节点服务观察事件。
 *
 * <p>观察侧故障不能回滚运输业务，但也不能被评价层静默解释为零事件；一旦发生投影失败，
 * 节点服务指标保持 INVALID，直到完整仿真 reset 同时清空账本和本状态。</p>
 */
@Component
public class NodeServiceLedgerHealth {

    private final AtomicLong projectionFailureCount = new AtomicLong();
    // Phase 7E：只锁存当前运行的首个失败，避免后续连锁异常覆盖最接近根因的上下文。
    private final AtomicReference<FailureDetail> firstFailure = new AtomicReference<>();
    private final SimulationContext simulationContext;

    /** Phase 7E：保留无参构造器供既有纯单元测试使用。 */
    public NodeServiceLedgerHealth() {
        this(null);
    }

    /** Phase 7E：生产环境从统一仿真上下文记录失败所属循环，不读取墙上时间。 */
    @Autowired
    public NodeServiceLedgerHealth(SimulationContext simulationContext) {
        this.simulationContext = simulationContext;
    }

    public void recordProjectionFailure() {
        // Phase 7E：兼容旧调用；没有事件上下文时仍保持原有失败封闭语义。
        recordProjectionFailure("UNKNOWN", null, null, null, null, "未提供失败上下文");
    }

    /** Phase 7E：记录发布、校验或投影阶段的首个结构化失败事实。 */
    public void recordProjectionFailure(
            String stage,
            NodeServiceObservation.Type eventType,
            Long assignmentId,
            Long assignmentNodeId,
            LocalDateTime occurredAt,
            String reason
    ) {
        long failureNumber = projectionFailureCount.incrementAndGet();
        Integer loopIndex = simulationContext == null ? null : simulationContext.getLoopCount();
        firstFailure.compareAndSet(null, new FailureDetail(
                failureNumber,
                loopIndex,
                occurredAt,
                normalize(stage),
                eventType,
                assignmentId,
                assignmentNodeId,
                summarize(reason)
        ));
    }

    public boolean hasProjectionFailures() {
        return projectionFailureCount.get() > 0L;
    }

    public long getProjectionFailureCount() {
        return projectionFailureCount.get();
    }

    /** Phase 7E：评价层可读取首错详情，但不能借此修改业务或账本状态。 */
    public Optional<FailureDetail> getFirstFailure() {
        return Optional.ofNullable(firstFailure.get());
    }

    /** Phase 7E：复用指标现有 reason 字段暴露诊断，不扩张评价快照契约结构。 */
    public String describeFirstFailure() {
        FailureDetail failure = firstFailure.get();
        if (failure == null) {
            return "首次失败上下文不可用";
        }
        return "stage=" + failure.stage()
                + ", loop=" + valueOf(failure.loopIndex())
                + ", simTime=" + valueOf(failure.occurredAt())
                + ", event=" + valueOf(failure.eventType())
                + ", assignmentId=" + valueOf(failure.assignmentId())
                + ", nodeId=" + valueOf(failure.assignmentNodeId())
                + ", reason=" + failure.reason();
    }

    public void reset() {
        // Phase 7E：完整 reset 同时清除计数与首错锁存，二者始终属于同一运行。
        projectionFailureCount.set(0L);
        firstFailure.set(null);
    }

    private String summarize(String reason) {
        String normalized = reason == null || reason.isBlank() ? "unknown" : reason.trim();
        // Phase 7E：限制快照 reason 长度，避免数据库异常文本无限放大轮询响应。
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    /** Phase 7E：诊断阶段缺失时使用稳定占位符，避免 reason 再次产生空值歧义。 */
    private String normalize(String stage) {
        return stage == null || stage.isBlank() ? "UNKNOWN" : stage.trim();
    }

    private String valueOf(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    /** Phase 7E：不可变首错记录，仅包含标量，不持有 JPA 实体。 */
    public record FailureDetail(
            long failureNumber,
            Integer loopIndex,
            LocalDateTime occurredAt,
            String stage,
            NodeServiceObservation.Type eventType,
            Long assignmentId,
            Long assignmentNodeId,
            String reason
    ) {
    }
}
