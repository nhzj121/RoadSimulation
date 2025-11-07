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
}