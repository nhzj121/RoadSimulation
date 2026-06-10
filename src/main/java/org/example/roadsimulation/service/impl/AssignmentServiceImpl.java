package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.service.AssignmentService;
import org.example.roadsimulation.service.TransportMetricsService;
import org.example.roadsimulation.service.TransportLifecycleService;
import org.example.roadsimulation.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private TransportMetricsService transportMetricsService;

    @Autowired
    private TransportLifecycleService transportLifecycleService;

    // ==================== CRUD ====================

    @Override
    public AssignmentResponseDTO createAssignment(AssignmentRequestDTO requestDTO) {
        Assignment assignment = new Assignment();
        assignment.setStatus(requestDTO.getStatus() != null ? requestDTO.getStatus() : AssignmentStatus.WAITING);
        assignment.setStartTime(requestDTO.getStartTime());
        assignment.setEndTime(requestDTO.getEndTime());
        assignmentRepository.save(assignment);

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    @Override
    public AssignmentResponseDTO getAssignmentById(Long id) {
        Assignment assignment = findAssignmentById(id);
        return convertToDTO(assignment);
    }

    @Override
    public Page<AssignmentResponseDTO> getAllAssignments(Pageable pageable) {
        return assignmentRepository.findAll(pageable).map(this::convertToDTO);
    }

    @Override
    public AssignmentResponseDTO updateAssignment(Long id, AssignmentRequestDTO requestDTO) {
        Assignment assignment = findAssignmentById(id);
        assignment.setStatus(requestDTO.getStatus());
        assignment.setStartTime(requestDTO.getStartTime());
        assignment.setEndTime(requestDTO.getEndTime());
        assignmentRepository.save(assignment);

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    @Override
    public void deleteAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);
        assignmentRepository.delete(assignment);
    }

    // ==================== 查询 ====================

    @Override
    public List<AssignmentResponseDTO> getAssignmentsByStatus(AssignmentStatus status) {
        List<Assignment> list = assignmentRepository.findByStatus(status);
        List<AssignmentResponseDTO> dtos = new ArrayList<>();
        for (Assignment a : list) dtos.add(convertToDTO(a));
        return dtos;
    }

    @Override
    public List<AssignmentResponseDTO> getAssignmentsByVehicle(Long vehicleId) {
        List<Assignment> list = assignmentRepository.findByAssignedVehicleId(vehicleId);
        List<AssignmentResponseDTO> dtos = new ArrayList<>();
        for (Assignment a : list) dtos.add(convertToDTO(a));
        return dtos;
    }

    @Override
    public List<AssignmentResponseDTO> getAssignmentsByDriver(Long driverId) {
        List<Assignment> list = assignmentRepository.findByAssignedDriverId(driverId);
        List<AssignmentResponseDTO> dtos = new ArrayList<>();
        for (Assignment a : list) dtos.add(convertToDTO(a));
        return dtos;
    }

    @Override
    public List<AssignmentResponseDTO> getAssignmentsByRoute(Long routeId) {
        List<Assignment> list = assignmentRepository.findByRouteId(routeId);
        List<AssignmentResponseDTO> dtos = new ArrayList<>();
        for (Assignment a : list) dtos.add(convertToDTO(a));
        return dtos;
    }

    // ==================== 业务操作 ====================

    @Override
    public AssignmentResponseDTO startAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setStartTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    @Override
    public AssignmentResponseDTO completeAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setEndTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    @Override
    public AssignmentResponseDTO cancelAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);
        transportLifecycleService.cancelAssignment(
                assignment,
                "AssignmentService cancel",
                LocalDateTime.now(),
                "AssignmentService"
        );

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    @Override
    public AssignmentResponseDTO moveToNextAction(Long id) {
        Assignment assignment = findAssignmentById(id);
        assignment.moveToNextAction(LocalDateTime.now());
        assignmentRepository.save(assignment);

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    @Override
    public AssignmentResponseDTO updateAssignmentStatus(Long id, AssignmentStatus status) {
        Assignment assignment = findAssignmentById(id);
        if (status == AssignmentStatus.CANCELLED) {
            transportLifecycleService.cancelAssignment(
                    assignment,
                    "AssignmentService status update",
                    LocalDateTime.now(),
                    "AssignmentService"
            );
            calculateVehicleMetrics(assignment);
            return convertToDTO(assignment);
        }

        assignment.setStatus(status);
        if (status == AssignmentStatus.COMPLETED) {
            assignment.setEndTime(LocalDateTime.now());
        }
        assignmentRepository.save(assignment);

        calculateVehicleMetrics(assignment);

        return convertToDTO(assignment);
    }

    // ==================== 批量操作 ====================

    @Override
    public List<AssignmentResponseDTO> batchCreateAssignments(List<AssignmentRequestDTO> requestDTOs) {
        List<AssignmentResponseDTO> dtos = new ArrayList<>();
        for(AssignmentRequestDTO dto: requestDTOs){
            dtos.add(createAssignment(dto));
        }
        return dtos;
    }

    @Override
    public void batchUpdateStatus(List<Long> assignmentIds, AssignmentStatus status) {
        for(Long id: assignmentIds){
            updateAssignmentStatus(id,status);
        }
    }

    // ==================== 前后端DTO ====================

    @Override
    public List<AssignmentBriefDTO> getActiveAssignments() {
        return dataInitializer.getActiveAssignments();
    }

    @Override
    public List<AssignmentBriefDTO> getNewAssignments() {
        return dataInitializer.getNewAssignmentsForDrawing();
    }

    @Override
    public AssignmentDTO getAssignmentDetail(Long assignmentId) {
        Assignment assignment = findAssignmentById(assignmentId);

        AssignmentDTO dto = new AssignmentDTO();

        // ===== Assignment 基础信息 =====
        dto.setId(assignment.getId());
        dto.setStatus(assignment.getStatus() != null ? assignment.getStatus().toString() : "UNKNOWN");
        dto.setStartTime(assignment.getStartTime());
        dto.setEndTime(assignment.getEndTime());
        dto.setCreatedTime(assignment.getCreatedTime());
        dto.setUpdatedTime(assignment.getUpdatedTime());
        dto.setCurrentActionIndex(assignment.getCurrentActionIndex());

        // ===== Vehicle 信息 =====
        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null) {
            VehicleDTO vehicleDTO = new VehicleDTO();
            vehicleDTO.setId(vehicle.getId());
            vehicleDTO.setLicensePlate(vehicle.getLicensePlate());
            vehicleDTO.setBrand(vehicle.getBrand());
            vehicleDTO.setModelType(vehicle.getModelType());
            vehicleDTO.setVehicleType(vehicle.getVehicleType());
            vehicleDTO.setDriverName(vehicle.getDriverName());

            // 新增指标字段
            vehicleDTO.setLoadingWaitTime(vehicle.getLoadingWaitTime());
            vehicleDTO.setEmptyDrivingTime(vehicle.getEmptyDrivingTime());
            vehicleDTO.setEmptyDrivingDistance(vehicle.getEmptyDrivingDistance());
            vehicleDTO.setTotalDrivingTime(vehicle.getTotalDrivingTime());
            vehicleDTO.setTotalDrivingDistance(vehicle.getTotalDrivingDistance());

            dto.setVehicle(vehicleDTO);
        }

        // ===== Route & POI 信息 =====
        Route route = assignment.getRoute();
        if (route != null) {
            RouteDTO routeDTO = new RouteDTO();
            routeDTO.setId(route.getId());
            routeDTO.setRouteCode(route.getRouteCode());
            routeDTO.setName(route.getName());
            routeDTO.setDistance(route.getDistance());
            routeDTO.setEstimatedTime(route.getEstimatedTime());
            routeDTO.setRouteType(route.getRouteType());
            routeDTO.setStatus(route.getStatus() != null ? route.getStatus().toString() : null);
            routeDTO.setDescription(route.getDescription());

            // 起点 POI
            POI startPOI = route.getStartPOI();
            if (startPOI != null) {
                routeDTO.setStartPOIId(startPOI.getId());
                routeDTO.setStartPOIName(startPOI.getName());
                routeDTO.setStartLng(startPOI.getLongitude());
                routeDTO.setStartLat(startPOI.getLatitude());
            }

            // 终点 POI
            POI endPOI = route.getEndPOI();
            if (endPOI != null) {
                routeDTO.setEndPOIId(endPOI.getId());
                routeDTO.setEndPOIName(endPOI.getName());
                routeDTO.setEndLng(endPOI.getLongitude());
                routeDTO.setEndLat(endPOI.getLatitude());
            }

            dto.setRoute(routeDTO);
        }

        // ===== ShipmentItem 信息 =====
        Set<ShipmentItem> items = assignment.getShipmentItems();
        List<ShipmentItemDTO> itemDTOs = new ArrayList<>();
        if (items != null && !items.isEmpty()) {
            for (ShipmentItem item : items) {
                ShipmentItemDTO itemDTO = new ShipmentItemDTO();
                itemDTO.setId(item.getId());
                itemDTO.setName(item.getName());
                itemDTO.setQty(item.getQty());
                itemDTO.setWeight(item.getWeight());
                itemDTO.setVolume(item.getVolume());

                if (item.getShipment() != null) {
                    itemDTO.setShipmentId(item.getShipment().getId());
                    itemDTO.setShipmentRefNo(item.getShipment().getRefNo());
                }

                if (item.getGoods() != null) {
                    itemDTO.setGoodsId(item.getGoods().getId());
                    itemDTO.setGoodsName(item.getGoods().getName());
                }

                itemDTOs.add(itemDTO);
            }
        }
        dto.setShipmentItems(itemDTOs);

        // ===== 进度信息 =====
        List<Long> actionLine = assignment.getActionLine();
        if (actionLine != null && !actionLine.isEmpty() && assignment.getCurrentActionIndex() != null) {
            double progress = ((double) assignment.getCurrentActionIndex() / actionLine.size()) * 100;
            dto.setProgressPercentage(progress);
        }

        if (route != null && route.getEstimatedTime() != null) {
            double estimatedHours = route.getEstimatedTime();
            double completedPercentage = dto.getProgressPercentage() != null ? dto.getProgressPercentage() / 100 : 0;
            double remainingHours = estimatedHours * (1 - completedPercentage);
            dto.setEstimatedRemainingTime((long) (remainingHours * 3600)); // 秒
        }

        return dto;
    }

    @Override
    public void markAssignmentAsDrawn(Long assignmentId) {
        dataInitializer.markAssignmentAsDrawn(assignmentId);
    }

    @Override
    public List<Long> getCompletedAssignments() {
        return dataInitializer.getCompletedAssignments();
    }

    @Override
    public List<AssignmentBriefDTO> getAssignmentBriefsByIds(List<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Assignment> assignments = assignmentRepository.findByIds(assignmentIds);
        List<AssignmentBriefDTO> result = new ArrayList<>();
        for (Assignment assignment : assignments) {
            result.add(convertToBriefDTO(assignment));
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    private Assignment findAssignmentById(Long id){
        return assignmentRepository.findById(id).orElseThrow(()->new RuntimeException("任务未找到:"+id));
    }

    private AssignmentResponseDTO convertToDTO(Assignment assignment){
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setId(assignment.getId());
        dto.setStatus(assignment.getStatus());
        dto.setStartTime(assignment.getStartTime());
        dto.setEndTime(assignment.getEndTime());
        return dto;
    }

    // ==================== 核心：车辆指标计算 ====================
    private AssignmentBriefDTO convertToBriefDTO(Assignment assignment) {
        AssignmentBriefDTO dto = new AssignmentBriefDTO();
        dto.setAssignmentId(assignment.getId());
        dto.setStatus(assignment.getStatus() != null ? assignment.getStatus().toString() : "UNKNOWN");
        dto.setCreatedTime(assignment.getCreatedTime());
        dto.setStartTime(assignment.getStartTime());

        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null) {
            dto.setVehicleId(vehicle.getId());
            dto.setLicensePlate(vehicle.getLicensePlate());
            dto.setVehicleStatus(vehicle.getCurrentStatus() != null ? vehicle.getCurrentStatus().toString() : "IDLE");
            dto.setVehicleCurrentLon(vehicle.getCurrentLongitude() != null ? vehicle.getCurrentLongitude().doubleValue() : null);
            dto.setVehicleCurrentLat(vehicle.getCurrentLatitude() != null ? vehicle.getCurrentLatitude().doubleValue() : null);
            dto.setCurrentLoad(vehicle.getCurrentLoad());
            dto.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());
            dto.setCurrentVolume(vehicle.getCurrentVolumn());
            dto.setMaxVolumeCapacity(vehicle.getCargoVolume());
        }

        Route route = assignment.getRoute();
        if (route != null) {
            dto.setRouteId(route.getId());
            dto.setRouteName(route.getName());
            fillBriefStartPOI(dto, route.getStartPOI());
            fillBriefEndPOI(dto, route.getEndPOI());
        } else {
            fillBriefStartPOI(dto, assignment.getOriginPOI());
            fillBriefEndPOI(dto, assignment.getDestPOI());
        }

        Set<ShipmentItem> items = assignment.getShipmentItems();
        if (items != null && !items.isEmpty()) {
            ShipmentItem firstItem = items.iterator().next();
            dto.setGoodsName(firstItem.getName());
            dto.setQuantity(firstItem.getQty());
            dto.setGoodsWeightPerUnit(firstItem.getWeight());
            dto.setGoodsVolumePerUnit(firstItem.getVolume());
            if (firstItem.getShipment() != null) {
                dto.setShipmentRefNo(firstItem.getShipment().getRefNo());
            }
        }

        if (dto.getStartPOIId() != null && dto.getEndPOIId() != null) {
            dto.setPairId(dto.getStartPOIId() + "_" + dto.getEndPOIId());
        }

        return dto;
    }

    private void fillBriefStartPOI(AssignmentBriefDTO dto, POI poi) {
        if (poi == null) {
            return;
        }
        dto.setStartPOIId(poi.getId());
        dto.setStartPOIName(poi.getName());
        dto.setStartLng(poi.getLongitude());
        dto.setStartLat(poi.getLatitude());
    }

    private void fillBriefEndPOI(AssignmentBriefDTO dto, POI poi) {
        if (poi == null) {
            return;
        }
        dto.setEndPOIId(poi.getId());
        dto.setEndPOIName(poi.getName());
        dto.setEndLng(poi.getLongitude());
        dto.setEndLat(poi.getLatitude());
    }

    private void calculateVehicleMetrics(Assignment assignment){
        if (assignment == null || assignment.getId() == null) {
            return;
        }
        transportMetricsService.rebuildMetricsForAssignment(assignment.getId());
    }
}
