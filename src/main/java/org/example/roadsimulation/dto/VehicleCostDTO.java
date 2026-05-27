package org.example.roadsimulation.dto;

public class VehicleCostDTO {
    private Long vehicleId;
    private String licensePlate;

    private Double costA;
    private Double costB;
    private Double costC;
    private Double costD;
    private Double costE;
    private Double costG;
    private Double costH;
    private Double costI;

    private Double normalizedCostA;
    private Double normalizedCostB;
    private Double normalizedCostC;
    private Double normalizedCostD;
    private Double normalizedCostE;
    private Double normalizedCostG;
    private Double normalizedCostH;
    private Double normalizedCostI;

    private Double totalCost;

    private Double totalWaitingHours;
    private Double totalTransportHours;
    private Double emptyDistanceKm;
    private Double totalDistanceKm;
    private Double theoryCapacity;
    private Double actualCapacity;
    private Double workload;
    private Double averageWorkload;
    private Double actualRouteDistanceKm;
    private Double baseRouteDistanceKm;
    private Double actualVolume;
    private Double cargoVolume;
    private Double actualAssignmentHours;
    private Double estimatedAssignmentHours;

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public Double getCostA() { return costA; }
    public void setCostA(Double costA) { this.costA = costA; }

    public Double getCostB() { return costB; }
    public void setCostB(Double costB) { this.costB = costB; }

    public Double getCostC() { return costC; }
    public void setCostC(Double costC) { this.costC = costC; }

    public Double getCostD() { return costD; }
    public void setCostD(Double costD) { this.costD = costD; }

    public Double getCostE() { return costE; }
    public void setCostE(Double costE) { this.costE = costE; }

    public Double getCostG() { return costG; }
    public void setCostG(Double costG) { this.costG = costG; }

    public Double getCostH() { return costH; }
    public void setCostH(Double costH) { this.costH = costH; }

    public Double getCostI() { return costI; }
    public void setCostI(Double costI) { this.costI = costI; }

    public Double getNormalizedCostA() { return normalizedCostA; }
    public void setNormalizedCostA(Double normalizedCostA) { this.normalizedCostA = normalizedCostA; }

    public Double getNormalizedCostB() { return normalizedCostB; }
    public void setNormalizedCostB(Double normalizedCostB) { this.normalizedCostB = normalizedCostB; }

    public Double getNormalizedCostC() { return normalizedCostC; }
    public void setNormalizedCostC(Double normalizedCostC) { this.normalizedCostC = normalizedCostC; }

    public Double getNormalizedCostD() { return normalizedCostD; }
    public void setNormalizedCostD(Double normalizedCostD) { this.normalizedCostD = normalizedCostD; }

    public Double getNormalizedCostE() { return normalizedCostE; }
    public void setNormalizedCostE(Double normalizedCostE) { this.normalizedCostE = normalizedCostE; }

    public Double getNormalizedCostG() { return normalizedCostG; }
    public void setNormalizedCostG(Double normalizedCostG) { this.normalizedCostG = normalizedCostG; }

    public Double getNormalizedCostH() { return normalizedCostH; }
    public void setNormalizedCostH(Double normalizedCostH) { this.normalizedCostH = normalizedCostH; }

    public Double getNormalizedCostI() { return normalizedCostI; }
    public void setNormalizedCostI(Double normalizedCostI) { this.normalizedCostI = normalizedCostI; }

    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }

    public Double getTotalWaitingHours() { return totalWaitingHours; }
    public void setTotalWaitingHours(Double totalWaitingHours) { this.totalWaitingHours = totalWaitingHours; }

    public Double getTotalTransportHours() { return totalTransportHours; }
    public void setTotalTransportHours(Double totalTransportHours) { this.totalTransportHours = totalTransportHours; }

    public Double getEmptyDistanceKm() { return emptyDistanceKm; }
    public void setEmptyDistanceKm(Double emptyDistanceKm) { this.emptyDistanceKm = emptyDistanceKm; }

    public Double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(Double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public Double getTheoryCapacity() { return theoryCapacity; }
    public void setTheoryCapacity(Double theoryCapacity) { this.theoryCapacity = theoryCapacity; }

    public Double getActualCapacity() { return actualCapacity; }
    public void setActualCapacity(Double actualCapacity) { this.actualCapacity = actualCapacity; }

    public Double getWorkload() { return workload; }
    public void setWorkload(Double workload) { this.workload = workload; }

    public Double getAverageWorkload() { return averageWorkload; }
    public void setAverageWorkload(Double averageWorkload) { this.averageWorkload = averageWorkload; }

    public Double getActualRouteDistanceKm() { return actualRouteDistanceKm; }
    public void setActualRouteDistanceKm(Double actualRouteDistanceKm) { this.actualRouteDistanceKm = actualRouteDistanceKm; }

    public Double getBaseRouteDistanceKm() { return baseRouteDistanceKm; }
    public void setBaseRouteDistanceKm(Double baseRouteDistanceKm) { this.baseRouteDistanceKm = baseRouteDistanceKm; }

    public Double getActualVolume() { return actualVolume; }
    public void setActualVolume(Double actualVolume) { this.actualVolume = actualVolume; }

    public Double getCargoVolume() { return cargoVolume; }
    public void setCargoVolume(Double cargoVolume) { this.cargoVolume = cargoVolume; }

    public Double getActualAssignmentHours() { return actualAssignmentHours; }
    public void setActualAssignmentHours(Double actualAssignmentHours) { this.actualAssignmentHours = actualAssignmentHours; }

    public Double getEstimatedAssignmentHours() { return estimatedAssignmentHours; }
    public void setEstimatedAssignmentHours(Double estimatedAssignmentHours) { this.estimatedAssignmentHours = estimatedAssignmentHours; }
}
