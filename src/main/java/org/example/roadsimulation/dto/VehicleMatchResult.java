package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.Vehicle;

public class VehicleMatchResult {
    private Vehicle vehicle;
    private Double matchScore;           // 匹配度分数 (0-100)
    private Boolean isFullyMatched;      // 是否完全匹配
    private String matchDescription;     // 匹配描述
    private Double capacityUtilization;  // 容量利用率
    private Double weightUtilization;    // 重量利用率
    private Double volumeUtilization;    // 容积利用率

    // 新增字段：距离相关信息
    private Double distanceKm;           // 车辆与出发地距离（公里）
    private Double distanceScore;        // 距离评分（0-100）
    private String originPoiName;        // 出发地名称
    private Double estimatedTimeHours;   // 预计到达时间（小时）

    public VehicleMatchResult() {}

    public VehicleMatchResult(Vehicle vehicle, Double matchScore, Boolean isFullyMatched, String matchDescription) {
        this.vehicle = vehicle;
        this.matchScore = matchScore;
        this.isFullyMatched = isFullyMatched;
        this.matchDescription = matchDescription;
    }

    // Getter和Setter
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public Boolean getFullyMatched() { return isFullyMatched; }
    public void setFullyMatched(Boolean fullyMatched) { isFullyMatched = fullyMatched; }

    public String getMatchDescription() { return matchDescription; }
    public void setMatchDescription(String matchDescription) { this.matchDescription = matchDescription; }

    public Double getCapacityUtilization() { return capacityUtilization; }
    public void setCapacityUtilization(Double capacityUtilization) { this.capacityUtilization = capacityUtilization; }

    public Double getWeightUtilization() { return weightUtilization; }
    public void setWeightUtilization(Double weightUtilization) { this.weightUtilization = weightUtilization; }

    public Double getVolumeUtilization() { return volumeUtilization; }
    public void setVolumeUtilization(Double volumeUtilization) { this.volumeUtilization = volumeUtilization; }

    // 新增字段的Getter和Setter
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Double getDistanceScore() { return distanceScore; }
    public void setDistanceScore(Double distanceScore) { this.distanceScore = distanceScore; }

    public String getOriginPoiName() { return originPoiName; }
    public void setOriginPoiName(String originPoiName) { this.originPoiName = originPoiName; }

    public Double getEstimatedTimeHours() { return estimatedTimeHours; }
    public void setEstimatedTimeHours(Double estimatedTimeHours) { this.estimatedTimeHours = estimatedTimeHours; }
}