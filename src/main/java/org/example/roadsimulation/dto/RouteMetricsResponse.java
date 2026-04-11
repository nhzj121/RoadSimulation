package org.example.roadsimulation.dto;

public class RouteMetricsResponse {

    private Long vehicleId;
    private Long shipmentId;

    /**
     * 空驶时间，单位：秒
     */
    private Long emptyDrivingTime;

    /**
     * 空驶距离，单位：米
     */
    private Double emptyDrivingDistance;

    /**
     * 总行驶时间，单位：秒
     */
    private Long totalDrivingTime;

    /**
     * 总行驶距离，单位：米
     */
    private Double totalDrivingDistance;

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Long getEmptyDrivingTime() {
        return emptyDrivingTime;
    }

    public void setEmptyDrivingTime(Long emptyDrivingTime) {
        this.emptyDrivingTime = emptyDrivingTime;
    }

    public Double getEmptyDrivingDistance() {
        return emptyDrivingDistance;
    }

    public void setEmptyDrivingDistance(Double emptyDrivingDistance) {
        this.emptyDrivingDistance = emptyDrivingDistance;
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
}
