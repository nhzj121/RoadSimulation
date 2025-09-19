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
 * 运单（业务主单）
 * 说明：使用 JPA 注解与校验注解；
 * 与 POI (起运/目的地) 关联；与 ShipmentItem 一对多；与 Customer 多对一。
 */
@Entity
@Table(
        name = "shipment",
        indexes = {
                @Index(name = "idx_shipment_status", columnList = "status"),
                @Index(name = "idx_shipment_origin_poi", columnList = "origin_poi_id"),
                @Index(name = "idx_shipment_dest_poi", columnList = "dest_poi_id"),
                @Index(name = "idx_shipment_customer", columnList = "customer_id") // 新增索引
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
    @Size(max = 100, message = "参考号长度不能超过100个字符")
    @Column(name = "ref_no", unique = true)
    private String refNo; // 客户/系统参考号

    @Size(max = 100, message = "货类长度不能超过100个字符")
    @Column(name = "cargo_type")
    private String cargoType; // 货类，如普货、冷链、危化

    @Min(value = 0, message = "总重量不能为负数")
    @Column(name = "total_weight")
    private Double totalWeight; // kg

    @Min(value = 0, message = "总体积不能为负数")
    @Column(name = "total_volume")
    private Double totalVolume; // m3

    public enum ShipmentStatus {
        CREATED,      // 已创建
        PLANNED,      // 已规划/待派车
        PICKED_UP,    // 已提货
        IN_TRANSIT,   // 在途
        DELIVERED,    // 已送达
        CANCELLED     // 已取消
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    // 与客户的多对一关系
    @NotNull(message = "客户不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // 起运地 / 目的地（与你项目里的 POI 实体关联）
    @NotNull(message = "起运地不能为空")
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "origin_poi_id")
    private POI originPOI;

    @NotNull(message = "目的地不能为空")
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
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

    // 与明细的一对多
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ShipmentItem> items = new HashSet<>();

    public Shipment() {}

    public Shipment(String refNo, String cargoType, Double totalWeight, Double totalVolume) {
        this.refNo = refNo;
        this.cargoType = cargoType;
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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public Double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(Double totalWeight) { this.totalWeight = totalWeight; }

    public Double getTotalVolume() { return totalVolume; }
    public void setTotalVolume(Double totalVolume) { this.totalVolume = totalVolume; }

    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public POI getOriginPOI() { return originPOI; }
    public void setOriginPOI(POI originPOI) { this.originPOI = originPOI; }

    public POI getDestPOI() { return destPOI; }
    public void setDestPOI(POI destPOI) { this.destPOI = destPOI; }

    public LocalDateTime getPickupAppoint() { return pickupAppoint; }
    public void setPickupAppoint(LocalDateTime pickupAppoint) { this.pickupAppoint = pickupAppoint; }

    public LocalDateTime getDeliveryAppoint() { return deliveryAppoint; }
    public void setDeliveryAppoint(LocalDateTime deliveryAppoint) { this.deliveryAppoint = deliveryAppoint; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Set<ShipmentItem> getItems() { return items; }
    public void setItems(Set<ShipmentItem> items) { this.items = items; }

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
                '}';
    }
}