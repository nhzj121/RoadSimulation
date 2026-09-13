package org.example.roadsimulation.evaluation;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 9A-1：锁存等待事实投影的首个失败。
 *
 * <p>等待账本属于评价观察侧。投影失败不能回滚运输业务，但后续等待指标必须失败封闭，
 * 不能把缺失事实解释成零等待。健康状态只在完整仿真 reset 后清除。</p>
 */
@Component
public class WaitFactLedgerHealth {

    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicReference<FailureDetail> firstFailure = new AtomicReference<>();

    public void recordProjectionFailure(
            String simulationRunId,
            Integer loopIndex,
            LocalDateTime occurredAt,
            String stage,
            String reason
    ) {
        long number = failureCount.incrementAndGet();
        firstFailure.compareAndSet(null, new FailureDetail(
                number,
                normalize(simulationRunId, "UNKNOWN_RUN"),
                loopIndex,
                occurredAt,
                normalize(stage, "UNKNOWN"),
                summarize(reason)
        ));
    }

    public boolean hasProjectionFailures() {
        return failureCount.get() > 0L;
    }

    public long getProjectionFailureCount() {
        return failureCount.get();
    }

    public Optional<FailureDetail> getFirstFailure() {
        return Optional.ofNullable(firstFailure.get());
    }

    public String describeFirstFailure() {
        FailureDetail failure = firstFailure.get();
        if (failure == null) {
            return "首次失败上下文不可用";
        }
        return "runId=" + failure.simulationRunId()
                + ", loop=" + valueOf(failure.loopIndex())
                + ", simTime=" + valueOf(failure.occurredAt())
                + ", stage=" + failure.stage()
                + ", reason=" + failure.reason();
    }

    public void reset() {
        failureCount.set(0L);
        firstFailure.set(null);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String summarize(String reason) {
        String normalized = normalize(reason, "unknown");
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private String valueOf(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    /** Phase 9A-1：只保存诊断标量，不持有业务实体或持久化上下文。 */
    public record FailureDetail(
            long failureNumber,
            String simulationRunId,
            Integer loopIndex,
            LocalDateTime occurredAt,
            String stage,
            String reason
    ) {
    }
}
