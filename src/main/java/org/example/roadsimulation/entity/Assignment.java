package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public boolean isAssigned() {
        return this.getStatus() == AssignmentStatus.ASSIGNED;
    }

    public enum AssignmentStatus {
        WAITING,        // 等待（未分配）
        ASSIGNED,       // 已分配
        IN_PROGRESS,    // 运输中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        CANCELLED,      // 已取消
        DELAYED         // 延迟
    }

    // 创建的时间
    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @NotNull(message = "任务状态不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssignmentStatus status;

    // 针对预设行为链的索引
    @Column(name = "current_action_index", columnDefinition = "integer default 0")
    private Integer currentActionIndex;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    // 行为序列存储为Action ID的JSON数组
    @Column(name = "action_line", columnDefinition = "TEXT")
    private String actionLineJson;

    // 与Vehicle的多对一关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle assignedVehicle;

    // 与Driver的多对一关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver assignedDriver;

    // 和ShipnmentItem的一对多的关系
    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ShipmentItem> shipmentItems = new HashSet<>();

    // 与Route的多对一的关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    // 进行修改的对象和时间
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    // 无参构造函数
    public Assignment() {
    }

    // 有参构造函数
    public Assignment(Shipment shipment, Vehicle vehicle, Driver driver) {
        // this.shipment = shipment;
        this.assignedVehicle = vehicle;
        this.assignedDriver = driver;
        this.status = AssignmentStatus.ASSIGNED;
    }

    // Getters and Setters
    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}
    public AssignmentStatus getStatus() {return status;}
    public void setStatus(AssignmentStatus status) {this.status = status;}
    public Integer getCurrentActionIndex() {return currentActionIndex;}
    public void setCurrentActionIndex(Integer currentActionIndex) {this.currentActionIndex = currentActionIndex;}
    public LocalDateTime getStartTime() {return startTime;}
    public void setStartTime(LocalDateTime startTime) {this.startTime = startTime;}
    public LocalDateTime getEndTime() {return endTime;}
    public void setEndTime(LocalDateTime endTime) {this.endTime = endTime;}
    public String getActionLineJson() {return actionLineJson;}
    public Vehicle getAssignedVehicle() {return assignedVehicle;}
    public Driver getAssignedDriver() {return assignedDriver;}
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }

    // 四元组字段的getter和setter
    public LocalDateTime getCreatedTime() {return createdTime;}
    public void setCreatedTime(LocalDateTime createdTime) {this.createdTime = createdTime;}
    public String getUpdatedBy() {return updatedBy;}
    public void setUpdatedBy(String updatedBy) {this.updatedBy = updatedBy;}
    public LocalDateTime getUpdatedTime() {return updatedTime;}
    public void setUpdatedTime(LocalDateTime updatedTime) {this.updatedTime = updatedTime;}


    public void setAssignedDriver(Driver assignedDriver) {
        this.assignedDriver = assignedDriver;
    }

    public void setAssignedVehicle(Vehicle vehicle) {
        // 如果当前车辆与要设置的车辆相同，则不执行任何操作
        if (this.assignedVehicle == vehicle) {
            return;
        }

        // 如果当前已有车辆，先从该车辆的集合中移除自己
        Vehicle oldVehicle = this.assignedVehicle;
        if (oldVehicle != null) {
            oldVehicle.removeAssignment(this);
        }

        // 设置新的车辆
        this.assignedVehicle = vehicle;

        // 如果新车辆不为空，将自己添加到新车辆的集合中
        if (vehicle != null) {
            vehicle.addAssignment(this);
        }
    }

    ///  Assignment和ShipmentItem
    // 添加对应的 getter 和 setter 方法
    public Set<ShipmentItem> getShipmentItems() { return shipmentItems; }
    public void setShipmentItems(Set<ShipmentItem> shipmentItems) { this.shipmentItems = shipmentItems; }

    // 添加便捷方法来维护双向关系
    public void addShipmentItem(ShipmentItem item) {
        if (item != null) {
            this.shipmentItems.add(item);
            item.setAssignment(this);
        }
    }

    public void removeShipmentItem(ShipmentItem item) {
        if (item != null) {
            this.shipmentItems.remove(item);
            item.setAssignment(null);
        }
    }
    /**
     * 检查任务是否正在进行中
     * @return 是否正在进行中
     */
    public boolean isInProgress() {
        return AssignmentStatus.IN_PROGRESS.equals(this.status);
    }

    /**
     * 检查任务是否已完成
     */
    public boolean isCompleted() {
        return AssignmentStatus.COMPLETED.equals(this.status);
    }

    /**
     * 检查任务是否待分配
     */
    public boolean isWaiting() {
        return AssignmentStatus.WAITING.equals(this.status);
    }

    /**
     * 检查任务是否已取消
     */
    public boolean isCancelled() {
        return AssignmentStatus.CANCELLED.equals(this.status);
    }

    // 行为序列的JSON处理方法
    @JsonIgnore
    public List<Long> getActionLine() {
        if (actionLineJson == null || actionLineJson.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(actionLineJson, new TypeReference<List<Long>>(){});
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @JsonProperty
    public void setActionLine(List<Long> actionIds) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.actionLineJson = mapper.writeValueAsString(actionIds);
        } catch (Exception e) {
            e.printStackTrace();
            this.actionLineJson = "[]";
        }
    }

    /**
     * 移动到下一个动作
     * @return 是否还有下一个动作
     */
    public boolean moveToNextAction() {
        List<Long> actionIds = getActionLine();
        if (currentActionIndex < actionIds.size() - 1) {
            currentActionIndex++;
            return true;
        } else {
            // 已经是最后一个动作，标记任务完成
            this.status = AssignmentStatus.COMPLETED;
            this.endTime = LocalDateTime.now();
            return false;
        }
    }

    /**
     * 获取当前动作ID
     * @return 当前动作ID，如果没有返回null
     */
    public Long getCurrentActionId() {
        List<Long> actionIds = getActionLine();
        if (actionIds.isEmpty() || currentActionIndex >= actionIds.size()) {
            return null;
        }
        return actionIds.get(currentActionIndex);
    }

    /// 以下具体方法的实际实现需要ActionRepository
    /**
     * 开始执行任务
     * TODO
     * 计算预计完成时间
     * TODO
     * 获取任务总预计时间（分钟）
     * TODO
     * 获取剩余预计时间（分钟）
     * TODO
     */

    @Override
    public String toString() {
        return "Assignment{" +
                "id=" + id +
                ", status=" + status +
                ", currentActionIndex=" + currentActionIndex +
                // ", shipment=" + (shipment != null ? shipment.getId() : "null") +
                ", assignedVehicle=" + (assignedVehicle != null ? assignedVehicle.getId() : "null") +
                ", assignedDriver=" + (assignedDriver != null ? assignedDriver.getId() : "null") +
                ", route=" + (route != null ? route.getId() : "null") +
                ", shipmentItemsCount=" + (shipmentItems != null ? shipmentItems.size() : 0) +
                '}';
    }

}
