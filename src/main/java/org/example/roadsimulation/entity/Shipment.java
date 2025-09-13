package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "shipment")
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "货物编号不能为空")
    @Size(max = 50, message = "货物编号长度不能超过50个字符")
    @Column(name = "shipment_number", nullable = false, unique = true)
    private String shipmentNumber;

    @NotBlank(message = "货物名称不能为空")
    @Size(max = 100, message = "货物名称长度不能超过100个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull(message = "重量不能为空")
    @Min(value = 0, message = "重量不能为负数")
    @Column(name = "weight")
    private Double weight; // 重量（千克）

    @NotNull(message = "体积不能为空")
    @Min(value = 0, message = "体积不能为负数")
    @Column(name = "volume")
    private Double volume; // 体积（立方米）

    @Column(name = "description", length = 500)
    private String description; // 货物描述

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ShipmentStatus status; // 货物状态

    @NotNull(message = "货物类型不能为空")
    @Column(name = "type")
    private String type; // 货物类型（普通、易碎、危险品等）

    @Column(name = "special_requirements", length = 500)
    private String specialRequirements; // 特殊要求

    // 货物状态枚举
    public enum ShipmentStatus {
        PENDING,        // 待处理
        LOADED,         // 已装车
        IN_TRANSIT,     // 运输中
        UNLOADED,       // 已卸货
        DELIVERED,      // 已送达
        CANCELLED,      // 已取消
        DAMAGED         // 损坏
    }

    // 与客户的关系 - 多对一
    @NotNull(message = "客户不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer; // 对应图中的customerBanner

    // 与任务的关系 - 多对一
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task; // 对应图中的subgreement（关联任务）

    // 与车辆的关系 - 多对一（一个货物只能在一辆车上，一辆车可以有多个货物）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    // 起点POI - 多对一
    @NotNull(message = "起点不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_poi_id", nullable = false)
    private POI startPOI; // 对应图中的startPCI

    // 终点POI - 多对一
    @NotNull(message = "终点不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_poi_id", nullable = false)
    private POI endPOI; // 对应图中的endPCI

    // 时间相关字段
    @Column(name = "created_time")
    private LocalDateTime createdTime; // 创建时间

    @Column(name = "planned_pickup_time")
    private LocalDateTime plannedPickupTime; // 计划取货时间

    @Column(name = "actual_pickup_time")
    private LocalDateTime actualPickupTime; // 实际取货时间

    @Column(name = "planned_delivery_time")
    private LocalDateTime plannedDeliveryTime; // 计划送达时间

    @Column(name = "actual_delivery_time")
    private LocalDateTime actualDeliveryTime; // 实际送达时间

    // 与PackBytes的关系 - 一对一（正常连接）
    @OneToOne(mappedBy = "shipment", cascade = CascadeType.ALL)
    private PackBytes packBytes; // 对应图中的packBytes

    // 构造方法
    public Shipment() {
        this.createdTime = LocalDateTime.now();
        this.status = ShipmentStatus.PENDING;
    }

    public Shipment(String shipmentNumber, String name, Double weight, Double volume,
                    Customer customer, POI startPOI, POI endPOI) {
        this();
        this.shipmentNumber = shipmentNumber;
        this.name = name;
        this.weight = weight;
        this.volume = volume;
        this.customer = customer;
        this.startPOI = startPOI;
        this.endPOI = endPOI;
    }

    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShipmentNumber() { return shipmentNumber; }
    public void setShipmentNumber(String shipmentNumber) { this.shipmentNumber = shipmentNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSpecialRequirements() { return specialRequirements; }
    public void setSpecialRequirements(String specialRequirements) { this.specialRequirements = specialRequirements; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public POI getStartPOI() { return startPOI; }
    public void setStartPOI(POI startPOI) { this.startPOI = startPOI; }

    public POI getEndPOI() { return endPOI; }
    public void setEndPOI(POI endPOI) { this.endPOI = endPOI; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getPlannedPickupTime() { return plannedPickupTime; }
    public void setPlannedPickupTime(LocalDateTime plannedPickupTime) { this.plannedPickupTime = plannedPickupTime; }

    public LocalDateTime getActualPickupTime() { return actualPickupTime; }
    public void setActualPickupTime(LocalDateTime actualPickupTime) { this.actualPickupTime = actualPickupTime; }

    public LocalDateTime getPlannedDeliveryTime() { return plannedDeliveryTime; }
    public void setPlannedDeliveryTime(LocalDateTime plannedDeliveryTime) { this.plannedDeliveryTime = plannedDeliveryTime; }

    public LocalDateTime getActualDeliveryTime() { return actualDeliveryTime; }
    public void setActualDeliveryTime(LocalDateTime actualDeliveryTime) { this.actualDeliveryTime = actualDeliveryTime; }

    public PackBytes getPackBytes() { return packBytes; }
    public void setPackBytes(PackBytes packBytes) { this.packBytes = packBytes; }

    // 业务方法
    public boolean canBeLoaded() {
        return status == ShipmentStatus.PENDING;
    }

    public boolean isInTransit() {
        return status == ShipmentStatus.IN_TRANSIT;
    }

    public boolean isDelivered() {
        return status == ShipmentStatus.DELIVERED;
    }

    @Override
    public String toString() {
        return "Shipment{" +
                "id=" + id +
                ", shipmentNumber='" + shipmentNumber + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", customer=" + (customer != null ? customer.getId() : "null") +
                '}';
    }
}