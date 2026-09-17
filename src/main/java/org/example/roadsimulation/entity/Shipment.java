package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 运单（业务主单）：只表示一次运输需求。
 */
@Entity
@Table(
        name = "shipment",
        indexes = {
                @Index(name = "idx_shipment_status", columnList = "status"),
                @Index(name = "idx_shipment_origin_poi", columnList = "origin_poi_id"),
                @Index(name = "idx_shipment_dest_poi", columnList = "dest_poi_id"),
                @Index(name = "idx_shipment_customer", columnList = "customer_id"),
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_shipment_ref_no", columnNames = "ref_no")
        }
)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "参考号不能为空")
    @Size(max = 100, message = "参考号长度不能超过 100 个字符")
    @Column(name = "ref_no", unique = true)
    private String refNo; // 客户/系统参考号

    @Size(max = 100, message = "货类长度不能超过 100 个字符")
    @Column(name = "cargo_type")
    private String cargoType;

    @Min(value = 0, message = "总重量不能为负数")
    @Column(name = "total_weight")
    private Double totalWeight; // kg

    @Min(value = 0, message = "总体积不能为负数")
    @Column(name = "total_volume")
    private Double totalVolume; // m3

    /**
     * 新增：总行驶时间（秒）
     */
    @Column(name = "total_driving_time")
    private Long totalDrivingTime;

    /**
     * 新增：总行驶距离（米）
     */
    @Column(name = "total_driving_distance")
    private Double totalDrivingDistance;

    public enum ShipmentStatus {
        CREATED, PLANNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    // 与客户的多对一关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // 起运地 / 目的地
    @NotNull(message = "起运地不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_poi_id")
    private POI originPOI;

    @NotNull(message = "目的地不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_poi_id")
    private POI destPOI;

    @Column(name = "pickup_appoint")
    private LocalDateTime pickupAppoint;   // 预约提货时间

    @Column(name = "delivery_appoint")
    private LocalDateTime deliveryAppoint; // 预约送达时间

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ==================== 与明细的一对多 ====================

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ShipmentItem> items = new HashSet<>();

    // 进行修改的对象和时间
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "loading_wait_time")
    private Long loadingWaitTime;            // 等待装货时间（秒）

    @Column(name = "unloading_wait_time")
    private Long unloadingWaitTime;          // 等待卸货时间（秒）

    @Column(name = "waiting_assignment_time")
    private Long waitingAssignmentTime;      // 等待分配任务时间（秒）

    @Column(name = "allocated_distance_meters")
    private Double allocatedDistanceMeters;

    @Column(name = "allocated_driving_seconds")
    private Long allocatedDrivingSeconds;

    @Column(name = "loaded_distance_meters")
    private Double loadedDistanceMeters;

    @Column(name = "loaded_driving_seconds")
    private Long loadedDrivingSeconds;

    @Column(name = "loading_wait_seconds")
    private Long loadingWaitSeconds;

    @Column(name = "unloading_wait_seconds")
    private Long unloadingWaitSeconds;

    @Column(name = "waiting_assignment_seconds")
    private Long waitingAssignmentSeconds;

    public Shipment() {}

    public Shipment(String refNo, POI startPOI, POI endPOI, Double totalWeight, Double totalVolume) {
        this.refNo = refNo;
        this.originPOI = startPOI;
        this.destPOI = endPOI;
        this.totalWeight = totalWeight;
        this.totalVolume = totalVolume;
    }

    // 便捷方法：维护双向关系
    public void addItem(ShipmentItem item) {
        if (item != null) {
            items.add(item);
            item.setShipment(this);
        }
    }

    public void removeItem(ShipmentItem item) {
        if (item != null) {
            items.remove(item);
            item.setShipment(null);
        }
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRefNo() {
        return refNo;
    }

    public void setRefNo(String refNo) {
        this.refNo = refNo;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public Double getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(Double totalWeight) {
        this.totalWeight = totalWeight;
    }

    public Double getTotalVolume() {
        return totalVolume;
    }

    public void setTotalVolume(Double totalVolume) {
        this.totalVolume = totalVolume;
    }

    public Long getTotalDrivingTime() {
        return totalDrivingTime;
    }

    public void setTotalDrivingTime(Long totalDrivingTime) {
        this.totalDrivingTime = totalDrivingTime;
    }

    public Double getTotalDrivingDistance() {
        return totalDrivingDistance;
    }

    public void setTotalDrivingDistance(Double totalDrivingDistance) {
        this.totalDrivingDistance = totalDrivingDistance;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public POI getOriginPOI() {
        return originPOI;
    }

    public void setOriginPOI(POI originPOI) {
        this.originPOI = originPOI;
    }

    public POI getDestPOI() {
        return destPOI;
    }

    public void setDestPOI(POI destPOI) {
        this.destPOI = destPOI;
    }

    public LocalDateTime getPickupAppoint() {
        return pickupAppoint;
    }

    public void setPickupAppoint(LocalDateTime pickupAppoint) {
        this.pickupAppoint = pickupAppoint;
    }

    public LocalDateTime getDeliveryAppoint() {
        return deliveryAppoint;
    }

    public void setDeliveryAppoint(LocalDateTime deliveryAppoint) {
        this.deliveryAppoint = deliveryAppoint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<ShipmentItem> getItems() {
        return items;
    }

    public void setItems(Set<ShipmentItem> items) {
        this.items = items;
    }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Long getLoadingWaitTime() { return loadingWaitTime; }
    public void setLoadingWaitTime(Long loadingWaitTime) { this.loadingWaitTime = loadingWaitTime; }

    public Long getUnloadingWaitTime() { return unloadingWaitTime; }
    public void setUnloadingWaitTime(Long unloadingWaitTime) { this.unloadingWaitTime = unloadingWaitTime; }

    public Long getWaitingAssignmentTime() { return waitingAssignmentTime; }
    public void setWaitingAssignmentTime(Long waitingAssignmentTime) { this.waitingAssignmentTime = waitingAssignmentTime; }

    public Double getAllocatedDistanceMeters() { return allocatedDistanceMeters; }
    public void setAllocatedDistanceMeters(Double allocatedDistanceMeters) { this.allocatedDistanceMeters = allocatedDistanceMeters; }

    public Long getAllocatedDrivingSeconds() { return allocatedDrivingSeconds; }
    public void setAllocatedDrivingSeconds(Long allocatedDrivingSeconds) { this.allocatedDrivingSeconds = allocatedDrivingSeconds; }

    public Double getLoadedDistanceMeters() { return loadedDistanceMeters; }
    public void setLoadedDistanceMeters(Double loadedDistanceMeters) { this.loadedDistanceMeters = loadedDistanceMeters; }

    public Long getLoadedDrivingSeconds() { return loadedDrivingSeconds; }
    public void setLoadedDrivingSeconds(Long loadedDrivingSeconds) { this.loadedDrivingSeconds = loadedDrivingSeconds; }

    public Long getLoadingWaitSeconds() { return loadingWaitSeconds; }
    public void setLoadingWaitSeconds(Long loadingWaitSeconds) { this.loadingWaitSeconds = loadingWaitSeconds; }

    public Long getUnloadingWaitSeconds() { return unloadingWaitSeconds; }
    public void setUnloadingWaitSeconds(Long unloadingWaitSeconds) { this.unloadingWaitSeconds = unloadingWaitSeconds; }

    public Long getWaitingAssignmentSeconds() { return waitingAssignmentSeconds; }
    public void setWaitingAssignmentSeconds(Long waitingAssignmentSeconds) { this.waitingAssignmentSeconds = waitingAssignmentSeconds; }

    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Shipment{" +
                "id=" + id +
                ", refNo='" + refNo + '\'' +
                ", status=" + status +
                ", customer=" + (customer != null ? customer.getId() : "null") +
                ", originPOI=" + (originPOI != null ? originPOI.getId() : "null") +
                ", destPOI=" + (destPOI != null ? destPOI.getId() : "null") +
                ", totalDrivingTime=" + totalDrivingTime +
                ", totalDrivingDistance=" + totalDrivingDistance +
                '}';
    }
}
