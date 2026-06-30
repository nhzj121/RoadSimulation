package org.example.roadsimulation.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchComparisonScenarioDTO {
    private String experimentId;
    private String status;
    private LocalDateTime preparedAt;
    private Integer shipmentCount;
    private Integer vehicleCount;
    private List<VehiclePositionSummary> vehicleInitialPositions = new ArrayList<>();
    private List<ExperimentShipmentSummary> shipments = new ArrayList<>();

    @Data
    public static class VehiclePositionSummary {
        private Long vehicleId;
        private String licensePlate;
        private Long poiId;
        private String poiName;
        private String poiType;
    }

    @Data
    public static class ExperimentShipmentSummary {
        private String templateCode;
        private Long shipmentId;
        private Long shipmentItemId;
        private Long originPoiId;
        private String originPoiName;
        private Long destinationPoiId;
        private String destinationPoiName;
        private Long goodsId;
        private String goodsSku;
        private String goodsName;
        private Integer quantity;
        private Double totalWeight;
        private Double totalVolume;
    }
}
