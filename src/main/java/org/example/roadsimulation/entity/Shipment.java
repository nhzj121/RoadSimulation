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
 * 运单（业务主单）- 方案 A：直接添加加工字段
 * 支持 Y 形加工链（多链合并）
 */
@Entity
@Table(
        name = "shipment",
        indexes = {
                @Index(name = "idx_shipment_status", columnList = "status"),
                @Index(name = "idx_shipment_origin_poi", columnList = "origin_poi_id"),
                @Index(name = "idx_shipment_dest_poi", columnList = "dest_poi_id"),
                @Index(name = "idx_shipment_customer", columnList = "customer_id"),
                @Index(name = "idx_processing_status", columnList = "processing_status"),
                @Index(name = "idx_processing_chain", columnList = "processing_chain_id")
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
    private String refNo;

    @Size(max = 100, message = "货类长度不能超过 100 个字符")
    @Column(name = "cargo_type")
    private String cargoType;

    @Min(value = 0, message = "总重量不能为负数")
    @Column(name = "total_weight")
    private Double totalWeight;

    @Min(value = 0, message = "总体积不能为负数")
    @Column(name = "total_volume")
    private Double totalVolume;

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

    /**
     * 加工状态枚举（加工运单特有）
     */
    public enum ProcessingStatus {
        PENDING,      // 待处理
        IN_PROCESS,   // 加工中
        COMPLETED,    // 已完成
        CANCELLED     // 已取消
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
    private LocalDateTime pickupAppoint;

    @Column(name = "delivery_appoint")
    private LocalDateTime deliveryAppoint;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ==================== 加工链特有字段（方案 A）====================

    @Column(name = "is_processing_shipment")
    private Boolean processingShipment = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_chain_id")
    private ProcessingChain processingChain;

    @Column(name = "chain_code", length = 50)
    private String chainCode;

    @Column(name = "chain_name", length = 100)
    private String chainName;

    @Column(name = "expected_yield_rate")
    private Double expectedYieldRate;

    @Column(name = "expected_output_weight")
    private Double expectedOutputWeight;

    @Column(name = "actual_output_weight")
    private Double actualOutputWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 20)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "processing_start_time")
    private LocalDateTime processingStartTime;

    @Column(name = "processing_expected_finish_time")
    private LocalDateTime processingExpectedFinishTime;

    @Column(name = "processing_actual_finish_time")
    private LocalDateTime processingActualFinishTime;

    /**
     * 上游运单 IDs（用于 Y 形加工链合并）
     * 例如：运单 C 的 upstreamShipmentIds = [A.id, B.id]
     */
    @ElementCollection
    @CollectionTable(name = "shipment_upstream_relations", 
                     joinColumns = @JoinColumn(name = "shipment_id"))
    @Column(name = "upstream_shipment_id")
    private Set<Long> upstreamShipmentIds = new HashSet<>();

    // ==================== 与明细的一对多 ====================

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ShipmentItem> items = new HashSet<>();

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    public Shipment() {}

    public Shipment(String refNo, POI startPOI, POI endPOI, Double totalWeight, Double totalVolume) {
        this.refNo = refNo;
        this.originPOI = startPOI;
        this.destPOI = endPOI;
        this.totalWeight = totalWeight;
        this.totalVolume = totalVolume;
    }

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

    // 加工链特有字段 Getter & Setter
    public Boolean getProcessingShipment() { return processingShipment; }
    public void setProcessingShipment(Boolean processingShipment) { this.processingShipment = processingShipment; }

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

    public Set<Long> getUpstreamShipmentIds() { return upstreamShipmentIds; }
    public void setUpstreamShipmentIds(Set<Long> upstreamShipmentIds) { this.upstreamShipmentIds = upstreamShipmentIds; }

    /**
     * 添加上游运单 ID
     */
    public void addUpstreamShipmentId(Long shipmentId) {
        if (shipmentId != null) {
            upstreamShipmentIds.add(shipmentId);
        }
    }

    /**
     * 判断是否是合并运单（Y 形的下游运单）
     */
    public boolean isMergeShipment() {
        return upstreamShipmentIds != null && !upstreamShipmentIds.isEmpty();
    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

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
                ", processingShipment=" + processingShipment +
                ", processingStatus=" + processingStatus +
                ", customer=" + (customer != null ? customer.getId() : "null") +
                ", originPOI=" + (originPOI != null ? originPOI.getId() : "null") +
                ", destPOI=" + (destPOI != null ? destPOI.getId() : "null") +
                ", totalDrivingTime=" + totalDrivingTime +
                ", totalDrivingDistance=" + totalDrivingDistance +
                '}';
    }
}
