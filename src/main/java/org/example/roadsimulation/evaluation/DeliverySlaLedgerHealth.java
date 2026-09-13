package org.example.roadsimulation.evaluation;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Phase 9B-2：锁存交付 SLA 账本的首个投影失败，避免后续评价静默少算。 */
@Component
public class DeliverySlaLedgerHealth {

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

    public boolean hasProjectionFailures() { return failureCount.get() > 0L; }
    public long getProjectionFailureCount() { return failureCount.get(); }
    public Optional<FailureDetail> getFirstFailure() { return Optional.ofNullable(firstFailure.get()); }

    public String describeFirstFailure() {
        FailureDetail failure = firstFailure.get();
        if (failure == null) return "首次失败上下文不可用";
        return "runId=" + failure.simulationRunId()
                + ", loop=" + valueOf(failure.loopIndex())
                + ", simTime=" + valueOf(failure.occurredAt())
                + ", stage=" + failure.stage()
                + ", reason=" + failure.reason();
    }

    /** Phase 9B-2：只有完整仿真 reset 才清除当前运行的失败锁存。 */
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

    private String valueOf(Object value) { return value == null ? "unknown" : String.valueOf(value); }

    /** Phase 9B-2：诊断只保留标量，不持有运输实体或持久化上下文。 */
    public record FailureDetail(
            long failureNumber,
            String simulationRunId,
            Integer loopIndex,
            LocalDateTime occurredAt,
            String stage,
            String reason
    ) { }
}
