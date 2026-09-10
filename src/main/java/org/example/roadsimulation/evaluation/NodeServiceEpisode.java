package org.example.roadsimulation.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase 7B：一次装货或卸货服务的持久化事实账本。
 *
 * <p>这里只保存任务、车辆、节点的标量标识，不建立反向 JPA 外键。评价账本是业务执行的
 * 观察结果，不能因为历史事件尚未清理而阻止 Assignment、AssignmentNode 或 Vehicle 的
 * 正常清理。完整仿真 reset 仍会显式删除本表。</p>
 */
@Entity
@Table(
        name = "node_service_episode",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_node_service_episode_event_key",
                columnNames = "event_key"
        ),
        indexes = {
                @Index(name = "idx_node_service_assignment", columnList = "assignment_id"),
                @Index(name = "idx_node_service_node", columnList = "assignment_node_id"),
                @Index(name = "idx_node_service_status_completed", columnList = "status,service_completed_at")
        }
)
public class NodeServiceEpisode {

    public enum ActionType {
        LOAD,
        UNLOAD
    }

    public enum Status {
        IN_SERVICE,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Phase 7B：由节点 id 或“任务+路段+动作”生成，保证重复回调不会生成第二条服务事件。
    @Column(name = "event_key", nullable = false, length = 190)
    private String eventKey;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "poi_id", nullable = false)
    private Long poiId;

    @Column(name = "assignment_node_id")
    private Long assignmentNodeId;

    @Column(name = "leg_sequence_index")
    private Integer legSequenceIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    // Phase 7B：当前没有节点容量竞争，因此到达和开始服务同刻，但仍独立保存以免锁死未来队列模型。
    @Column(name = "arrived_at", nullable = false)
    private LocalDateTime arrivedAt;

    @Column(name = "service_started_at", nullable = false)
    private LocalDateTime serviceStartedAt;

    @Column(name = "service_completed_at")
    private LocalDateTime serviceCompletedAt;

    // Phase 7B：仅在服务完成后写入；使用绝对吨数，LOAD 与 UNLOAD 都表示节点实际处理工作量。
    @Column(name = "processed_tonnes")
    private Double processedTonnes;

    @Version
    private Long version;

    protected NodeServiceEpisode() {
        // Phase 7B：仅供 JPA 构造；业务创建必须走 start(...) 以执行事实约束。
    }

    public static NodeServiceEpisode start(NodeServiceObservation observation) {
        if (observation.type() != NodeServiceObservation.Type.SERVICE_STARTED) {
            throw new IllegalArgumentException("start requires a SERVICE_STARTED observation");
        }
        NodeServiceEpisode episode = new NodeServiceEpisode();
        episode.eventKey = requireText(observation.eventKey(), "eventKey");
        episode.assignmentId = requirePositive(observation.assignmentId(), "assignmentId");
        episode.vehicleId = requirePositive(observation.vehicleId(), "vehicleId");
        episode.poiId = requirePositive(observation.poiId(), "poiId");
        episode.assignmentNodeId = optionalPositive(observation.assignmentNodeId(), "assignmentNodeId");
        episode.legSequenceIndex = observation.legSequenceIndex();
        episode.actionType = Objects.requireNonNull(observation.actionType(), "actionType is required");
        episode.status = Status.IN_SERVICE;
        episode.arrivedAt = Objects.requireNonNull(observation.occurredAt(), "arrivedAt is required");
        episode.serviceStartedAt = observation.occurredAt();
        return episode;
    }

    public void complete(LocalDateTime completedAt, double tonnes) {
        LocalDateTime completion = Objects.requireNonNull(completedAt, "completedAt is required");
        if (completion.isBefore(serviceStartedAt)) {
            throw new IllegalArgumentException("service completion cannot precede service start");
        }
        if (!Double.isFinite(tonnes) || tonnes < 0.0) {
            throw new IllegalArgumentException("processedTonnes must be finite and non-negative");
        }
        // Phase 7B：完成操作幂等；同一节点被重复观察时不得改变首次完成事实。
        if (status == Status.COMPLETED) {
            return;
        }
        status = Status.COMPLETED;
        serviceCompletedAt = completion;
        processedTonnes = tonnes;
    }

    public long getServiceSeconds() {
        if (status != Status.COMPLETED || serviceCompletedAt == null) {
            throw new IllegalStateException("service duration is only available for completed episodes");
        }
        return Duration.between(serviceStartedAt, serviceCompletedAt).getSeconds();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Long optionalPositive(Long value, String name) {
        if (value != null && value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive when present");
        }
        return value;
    }

    public Long getId() { return id; }
    public String getEventKey() { return eventKey; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getVehicleId() { return vehicleId; }
    public Long getPoiId() { return poiId; }
    public Long getAssignmentNodeId() { return assignmentNodeId; }
    public Integer getLegSequenceIndex() { return legSequenceIndex; }
    public ActionType getActionType() { return actionType; }
    public Status getStatus() { return status; }
    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public LocalDateTime getServiceStartedAt() { return serviceStartedAt; }
    public LocalDateTime getServiceCompletedAt() { return serviceCompletedAt; }
    public Double getProcessedTonnes() { return processedTonnes; }
    public Long getVersion() { return version; }
}
