package org.example.roadsimulation.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TransportMonitorDTO {

    private LocalDateTime generatedAt;
    private Summary summary = new Summary();
    private List<ShipmentMonitorDTO> shipments = new ArrayList<>();
    private List<AssignmentMonitorDTO> assignments = new ArrayList<>();
    private List<VehicleMonitorDTO> vehicles = new ArrayList<>();
    private List<LinkDTO> links = new ArrayList<>();

    @Data
    public static class Summary {
        private int activeShipmentCount;
        private int activeAssignmentCount;
        private int activeVehicleCount;
    }

    @Data
    public static class ShipmentMonitorDTO {
        private Long shipmentId;
        private String refNo;
        private String cargoType;
        private String originPOIName;
        private String destPOIName;
        private String status;
        private String statusText;
        private int totalItems;
        private int completedItems;
        private int inProgressItems;
        private int waitingItems;
        private double progressPercentage;
        private List<Long> assignmentIds = new ArrayList<>();
        private List<Long> vehicleIds = new ArrayList<>();
    }

    @Data
    public static class AssignmentMonitorDTO {
        private Long assignmentId;
        private String status;
        private String statusText;
        private Long vehicleId;
        private String licensePlate;
        private String vehicleStatus;
        private String routeName;
        private Long startPOIId;
        private String startPOIName;
        private Double startLng;
        private Double startLat;
        private Long endPOIId;
        private String endPOIName;
        private Double endLng;
        private Double endLat;
        private String goodsName;
        private Integer quantity;
        private Double currentLoad;
        private Double maxLoadCapacity;
        private Double currentVolume;
        private Double maxVolumeCapacity;
        private List<Long> shipmentIds = new ArrayList<>();
        private List<String> shipmentRefNos = new ArrayList<>();
    }

    @Data
    public static class VehicleMonitorDTO {
        private Long vehicleId;
        private String licensePlate;
        private String status;
        private String statusText;
        private Double currentLoad;
        private Double maxLoadCapacity;
        private Double currentVolume;
        private Double maxVolumeCapacity;
        private List<Long> assignmentIds = new ArrayList<>();
        private List<Long> shipmentIds = new ArrayList<>();
    }

    @Data
    public static class LinkDTO {
        private Long shipmentId;
        private Long assignmentId;
        private Long vehicleId;
        private List<Long> shipmentItemIds = new ArrayList<>();
    }
}
