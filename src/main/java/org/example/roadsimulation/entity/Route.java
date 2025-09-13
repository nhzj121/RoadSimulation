package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "route")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "路线编号不能为空")
    @Size(max = 50, message = "路线编号长度不能超过50个字符")
    @Column(name = "route_code", nullable = false, unique = true)
    private String routeCode;

    @NotBlank(message = "路线名称不能为空")
    @Size(max = 100, message = "路线名称长度不能超过100个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull(message = "起点不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_poi_id", nullable = false)
    private POI startPOI; // 对应图中的startPCI

    @NotNull(message = "终点不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_poi_id", nullable = false)
    private POI endPOI; // 对应图中的endPCI

    @NotNull(message = "距离不能为空")
    @Min(value = 0, message = "距离不能为负数")
    @Column(name = "distance")
    private Double distance; // 路线距离（公里）

    @NotNull(message = "预计时间不能为空")
    @Min(value = 0, message = "预计时间不能为负数")
    @Column(name = "estimated_time")
    private Double estimatedTime; // 预计行驶时间（小时）

    @Column(name = "description", length = 500)
    private String description; // 路线描述

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private RouteStatus status; // 路线状态

    @NotNull(message = "路线类型不能为空")
    @Column(name = "route_type")
    private String routeType; // 路线类型（高速、国道、省道等）

    @Column(name = "toll_cost")
    private Double tollCost; // 过路费

    @Column(name = "fuel_consumption")
    private Double fuelConsumption; // 预计燃油消耗（升）

    // 路线状态枚举
    public enum RouteStatus {
        ACTIVE,         // 活跃
        UNDER_MAINTENANCE, // 维护中
        CLOSED,         // 关闭
        CONGESTED       // 拥堵
    }

    // 与货物运输的关系 - 一对多（一条路线可以有多个货物运输）
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL)
    private Set<Shipment> shipments = new HashSet<>(); // 对应图中的Shipment（维护）

    // 与任务的关系 - 一对多（一条路线可以有多个任务）
    @OneToMany(mappedBy = "plannedRoute")
    private Set<Task> tasks = new HashSet<>();

    // 禁止点集合 - 多对多（一条路线可以有多个禁止点，一个禁止点可以属于多条路线）
    @ManyToMany
    @JoinTable(
            name = "route_forbidden_poi",
            joinColumns = @JoinColumn(name = "route_id"),
            inverseJoinColumns = @JoinColumn(name = "poi_id")
    )
    private Set<POI> forbiddenPOIs = new HashSet<>(); // 对应图中的endPCI(禁止点)

    // 构造方法
    public Route() {
        this.status = RouteStatus.ACTIVE;
    }

    public Route(String routeCode, String name, POI startPOI, POI endPOI,
                 Double distance, Double estimatedTime) {
        this();
        this.routeCode = routeCode;
        this.name = name;
        this.startPOI = startPOI;
        this.endPOI = endPOI;
        this.distance = distance;
        this.estimatedTime = estimatedTime;
    }

    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public POI getStartPOI() { return startPOI; }
    public void setStartPOI(POI startPOI) { this.startPOI = startPOI; }

    public POI getEndPOI() { return endPOI; }
    public void setEndPOI(POI endPOI) { this.endPOI = endPOI; }

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }

    public Double getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Double estimatedTime) { this.estimatedTime = estimatedTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RouteStatus getStatus() { return status; }
    public void setStatus(RouteStatus status) { this.status = status; }

    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }

    public Double getTollCost() { return tollCost; }
    public void setTollCost(Double tollCost) { this.tollCost = tollCost; }

    public Double getFuelConsumption() { return fuelConsumption; }
    public void setFuelConsumption(Double fuelConsumption) { this.fuelConsumption = fuelConsumption; }

    public Set<Shipment> getShipments() { return shipments; }
    public void setShipments(Set<Shipment> shipments) { this.shipments = shipments; }

    public Set<Task> getTasks() { return tasks; }
    public void setTasks(Set<Task> tasks) { this.tasks = tasks; }

    public Set<POI> getForbiddenPOIs() { return forbiddenPOIs; }
    public void setForbiddenPOIs(Set<POI> forbiddenPOIs) { this.forbiddenPOIs = forbiddenPOIs; }

    // 业务方法
    public void addForbiddenPOI(POI poi) {
        this.forbiddenPOIs.add(poi);
        poi.getForbiddenRoutes().add(this);
    }

    public void removeForbiddenPOI(POI poi) {
        this.forbiddenPOIs.remove(poi);
        poi.getForbiddenRoutes().remove(this);
    }

    public boolean isForbiddenPOI(POI poi) {
        return this.forbiddenPOIs.contains(poi);
    }

    public boolean isAvailable() {
        return this.status == RouteStatus.ACTIVE;
    }

    // 计算路线成本
    public Double calculateTotalCost(Double fuelPrice) {
        Double fuelCost = (fuelConsumption != null && fuelPrice != null) ? fuelConsumption * fuelPrice : 0.0;
        return (tollCost != null ? tollCost : 0.0) + fuelCost;
    }

    @Override
    public String toString() {
        return "Route{" +
                "id=" + id +
                ", routeCode='" + routeCode + '\'' +
                ", name='" + name + '\'' +
                ", startPOI=" + (startPOI != null ? startPOI.getId() : "null") +
                ", endPOI=" + (endPOI != null ? endPOI.getId() : "null") +
                ", status=" + status +
                '}';
    }
}