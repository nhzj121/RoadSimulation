package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "assignment_leg",
        // Phase 2：同一任务的路段序号必须唯一，否则 currentLegIndex 无法稳定定位执行对象。
        uniqueConstraints = @UniqueConstraint(
                name = "uk_assignment_leg_assignment_sequence",
                columnNames = {"assignment_id", "sequence_index"}
        ),
        indexes = {
                @Index(name = "idx_assignment_leg_assignment", columnList = "assignment_id"),
                @Index(name = "idx_assignment_leg_vehicle", columnList = "vehicle_id"),
                @Index(name = "idx_assignment_leg_load_state", columnList = "load_state"),
                // Phase 4：该联合索引现用于后端权威循环按任务和执行状态筛选未完成路段。
                @Index(
                        name = "idx_assignment_leg_assignment_progress",
                        columnList = "assignment_id,progress_status"
                )
        }
)
public class AssignmentLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_poi_id")
    private POI fromPOI;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_poi_id", nullable = false)
    private POI toPOI;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id")
    private AssignmentNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id")
    private AssignmentNode toNode;

    @Column(name = "sequence_index", nullable = false)
    private Integer sequenceIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_state", nullable = false, length = 20)
    private LoadState loadState = LoadState.EMPTY;

    @Column(name = "distance_meters")
    // Phase 2：旧列保留，但语义正式固定为“计划路段距离（米）”，不再表示实际执行距离。
    private Double distanceMeters;

    @Column(name = "driving_seconds")
    // Phase 2：旧列保留，但语义正式固定为“计划路段行驶时间（秒）”。
    private Long drivingSeconds;

    // Phase 2：实际累计距离单独持久化，初始为 0；Phase 3 才会逐轮更新该字段。
    @Column(name = "executed_distance_meters", nullable = false, columnDefinition = "double default 0")
    private Double executedDistanceMeters = 0.0;

    // Phase 2：实际累计有效行驶秒数单独持久化，不能与计划 drivingSeconds 混用。
    @Column(name = "executed_driving_seconds", nullable = false, columnDefinition = "bigint default 0")
    private Long executedDrivingSeconds = 0L;

    // Phase 2：该路段第一次进入 RUNNING 时记录后端仿真时间；当前阶段保持 null。
    @Column(name = "started_sim_time")
    private LocalDateTime startedSimTime;

    // Phase 2：该路段第一次完成时记录后端仿真时间；当前阶段保持 null。
    @Column(name = "completed_sim_time")
    private LocalDateTime completedSimTime;

    // Phase 2：路段执行状态与 Assignment/Vehicle 状态分离，避免 Phase 4 再复用模糊状态字段。
    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 20)
    private ProgressStatus progressStatus = ProgressStatus.PENDING;

    // Phase 2：冻结该路段开始时的吨制载重快照，车辆后续变化不会改写历史路段语义。
    @Column(name = "current_load_tonnes", nullable = false, columnDefinition = "double default 0")
    private Double currentLoadTonnes = 0.0;

    // Phase 2：主循环与 HTTP 路径以后可能同时更新同一路段，使用乐观锁拒绝静默覆盖。
    // Phase 2 修复：新建实体保持 null，首次持久化时由 JPA/Hibernate 分配初始版本，业务代码不预置版本号。
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    // Phase 3：记录最后一次实际消费本路段时间预算的全局循环序号，用于识别重复 tick。
    // 该字段属于单次模拟运行数据，随 assignment_leg 在 reset 时一并删除。
    @Column(name = "last_processed_loop_index")
    private Integer lastProcessedLoopIndex;

    @Column(name = "carried_shipment_item_ids", columnDefinition = "TEXT")
    private String carriedShipmentItemIds;

    @Column(name = "created_at")
    // Phase 2：createdAt 仅是数据库记录审计时间，不作为 startedSimTime 的业务替代品。
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum LoadState {
        EMPTY,
        LOADED
    }

    /**
     * Phase 2：路段执行状态只描述后端路段进度，不复用车辆或任务生命周期枚举。
     */
    public enum ProgressStatus {
        PENDING,
        RUNNING,
        COMPLETED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public POI getFromPOI() { return fromPOI; }
    public void setFromPOI(POI fromPOI) { this.fromPOI = fromPOI; }

    public POI getToPOI() { return toPOI; }
    public void setToPOI(POI toPOI) { this.toPOI = toPOI; }

    public AssignmentNode getFromNode() { return fromNode; }
    public void setFromNode(AssignmentNode fromNode) { this.fromNode = fromNode; }

    public AssignmentNode getToNode() { return toNode; }
    public void setToNode(AssignmentNode toNode) { this.toNode = toNode; }

    public Integer getSequenceIndex() { return sequenceIndex; }
    public void setSequenceIndex(Integer sequenceIndex) { this.sequenceIndex = sequenceIndex; }

    public LoadState getLoadState() { return loadState; }
    public void setLoadState(LoadState loadState) { this.loadState = loadState; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public Long getDrivingSeconds() { return drivingSeconds; }
    public void setDrivingSeconds(Long drivingSeconds) { this.drivingSeconds = drivingSeconds; }

    /**
     * Phase 2：计划距离的明确米制读取入口；旧 getDistanceMeters() 为指标代码保持兼容。
     */
    public Double getPlannedDistanceMeters() { return distanceMeters; }

    /**
     * Phase 2：计划距离的明确米制写入口；仍写入既有 distance_meters 列。
     */
    public void setPlannedDistanceMeters(Double plannedDistanceMeters) { this.distanceMeters = plannedDistanceMeters; }

    /**
     * Phase 2：计划行驶时间的明确秒制读取入口；旧 getDrivingSeconds() 为指标代码保持兼容。
     */
    public Long getPlannedDrivingSeconds() { return drivingSeconds; }

    /**
     * Phase 2：计划行驶时间的明确秒制写入口；仍写入既有 driving_seconds 列。
     */
    public void setPlannedDrivingSeconds(Long plannedDrivingSeconds) { this.drivingSeconds = plannedDrivingSeconds; }

    // Phase 2：实际距离只允许非负有限值，且不能超过已知计划距离。
    public Double getExecutedDistanceMeters() { return executedDistanceMeters == null ? 0.0 : executedDistanceMeters; }
    public void setExecutedDistanceMeters(Double executedDistanceMeters) {
        double value = requireNonNegativeFinite(executedDistanceMeters, "executedDistanceMeters");
        if (distanceMeters != null && value > distanceMeters + 1.0e-6) {
            throw new IllegalArgumentException("executedDistanceMeters cannot exceed plannedDistanceMeters");
        }
        this.executedDistanceMeters = value;
    }

    // Phase 2：实际有效行驶秒数只允许非负值，且不能超过已知计划秒数。
    public Long getExecutedDrivingSeconds() { return executedDrivingSeconds == null ? 0L : executedDrivingSeconds; }
    public void setExecutedDrivingSeconds(Long executedDrivingSeconds) {
        long value = requireNonNegative(executedDrivingSeconds, "executedDrivingSeconds");
        if (drivingSeconds != null && value > drivingSeconds) {
            throw new IllegalArgumentException("executedDrivingSeconds cannot exceed plannedDrivingSeconds");
        }
        this.executedDrivingSeconds = value;
    }

    // Phase 2：开始/完成时间必须由后续后端进度服务传入仿真时间，实体不读取系统时间。
    public LocalDateTime getStartedSimTime() { return startedSimTime; }
    public void setStartedSimTime(LocalDateTime startedSimTime) { this.startedSimTime = startedSimTime; }
    public LocalDateTime getCompletedSimTime() { return completedSimTime; }
    public void setCompletedSimTime(LocalDateTime completedSimTime) { this.completedSimTime = completedSimTime; }

    // Phase 2：旧数据若出现 null，读取时按 PENDING 兼容；新写入由持久化校验补齐。
    public ProgressStatus getProgressStatus() {
        return progressStatus == null ? ProgressStatus.PENDING : progressStatus;
    }
    public void setProgressStatus(ProgressStatus progressStatus) {
        this.progressStatus = progressStatus == null ? ProgressStatus.PENDING : progressStatus;
    }

    // Phase 2：路段载重快照的单位固定为吨，并拒绝负数、NaN 和 Infinity。
    public Double getCurrentLoadTonnes() { return currentLoadTonnes == null ? 0.0 : currentLoadTonnes; }
    public void setCurrentLoadTonnes(Double currentLoadTonnes) {
        this.currentLoadTonnes = requireNonNegativeFinite(currentLoadTonnes, "currentLoadTonnes");
    }

    // Phase 2 修复：version 只由 JPA 乐观锁维护；只读暴露真实值，新建未持久化实体返回 null。
    public Long getVersion() { return version; }

    // Phase 3：循环序号由后端进度服务写入；null 表示该路段尚未消费过任何 tick。
    public Integer getLastProcessedLoopIndex() { return lastProcessedLoopIndex; }
    public void setLastProcessedLoopIndex(Integer lastProcessedLoopIndex) {
        if (lastProcessedLoopIndex != null && lastProcessedLoopIndex < 0) {
            throw new IllegalArgumentException(
                    "lastProcessedLoopIndex must be non-negative: " + lastProcessedLoopIndex
            );
        }
        this.lastProcessedLoopIndex = lastProcessedLoopIndex;
    }

    public String getCarriedShipmentItemIds() { return carriedShipmentItemIds; }
    public void setCarriedShipmentItemIds(String carriedShipmentItemIds) { this.carriedShipmentItemIds = carriedShipmentItemIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Long> getCarriedShipmentItemIdList() {
        List<Long> ids = new ArrayList<>();
        if (carriedShipmentItemIds == null || carriedShipmentItemIds.isBlank()) {
            return ids;
        }
        String[] parts = carriedShipmentItemIds.split(",");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy values.
            }
        }
        return ids;
    }

    public void setCarriedShipmentItemIdList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            this.carriedShipmentItemIds = "";
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        this.carriedShipmentItemIds = builder.toString();
    }

    /**
     * Phase 2：判断指标重建是否仍可安全删除并重建该路段。
     * 一旦有执行状态、进度或仿真时间，规划服务不得覆盖它。
     */
    @Transient
    public boolean hasExecutionStarted() {
        return getProgressStatus() != ProgressStatus.PENDING
                || getExecutedDistanceMeters() > 0.0
                || getExecutedDrivingSeconds() > 0L
                || startedSimTime != null
                || completedSimTime != null;
    }

    /**
     * Phase 2：集中校验路段执行不变量；Phase 3 的每次进度写入必须继续满足这些约束。
     */
    @Transient
    public void validateExecutionState() {
        double executedMeters = requireNonNegativeFinite(getExecutedDistanceMeters(), "executedDistanceMeters");
        long executedSeconds = requireNonNegative(getExecutedDrivingSeconds(), "executedDrivingSeconds");
        requireNonNegativeFinite(getCurrentLoadTonnes(), "currentLoadTonnes");
        // Phase 3：重复 tick 去重标记不能使用负循环序号。
        if (lastProcessedLoopIndex != null && lastProcessedLoopIndex < 0) {
            throw new IllegalStateException("lastProcessedLoopIndex must be non-negative");
        }

        if (distanceMeters != null && executedMeters > distanceMeters + 1.0e-6) {
            throw new IllegalStateException("executedDistanceMeters cannot exceed plannedDistanceMeters");
        }
        if (drivingSeconds != null && executedSeconds > drivingSeconds) {
            throw new IllegalStateException("executedDrivingSeconds cannot exceed plannedDrivingSeconds");
        }
        if (startedSimTime != null && completedSimTime != null && completedSimTime.isBefore(startedSimTime)) {
            throw new IllegalStateException("completedSimTime cannot be before startedSimTime");
        }
        if (getProgressStatus() == ProgressStatus.PENDING && hasPendingStateResidue()) {
            throw new IllegalStateException("PENDING leg cannot contain executed progress or simulation timestamps");
        }
        if (getProgressStatus() == ProgressStatus.RUNNING && startedSimTime == null) {
            throw new IllegalStateException("RUNNING leg requires startedSimTime");
        }
        if (getProgressStatus() == ProgressStatus.COMPLETED) {
            if (startedSimTime == null || completedSimTime == null) {
                throw new IllegalStateException("COMPLETED leg requires start and completion simulation times");
            }
            if (distanceMeters != null && Math.abs(executedMeters - distanceMeters) > 1.0e-6) {
                throw new IllegalStateException("COMPLETED leg must end at plannedDistanceMeters");
            }
            if (drivingSeconds != null && executedSeconds != drivingSeconds) {
                throw new IllegalStateException("COMPLETED leg must end at plannedDrivingSeconds");
            }
        }
    }

    // Phase 2：旧表只在单次模拟内存活；持久化时统一补齐新增字段默认值并执行不变量校验。
    @PrePersist
    @PreUpdate
    private void normalizeAndValidateExecutionState() {
        if (executedDistanceMeters == null) executedDistanceMeters = 0.0;
        if (executedDrivingSeconds == null) executedDrivingSeconds = 0L;
        if (progressStatus == null) progressStatus = ProgressStatus.PENDING;
        if (currentLoadTonnes == null) currentLoadTonnes = 0.0;
        // Phase 2 修复：不在回调中修改 @Version，否则会破坏 JPA 对新建/已持久化实体的版本判定。
        validateExecutionState();
    }

    // Phase 2：PENDING 状态不能携带任何执行痕迹，避免“状态未开始但指标已推进”的脏数据。
    private boolean hasPendingStateResidue() {
        return getExecutedDistanceMeters() > 0.0
                || getExecutedDrivingSeconds() > 0L
                || startedSimTime != null
                || completedSimTime != null
                // Phase 3：PENDING 路段不能声称已经处理过循环，否则重复执行保护会掩盖脏数据。
                || lastProcessedLoopIndex != null;
    }

    // Phase 2：执行数据的浮点字段统一执行非负有限值校验。
    private double requireNonNegativeFinite(Double value, String fieldName) {
        if (value == null || !Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative finite value: " + value);
        }
        return value;
    }

    // Phase 2：执行秒数字段统一执行非负校验。
    private long requireNonNegative(Long value, String fieldName) {
        if (value == null || value < 0L) {
            throw new IllegalArgumentException(fieldName + " must be non-negative: " + value);
        }
        return value;
    }
}
