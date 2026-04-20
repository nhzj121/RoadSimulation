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
//=====================加工状态枚举=================================
    public enum ProcessingStatus {
        PENDING, IN_PROCESS, COMPLETED, CANCELLED
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

    //=======================同步shipment=======================

    // ==================== 行驶统计 ====================
    @Column(name = "total_driving_time")
    private Long totalDrivingTime;           // 总行驶时间（秒）

    @Column(name = "total_driving_distance")
    private Double totalDrivingDistance;      // 总行驶里程（公里）

    @Column(name = "empty_driving_time_seconds")
    private Long emptyDrivingTime;     // 空驶时间（秒）


    @Column(name = "empty_driving_distance_km")
    private Double emptyDrivingDistance;    // 空驶里程（公里）

    // ==================== 货物信息 ====================
    @Column(name = "cargo_type", length = 100)
    private String cargoType;                 // 货类

    // ==================== 客户 ====================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;                // 客户

    // ==================== 起运地/目的地 ====================
    @NotNull(message = "起运地不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_poi_id")
    private POI originPOI;                    // 起运地

    @NotNull(message = "目的地不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_poi_id")
    private POI destPOI;                      // 目的地

    // ==================== 预约时间 ====================
    @Column(name = "pickup_appoint")
    private LocalDateTime pickupAppoint;      // 预约提货时间

    @Column(name = "delivery_appoint")
    private LocalDateTime deliveryAppoint;    // 预约送达时间

    // ==================== 加工链相关（新增） ====================
    @Column(name = "is_processing_assignment")
    private Boolean processingAssignment = false;   // 是否为加工任务

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_chain_id")
    private ProcessingChain processingChain;        // 所属加工链

    @Column(name = "chain_code", length = 50)
    private String chainCode;                       // 加工链编码

    @Column(name = "chain_name", length = 100)
    private String chainName;                       // 加工链名称

    @Column(name = "expected_yield_rate")
    private Double expectedYieldRate;               // 预期出品率

    @Column(name = "expected_output_weight")
    private Double expectedOutputWeight;            // 预期产出重量

    @Column(name = "actual_output_weight")
    private Double actualOutputWeight;              // 实际产出重量

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 20)
    private ProcessingStatus processingStatus;      // 加工状态（PENDING/IN_PROCESS/COMPLETED/CANCELLED）

    @Column(name = "processing_start_time")
    private LocalDateTime processingStartTime;      // 加工开始时间

    @Column(name = "processing_expected_finish_time")
    private LocalDateTime processingExpectedFinishTime; // 预计加工完成时间

    @Column(name = "processing_actual_finish_time")
    private LocalDateTime processingActualFinishTime;   // 实际加工完成时间

    @Column(name = "loading_wait_time")
    private Long loadingWaitTime;            // 等待装货时间（秒）

    @Column(name = "unloading_wait_time")
    private Long unloadingWaitTime;          // 等待卸货时间（秒）

    @Column(name = "waiting_assignment_time")
    private Long waitingAssignmentTime;      // 等待分配任务时间（秒）
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

    //===========================================添加部分=========================================================
    // 行驶统计
    public Long getTotalDrivingTime() { return totalDrivingTime; }
    public void setTotalDrivingTime(Long totalDrivingTime) { this.totalDrivingTime = totalDrivingTime; }

    public Double getTotalDrivingDistance() { return totalDrivingDistance; }
    public void setTotalDrivingDistance(Double totalDrivingDistance) { this.totalDrivingDistance = totalDrivingDistance; }

    public Long getEmptyDrivingTime() { return emptyDrivingTime; }
    public void setEmptyDrivingTime(Long emptyDrivingTime) { this.emptyDrivingTime = emptyDrivingTime; }

    public Double getEmptyDrivingDistance() { return emptyDrivingDistance; }
    public void setEmptyDrivingDistance(Double emptyDrivingDistance) { this.emptyDrivingDistance = emptyDrivingDistance; }
    // 货物信息
    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    // 客户
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    // 起运地/目的地
    public POI getOriginPOI() { return originPOI; }
    public void setOriginPOI(POI originPOI) { this.originPOI = originPOI; }

    public POI getDestPOI() { return destPOI; }
    public void setDestPOI(POI destPOI) { this.destPOI = destPOI; }

    // 预约时间
    public LocalDateTime getPickupAppoint() { return pickupAppoint; }
    public void setPickupAppoint(LocalDateTime pickupAppoint) { this.pickupAppoint = pickupAppoint; }

    public LocalDateTime getDeliveryAppoint() { return deliveryAppoint; }
    public void setDeliveryAppoint(LocalDateTime deliveryAppoint) { this.deliveryAppoint = deliveryAppoint; }

    // 加工链相关
    public Boolean getProcessingAssignment() { return processingAssignment; }
    public void setProcessingAssignment(Boolean processingAssignment) { this.processingAssignment = processingAssignment; }

    public ProcessingChain getProcessingChain() { return processingChain; }
    public void setProcessingChain(ProcessingChain processingChain) { this.processingChain = processingChain; }

    public String getChainCode() { return chainCode; }
    public void setChainCode(String chainCode) { this.chainCode = chainCode; }

    public String getChainName() { return chainName; }
    public void setChainName(String chainName) { this.chainName = chainName; }

    public Double getExpectedYieldRate() { return expectedYieldRate; }
    public void setExpectedYieldRate(Double expectedYieldRate) { this.expectedYieldRate = expectedYieldRate; }

    public Double getExpectedOutputWeight() { return expectedOutputWeight; }
    public void setExpectedOutputWeight(Double expectedOutputWeight) { this.expectedOutputWeight = expectedOutputWeight; }

    public Double getActualOutputWeight() { return actualOutputWeight; }
    public void setActualOutputWeight(Double actualOutputWeight) { this.actualOutputWeight = actualOutputWeight; }

    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }

    public LocalDateTime getProcessingStartTime() { return processingStartTime; }
    public void setProcessingStartTime(LocalDateTime processingStartTime) { this.processingStartTime = processingStartTime; }

    public LocalDateTime getProcessingExpectedFinishTime() { return processingExpectedFinishTime; }
    public void setProcessingExpectedFinishTime(LocalDateTime processingExpectedFinishTime) { this.processingExpectedFinishTime = processingExpectedFinishTime; }

    public LocalDateTime getProcessingActualFinishTime() { return processingActualFinishTime; }
    public void setProcessingActualFinishTime(LocalDateTime processingActualFinishTime) { this.processingActualFinishTime = processingActualFinishTime; }

    public Long getLoadingWaitTime() { return loadingWaitTime; }
    public void setLoadingWaitTime(Long loadingWaitTime) { this.loadingWaitTime = loadingWaitTime; }

    public Long getUnloadingWaitTime() { return unloadingWaitTime; }
    public void setUnloadingWaitTime(Long unloadingWaitTime) { this.unloadingWaitTime = unloadingWaitTime; }

    public Long getWaitingAssignmentTime() { return waitingAssignmentTime; }
    public void setWaitingAssignmentTime(Long waitingAssignmentTime) { this.waitingAssignmentTime = waitingAssignmentTime; }
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
