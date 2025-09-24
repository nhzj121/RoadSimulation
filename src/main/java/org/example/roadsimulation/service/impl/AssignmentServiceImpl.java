package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.AssignmentRequestDTO;
import org.example.roadsimulation.dto.AssignmentResponseDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.service.AssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private VehicleService vehicleService; // 假设存在

    @Autowired
    private DriverService driverService; // 假设存在

    @Autowired
    private RouteService routeService; // 假设存在

    @Autowired
    private ShipmentItemService shipmentItemService; // 假设存在

    @Override
    @Transactional
    public AssignmentResponseDTO createAssignment(AssignmentRequestDTO requestDTO) {
        // 验证数据
        validateAssignmentRequest(requestDTO);

        // 创建实体
        Assignment assignment = new Assignment();
        updateAssignmentFromDTO(assignment, requestDTO);

        // 设置关联关系
        setAssignmentRelationships(assignment, requestDTO);

        // 设置默认值
        if (assignment.getCurrentActionIndex() == null) {
            assignment.setCurrentActionIndex(0);
        }

        if (assignment.getStatus() == null) {
            assignment.setStatus(AssignmentStatus.WAITING);
        }

        Assignment savedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(savedAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponseDTO getAssignmentById(Long id) {
        Assignment assignment = findAssignmentById(id);
        return convertToDTO(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponseDTO> getAllAssignments(Pageable pageable) {
        Page<Assignment> assignments = assignmentRepository.findAll(pageable);
        return assignments.map(this::convertToDTO);
    }

    @Override
    @Transactional
    public AssignmentResponseDTO updateAssignment(Long id, AssignmentRequestDTO requestDTO) {
        Assignment assignment = findAssignmentById(id);

        // 检查是否可以修改（如已完成的任务不能修改）
        if (assignment.isCompleted() || assignment.isCancelled()) {
            throw new IllegalStateException("已完成或已取消的任务不能修改");
        }

        // 更新字段
        updateAssignmentFromDTO(assignment, requestDTO);

        // 更新关联关系
        setAssignmentRelationships(assignment, requestDTO);

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        // 检查是否可以删除
        if (assignment.isInProgress()) {
            throw new IllegalStateException("进行中的任务不能删除");
        }

        assignmentRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByStatus(AssignmentStatus status) {
        List<Assignment> assignments = assignmentRepository.findByStatus(status);
        return assignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByVehicle(Long vehicleId) {
        List<Assignment> assignments = assignmentRepository.findByAssignedVehicleId(vehicleId);
        return assignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByDriver(Long driverId) {
        List<Assignment> assignments = assignmentRepository.findByAssignedDriverId(driverId);
        return assignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByRoute(Long routeId) {
        List<Assignment> assignments = assignmentRepository.findByRouteId(routeId);
        return assignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AssignmentResponseDTO startAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isWaiting() && !assignment.isAssigned()) {
            throw new IllegalStateException("只有等待或已分配状态的任务可以开始");
        }

        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setStartTime(LocalDateTime.now());

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    @Override
    @Transactional
    public AssignmentResponseDTO completeAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isInProgress()) {
            throw new IllegalStateException("只有进行中的任务可以完成");
        }

        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setEndTime(LocalDateTime.now());

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    @Override
    @Transactional
    public AssignmentResponseDTO cancelAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (assignment.isCompleted() || assignment.isCancelled()) {
            throw new IllegalStateException("已完成或已取消的任务不能再次取消");
        }

        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setEndTime(LocalDateTime.now());

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    @Override
    @Transactional
    public AssignmentResponseDTO moveToNextAction(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isInProgress()) {
            throw new IllegalStateException("只有进行中的任务可以执行动作切换");
        }

        assignment.moveToNextAction();
        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    @Override
    @Transactional
    public AssignmentResponseDTO updateAssignmentStatus(Long id, AssignmentStatus status) {
        Assignment assignment = findAssignmentById(id);
        assignment.setStatus(status);

        // 如果是完成或取消状态，设置结束时间
        if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.CANCELLED) {
            assignment.setEndTime(LocalDateTime.now());
        }

        // 如果是开始状态，设置开始时间
        if (status == AssignmentStatus.IN_PROGRESS && assignment.getStartTime() == null) {
            assignment.setStartTime(LocalDateTime.now());
        }

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<AssignmentStatus, Long> getAssignmentStatistics() {
        List<Object[]> results = assignmentRepository.countAssignmentsByStatus();
        return results.stream()
                .collect(Collectors.toMap(
                        result -> (AssignmentStatus) result[0],
                        result -> (Long) result[1]
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getOverdueAssignments() {
        LocalDateTime now = LocalDateTime.now();
        List<Assignment> overdueAssignments = assignmentRepository.findOverdueAssignments(
                AssignmentStatus.ASSIGNED, now);

        return overdueAssignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getActiveAssignmentsByDriver(Long driverId) {
        List<AssignmentStatus> activeStatuses = Arrays.asList(
                AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS);

        List<Assignment> assignments = assignmentRepository.findDriverCurrentAssignments(
                driverId, activeStatuses);

        return assignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AssignmentResponseDTO> batchCreateAssignments(List<AssignmentRequestDTO> requestDTOs) {
        List<Assignment> assignments = new ArrayList<>();

        for (AssignmentRequestDTO dto : requestDTOs) {
            validateAssignmentRequest(dto);

            Assignment assignment = new Assignment();
            updateAssignmentFromDTO(assignment, dto);
            setAssignmentRelationships(assignment, dto);

            assignments.add(assignment);
        }

        List<Assignment> savedAssignments = assignmentRepository.saveAll(assignments);
        return savedAssignments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void batchUpdateStatus(List<Long> assignmentIds, AssignmentStatus status) {
        List<Assignment> assignments = assignmentRepository.findAllById(assignmentIds);

        for (Assignment assignment : assignments) {
            if (!assignment.isCompleted() && !assignment.isCancelled()) {
                assignment.setStatus(status);

                if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.CANCELLED) {
                    assignment.setEndTime(LocalDateTime.now());
                }
            }
        }

        assignmentRepository.saveAll(assignments);
    }

    // 私有辅助方法
    private Assignment findAssignmentById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到ID为 " + id + "的任务分配"));
    }

    private void validateAssignmentRequest(AssignmentRequestDTO requestDTO) {
        if (requestDTO.getStatus() == null) {
            throw new IllegalArgumentException("任务状态不能为空");
        }

        // 可以添加更多验证逻辑
    }

    private void updateAssignmentFromDTO(Assignment assignment, AssignmentRequestDTO dto) {
        assignment.setStatus(dto.getStatus());
        assignment.setCurrentActionIndex(dto.getCurrentActionIndex());
        assignment.setStartTime(dto.getStartTime());
        assignment.setEndTime(dto.getEndTime());

        if (dto.getActionLine() != null) {
            assignment.setActionLine(dto.getActionLine());
        }
    }

    private void setAssignmentRelationships(Assignment assignment, AssignmentRequestDTO dto) {
        // 设置车辆关联
        if (dto.getVehicleId() != null) {
            // 这里需要调用VehicleService来获取车辆实体
            // Vehicle vehicle = vehicleService.getVehicleEntityById(dto.getVehicleId());
            // assignment.setAssignedVehicle(vehicle);
        }

        // 设置司机关联
        if (dto.getDriverId() != null) {
            // Driver driver = driverService.getDriverEntityById(dto.getDriverId());
            // assignment.setAssignedDriver(driver);
        }

        // 设置路线关联
        if (dto.getRouteId() != null) {
            // Route route = routeService.getRouteEntityById(dto.getRouteId());
            // assignment.setRoute(route);
        }

        // 设置货物项关联（需要更复杂的逻辑）
    }

    private AssignmentResponseDTO convertToDTO(Assignment assignment) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setId(assignment.getId());
        dto.setStatus(assignment.getStatus());
        dto.setCurrentActionIndex(assignment.getCurrentActionIndex());
        dto.setStartTime(assignment.getStartTime());
        dto.setEndTime(assignment.getEndTime());
        dto.setActionLine(assignment.getActionLine());

        // 设置关联实体的基本信息
        if (assignment.getAssignedVehicle() != null) {
            dto.setVehicleId(assignment.getAssignedVehicle().getId());
            dto.setVehicleInfo(assignment.getAssignedVehicle().getLicensePlate()); // 假设有车牌号
        }

        if (assignment.getAssignedDriver() != null) {
            dto.setDriverId(assignment.getAssignedDriver().getId());
            dto.setDriverInfo(assignment.getAssignedDriver().getName()); // 假设有姓名
        }

        if (assignment.getRoute() != null) {
            dto.setRouteId(assignment.getRoute().getId());
            dto.setRouteInfo(assignment.getRoute().getName()); // 假设有路线名称
        }

        dto.setShipmentItemsCount(assignment.getShipmentItems().size());

        // 计算持续时间
        if (assignment.getStartTime() != null && assignment.getEndTime() != null) {
            Duration duration = Duration.between(assignment.getStartTime(), assignment.getEndTime());
            dto.setDuration(formatDuration(duration));
        }

        return dto;
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        return String.format("%d小时%d分钟", hours, minutes);
    }
}