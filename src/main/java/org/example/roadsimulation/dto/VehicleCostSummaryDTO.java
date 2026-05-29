package org.example.roadsimulation.dto;

import java.util.ArrayList;
import java.util.List;

public class VehicleCostSummaryDTO {
    private Double weightA;
    private Double weightB;
    private Double weightC;
    private Double weightD;
    private Double weightE;
    private Double weightG;
    private Double weightH;
    private Double weightI;
    private Double globalWeightVehicleCost;
    private Double globalWeightUnassignedCost;

    private Double costAMin;
    private Double costAMax;
    private Double costBMin;
    private Double costBMax;
    private Double costCMin;
    private Double costCMax;
    private Double costDMin;
    private Double costDMax;
    private Double costEMin;
    private Double costEMax;
    private Double costGMin;
    private Double costGMax;
    private Double costHMin;
    private Double costHMax;
    private Double costIMin;
    private Double costIMax;

    private Integer vehicleCount;
    private Long totalTaskCount;
    private Long unassignedTaskCount;
    private Double unassignedTaskCost;
    private Double averageTotalCost;
    private Double globalCost;
    private List<VehicleCostDTO> vehicleCosts = new ArrayList<>();

    public Double getWeightA() { return weightA; }
    public void setWeightA(Double weightA) { this.weightA = weightA; }

    public Double getWeightB() { return weightB; }
    public void setWeightB(Double weightB) { this.weightB = weightB; }

    public Double getWeightC() { return weightC; }
    public void setWeightC(Double weightC) { this.weightC = weightC; }

    public Double getWeightD() { return weightD; }
    public void setWeightD(Double weightD) { this.weightD = weightD; }

    public Double getWeightE() { return weightE; }
    public void setWeightE(Double weightE) { this.weightE = weightE; }

    public Double getWeightG() { return weightG; }
    public void setWeightG(Double weightG) { this.weightG = weightG; }

    public Double getWeightH() { return weightH; }
    public void setWeightH(Double weightH) { this.weightH = weightH; }

    public Double getWeightI() { return weightI; }
    public void setWeightI(Double weightI) { this.weightI = weightI; }

    public Double getGlobalWeightVehicleCost() { return globalWeightVehicleCost; }
    public void setGlobalWeightVehicleCost(Double globalWeightVehicleCost) { this.globalWeightVehicleCost = globalWeightVehicleCost; }

    public Double getGlobalWeightUnassignedCost() { return globalWeightUnassignedCost; }
    public void setGlobalWeightUnassignedCost(Double globalWeightUnassignedCost) { this.globalWeightUnassignedCost = globalWeightUnassignedCost; }

    public Double getCostAMin() { return costAMin; }
    public void setCostAMin(Double costAMin) { this.costAMin = costAMin; }

    public Double getCostAMax() { return costAMax; }
    public void setCostAMax(Double costAMax) { this.costAMax = costAMax; }

    public Double getCostBMin() { return costBMin; }
    public void setCostBMin(Double costBMin) { this.costBMin = costBMin; }

    public Double getCostBMax() { return costBMax; }
    public void setCostBMax(Double costBMax) { this.costBMax = costBMax; }

    public Double getCostCMin() { return costCMin; }
    public void setCostCMin(Double costCMin) { this.costCMin = costCMin; }

    public Double getCostCMax() { return costCMax; }
    public void setCostCMax(Double costCMax) { this.costCMax = costCMax; }

    public Double getCostDMin() { return costDMin; }
    public void setCostDMin(Double costDMin) { this.costDMin = costDMin; }

    public Double getCostDMax() { return costDMax; }
    public void setCostDMax(Double costDMax) { this.costDMax = costDMax; }

    public Double getCostEMin() { return costEMin; }
    public void setCostEMin(Double costEMin) { this.costEMin = costEMin; }

    public Double getCostEMax() { return costEMax; }
    public void setCostEMax(Double costEMax) { this.costEMax = costEMax; }

    public Double getCostGMin() { return costGMin; }
    public void setCostGMin(Double costGMin) { this.costGMin = costGMin; }

    public Double getCostGMax() { return costGMax; }
    public void setCostGMax(Double costGMax) { this.costGMax = costGMax; }

    public Double getCostHMin() { return costHMin; }
    public void setCostHMin(Double costHMin) { this.costHMin = costHMin; }

    public Double getCostHMax() { return costHMax; }
    public void setCostHMax(Double costHMax) { this.costHMax = costHMax; }

    public Double getCostIMin() { return costIMin; }
    public void setCostIMin(Double costIMin) { this.costIMin = costIMin; }

    public Double getCostIMax() { return costIMax; }
    public void setCostIMax(Double costIMax) { this.costIMax = costIMax; }

    public Integer getVehicleCount() { return vehicleCount; }
    public void setVehicleCount(Integer vehicleCount) { this.vehicleCount = vehicleCount; }

    public Long getTotalTaskCount() { return totalTaskCount; }
    public void setTotalTaskCount(Long totalTaskCount) { this.totalTaskCount = totalTaskCount; }

    public Long getUnassignedTaskCount() { return unassignedTaskCount; }
    public void setUnassignedTaskCount(Long unassignedTaskCount) { this.unassignedTaskCount = unassignedTaskCount; }

    public Double getUnassignedTaskCost() { return unassignedTaskCost; }
    public void setUnassignedTaskCost(Double unassignedTaskCost) { this.unassignedTaskCost = unassignedTaskCost; }

    public Double getAverageTotalCost() { return averageTotalCost; }
    public void setAverageTotalCost(Double averageTotalCost) { this.averageTotalCost = averageTotalCost; }

    public Double getGlobalCost() { return globalCost; }
    public void setGlobalCost(Double globalCost) { this.globalCost = globalCost; }

    public List<VehicleCostDTO> getVehicleCosts() { return vehicleCosts; }
    public void setVehicleCosts(List<VehicleCostDTO> vehicleCosts) { this.vehicleCosts = vehicleCosts; }
}
