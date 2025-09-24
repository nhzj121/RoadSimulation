package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.Assignment.AssignmentStatus;

import java.time.LocalDateTime;
import java.util.List;

public class AssignmentResponseDTO {

    private Long id;
    private AssignmentStatus status;
    private Integer currentActionIndex;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<Long> actionLine;
    private Long vehicleId;
    private String vehicleInfo;
    private Long driverId;
    private String driverInfo;
    private Long routeId;
    private String routeInfo;
    private List<Long> shipmentItemIds;
    private int shipmentItemsCount;
    private String duration; // 格式化后的持续时间

    // 构造函数
    public AssignmentResponseDTO() {}

    // Getter和Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public Integer getCurrentActionIndex() { return currentActionIndex; }
    public void setCurrentActionIndex(Integer currentActionIndex) { this.currentActionIndex = currentActionIndex; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public List<Long> getActionLine() { return actionLine; }
    public void setActionLine(List<Long> actionLine) { this.actionLine = actionLine; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getVehicleInfo() { return vehicleInfo; }
    public void setVehicleInfo(String vehicleInfo) { this.vehicleInfo = vehicleInfo; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getDriverInfo() { return driverInfo; }
    public void setDriverInfo(String driverInfo) { this.driverInfo = driverInfo; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getRouteInfo() { return routeInfo; }
    public void setRouteInfo(String routeInfo) { this.routeInfo = routeInfo; }
    public List<Long> getShipmentItemIds() { return shipmentItemIds; }
    public void setShipmentItemIds(List<Long> shipmentItemIds) { this.shipmentItemIds = shipmentItemIds; }
    public int getShipmentItemsCount() { return shipmentItemsCount; }
    public void setShipmentItemsCount(int shipmentItemsCount) { this.shipmentItemsCount = shipmentItemsCount; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
}