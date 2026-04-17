package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(
        name = "assignment",
        indexes = {
                @Index(name = "idx_assignment_status", columnList = "status"),
                @Index(name = "idx_assignment_vehicle", columnList = "vehicle_id"),
                @Index(name = "idx_assignment_driver", columnList = "driver_id"),
                @Index(name = "idx_assignment_route", columnList = "route_id")
        })
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Assignment(ShipmentItem item, Route route) {
        // 先把路线存进去，解决 null 的问题！
        this.route = route;

        // 原来的老逻辑好像还期待这里把 shipmentItem 绑定上
        if (item != null) {
            this.addShipmentItem(item);
        }
    }

    public void addShipmentItem(ShipmentItem shipmentItem) {
        if (shipmentItem != null) {
            this.shipmentItems.add(shipmentItem);
            shipmentItem.setAssignment(this);
        }
    }

    public enum AssignmentStatus {
        WAITING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED, DELAYED
    }

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssignmentStatus status;

    @Column(name = "current_action_index", columnDefinition = "integer default 0")
    private Integer currentActionIndex;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "action_line", columnDefinition = "TEXT")
    private String actionLineJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle assignedVehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver assignedDriver;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ShipmentItem> shipmentItems = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // ================= 新增：支持一车多装的节点集合 =================
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceIndex ASC") // 确保从数据库查出来时是有序的
    private List<AssignmentNode> nodes = new ArrayList<>();

    public Assignment() {}

    // ==================== Getter / Setter ====================
    public Long getId() { return id; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public Integer getCurrentActionIndex() { return currentActionIndex; }
    public void setCurrentActionIndex(Integer currentActionIndex) { this.currentActionIndex = currentActionIndex; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getActionLineJson() { return actionLineJson; }
    public void setActionLineJson(String actionLineJson) { this.actionLineJson = actionLineJson; }
    public Vehicle getAssignedVehicle() { return assignedVehicle; }
    public void setAssignedVehicle(Vehicle assignedVehicle) { this.assignedVehicle = assignedVehicle; }
    public Driver getAssignedDriver() { return assignedDriver; }
    public void setAssignedDriver(Driver assignedDriver) { this.assignedDriver = assignedDriver; }
    public Set<ShipmentItem> getShipmentItems() { return shipmentItems; }
    public void setShipmentItems(Set<ShipmentItem> shipmentItems) { this.shipmentItems = shipmentItems; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public List<AssignmentNode> getNodes() {
        return nodes;
    }
    public void setNodes(List<AssignmentNode> nodes) {
        this.nodes = nodes;
    }

    // ==================== Action Line JSON ====================
    @JsonIgnore
    public List<Long> getActionLine() {
        if (actionLineJson == null || actionLineJson.isEmpty()) return new ArrayList<>();
        try {
            return new ObjectMapper().readValue(actionLineJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @JsonProperty
    public void setActionLine(List<Long> actionIds) {
        try {
            this.actionLineJson = new ObjectMapper().writeValueAsString(actionIds);
        } catch (Exception e) {
            this.actionLineJson = "[]";
        }
    }

    // ==================== 任务动作推进 ====================
    public boolean moveToNextAction() {
        return moveToNextAction(LocalDateTime.now());
    }

    public boolean moveToNextAction(LocalDateTime simNow) {
        List<Long> actionIds = getActionLine();
        if (actionIds == null || actionIds.isEmpty()) return true;

        if (currentActionIndex == null) currentActionIndex = 0;

        if (currentActionIndex < actionIds.size() - 1) {
            currentActionIndex++;
            return true;
        } else {
            this.status = AssignmentStatus.COMPLETED;
            this.endTime = simNow;
            return false;
        }
    }
    /// ======================= 涉及AssignmentNode的相关辅助方法 ================
    // 辅助方法：确保双向关联的正确性
    public void addNode(AssignmentNode node) {
        nodes.add(node);
        node.setAssignment(this);
    }

    public void removeNode(AssignmentNode node) {
        nodes.remove(node);
        node.setAssignment(null);
    }

    // 辅助方法：获取当前未完成的下一个节点
    public AssignmentNode getNextPendingNode() {
        if (nodes == null || nodes.isEmpty()) return null;
        for (AssignmentNode node : nodes) {
            if (!node.isCompleted()) {
                return node;
            }
        }
        return null; // 全部完成
    }
    ///  ===============================================

    public boolean isAssigned() {
        return this.status == AssignmentStatus.ASSIGNED;
    }
    public boolean isInProgress() { return AssignmentStatus.IN_PROGRESS.equals(this.status); }
    public boolean isCompleted() { return AssignmentStatus.COMPLETED.equals(this.status); }
    public boolean isWaiting() { return AssignmentStatus.WAITING.equals(this.status); }
    public boolean isCancelled() { return AssignmentStatus.CANCELLED.equals(this.status); }
}
