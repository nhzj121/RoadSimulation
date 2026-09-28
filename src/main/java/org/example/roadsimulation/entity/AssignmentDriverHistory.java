package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 司机-任务交接历史（append-only）。
 * 记录任务绑定/释放/换司机的完整链，供追溯与统计使用；
 * 不作为独立评价口径，评价仍以 Assignment 与后端仿真时间为权威事实。
 */
@Entity
@Table(name = "assignment_driver_history")
public class AssignmentDriverHistory {

    public enum Action {
        BIND,    // 司机绑定任务（首次绑定或换司机接手）
        RELEASE  // 司机离开任务（任务完成释放/换司机让位/无替补离开）
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "driver_name", length = 50)
    private String driverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private Action action;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "sim_time")
    private LocalDateTime simTime;

    @Column(name = "actor", length = 50)
    private String actor;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public LocalDateTime getSimTime() { return simTime; }
    public void setSimTime(LocalDateTime simTime) { this.simTime = simTime; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
