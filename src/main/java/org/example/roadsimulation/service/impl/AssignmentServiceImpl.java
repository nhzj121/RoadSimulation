package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.AssignmentRequestDTO;
import org.example.roadsimulation.dto.AssignmentResponseDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AssignmentService 的实现类（已集成车辆状态实时同步）
 */
@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentServiceImpl.class);

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private RouteService routeService;

    @Autowired
    private ShipmentItemService shipmentItemService;

    // 关键：注入车辆状态转移服务（实现类）
    @Autowired
    private StateTransitionService stateTransitionService;

    /**
     * 创建任务分配（创建后立即同步车辆状态）
     */
    @Override
    @Transactional
    public AssignmentResponseDTO createAssignment(AssignmentRequestDTO requestDTO) {
        validateAssignmentRequest(requestDTO);

        Assignment assignment = new Assignment();
        updateAssignmentFromDTO(assignment, requestDTO);
        setAssignmentRelationships(assignment, requestDTO);

        if (assignment.getCurrentActionIndex() == null) {
            assignment.setCurrentActionIndex(0);
        }
        if (assignment.getStatus() == null) {
            assignment.setStatus(AssignmentStatus.WAITING);
        }

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 新增：创建任务后同步更新车辆状态
        updateVehicleStateIfAssigned(savedAssignment);

        return convertToDTO(savedAssignment);
    }

    /**
     * 根据 ID 查询任务
     */
    @Override
    @Transactional(readOnly = true)
    public AssignmentResponseDTO getAssignmentById(Long id) {
        Assignment assignment = findAssignmentById(id);
        return convertToDTO(assignment);
    }

    /**
     * 分页查询所有任务
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponseDTO> getAllAssignments(Pageable pageable) {
        Page<Assignment> assignments = assignmentRepository.findAll(pageable);
        return assignments.map(this::convertToDTO);
    }

    /**
     * 更新任务
     */
    @Override
    @Transactional
    public AssignmentResponseDTO updateAssignment(Long id, AssignmentRequestDTO requestDTO) {
        Assignment assignment = findAssignmentById(id);

        if (assignment.isCompleted() || assignment.isCancelled()) {
            throw new IllegalStateException("已完成或已取消的任务不能修改");
        }

        updateAssignmentFromDTO(assignment, requestDTO);
        setAssignmentRelationships(assignment, requestDTO);

        Assignment updatedAssignment = assignmentRepository.save(assignment);

        // 新增：更新任务后同步车辆状态
        updateVehicleStateIfAssigned(updatedAssignment);

        return convertToDTO(updatedAssignment);
    }

    /**
     * 删除任务
     */
    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (assignment.isInProgress()) {
            throw new IllegalStateException("进行中的任务不能删除");
        }

        assignmentRepository.deleteById(id);
    }

    /**
     * 根据状态获取任务
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByStatus(AssignmentStatus status) {
        return assignmentRepository.findByStatus(status)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据车辆 ID 获取任务
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByVehicle(Long vehicleId) {
        return assignmentRepository.findByAssignedVehicleId(vehicleId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据司机 ID 获取任务
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByDriver(Long driverId) {
        return assignmentRepository.findByAssignedDriverId(driverId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据路线 ID 获取任务
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getAssignmentsByRoute(Long routeId) {
        return assignmentRepository.findByRouteId(routeId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 启动任务
     */
    @Override
    @Transactional
    public AssignmentResponseDTO startAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isWaiting() && !assignment.isAssigned()) {
            throw new IllegalStateException("只有等待或已分配状态的任务可以开始");
        }

        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setStartTime(LocalDateTime.now());

        Assignment saved = assignmentRepository.save(assignment);

        // 新增：开始任务后同步车辆状态
        updateVehicleStateIfAssigned(saved);

        return convertToDTO(saved);
    }

    /**
     * 完成任务
     */
    @Override
    @Transactional
    public AssignmentResponseDTO completeAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isInProgress()) {
            throw new IllegalStateException("只有进行中的任务可以完成");
        }

        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setEndTime(LocalDateTime.now());

        Assignment saved = assignmentRepository.save(assignment);

        // 新增：任务完成后强制车辆为空闲
        forceVehicleToIdleIfAssigned(saved);

        logger.info("任务[{}]已完成，车辆状态已强制更新为IDLE", id);

        return convertToDTO(saved);
    }

    /**
     * 取消任务
     */
    @Override
    @Transactional
    public AssignmentResponseDTO cancelAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (assignment.isCompleted() || assignment.isCancelled()) {
            throw new IllegalStateException("已完成或已取消的任务不能再次取消");
        }

        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setEndTime(LocalDateTime.now());

        Assignment saved = assignmentRepository.save(assignment);

        // 新增：取消任务后强制车辆为空闲
        forceVehicleToIdleIfAssigned(saved);

        logger.info("任务[{}]已取消，车辆状态已强制更新为IDLE", id);

        return convertToDTO(saved);
    }

    /**
     * 切换到下一个动作（核心：每次动作推进都同步车辆状态）
     */
    @Override
    @Transactional
    public AssignmentResponseDTO moveToNextAction(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isInProgress()) {
            throw new IllegalStateException("只有进行中的任务可以执行动作切换");
        }

        assignment.moveToNextAction();
        Assignment updatedAssignment = assignmentRepository.save(assignment);

        // 新增：动作推进后立即同步车辆状态（最关键的一步！）
        updateVehicleStateIfAssigned(updatedAssignment);

        logger.info("任务[{}]推进到动作索引: {}", id, updatedAssignment.getCurrentActionIndex());

        return convertToDTO(updatedAssignment);
    }

    /**
     * 更新任务状态
     */
    @Override
    @Transactional
    public AssignmentResponseDTO updateAssignmentStatus(Long id, AssignmentStatus status) {
        Assignment assignment = findAssignmentById(id);
        assignment.setStatus(status);

        if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.CANCELLED) {
            assignment.setEndTime(LocalDateTime.now());
        }
        if (status == AssignmentStatus.IN_PROGRESS && assignment.getStartTime() == null) {
            assignment.setStartTime(LocalDateTime.now());
        }

        Assignment saved = assignmentRepository.save(assignment);

        // 新增：状态变更后同步车辆状态
        if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.CANCELLED) {
            forceVehicleToIdleIfAssigned(saved);
        } else {
            updateVehicleStateIfAssigned(saved);
        }

        return convertToDTO(saved);
    }

    /**
     * 获取任务数量统计（按状态分类）
     */
    @Override
    @Transactional(readOnly = true)
    public Map<AssignmentStatus, Long> getAssignmentStatistics() {
        List<Object[]> results = assignmentRepository.countAssignmentsByStatus();
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (AssignmentStatus) r[0],
                        r -> (Long) r[1]
                ));
    }

    /**
     * 获取超期任务
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getOverdueAssignments() {
        LocalDateTime now = LocalDateTime.now();
        return assignmentRepository.findOverdueAssignments(AssignmentStatus.ASSIGNED, now)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取司机的活动任务（未完成/未取消）
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponseDTO> getActiveAssignmentsByDriver(Long driverId) {
        List<AssignmentStatus> activeStatuses = Arrays.asList(
                AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS);

        return assignmentRepository.findDriverCurrentAssignments(driverId, activeStatuses)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 批量创建任务
     */
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
        List<Assignment> saved = assignmentRepository.saveAll(assignments);

        // 新增：批量创建后同步每个任务的车辆状态
        for (Assignment assignment : saved) {
            updateVehicleStateIfAssigned(assignment);
        }

        return saved.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 批量更新任务状态
     */
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
        List<Assignment> saved = assignmentRepository.saveAll(assignments);

        // 新增：批量更新后同步车辆状态
        for (Assignment assignment : saved) {
            if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.CANCELLED) {
                forceVehicleToIdleIfAssigned(assignment);
            } else {
                updateVehicleStateIfAssigned(assignment);
            }
        }
    }

    // ================= 新增辅助方法：车辆状态同步 =================

    /** 如果任务已分配车辆，使用状态转移服务智能更新状态 */
    private void updateVehicleStateIfAssigned(Assignment assignment) {
        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null && stateTransitionService instanceof StateTransitionServiceImpl impl) {
            try {
                impl.updateVehicleStateWithContext(vehicle);
                logger.debug("任务[{}]变更，车辆[{}]状态已同步更新", assignment.getId(), vehicle.getLicensePlate());
            } catch (Exception e) {
                logger.warn("同步车辆[{}]状态失败: {}", vehicle.getLicensePlate(), e.getMessage());
            }
        }
    }

    /** 强制将车辆状态设为空闲（用于任务结束/取消） */
    private void forceVehicleToIdleIfAssigned(Assignment assignment) {
        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null) {
            vehicle.setPreviousStatus(vehicle.getCurrentStatus());
            vehicle.setCurrentStatus(VehicleStatus.IDLE);
            vehicle.setStatusStartTime(LocalDateTime.now());
            // 如果 VehicleService 有 update 方法，可调用；否则直接保存
            vehicleService.updateVehicleStatus(vehicle.getId(), VehicleStatus.IDLE);
            logger.info("任务[{}]结束，车辆[{}]强制设为IDLE", assignment.getId(), vehicle.getLicensePlate());
        }
    }

    // ================= 原有辅助方法（保持不变） =================

    private Assignment findAssignmentById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到ID为 " + id + " 的任务分配"));
    }

    private void validateAssignmentRequest(AssignmentRequestDTO requestDTO) {
        if (requestDTO.getStatus() == null) {
            throw new IllegalArgumentException("任务状态不能为空");
        }
        // TODO: 其他业务校验
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
        // TODO: 实现与 Vehicle / Driver / Route / ShipmentItem 的实际绑定
    }

    private AssignmentResponseDTO convertToDTO(Assignment assignment) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setId(assignment.getId());
        dto.setStatus(assignment.getStatus());
        dto.setCurrentActionIndex(assignment.getCurrentActionIndex());
        dto.setStartTime(assignment.getStartTime());
        dto.setEndTime(assignment.getEndTime());
        dto.setActionLine(assignment.getActionLine());
        dto.setShipmentItemsCount(assignment.getShipmentItems().size());

        if (assignment.getAssignedVehicle() != null) {
            dto.setVehicleId(assignment.getAssignedVehicle().getId());
            dto.setVehicleInfo(assignment.getAssignedVehicle().getLicensePlate());
        }
        if (assignment.getAssignedDriver() != null) {
            dto.setDriverId(assignment.getAssignedDriver().getId());
            dto.setDriverInfo(assignment.getAssignedDriver().getDriverName());
        }
        if (assignment.getRoute() != null) {
            dto.setRouteId(assignment.getRoute().getId());
            dto.setRouteInfo(assignment.getRoute().getName());
        }

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