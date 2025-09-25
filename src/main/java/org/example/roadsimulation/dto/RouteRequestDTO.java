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

    @NotNull(message = "距离不能为空")
    @Positive(message = "距离必须为正数")
    private Double distance;

    @NotNull(message = "预计时间不能为空")
    @Positive(message = "预计时间必须为正数")
    private Double estimatedTime;

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