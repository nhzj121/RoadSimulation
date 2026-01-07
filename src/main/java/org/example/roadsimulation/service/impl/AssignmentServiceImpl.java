package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
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
    private POIRepository poiRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    // 关键：注入车辆状态转移服务（实现类）
    @Autowired
    private StateTransitionService stateTransitionService;

    @Autowired
    private DataInitializer dataInitializer;

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

    public List<AssignmentBriefDTO> getActiveAssignments() {
        return dataInitializer.getActiveAssignments();
    }

    public List<AssignmentBriefDTO> getNewAssignments() {
        return dataInitializer.getNewAssignmentsForDrawing();
    }

    public AssignmentDTO getAssignmentDetail(Long assignmentId) {
        try {
            System.out.println("获取Assignment详情: " + assignmentId);

            // 1. 获取Assignment实体
            Assignment assignment = assignmentRepository.findById(assignmentId)
                    .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentId));

            // 2. 获取车辆信息
            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle == null) {
                throw new RuntimeException("Assignment " + assignmentId + " has no assigned vehicle");
            }

            // 3. 获取路线信息
            Route route = assignment.getRoute();
            if (route == null) {
                throw new RuntimeException("Assignment " + assignmentId + " has no route");
            }

            // 4. 获取POI信息（起点和终点）
            POI startPOI = poiRepository.findById(route.getStartPOI().getId())
                    .orElseThrow(() -> new RuntimeException("Start POI not found: " + route.getStartPOI().getId()));
            POI endPOI = poiRepository.findById(route.getEndPOI().getId())
                    .orElseThrow(() -> new RuntimeException("End POI not found: " + route.getEndPOI().getId()));

            // 5. 获取ShipmentItem集合
            Set<ShipmentItem> shipmentItems = assignment.getShipmentItems();
            List<ShipmentItemDTO> shipmentItemDTOs = new ArrayList<>();

            // 6. 获取关联的Shipment（通过第一个ShipmentItem）
            Shipment shipment = null;
            if (shipmentItems != null && !shipmentItems.isEmpty()) {
                for (ShipmentItem item : shipmentItems) {
                    // 转换为DTO
                    ShipmentItemDTO itemDTO = convertToShipmentItemDTO(item);
                    shipmentItemDTOs.add(itemDTO);

                    // 获取Shipment（第一次循环时获取）
                    if (shipment == null && item.getShipment() != null) {
                        shipment = shipmentRepository.findById(item.getShipment().getId())
                                .orElse(null);
                    }
                }
            }

            // 7. 构建完整的AssignmentDTO
            AssignmentDTO assignmentDTO = new AssignmentDTO();

            // Assignment基础信息
            assignmentDTO.setId(assignment.getId());
            assignmentDTO.setStatus(assignment.getStatus() != null ?
                    assignment.getStatus().toString() : "UNKNOWN");
            assignmentDTO.setCreatedTime(assignment.getCreatedTime());
            assignmentDTO.setUpdatedTime(assignment.getUpdatedTime());
            assignmentDTO.setStartTime(assignment.getStartTime());
            assignmentDTO.setEndTime(assignment.getEndTime());
            assignmentDTO.setCurrentActionIndex(assignment.getCurrentActionIndex());

            // 车辆信息
            assignmentDTO.setVehicle(convertToVehicleDTO(vehicle));

            // 路线信息
            assignmentDTO.setRoute(convertToRouteDTO(route, startPOI, endPOI));

            // 货物清单信息
            assignmentDTO.setShipmentItems(shipmentItemDTOs);

            // 货物统计信息
            calculateGoodsSummary(assignmentDTO, shipmentItems);

            // 运单信息
            if (shipment != null) {
                assignmentDTO.setShipmentRefNo(shipment.getRefNo());
                assignmentDTO.setTotalWeight(shipment.getTotalWeight());
                assignmentDTO.setTotalVolume(shipment.getTotalVolume());
            }

            // 进度信息
            calculateProgressInfo(assignmentDTO, assignment);

            return assignmentDTO;

        } catch (Exception e) {
            System.err.println("获取Assignment详情失败: " + e.getMessage());
            throw new RuntimeException("Failed to get assignment detail", e);
        }
    }

    /**
     * 将Vehicle转换为VehicleDTO
     */
    private VehicleDTO convertToVehicleDTO(Vehicle vehicle) {
        VehicleDTO dto = new VehicleDTO();

        // 基础信息
        dto.setId(vehicle.getId());
        dto.setLicensePlate(vehicle.getLicensePlate());
        dto.setBrand(vehicle.getBrand());
        dto.setModelType(vehicle.getModelType());
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());
        dto.setCurrentLoad(vehicle.getCurrentLoad());
        dto.setSuitableGoods(vehicle.getSuitableGoods());

        // 状态信息
        if (vehicle.getCurrentStatus() != null) {
            dto.setCurrentStatus(vehicle.getCurrentStatus().toString());
        }
        if (vehicle.getPreviousStatus() != null) {
            dto.setPreviousStatus(vehicle.getPreviousStatus().toString());
        }

        dto.setStatusStartTime(vehicle.getStatusStartTime());
        dto.setStatusDurationSeconds(vehicle.getStatusDurationSeconds());

        // 位置信息
        if (vehicle.getCurrentPOI() != null) {
            dto.setCurrentPOIId(vehicle.getCurrentPOI().getId());
            dto.setCurrentPOIName(vehicle.getCurrentPOI().getName());
        }

        dto.setCurrentLongitude(vehicle.getCurrentLongitude());
        dto.setCurrentLatitude(vehicle.getCurrentLatitude());

        // 驾驶员信息
        dto.setDriverName(vehicle.getDriverName());

        // 当前任务信息
        dto.setCurrentAssignmentId(vehicle.getCurrentAssignment() != null ?
                vehicle.getCurrentAssignment().getId() : null);
        dto.setHasActiveAssignment(vehicle.getCurrentAssignment() != null);

        // 元数据
        dto.setCreatedTime(vehicle.getCreatedTime());
        dto.setUpdatedTime(vehicle.getUpdatedTime());
        dto.setUpdatedBy(vehicle.getUpdatedBy());

        // 状态显示
        Map<String, String> statusConfig = getVehicleStatusConfig(vehicle);
        dto.setStatusText(statusConfig.get("text"));
        dto.setStatusColor(statusConfig.get("color"));

        return dto;
    }

    /**
     * 将Route转换为RouteDTO（包含POI信息）
     */
    private RouteDTO convertToRouteDTO(Route route, POI startPOI, POI endPOI) {
        RouteDTO dto = new RouteDTO();

        // 路线基础信息
        dto.setId(route.getId());
        dto.setRouteCode(route.getRouteCode());
        dto.setName(route.getName());
        dto.setDistance(route.getDistance());
        dto.setEstimatedTime(route.getEstimatedTime());
        dto.setRouteType(route.getRouteType());
        dto.setStatus(route.getStatus() != null ? route.getStatus().toString() : null);
        dto.setDescription(route.getDescription());

        // 起点信息
        dto.setStartPOIId(startPOI.getId());
        dto.setStartPOIName(startPOI.getName());
        dto.setStartLng(startPOI.getLongitude());
        dto.setStartLat(startPOI.getLatitude());
        dto.setStartPOIType(startPOI.getPoiType().toString());

        // 终点信息
        dto.setEndPOIId(endPOI.getId());
        dto.setEndPOIName(endPOI.getName());
        dto.setEndLng(endPOI.getLongitude());
        dto.setEndLat(endPOI.getLatitude());
        dto.setEndPOIType(endPOI.getPoiType().toString());

        // 成本信息
        dto.setTollCost(route.getTollCost());
        dto.setFuelConsumption(route.getFuelConsumption());

        // 元数据
        dto.setCreatedTime(route.getCreatedTime());
        dto.setUpdatedTime(route.getUpdatedTime());

        return dto;
    }

    /**
     * 将ShipmentItem转换为ShipmentItemDTO
     */
    private ShipmentItemDTO convertToShipmentItemDTO(ShipmentItem item) {
        ShipmentItemDTO dto = new ShipmentItemDTO();

        // 基础信息
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setSku(item.getSku());
        dto.setQty(item.getQty());
        dto.setWeight(item.getWeight());
        dto.setVolume(item.getVolume());

        // 关联信息
        if (item.getShipment() != null) {
            dto.setShipmentId(item.getShipment().getId());
            dto.setShipmentRefNo(item.getShipment().getRefNo());
        }

        if (item.getGoods() != null) {
            dto.setGoodsId(item.getGoods().getId());
            dto.setGoodsName(item.getGoods().getName());
        }

        dto.setAssignmentId(item.getAssignment() != null ? item.getAssignment().getId() : null);

        // 元数据
        dto.setCreatedTime(item.getCreatedTime());
        dto.setUpdatedTime(item.getUpdatedTime());

        return dto;
    }

    /**
     * 计算货物统计信息
     */
    private void calculateGoodsSummary(AssignmentDTO assignmentDTO, Set<ShipmentItem> shipmentItems) {
        if (shipmentItems == null || shipmentItems.isEmpty()) {
            return;
        }

        // 统计总数量和货物名称
        int totalQuantity = 0;
        String goodsName = "";

        for (ShipmentItem item : shipmentItems) {
            totalQuantity += item.getQty() != null ? item.getQty() : 0;
            if (!goodsName.isEmpty() && item.getName() != null) {
                goodsName = item.getName();
            }
        }

        assignmentDTO.setGoodsName(goodsName);
        assignmentDTO.setTotalQuantity(totalQuantity);
    }

    /**
     * 计算进度信息
     */
    private void calculateProgressInfo(AssignmentDTO assignmentDTO, Assignment assignment) {
        // 计算进度百分比（基于action index）
        if (assignment.getCurrentActionIndex() != null) {
            List<Long> actionLine = assignment.getActionLine();
            if (actionLine != null && !actionLine.isEmpty()) {
                double progress = ((double) assignment.getCurrentActionIndex() / actionLine.size()) * 100;
                assignmentDTO.setProgressPercentage(progress);
            }
        }

        // 计算预计剩余时间（简单估算）
        if (assignment.getRoute() != null && assignment.getRoute().getEstimatedTime() != null) {
            double estimatedHours = assignment.getRoute().getEstimatedTime();
            double completedPercentage = assignmentDTO.getProgressPercentage() != null ?
                    assignmentDTO.getProgressPercentage() / 100 : 0;
            double remainingHours = estimatedHours * (1 - completedPercentage);

            assignmentDTO.setEstimatedRemainingTime((long) (remainingHours * 3600)); // 转换为秒
        }
    }

    /**
     * 获取车辆状态显示配置
     */
    private Map<String, String> getVehicleStatusConfig(Vehicle vehicle) {
        Map<String, String> config = new HashMap<>();

        if (vehicle.getCurrentStatus() == null) {
            config.put("text", "未知");
            config.put("color", "#ccc");
            return config;
        }

        switch (vehicle.getCurrentStatus()) {
            case IDLE:
                config.put("text", "空闲");
                config.put("color", "#95a5a6");
                break;
            case ORDER_DRIVING:
                config.put("text", "前往接货");
                config.put("color", "#f39c12");
                break;
            case LOADING:
                config.put("text", "装货中");
                config.put("color", "#f39c12");
                break;
            case TRANSPORT_DRIVING:
                config.put("text", "运输中");
                config.put("color", "#2ecc71");
                break;
            case UNLOADING:
                config.put("text", "卸货中");
                config.put("color", "#f39c12");
                break;
            case WAITING:
                config.put("text", "等待中");
                config.put("color", "#e74c3c");
                break;
            case BREAKDOWN:
                config.put("text", "故障");
                config.put("color", "#e74c3c");
                break;
            default:
                config.put("text", vehicle.getCurrentStatus().toString());
                config.put("color", "#ccc");
        }

        return config;
    }

    /**
     * 批量获取Assignment详情（用于前端轮询）
     */
    @Transactional(readOnly = true)
    public List<AssignmentBriefDTO> getBatchAssignmentDetails(List<Long> assignmentIds) {
        List<AssignmentBriefDTO> result = new ArrayList<>();

        for (Long assignmentId : assignmentIds) {
            try {
                Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
                if (assignment != null) {
                    // 转换为简略DTO
                    AssignmentBriefDTO briefDTO = convertToAssignmentBriefDTO(assignment);
                    result.add(briefDTO);
                }
            } catch (Exception e) {
                System.err.println("转换Assignment " + assignmentId + " 失败: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * 将Assignment转换为AssignmentBriefDTO
     */
    private AssignmentBriefDTO convertToAssignmentBriefDTO(Assignment assignment) {
        AssignmentBriefDTO dto = new AssignmentBriefDTO();

        // 基础信息
        dto.setAssignmentId(assignment.getId());
        dto.setStatus(assignment.getStatus() != null ? assignment.getStatus().toString() : "WAITING");
        dto.setCreatedTime(assignment.getCreatedTime());
        dto.setStartTime(assignment.getStartTime());

        // 车辆信息
        if (assignment.getAssignedVehicle() != null) {
            Vehicle vehicle = assignment.getAssignedVehicle();
            dto.setVehicleId(vehicle.getId());
            dto.setLicensePlate(vehicle.getLicensePlate());
            dto.setVehicleStatus(vehicle.getCurrentStatus() != null ?
                    vehicle.getCurrentStatus().toString() : "IDLE");

            // 新增：车辆起始位置
            if (vehicle.getCurrentPOI() != null) {
                dto.setVehicleStartLng(vehicle.getCurrentPOI().getLongitude());
                dto.setVehicleStartLat(vehicle.getCurrentPOI().getLatitude());
            } else if (assignment.getRoute() != null && assignment.getRoute().getStartPOI() != null) {
                // 如果车辆没有当前位置，使用路线起点
                POI startPOI = assignment.getRoute().getStartPOI();
                dto.setVehicleStartLng(startPOI.getLongitude());
                dto.setVehicleStartLat(startPOI.getLatitude());
            }
        }

        // 路线信息
        if (assignment.getRoute() != null) {
            dto.setRouteId(assignment.getRoute().getId());
            dto.setRouteName(assignment.getRoute().getName());

            // 起点信息
            if (assignment.getRoute().getStartPOI() != null) {
                POI startPOI = assignment.getRoute().getStartPOI();
                dto.setStartPOIId(startPOI.getId());
                dto.setStartPOIName(startPOI.getName());
                dto.setStartLng(startPOI.getLongitude());
                dto.setStartLat(startPOI.getLatitude());
            }

            // 终点信息
            if (assignment.getRoute().getEndPOI() != null) {
                POI endPOI = assignment.getRoute().getEndPOI();
                dto.setEndPOIId(endPOI.getId());
                dto.setEndPOIName(endPOI.getName());
                dto.setEndLng(endPOI.getLongitude());
                dto.setEndLat(endPOI.getLatitude());
            }
        }

        // 货物信息（从第一个ShipmentItem获取）
        Set<ShipmentItem> items = assignment.getShipmentItems();
        if (items != null && !items.isEmpty()) {
            ShipmentItem firstItem = items.iterator().next();
            dto.setGoodsName(firstItem.getName());
            dto.setQuantity(firstItem.getQty());

            // 运单信息
            if (firstItem.getShipment() != null) {
                dto.setShipmentRefNo(firstItem.getShipment().getRefNo());
            }
        }

        // 兼容字段
        if (assignment.getRoute() != null &&
                assignment.getRoute().getStartPOI() != null &&
                assignment.getRoute().getEndPOI() != null) {

            POI startPOI = assignment.getRoute().getStartPOI();
            POI endPOI = assignment.getRoute().getEndPOI();
            dto.setPairId(generatePoiPairKey(startPOI, endPOI));
        }

        return dto;
    }

    /**
     * 生成POI配对键（辅助方法）
     */
    private String generatePoiPairKey(POI startPOI, POI endPOI) {
        return startPOI.getId() + "_" + endPOI.getId();
    }

    public void markAssignmentAsDrawn(Long assignmentId) {
        dataInitializer.markAssignmentAsDrawn(assignmentId);
    }

    public List<Long> getCompletedAssignments() {
        return dataInitializer.getCompletedAssignments();
    }
}