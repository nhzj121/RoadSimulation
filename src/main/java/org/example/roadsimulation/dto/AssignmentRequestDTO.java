package org.example.roadsimulation.dto;

import jakarta.validation.constraints.NotNull;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;

import java.time.LocalDateTime;
import java.util.List;

public class AssignmentRequestDTO {

    @NotNull(message = "状态不能为空")
    private AssignmentStatus status;

    private Integer currentActionIndex = 0;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<Long> actionLine;

    private Long vehicleId;

    private Long driverId;

    private Long routeId;

    private List<Long> shipmentItemIds;

    // 构造函数
    public AssignmentRequestDTO() {}

    // Getter和Setter
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
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public List<Long> getShipmentItemIds() { return shipmentItemIds; }
    public void setShipmentItemIds(List<Long> shipmentItemIds) { this.shipmentItemIds = shipmentItemIds; }
}