package org.example.roadsimulation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
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

    @Data
    public static class NodeInfo {
        private Integer sequenceIndex;
        private Long poiId;
        private String poiName;
        private String poiType;
        private String actionType;
        private Double weightDelta;
        private Double volumeDelta;
        private boolean completed;
        private Long shipmentItemId;
        private String goodsName;
        private Integer quantity;
    }
}
