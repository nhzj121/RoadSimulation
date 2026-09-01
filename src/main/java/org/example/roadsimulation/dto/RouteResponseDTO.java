package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.Route.RouteStatus;
import org.example.roadsimulation.core.TransportUnits;

public class RouteResponseDTO {

    private Long id;
    private String routeCode;
    private String name;
    private Long startPoiId;
    private String startPoiName;
    private Long endPoiId;
    private String endPoiName;
    // Phase 1（方案 A 兼容字段）：distance 继续表示公里。
    private Double distance;
    // Phase 1（方案 A 兼容字段）：estimatedTime 继续表示小时。
    private Double estimatedTime;
    private String description;
    private RouteStatus status;
    private String routeType;
    private Double tollCost;
    private Double fuelConsumption;
    private Integer assignmentCount;
    private Double totalCost; // 计算后的总成本

    // 构造函数、Getter和Setter
    public RouteResponseDTO() {}

    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getStartPoiId() { return startPoiId; }
    public void setStartPoiId(Long startPoiId) { this.startPoiId = startPoiId; }
    public String getStartPoiName() { return startPoiName; }
    public void setStartPoiName(String startPoiName) { this.startPoiName = startPoiName; }
    public Long getEndPoiId() { return endPoiId; }
    public void setEndPoiId(Long endPoiId) { this.endPoiId = endPoiId; }
    public String getEndPoiName() { return endPoiName; }
    public void setEndPoiName(String endPoiName) { this.endPoiName = endPoiName; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
    public Double getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Double estimatedTime) { this.estimatedTime = estimatedTime; }

    /** Phase 1：新增的规范距离响应字段，单位固定为米。 */
    public Double getDistanceMeters() {
        return distance == null ? null : TransportUnits.kilometersToMeters(distance);
    }

    /** Phase 1：接受米制赋值时换算到旧公里字段，避免双份数据失配。 */
    public void setDistanceMeters(Double distanceMeters) {
        this.distance = distanceMeters == null ? null : TransportUnits.metersToKilometers(distanceMeters);
    }

    /** Phase 1：新增的规范计划耗时响应字段，单位固定为秒。 */
    public Long getEstimatedDrivingSeconds() {
        return estimatedTime == null ? null : TransportUnits.hoursToSeconds(estimatedTime);
    }

    /** Phase 1：接受秒制赋值时换算到旧小时字段，避免双份数据失配。 */
    public void setEstimatedDrivingSeconds(Long seconds) {
        this.estimatedTime = seconds == null ? null : TransportUnits.secondsToHours(seconds);
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
    public Integer getAssignmentCount() { return assignmentCount; }
    public void setAssignmentCount(Integer assignmentCount) { this.assignmentCount = assignmentCount; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
}
