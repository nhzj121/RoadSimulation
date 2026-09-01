package org.example.roadsimulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.roadsimulation.entity.Route.RouteStatus;

public class RouteRequestDTO {

    @NotBlank(message = "路线编号不能为空")
    private String routeCode;

    @NotBlank(message = "路线名称不能为空")
    private String name;

    @NotNull(message = "起点POI不能为空")
    private Long startPoiId;

    @NotNull(message = "终点POI不能为空")
    private Long endPoiId;

    @Positive(message = "距离必须为正数")
    // Phase 1（方案 A 兼容字段）：旧请求中的 distance 仍按公里解释；与米字段二选一。
    private Double distance;

    @Positive(message = "预计时间必须为正数")
    // Phase 1（方案 A 兼容字段）：旧请求中的 estimatedTime 仍按小时解释；与秒字段二选一。
    private Double estimatedTime;

    // Phase 1：新请求可显式使用米；与 distance 同时出现时由服务层拒绝，避免歧义。
    @Positive(message = "距离（米）必须为正数")
    private Double distanceMeters;

    // Phase 1：新请求可显式使用秒；与 estimatedTime 同时出现时由服务层拒绝，避免歧义。
    @Positive(message = "预计行驶秒数必须为正数")
    private Long estimatedDrivingSeconds;

    private String description;

    private RouteStatus status = RouteStatus.ACTIVE;

    @NotNull(message = "路线类型不能为空")
    private String routeType;

    private Double tollCost = 0.0;

    private Double fuelConsumption = 0.0;

    // 构造函数、Getter和Setter
    public RouteRequestDTO() {}

    // Getter和Setter方法
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getStartPoiId() { return startPoiId; }
    public void setStartPoiId(Long startPoiId) { this.startPoiId = startPoiId; }
    public Long getEndPoiId() { return endPoiId; }
    public void setEndPoiId(Long endPoiId) { this.endPoiId = endPoiId; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
    public Double getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Double estimatedTime) { this.estimatedTime = estimatedTime; }
    // Phase 1：规范米制请求字段的显式访问器。
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    // Phase 1：规范秒制请求字段的显式访问器。
    public Long getEstimatedDrivingSeconds() { return estimatedDrivingSeconds; }
    public void setEstimatedDrivingSeconds(Long estimatedDrivingSeconds) {
        this.estimatedDrivingSeconds = estimatedDrivingSeconds;
    }
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
}
