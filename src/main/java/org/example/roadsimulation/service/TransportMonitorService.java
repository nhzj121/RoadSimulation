package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.TransportMonitorDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TransportMonitorService {

    private static final List<Shipment.ShipmentStatus> ACTIVE_SHIPMENT_STATUSES = Arrays.asList(
            Shipment.ShipmentStatus.PLANNED,
            Shipment.ShipmentStatus.PICKED_UP,
            Shipment.ShipmentStatus.IN_TRANSIT
    );

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public TransportMonitorDTO getActiveMonitor() {
        TransportMonitorDTO dto = new TransportMonitorDTO();
        dto.setGeneratedAt(LocalDateTime.now());

        List<Shipment> activeShipments = shipmentRepository.findByStatusIn(ACTIVE_SHIPMENT_STATUSES);
        activeShipments.sort((left, right) -> {
            LocalDateTime leftTime = left.getUpdatedAt();
            LocalDateTime rightTime = right.getUpdatedAt();
            if (leftTime == null && rightTime == null) return 0;
            if (leftTime == null) return 1;
            if (rightTime == null) return -1;
            return rightTime.compareTo(leftTime);
        });

        Map<Long, TransportMonitorDTO.ShipmentMonitorDTO> shipmentMap = new LinkedHashMap<>();
        Map<Long, TransportMonitorDTO.AssignmentMonitorDTO> assignmentMap = new LinkedHashMap<>();
        Map<Long, TransportMonitorDTO.VehicleMonitorDTO> vehicleMap = new LinkedHashMap<>();
        Map<Long, Map<Long, TransportMonitorDTO.LinkDTO>> linkMap = new LinkedHashMap<>();

        for (Shipment shipment : activeShipments) {
            List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
            TransportMonitorDTO.ShipmentMonitorDTO shipmentDTO = buildShipmentDTO(shipment, items);
            shipmentMap.put(shipment.getId(), shipmentDTO);

            for (ShipmentItem item : items) {
                Assignment assignment = item.getAssignment();
                if (assignment == null || assignment.getId() == null) {
                    continue;
                }

                Vehicle vehicle = assignment.getAssignedVehicle();
                Long vehicleId = vehicle != null ? vehicle.getId() : null;

                addUnique(shipmentDTO.getAssignmentIds(), assignment.getId());
                if (vehicleId != null) {
                    addUnique(shipmentDTO.getVehicleIds(), vehicleId);
                }

                TransportMonitorDTO.AssignmentMonitorDTO assignmentDTO = assignmentMap.computeIfAbsent(
                        assignment.getId(),
                        ignored -> buildAssignmentDTO(assignment)
                );
                addUnique(assignmentDTO.getShipmentIds(), shipment.getId());
                addUnique(assignmentDTO.getShipmentRefNos(), shipment.getRefNo());

                if (vehicle != null && vehicleId != null) {
                    TransportMonitorDTO.VehicleMonitorDTO vehicleDTO = vehicleMap.computeIfAbsent(
                            vehicleId,
                            ignored -> buildVehicleDTO(vehicle)
                    );
                    addUnique(vehicleDTO.getAssignmentIds(), assignment.getId());
                    addUnique(vehicleDTO.getShipmentIds(), shipment.getId());
                }

                TransportMonitorDTO.LinkDTO linkDTO = linkMap
                        .computeIfAbsent(shipment.getId(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(assignment.getId(), ignored -> buildLinkDTO(shipment.getId(), assignment.getId(), vehicleId));
                if (vehicleId != null) {
                    linkDTO.setVehicleId(vehicleId);
                }
                addUnique(linkDTO.getShipmentItemIds(), item.getId());
            }
        }

        for (Assignment assignment : assignmentRepository.findRuntimeActiveAssignments()) {
            if (assignment == null || assignment.getId() == null) {
                continue;
            }
            TransportMonitorDTO.AssignmentMonitorDTO assignmentDTO = assignmentMap.computeIfAbsent(
                    assignment.getId(),
                    ignored -> buildAssignmentDTO(assignment)
            );

            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle != null && vehicle.getId() != null) {
                TransportMonitorDTO.VehicleMonitorDTO vehicleDTO = vehicleMap.computeIfAbsent(
                        vehicle.getId(),
                        ignored -> buildVehicleDTO(vehicle)
                );
                addUnique(vehicleDTO.getAssignmentIds(), assignment.getId());
                for (Long shipmentId : assignmentDTO.getShipmentIds()) {
                    addUnique(vehicleDTO.getShipmentIds(), shipmentId);
                }
            }
        }

        dto.setShipments(new ArrayList<>(shipmentMap.values()));
        dto.setAssignments(new ArrayList<>(assignmentMap.values()));
        dto.setVehicles(new ArrayList<>(vehicleMap.values()));
        dto.setLinks(linkMap.values().stream()
                .flatMap(perShipment -> perShipment.values().stream())
                .collect(Collectors.toList()));

        TransportMonitorDTO.Summary summary = new TransportMonitorDTO.Summary();
        summary.setActiveShipmentCount(dto.getShipments().size());
        summary.setActiveAssignmentCount(dto.getAssignments().size());
        summary.setActiveVehicleCount(dto.getVehicles().size());
        dto.setSummary(summary);

        return dto;
    }

    private TransportMonitorDTO.ShipmentMonitorDTO buildShipmentDTO(Shipment shipment, List<ShipmentItem> items) {
        TransportMonitorDTO.ShipmentMonitorDTO dto = new TransportMonitorDTO.ShipmentMonitorDTO();
        dto.setShipmentId(shipment.getId());
        dto.setRefNo(shipment.getRefNo());
        dto.setCargoType(shipment.getCargoType());
        dto.setOriginPOIName(shipment.getOriginPOI() != null ? shipment.getOriginPOI().getName() : null);
        dto.setDestPOIName(shipment.getDestPOI() != null ? shipment.getDestPOI().getName() : null);
        dto.setStatus(shipment.getStatus() != null ? shipment.getStatus().name() : null);
        dto.setStatusText(shipmentStatusText(shipment.getStatus()));

        int completed = 0;
        int inProgress = 0;
        int waiting = 0;
        int effectiveTotal = 0;

        for (ShipmentItem item : items) {
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                continue;
            }
            effectiveTotal++;
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                completed++;
            } else if (item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED
                    || item.getStatus() == ShipmentItem.ShipmentItemStatus.IN_TRANSIT) {
                inProgress++;
            } else {
                waiting++;
            }
        }

        dto.setTotalItems(effectiveTotal);
        dto.setCompletedItems(completed);
        dto.setInProgressItems(inProgress);
        dto.setWaitingItems(waiting);
        dto.setProgressPercentage(effectiveTotal == 0 ? 0 : completed * 100.0 / effectiveTotal);
        return dto;
    }

    private TransportMonitorDTO.AssignmentMonitorDTO buildAssignmentDTO(Assignment assignment) {
        TransportMonitorDTO.AssignmentMonitorDTO dto = new TransportMonitorDTO.AssignmentMonitorDTO();
        dto.setAssignmentId(assignment.getId());
        dto.setStatus(assignment.getStatus() != null ? assignment.getStatus().name() : null);
        dto.setStatusText(assignmentStatusText(assignment.getStatus()));

        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null) {
            dto.setVehicleId(vehicle.getId());
            dto.setLicensePlate(vehicle.getLicensePlate());
            dto.setVehicleStatus(vehicle.getCurrentStatus() != null ? vehicle.getCurrentStatus().name() : null);
            dto.setCurrentLoad(valueOrZero(vehicle.getCurrentLoad()));
            dto.setMaxLoadCapacity(valueOrZero(vehicle.getMaxLoadCapacity()));
            dto.setCurrentVolume(valueOrZero(vehicle.getCurrentVolumn()));
            dto.setMaxVolumeCapacity(valueOrZero(vehicle.getCargoVolume()));
        }

        Route route = assignment.getRoute();
        if (route != null) {
            dto.setRouteName(route.getName());
            fillStartPOI(dto, route.getStartPOI());
            fillEndPOI(dto, route.getEndPOI());
        } else {
            fillStartPOI(dto, assignment.getOriginPOI());
            fillEndPOI(dto, assignment.getDestPOI());
        }

        Set<String> goodsNames = new LinkedHashSet<>();
        int quantity = 0;
        if (assignment.getShipmentItems() != null) {
            for (ShipmentItem item : assignment.getShipmentItems()) {
                if (item.getName() != null && !item.getName().isBlank()) {
                    goodsNames.add(item.getName());
                }
                if (item.getQty() != null) {
                    quantity += item.getQty();
                }
            }
        }
        dto.setGoodsName(String.join(", ", goodsNames));
        dto.setQuantity(quantity);

        return dto;
    }

    private TransportMonitorDTO.VehicleMonitorDTO buildVehicleDTO(Vehicle vehicle) {
        TransportMonitorDTO.VehicleMonitorDTO dto = new TransportMonitorDTO.VehicleMonitorDTO();
        dto.setVehicleId(vehicle.getId());
        dto.setLicensePlate(vehicle.getLicensePlate());
        dto.setStatus(vehicle.getCurrentStatus() != null ? vehicle.getCurrentStatus().name() : null);
        dto.setStatusText(vehicleStatusText(vehicle.getCurrentStatus()));
        dto.setCurrentLoad(valueOrZero(vehicle.getCurrentLoad()));
        dto.setMaxLoadCapacity(valueOrZero(vehicle.getMaxLoadCapacity()));
        dto.setCurrentVolume(valueOrZero(vehicle.getCurrentVolumn()));
        dto.setMaxVolumeCapacity(valueOrZero(vehicle.getCargoVolume()));
        return dto;
    }

    private TransportMonitorDTO.LinkDTO buildLinkDTO(Long shipmentId, Long assignmentId, Long vehicleId) {
        TransportMonitorDTO.LinkDTO dto = new TransportMonitorDTO.LinkDTO();
        dto.setShipmentId(shipmentId);
        dto.setAssignmentId(assignmentId);
        dto.setVehicleId(vehicleId);
        return dto;
    }

    private void fillStartPOI(TransportMonitorDTO.AssignmentMonitorDTO dto, POI poi) {
        if (poi == null) {
            return;
        }
        dto.setStartPOIId(poi.getId());
        dto.setStartPOIName(poi.getName());
        dto.setStartLng(toDouble(poi.getLongitude()));
        dto.setStartLat(toDouble(poi.getLatitude()));
    }

    private void fillEndPOI(TransportMonitorDTO.AssignmentMonitorDTO dto, POI poi) {
        if (poi == null) {
            return;
        }
        dto.setEndPOIId(poi.getId());
        dto.setEndPOIName(poi.getName());
        dto.setEndLng(toDouble(poi.getLongitude()));
        dto.setEndLat(toDouble(poi.getLatitude()));
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private Double valueOrZero(Double value) {
        return value != null ? value : 0.0;
    }

    private <T> void addUnique(List<T> list, T value) {
        if (value != null && !list.contains(value)) {
            list.add(value);
        }
    }

    private String shipmentStatusText(Shipment.ShipmentStatus status) {
        if (status == null) return "\u672a\u77e5";
        switch (status) {
            case PLANNED: return "\u5df2\u8ba1\u5212";
            case PICKED_UP: return "\u5df2\u63d0\u8d27";
            case IN_TRANSIT: return "\u8fd0\u8f93\u4e2d";
            case DELIVERED: return "\u5df2\u9001\u8fbe";
            case CANCELLED: return "\u5df2\u53d6\u6d88";
            case CREATED:
            default: return "\u5df2\u521b\u5efa";
        }
    }

    private String assignmentStatusText(Assignment.AssignmentStatus status) {
        if (status == null) return "\u672a\u77e5";
        switch (status) {
            case WAITING: return "\u7b49\u5f85\u4e2d";
            case ASSIGNED: return "\u5df2\u5206\u914d";
            case IN_PROGRESS: return "\u6267\u884c\u4e2d";
            case COMPLETED: return "\u5df2\u5b8c\u6210";
            case FAILED: return "\u5931\u8d25";
            case CANCELLED: return "\u5df2\u53d6\u6d88";
            case DELAYED:
            default: return "\u5ef6\u8fdf";
        }
    }

    private String vehicleStatusText(Vehicle.VehicleStatus status) {
        if (status == null) return "\u672a\u77e5";
        switch (status) {
            case IDLE: return "\u7a7a\u95f2";
            case ORDER_DRIVING: return "\u524d\u5f80\u88c5\u8d27\u70b9";
            case LOADING: return "\u88c5\u8d27\u4e2d";
            case TRANSPORT_DRIVING: return "\u8fd0\u8f93\u4e2d";
            case UNLOADING: return "\u5378\u8d27\u4e2d";
            case WAITING: return "\u7b49\u5f85\u4e2d";
            case BREAKDOWN:
            default: return "\u6545\u969c";
        }
    }
}
