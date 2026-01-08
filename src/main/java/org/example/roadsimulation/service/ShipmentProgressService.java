package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.ActiveShipmentSummaryDTO;
import org.example.roadsimulation.dto.AssignmentBriefDTO;
import org.example.roadsimulation.dto.ShipmentProgressDTO;
import org.example.roadsimulation.dto.ShipmentItemProgressDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运单进度计算服务
 */
@Service
@Transactional
public class ShipmentProgressService {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentProgressService.class);

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private VehicleService vehicleService;

    /**
     * 获取活跃运单列表（包含进度概览）
     * 活跃运单：状态为PLANNED、PICKED_UP、IN_TRANSIT的运单
     */
    @Transactional(readOnly = true)
    public List<ActiveShipmentSummaryDTO> getActiveShipments() {
        logger.info("获取活跃运单列表");

        try {
            // 获取所有运单
            List<Shipment> allShipments = shipmentRepository.findAll();

            // 过滤活跃运单并转换为DTO
            return allShipments.stream()
                    .filter(shipment -> isActiveShipment(shipment))
                    .map(this::convertToActiveShipmentSummaryDTO)
                    .sorted(Comparator.comparing(ActiveShipmentSummaryDTO::getUpdatedAt).reversed())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("获取活跃运单列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 检查是否为活跃运单
     */
    private boolean isActiveShipment(Shipment shipment) {
        Shipment.ShipmentStatus status = shipment.getStatus();
        return status == Shipment.ShipmentStatus.PLANNED ||
                status == Shipment.ShipmentStatus.PICKED_UP ||
                status == Shipment.ShipmentStatus.IN_TRANSIT;
    }

    /**
     * 获取运单的完整进度信息
     */
    @Transactional(readOnly = true)
    public ShipmentProgressDTO getShipmentProgress(Long shipmentId) {
        logger.info("获取运单进度信息，运单ID: {}", shipmentId);

        try {
            // 获取运单
            Shipment shipment = shipmentRepository.findById(shipmentId)
                    .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + shipmentId));

            // 获取运单项列表
            List<ShipmentItem> shipmentItems = shipmentItemRepository.findByShipmentId(shipmentId);

            // 转换为进度DTO
            ShipmentProgressDTO progressDTO = convertToShipmentProgressDTO(shipment, shipmentItems);

            logger.info("运单进度信息获取成功，运单ID: {}, 进度: {}%",
                    shipmentId, progressDTO.getProgressPercentage());

            return progressDTO;

        } catch (Exception e) {
            logger.error("获取运单进度信息失败，运单ID: {}", shipmentId, e);
            throw new RuntimeException("获取运单进度信息失败", e);
        }
    }

    /**
     * 批量获取运单进度信息
     */
    @Transactional(readOnly = true)
    public List<ShipmentProgressDTO> getBatchShipmentProgress(List<Long> shipmentIds) {
        logger.info("批量获取运单进度信息，运单数量: {}", shipmentIds.size());

        List<ShipmentProgressDTO> result = new ArrayList<>();

        for (Long shipmentId : shipmentIds) {
            try {
                ShipmentProgressDTO progress = getShipmentProgress(shipmentId);
                result.add(progress);
            } catch (Exception e) {
                logger.warn("获取运单进度失败，运单ID: {}", shipmentId, e);
            }
        }

        return result;
    }

    /**
     * 更新运单进度（通常在Assignment完成后调用）
     */
    @Transactional
    public void updateShipmentProgress(Long shipmentId) {
        logger.info("更新运单进度，运单ID: {}", shipmentId);

        try {
            // 获取运单和运单项
            Shipment shipment = shipmentRepository.findById(shipmentId)
                    .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + shipmentId));

            List<ShipmentItem> shipmentItems = shipmentItemRepository.findByShipmentId(shipmentId);

            // 计算进度
            int totalItems = shipmentItems.size();
            int completedItems = 0;
            int inProgressItems = 0;
            int waitingItems = 0;

            double completedWeight = 0.0;
            double completedVolume = 0.0;

            // 统计各项状态
            for (ShipmentItem item : shipmentItems) {
                if (item.getAssignment() != null) {
                    Assignment assignment = item.getAssignment();

                    if (assignment.isCompleted()) {
                        completedItems++;
                        completedWeight += item.getWeight() != null ? item.getWeight() : 0;
                        completedVolume += item.getVolume() != null ? item.getVolume() : 0;
                    } else if (assignment.isInProgress()) {
                        inProgressItems++;
                    } else if (assignment.isWaiting() || assignment.isAssigned()) {
                        waitingItems++;
                    }
                } else {
                    waitingItems++; // 未分配任务
                }
            }

            // 更新运单状态
            updateShipmentStatus(shipment, completedItems, totalItems);

            // 保存更新
            shipmentRepository.save(shipment);

            logger.info("运单进度更新完成，运单ID: {}, 完成: {}/{}, 新状态: {}",
                    shipmentId, completedItems, totalItems, shipment.getStatus());

        } catch (Exception e) {
            logger.error("更新运单进度失败，运单ID: {}", shipmentId, e);
            throw new RuntimeException("更新运单进度失败", e);
        }
    }

    /**
     * 根据进度更新运单状态
     */
    private void updateShipmentStatus(Shipment shipment, int completedItems, int totalItems) {
        if (totalItems == 0) return;

        double progress = (double) completedItems / totalItems;

        // 根据进度更新状态
        if (progress >= 1.0) {
            // 所有Items都已完成
            shipment.setStatus(Shipment.ShipmentStatus.DELIVERED);
        } else if (progress > 0) {
            // 至少有一个Item在进行中或已完成
            if (shipment.getStatus() == Shipment.ShipmentStatus.CREATED) {
                shipment.setStatus(Shipment.ShipmentStatus.PLANNED);
            }
            if (shipment.getStatus() == Shipment.ShipmentStatus.PLANNED) {
                shipment.setStatus(Shipment.ShipmentStatus.IN_TRANSIT);
            }
        }

        // 更新时间戳
        shipment.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 将Shipment转换为ActiveShipmentSummaryDTO
     */
    private ActiveShipmentSummaryDTO convertToActiveShipmentSummaryDTO(Shipment shipment) {
        ActiveShipmentSummaryDTO dto = new ActiveShipmentSummaryDTO();

        // 基本信息
        dto.setShipmentId(shipment.getId());
        dto.setRefNo(shipment.getRefNo());
        dto.setCargoType(shipment.getCargoType());
        dto.setStatus(shipment.getStatus().toString());
        dto.setStatusText(dto.getStatusDisplayText());

        // 起点和终点
        if (shipment.getOriginPOI() != null) {
            dto.setOriginPOIName(shipment.getOriginPOI().getName());
        }
        if (shipment.getDestPOI() != null) {
            dto.setDestPOIName(shipment.getDestPOI().getName());
        }

        // 时间信息
        dto.setCreatedAt(shipment.getCreatedAt());
        dto.setUpdatedAt(shipment.getUpdatedAt());
        dto.setLatestActivityTime(shipment.getUpdatedAt());

        // 计算进度
        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
        int totalItems = items.size();
        int completedItems = 0;

        for (ShipmentItem item : items) {
            if (item.getAssignment() != null && item.getAssignment().isCompleted()) {
                completedItems++;
            }
        }

        dto.setTotalItems(totalItems);
        dto.setCompletedItems(completedItems);

        if (totalItems > 0) {
            dto.setProgressPercentage((double) completedItems / totalItems * 100);
        } else {
            dto.setProgressPercentage(0.0);
        }

        return dto;
    }

    /**
     * 将Shipment转换为ShipmentProgressDTO
     */
    private ShipmentProgressDTO convertToShipmentProgressDTO(Shipment shipment, List<ShipmentItem> shipmentItems) {
        ShipmentProgressDTO dto = new ShipmentProgressDTO();

        // 基本信息
        dto.setShipmentId(shipment.getId());
        dto.setRefNo(shipment.getRefNo());
        dto.setCargoType(shipment.getCargoType());
        dto.setTotalWeight(shipment.getTotalWeight());
        dto.setTotalVolume(shipment.getTotalVolume());
        dto.setStatus(shipment.getStatus().toString());
        dto.setStatusText(dto.getStatusDisplayText());

        // 起点和终点
        if (shipment.getOriginPOI() != null) {
            dto.setOriginPOIId(shipment.getOriginPOI().getId());
            dto.setOriginPOIName(shipment.getOriginPOI().getName());
        }
        if (shipment.getDestPOI() != null) {
            dto.setDestPOIId(shipment.getDestPOI().getId());
            dto.setDestPOIName(shipment.getDestPOI().getName());
        }

        // 时间信息
        dto.setCreatedAt(shipment.getCreatedAt());
        dto.setUpdatedAt(shipment.getUpdatedAt());

        // 计算进度统计
        int totalItems = shipmentItems.size();
        int completedItems = 0;
        int inProgressItems = 0;
        int waitingItems = 0;

        double completedWeight = 0.0;
        double completedVolume = 0.0;

        List<ShipmentItemProgressDTO> itemProgressList = new ArrayList<>();

        for (ShipmentItem item : shipmentItems) {
            ShipmentItemProgressDTO itemProgress = convertToShipmentItemProgressDTO(item);
            itemProgressList.add(itemProgress);

            // 统计状态
            if (item.getAssignment() != null) {
                Assignment assignment = item.getAssignment();

                if (assignment.isCompleted()) {
                    completedItems++;
                    completedWeight += item.getWeight() != null ? item.getWeight() : 0;
                    completedVolume += item.getVolume() != null ? item.getVolume() : 0;
                } else if (assignment.isInProgress()) {
                    inProgressItems++;
                } else if (assignment.isWaiting() || assignment.isAssigned()) {
                    waitingItems++;
                }
            } else {
                waitingItems++;
            }
        }

        // 设置进度信息
        dto.setTotalItems(totalItems);
        dto.setCompletedItems(completedItems);
        dto.setInProgressItems(inProgressItems);
        dto.setWaitingItems(waitingItems);
        dto.setCompletedWeight(completedWeight);
        dto.setCompletedVolume(completedVolume);
        dto.setItems(itemProgressList);

        // 计算百分比
        dto.calculateProgress();

        // 获取关联的Assignments和Vehicles
        Set<Long> assignmentIds = new HashSet<>();
        Set<Long> vehicleIds = new HashSet<>();

        for (ShipmentItem item : shipmentItems) {
            if (item.getAssignment() != null) {
                assignmentIds.add(item.getAssignment().getId());

                Assignment assignment = item.getAssignment();
                if (assignment.getAssignedVehicle() != null) {
                    vehicleIds.add(assignment.getAssignedVehicle().getId());
                }
            }
        }

        // 获取Assignment简要信息
        if (!assignmentIds.isEmpty()) {
            try {
                List<AssignmentBriefDTO> assignmentBriefs = assignmentService.getAssignmentBriefsByIds(
                        new ArrayList<>(assignmentIds)
                );
                dto.setAssignments(assignmentBriefs);
            } catch (Exception e) {
                logger.warn("获取Assignment简要信息失败", e);
            }
        }

        return dto;
    }

    /**
     * 将ShipmentItem转换为ShipmentItemProgressDTO
     */
    private ShipmentItemProgressDTO convertToShipmentItemProgressDTO(ShipmentItem item) {
        ShipmentItemProgressDTO dto = new ShipmentItemProgressDTO();

        // 基本信息
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setSku(item.getSku());
        dto.setQty(item.getQty());
        dto.setWeight(item.getWeight());
        dto.setVolume(item.getVolume());

        // 确定运单项状态
        String status = "NOT_ASSIGNED";
        LocalDateTime loadedTime = null;
        LocalDateTime deliveredTime = null;

        if (item.getAssignment() != null) {
            Assignment assignment = item.getAssignment();

            if (assignment.isCompleted()) {
                status = "DELIVERED";
                deliveredTime = assignment.getEndTime();
            } else if (assignment.isInProgress()) {
                // 检查是否已装货
                if (assignment.getCurrentActionIndex() != null && assignment.getCurrentActionIndex() > 0) {
                    status = "IN_TRANSIT";
                } else {
                    status = "ASSIGNED";
                }
            } else if (assignment.isAssigned() || assignment.isWaiting()) {
                status = "ASSIGNED";
            }

            // 记录分配时间
            dto.setAssignedTime(item.getCreatedTime());

            // 设置Assignment信息
            dto.setAssignmentId(assignment.getId());
            dto.setAssignmentStatus(assignment.getStatus().toString());

            // 设置车辆信息
            if (assignment.getAssignedVehicle() != null) {
                Vehicle vehicle = assignment.getAssignedVehicle();
                dto.setVehicleId(vehicle.getId());
                dto.setVehicleLicensePlate(vehicle.getLicensePlate());
                dto.setVehicleStatus(vehicle.getCurrentStatus() != null ?
                        vehicle.getCurrentStatus().toString() : "IDLE");
            }
        }

        dto.setStatus(status);
        dto.setStatusText(dto.getStatusDisplayText());
        dto.setLoadedTime(loadedTime);
        dto.setDeliveredTime(deliveredTime);

        return dto;
    }

    /**
     * 获取所有运单的进度摘要（用于仪表板）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOverallProgressSummary() {
        logger.info("获取所有运单进度摘要");

        Map<String, Object> summary = new HashMap<>();

        try {
            // 获取所有运单
            List<Shipment> allShipments = shipmentRepository.findAll();
            List<ShipmentItem> allItems = shipmentItemRepository.findAll();

            // 统计运单状态
            long totalShipments = allShipments.size();
            long createdShipments = allShipments.stream()
                    .filter(s -> s.getStatus() == Shipment.ShipmentStatus.CREATED)
                    .count();
            long plannedShipments = allShipments.stream()
                    .filter(s -> s.getStatus() == Shipment.ShipmentStatus.PLANNED)
                    .count();
            long inTransitShipments = allShipments.stream()
                    .filter(s -> s.getStatus() == Shipment.ShipmentStatus.IN_TRANSIT)
                    .count();
            long deliveredShipments = allShipments.stream()
                    .filter(s -> s.getStatus() == Shipment.ShipmentStatus.DELIVERED)
                    .count();

            // 统计运单项状态
            long totalItems = allItems.size();
            long notAssignedItems = allItems.stream()
                    .filter(item -> item.getAssignment() == null)
                    .count();
            long assignedItems = allItems.stream()
                    .filter(item -> item.getAssignment() != null &&
                            (item.getAssignment().isWaiting() || item.getAssignment().isAssigned()))
                    .count();
            long inProgressItems = allItems.stream()
                    .filter(item -> item.getAssignment() != null && item.getAssignment().isInProgress())
                    .count();
            long completedItems = allItems.stream()
                    .filter(item -> item.getAssignment() != null && item.getAssignment().isCompleted())
                    .count();

            // 计算总进度
            double overallProgress = totalItems > 0 ?
                    (double) completedItems / totalItems * 100 : 0;

            // 统计总重量和体积
            double totalWeight = allItems.stream()
                    .mapToDouble(item -> item.getWeight() != null ? item.getWeight() : 0)
                    .sum();
            double totalVolume = allItems.stream()
                    .mapToDouble(item -> item.getVolume() != null ? item.getVolume() : 0)
                    .sum();

            double completedWeight = allItems.stream()
                    .filter(item -> item.getAssignment() != null && item.getAssignment().isCompleted())
                    .mapToDouble(item -> item.getWeight() != null ? item.getWeight() : 0)
                    .sum();
            double completedVolume = allItems.stream()
                    .filter(item -> item.getAssignment() != null && item.getAssignment().isCompleted())
                    .mapToDouble(item -> item.getVolume() != null ? item.getVolume() : 0)
                    .sum();

            // 填充摘要信息
            summary.put("totalShipments", totalShipments);
            summary.put("createdShipments", createdShipments);
            summary.put("plannedShipments", plannedShipments);
            summary.put("inTransitShipments", inTransitShipments);
            summary.put("deliveredShipments", deliveredShipments);

            summary.put("totalItems", totalItems);
            summary.put("notAssignedItems", notAssignedItems);
            summary.put("assignedItems", assignedItems);
            summary.put("inProgressItems", inProgressItems);
            summary.put("completedItems", completedItems);

            summary.put("overallProgress", overallProgress);
            summary.put("totalWeight", totalWeight);
            summary.put("totalVolume", totalVolume);
            summary.put("completedWeight", completedWeight);
            summary.put("completedVolume", completedVolume);

            // 计算百分比
            summary.put("weightProgress", totalWeight > 0 ?
                    (completedWeight / totalWeight) * 100 : 0);
            summary.put("volumeProgress", totalVolume > 0 ?
                    (completedVolume / totalVolume) * 100 : 0);

            logger.info("运单进度摘要计算完成，总运单: {}, 总进度: {}%",
                    totalShipments, overallProgress);

        } catch (Exception e) {
            logger.error("计算运单进度摘要失败", e);
            summary.put("error", e.getMessage());
        }

        return summary;
    }
}