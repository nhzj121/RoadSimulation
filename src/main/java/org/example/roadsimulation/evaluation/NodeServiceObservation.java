package org.example.roadsimulation.evaluation;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase 7B：运输生命周期提交给节点服务账本的不可变观察事件。
 *
 * <p>事件不包含 JPA 实体引用，事务提交后的投影不会继续访问已经脱离持久化上下文的懒加载关系。</p>
 */
public record NodeServiceObservation(
        Type type,
        String eventKey,
        Long assignmentId,
        Long vehicleId,
        Long poiId,
        Long assignmentNodeId,
        Integer legSequenceIndex,
        NodeServiceEpisode.ActionType actionType,
        LocalDateTime occurredAt,
        Double processedTonnes
) {
    public enum Type {
        SERVICE_STARTED,
        SERVICE_COMPLETED
    }

    public NodeServiceObservation {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(actionType, "actionType is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (type == Type.SERVICE_STARTED && (eventKey == null || eventKey.isBlank())) {
            throw new IllegalArgumentException("started observation requires eventKey");
        }
        if (type == Type.SERVICE_COMPLETED && processedTonnes == null) {
            throw new IllegalArgumentException("completed observation requires processedTonnes");
        }
        if (processedTonnes != null && (!Double.isFinite(processedTonnes) || processedTonnes < 0.0)) {
            throw new IllegalArgumentException("processedTonnes must be finite and non-negative");
        }
    }
}
