package org.example.roadsimulation.dto;

import java.util.ArrayList;
import java.util.List;

public class DispatchComparisonVehicleDisplayInfoDTO {

    private Long vehicleId;
    private String licensePlate;
    private String vehicleStatus;
    private Long assignmentId;
    private String assignmentStatus;
    private String currentStrategy;
    private Long routeId;
    private String routeName;
    private Long startPOIId;
    private String startPOIName;
    private Long endPOIId;
    private String endPOIName;
    private String goodsName;
    private Integer quantity;
    private String shipmentRefNos;
    private Double currentLoad;
    private Double maxLoadCapacity;
    private Double currentVolume;
    private Double maxVolumeCapacity;
    private List<NodeInfo> nodes = new ArrayList<>();

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

    public String getVehicleStatus() {
        return vehicleStatus;
    }

    public void setVehicleStatus(String vehicleStatus) {
        this.vehicleStatus = vehicleStatus;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getAssignmentStatus() {
        return assignmentStatus;
    }

    public void setAssignmentStatus(String assignmentStatus) {
        this.assignmentStatus = assignmentStatus;
    }

    public String getCurrentStrategy() {
        return currentStrategy;
    }

    public void setCurrentStrategy(String currentStrategy) {
        this.currentStrategy = currentStrategy;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public Long getStartPOIId() {
        return startPOIId;
    }

    public void setStartPOIId(Long startPOIId) {
        this.startPOIId = startPOIId;
    }

    public String getStartPOIName() {
        return startPOIName;
    }

    public void setStartPOIName(String startPOIName) {
        this.startPOIName = startPOIName;
    }

    public Long getEndPOIId() {
        return endPOIId;
    }

    public void setEndPOIId(Long endPOIId) {
        this.endPOIId = endPOIId;
    }

    public String getEndPOIName() {
        return endPOIName;
    }

    public void setEndPOIName(String endPOIName) {
        this.endPOIName = endPOIName;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getShipmentRefNos() {
        return shipmentRefNos;
    }

    public void setShipmentRefNos(String shipmentRefNos) {
        this.shipmentRefNos = shipmentRefNos;
    }

    public Double getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(Double currentLoad) {
        this.currentLoad = currentLoad;
    }

    public Double getMaxLoadCapacity() {
        return maxLoadCapacity;
    }

    public void setMaxLoadCapacity(Double maxLoadCapacity) {
        this.maxLoadCapacity = maxLoadCapacity;
    }

    public Double getCurrentVolume() {
        return currentVolume;
    }

    public void setCurrentVolume(Double currentVolume) {
        this.currentVolume = currentVolume;
    }

    public Double getMaxVolumeCapacity() {
        return maxVolumeCapacity;
    }

    public void setMaxVolumeCapacity(Double maxVolumeCapacity) {
        this.maxVolumeCapacity = maxVolumeCapacity;
    }

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    public static class NodeInfo {
        private Integer sequenceIndex;
        private Long poiId;
        private String poiName;
        private String actionType;
        private String poiType;
        private Double weightDelta;
        private Double volumeDelta;
        private Boolean completed;
        private Long shipmentItemId;
        private String goodsName;
        private Integer quantity;

        public Integer getSequenceIndex() {
            return sequenceIndex;
        }

        public void setSequenceIndex(Integer sequenceIndex) {
            this.sequenceIndex = sequenceIndex;
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

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public String getPoiType() {
            return poiType;
        }

        public void setPoiType(String poiType) {
            this.poiType = poiType;
        }

        public Double getWeightDelta() {
            return weightDelta;
        }

        public void setWeightDelta(Double weightDelta) {
            this.weightDelta = weightDelta;
        }

        public Double getVolumeDelta() {
            return volumeDelta;
        }

        public void setVolumeDelta(Double volumeDelta) {
            this.volumeDelta = volumeDelta;
        }

        public Boolean getCompleted() {
            return completed;
        }

        public void setCompleted(Boolean completed) {
            this.completed = completed;
        }

        public Long getShipmentItemId() {
            return shipmentItemId;
        }

        public void setShipmentItemId(Long shipmentItemId) {
            this.shipmentItemId = shipmentItemId;
        }

        public String getGoodsName() {
            return goodsName;
        }

        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
