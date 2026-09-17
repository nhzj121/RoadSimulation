package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.roadsimulation.core.TransportUnits;

import java.time.LocalDateTime;

/**
 * 运单明细：只表示运输需求中的货物项。
 */
@Entity
@Table(
        name = "shipment_item",
        indexes = {
                @Index(name = "idx_item_shipment", columnList = "shipment_id"),
                @Index(name = "idx_item_goods", columnList = "goods_id"),
        }
)
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "运单不能为空")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id")
    private Goods goods;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    // ==================== 原有字段 ====================

    @NotNull
    @Size(max = 200, message = "品名长度不能超过 200 个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    public enum ShipmentItemStatus {
        NOT_ASSIGNED, ASSIGNED, LOADED, IN_TRANSIT, DELIVERED, CANCELLED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ShipmentItem.ShipmentItemStatus status = ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED;

    @Size(max = 100, message = "SKU 长度不能超过 100 个字符")
    @Column(name = "sku")
    private String sku;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    @Column(name = "qty")
    private Integer qty;

    @Min(value = 0, message = "重量不能为负数")
    // Phase 1（运输语义）：weight 是该 ShipmentItem 的总重量，单位为吨。
    @Column(name = "weight")
    private Double weight;

    @Min(value = 0, message = "体积不能为负数")
    @Column(name = "volume")
    private Double volume;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    @Column(name = "allocated_distance_meters")
    private Double allocatedDistanceMeters;

    @Column(name = "allocated_driving_seconds")
    private Long allocatedDrivingSeconds;

    @Column(name = "loading_wait_seconds")
    private Long loadingWaitSeconds;

    @Column(name = "unloading_wait_seconds")
    private Long unloadingWaitSeconds;

    @Column(name = "waiting_assignment_seconds")
    private Long waitingAssignmentSeconds;

    public ShipmentItem() {}

    public ShipmentItem(@NotNull Shipment shipment, String name, Integer qty, String sku, Double weight, Double volume) {
        this.shipment = shipment;
        this.name = name;
        this.qty = qty;
        this.sku = sku;
        this.weight = weight;
        this.volume = volume;
    }

    // ==================== Getter & Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) {
        if (this.shipment == shipment) return;
        Shipment oldShipment = this.shipment;
        this.shipment = shipment;
        if (oldShipment != null) {
            oldShipment.removeItem(this);
        }
        if (shipment != null) {
            shipment.addItem(this);
        }
    }

    public Goods getGoods() { return goods; }
    public void setGoods(Goods goods) { this.goods = goods; }

    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) {
        if (this.assignment == assignment) return;
        Assignment oldAssignment = this.assignment;
        if (oldAssignment != null) {
            oldAssignment.getShipmentItems().remove(this);
        }
        this.assignment = assignment;
        if (assignment != null) {
            assignment.addShipmentItem(this);
        }
    }

    // 原有字段 Getter & Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public ShipmentItemStatus getStatus() { return status; }
    public void setStatus(ShipmentItemStatus status) { this.status = status; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    /** Phase 1：货物项总重量的规范吨制读取入口。 */
    @Transient
    public Double getWeightTonnes() {
        return weight == null ? null : TransportUnits.tonnes(weight);
    }

    /** Phase 1：货物项总重量的规范吨制写入口，仍写入旧 weight 列。 */
    public void setWeightTonnes(Double tonnes) {
        this.weight = tonnes == null ? null : TransportUnits.tonnes(tonnes);
    }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    public Double getAllocatedDistanceMeters() { return allocatedDistanceMeters; }
    public void setAllocatedDistanceMeters(Double allocatedDistanceMeters) { this.allocatedDistanceMeters = allocatedDistanceMeters; }

    public Long getAllocatedDrivingSeconds() { return allocatedDrivingSeconds; }
    public void setAllocatedDrivingSeconds(Long allocatedDrivingSeconds) { this.allocatedDrivingSeconds = allocatedDrivingSeconds; }

    public Long getLoadingWaitSeconds() { return loadingWaitSeconds; }
    public void setLoadingWaitSeconds(Long loadingWaitSeconds) { this.loadingWaitSeconds = loadingWaitSeconds; }

    public Long getUnloadingWaitSeconds() { return unloadingWaitSeconds; }
    public void setUnloadingWaitSeconds(Long unloadingWaitSeconds) { this.unloadingWaitSeconds = unloadingWaitSeconds; }

    public Long getWaitingAssignmentSeconds() { return waitingAssignmentSeconds; }
    public void setWaitingAssignmentSeconds(Long waitingAssignmentSeconds) { this.waitingAssignmentSeconds = waitingAssignmentSeconds; }

    @Override
    public String toString() {
        return "ShipmentItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sku='" + sku + '\'' +
                ", status=" + status +
                '}';
    }
}
