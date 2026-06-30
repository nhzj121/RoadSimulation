package org.example.roadsimulation.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DispatchComparisonExperimentResultDTO {

    private String experimentId;
    private LocalDateTime generatedAt;
    private ScenarioDTO scenario;
    private StrategyResultDTO original;
    private StrategyResultDTO heuristic;
    private ImprovementDTO improvement;

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public ScenarioDTO getScenario() {
        return scenario;
    }

    public void setScenario(ScenarioDTO scenario) {
        this.scenario = scenario;
    }

    public StrategyResultDTO getOriginal() {
        return original;
    }

    public void setOriginal(StrategyResultDTO original) {
        this.original = original;
    }

    public StrategyResultDTO getHeuristic() {
        return heuristic;
    }

    public void setHeuristic(StrategyResultDTO heuristic) {
        this.heuristic = heuristic;
    }

    public ImprovementDTO getImprovement() {
        return improvement;
    }

    public void setImprovement(ImprovementDTO improvement) {
        this.improvement = improvement;
    }

    public static class ScenarioDTO {
        private Integer shipmentCount;
        private Integer vehicleCount;
        private List<VehicleInitialPositionDTO> vehicleInitialPositions = new ArrayList<>();
        private List<ShipmentPlanDTO> shipmentPlans = new ArrayList<>();

        public Integer getShipmentCount() {
            return shipmentCount;
        }

        public void setShipmentCount(Integer shipmentCount) {
            this.shipmentCount = shipmentCount;
        }

        public Integer getVehicleCount() {
            return vehicleCount;
        }

        public void setVehicleCount(Integer vehicleCount) {
            this.vehicleCount = vehicleCount;
        }

        public List<VehicleInitialPositionDTO> getVehicleInitialPositions() {
            return vehicleInitialPositions;
        }

        public void setVehicleInitialPositions(List<VehicleInitialPositionDTO> vehicleInitialPositions) {
            this.vehicleInitialPositions = vehicleInitialPositions;
        }

        public List<ShipmentPlanDTO> getShipmentPlans() {
            return shipmentPlans;
        }

        public void setShipmentPlans(List<ShipmentPlanDTO> shipmentPlans) {
            this.shipmentPlans = shipmentPlans;
        }
    }

    public static class VehicleInitialPositionDTO {
        private Long vehicleId;
        private String licensePlate;
        private Long poiId;
        private String poiName;

        public Long getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(Long vehicleId) {
            this.vehicleId = vehicleId;
        }

        public String getLicensePlate() {
            return licensePlate;
        }

        public void setLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
        }

        public Long getPoiId() {
            return poiId;
        }

        public void setPoiId(Long poiId) {
            this.poiId = poiId;
        }

        public String getPoiName() {
            return poiName;
        }

        public void setPoiName(String poiName) {
            this.poiName = poiName;
        }
    }

    public static class ShipmentPlanDTO {
        private String refNo;
        private Long originPoiId;
        private String originPoiName;
        private Long destinationPoiId;
        private String destinationPoiName;
        private Long goodsId;
        private String goodsName;
        private String sku;
        private Integer quantity;
        private Double totalWeight;
        private Double totalVolume;

        public String getRefNo() {
            return refNo;
        }

        public void setRefNo(String refNo) {
            this.refNo = refNo;
        }

        public Long getOriginPoiId() {
            return originPoiId;
        }

        public void setOriginPoiId(Long originPoiId) {
            this.originPoiId = originPoiId;
        }

        public String getOriginPoiName() {
            return originPoiName;
        }

        public void setOriginPoiName(String originPoiName) {
            this.originPoiName = originPoiName;
        }

        public Long getDestinationPoiId() {
            return destinationPoiId;
        }

        public void setDestinationPoiId(Long destinationPoiId) {
            this.destinationPoiId = destinationPoiId;
        }

        public String getDestinationPoiName() {
            return destinationPoiName;
        }

        public void setDestinationPoiName(String destinationPoiName) {
            this.destinationPoiName = destinationPoiName;
        }

        public Long getGoodsId() {
            return goodsId;
        }

        public void setGoodsId(Long goodsId) {
            this.goodsId = goodsId;
        }

        public String getGoodsName() {
            return goodsName;
        }

        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Double getTotalWeight() {
            return totalWeight;
        }

        public void setTotalWeight(Double totalWeight) {
            this.totalWeight = totalWeight;
        }

        public Double getTotalVolume() {
            return totalVolume;
        }

        public void setTotalVolume(Double totalVolume) {
            this.totalVolume = totalVolume;
        }
    }

    public static class StrategyResultDTO {
        private String strategy;
        private RuntimeCostDTO summary;
        private RuntimeCostDetailDTO detail;
        private Integer assignedItems;
        private Integer unassignedItems;
        private Integer vehicleUsedCount;
        private Integer assignmentCount;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public RuntimeCostDTO getSummary() {
            return summary;
        }

        public void setSummary(RuntimeCostDTO summary) {
            this.summary = summary;
        }

        public RuntimeCostDetailDTO getDetail() {
            return detail;
        }

        public void setDetail(RuntimeCostDetailDTO detail) {
            this.detail = detail;
        }

        public Integer getAssignedItems() {
            return assignedItems;
        }

        public void setAssignedItems(Integer assignedItems) {
            this.assignedItems = assignedItems;
        }

        public Integer getUnassignedItems() {
            return unassignedItems;
        }

        public void setUnassignedItems(Integer unassignedItems) {
            this.unassignedItems = unassignedItems;
        }

        public Integer getVehicleUsedCount() {
            return vehicleUsedCount;
        }

        public void setVehicleUsedCount(Integer vehicleUsedCount) {
            this.vehicleUsedCount = vehicleUsedCount;
        }

        public Integer getAssignmentCount() {
            return assignmentCount;
        }

        public void setAssignmentCount(Integer assignmentCount) {
            this.assignmentCount = assignmentCount;
        }
    }

    public static class ImprovementDTO {
        private Double normalizedAllCostDelta;
        private Double normalizedAllCostImprovementRate;
        private Double allCostDelta;
        private Double allCostImprovementRate;
        private Double costADelta;
        private Double costBDelta;
        private Double costCDelta;
        private Double costDDelta;
        private Double costEDelta;

        public Double getNormalizedAllCostDelta() {
            return normalizedAllCostDelta;
        }

        public void setNormalizedAllCostDelta(Double normalizedAllCostDelta) {
            this.normalizedAllCostDelta = normalizedAllCostDelta;
        }

        public Double getNormalizedAllCostImprovementRate() {
            return normalizedAllCostImprovementRate;
        }

        public void setNormalizedAllCostImprovementRate(Double normalizedAllCostImprovementRate) {
            this.normalizedAllCostImprovementRate = normalizedAllCostImprovementRate;
        }

        public Double getAllCostDelta() {
            return allCostDelta;
        }

        public void setAllCostDelta(Double allCostDelta) {
            this.allCostDelta = allCostDelta;
        }

        public Double getAllCostImprovementRate() {
            return allCostImprovementRate;
        }

        public void setAllCostImprovementRate(Double allCostImprovementRate) {
            this.allCostImprovementRate = allCostImprovementRate;
        }

        public Double getCostADelta() {
            return costADelta;
        }

        public void setCostADelta(Double costADelta) {
            this.costADelta = costADelta;
        }

        public Double getCostBDelta() {
            return costBDelta;
        }

        public void setCostBDelta(Double costBDelta) {
            this.costBDelta = costBDelta;
        }

        public Double getCostCDelta() {
            return costCDelta;
        }

        public void setCostCDelta(Double costCDelta) {
            this.costCDelta = costCDelta;
        }

        public Double getCostDDelta() {
            return costDDelta;
        }

        public void setCostDDelta(Double costDDelta) {
            this.costDDelta = costDDelta;
        }

        public Double getCostEDelta() {
            return costEDelta;
        }

        public void setCostEDelta(Double costEDelta) {
            this.costEDelta = costEDelta;
        }
    }
}
