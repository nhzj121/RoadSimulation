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
import java.util.*;
import java.util.stream.Collectors;

/**
 * AssignmentService 的实现类
 *
 * 主要功能：
 * - 任务分配（Assignment）的增删改查
 * - 状态管理（开始、完成、取消等）
 * - 批量操作（批量创建、批量更新状态）
 * - 任务统计与超期查询
 *
 * 使用 Spring Data JPA 操作数据库，结合事务管理确保数据一致性。
 */
@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private VehicleService vehicleService; // 车辆服务（假设存在）

    @Autowired
    private DriverService driverService; // 驾驶员服务（假设存在）

    @Autowired
    private RouteService routeService; // 路线服务（假设存在）

    @Autowired
    private ShipmentItemService shipmentItemService; // 货物项服务（假设存在）

    /**
     * 创建任务分配
     */
    @Override
    @Transactional
    public AssignmentResponseDTO createAssignment(AssignmentRequestDTO requestDTO) {
        // 1. 参数验证
        validateAssignmentRequest(requestDTO);

        // 2. 创建任务对象
        Assignment assignment = new Assignment();
        updateAssignmentFromDTO(assignment, requestDTO);

        // 3. 处理任务的关联关系（车辆、司机、路线、货物）
        setAssignmentRelationships(assignment, requestDTO);

        // 4. 设置默认值
        if (assignment.getCurrentActionIndex() == null) {
            assignment.setCurrentActionIndex(0);
        }
        if (assignment.getStatus() == null) {
            assignment.setStatus(AssignmentStatus.WAITING);
        }

        // 5. 保存到数据库
        Assignment savedAssignment = assignmentRepository.save(assignment);
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

        // 已完成或已取消的任务不可修改
        if (assignment.isCompleted() || assignment.isCancelled()) {
            throw new IllegalStateException("已完成或已取消的任务不能修改");
        }

        // 更新属性与关联关系
        updateAssignmentFromDTO(assignment, requestDTO);
        setAssignmentRelationships(assignment, requestDTO);

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return convertToDTO(updatedAssignment);
    }

    /**
     * 删除任务
     */
    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        Assignment assignment = findAssignmentById(id);

        // 进行中的任务不可删除
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

        return convertToDTO(assignmentRepository.save(assignment));
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

        return convertToDTO(assignmentRepository.save(assignment));
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

        return convertToDTO(assignmentRepository.save(assignment));
    }

    /**
     * 切换到下一个动作
     */
    @Override
    @Transactional
    public AssignmentResponseDTO moveToNextAction(Long id) {
        Assignment assignment = findAssignmentById(id);

        if (!assignment.isInProgress()) {
            throw new IllegalStateException("只有进行中的任务可以执行动作切换");
        }

        assignment.moveToNextAction();
        return convertToDTO(assignmentRepository.save(assignment));
    }

    /**
     * 更新任务状态
     */
    @Override
    @Transactional
    public AssignmentResponseDTO updateAssignmentStatus(Long id, AssignmentStatus status) {
        Assignment assignment = findAssignmentById(id);
        assignment.setStatus(status);

        // 状态切换时同步更新时间
        if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.CANCELLED) {
            assignment.setEndTime(LocalDateTime.now());
        }
        if (status == AssignmentStatus.IN_PROGRESS && assignment.getStartTime() == null) {
            assignment.setStartTime(LocalDateTime.now());
        }

        return convertToDTO(assignmentRepository.save(assignment));
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
        return assignmentRepository.saveAll(assignments)
                .stream()
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
        assignmentRepository.saveAll(assignments);
    }

    // ================= 辅助方法 ================= //

    /** 根据 ID 查找任务，若不存在则抛出异常 */
    private Assignment findAssignmentById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到ID为 " + id + " 的任务分配"));
    }

    /** 校验任务请求 */
    private void validateAssignmentRequest(AssignmentRequestDTO requestDTO) {
        if (requestDTO.getStatus() == null) {
            throw new IllegalArgumentException("任务状态不能为空");
        }
        // TODO: 其他业务校验
    }

    /** DTO -> 实体更新 */
    private void updateAssignmentFromDTO(Assignment assignment, AssignmentRequestDTO dto) {
        assignment.setStatus(dto.getStatus());
        assignment.setCurrentActionIndex(dto.getCurrentActionIndex());
        assignment.setStartTime(dto.getStartTime());
        assignment.setEndTime(dto.getEndTime());
        if (dto.getActionLine() != null) {
            assignment.setActionLine(dto.getActionLine());
        }
    }

    /** 设置任务的关联关系 */
    private void setAssignmentRelationships(Assignment assignment, AssignmentRequestDTO dto) {
        // TODO: 实现与 Vehicle / Driver / Route / ShipmentItem 的实际绑定
    }

    /** 实体 -> DTO 转换 */
    private AssignmentResponseDTO convertToDTO(Assignment assignment) {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setId(assignment.getId());
        dto.setStatus(assignment.getStatus());
        dto.setCurrentActionIndex(assignment.getCurrentActionIndex());
        dto.setStartTime(assignment.getStartTime());
        dto.setEndTime(assignment.getEndTime());
        dto.setActionLine(assignment.getActionLine());
        dto.setShipmentItemsCount(assignment.getShipmentItems().size());

        // 设置车辆信息
        if (assignment.getAssignedVehicle() != null) {
            dto.setVehicleId(assignment.getAssignedVehicle().getId());
            dto.setVehicleInfo(assignment.getAssignedVehicle().getLicensePlate());
        }
        // 设置司机信息
        if (assignment.getAssignedDriver() != null) {
            dto.setDriverId(assignment.getAssignedDriver().getId());
            dto.setDriverInfo(assignment.getAssignedDriver().getName());
        }
        // 设置路线信息
        if (assignment.getRoute() != null) {
            dto.setRouteId(assignment.getRoute().getId());
            dto.setRouteInfo(assignment.getRoute().getName());
        }

        // 计算持续时间
        if (assignment.getStartTime() != null && assignment.getEndTime() != null) {
            Duration duration = Duration.between(assignment.getStartTime(), assignment.getEndTime());
            dto.setDuration(formatDuration(duration));
        }

        return dto;
    }

    /** 格式化任务时长 */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        return String.format("%d小时%d分钟", hours, minutes);
    }
}
