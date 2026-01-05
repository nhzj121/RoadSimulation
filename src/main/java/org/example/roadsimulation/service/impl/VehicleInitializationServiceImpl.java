package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.service.VehicleInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class VehicleInitializationServiceImpl implements VehicleInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleInitializationServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final POIRepository poiRepository;

    // 默认POI ID（可以配置化）
    private Long defaultPoiId = null;

    @Autowired
    public VehicleInitializationServiceImpl(
            VehicleRepository vehicleRepository,
            POIRepository poiRepository) {
        this.vehicleRepository = vehicleRepository;
        this.poiRepository = poiRepository;
    }

    @Override
    public void initializeAllVehicleStatus() {
        logger.info("开始初始化所有车辆状态");

        // 1. 获取所有车辆
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        logger.info("需要初始化的车辆总数: {}", allVehicles.size());

        // 2. 获取默认POI（如果没有设置，则使用第一个仓库）
        POI defaultPOI = getDefaultPOI();
        if (defaultPOI == null) {
            logger.error("无法找到默认POI，初始化失败");
            throw new IllegalStateException("没有可用的默认POI，无法初始化车辆状态");
        }

        logger.info("使用默认POI进行初始化: {} (ID: {})", defaultPOI.getName(), defaultPOI.getId());

        // 3. 初始化每辆车
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        for (Vehicle vehicle : allVehicles) {
            try {
                // 检查是否可以初始化
                if (!canInitializeVehicle(vehicle.getId())) {
                    logger.warn("车辆 {} (车牌: {}) 无法初始化，跳过",
                            vehicle.getId(), vehicle.getLicensePlate());
                    skipCount++;
                    continue;
                }

                // 执行初始化
                initializeSingleVehicle(vehicle, defaultPOI);
                successCount++;

                logger.debug("车辆 {} (车牌: {}) 初始化成功",
                        vehicle.getId(), vehicle.getLicensePlate());

            } catch (Exception e) {
                logger.error("车辆 {} (车牌: {}) 初始化失败: {}",
                        vehicle.getId(), vehicle.getLicensePlate(), e.getMessage());
                errorCount++;
            }
        }

        // 4. 保存所有更改
        vehicleRepository.saveAll(allVehicles);

        logger.info("车辆初始化完成: 成功 {} 辆, 跳过 {} 辆, 失败 {} 辆",
                successCount, skipCount, errorCount);
    }

    @Override
    public Vehicle resetVehicleToPOI(Long vehicleId, Long poiId) {
        logger.info("重置车辆 {} 到 POI {}", vehicleId, poiId);

        // 1. 获取车辆
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        // 2. 获取目标POI
        POI targetPOI = poiRepository.findById(poiId)
                .orElseThrow(() -> new RuntimeException("POI不存在，ID: " + poiId));

        // 3. 检查是否可以重置
        if (!canInitializeVehicle(vehicleId)) {
            throw new IllegalStateException("车辆 " + vehicleId + " 当前无法重置");
        }

        // 4. 执行重置
        initializeSingleVehicle(vehicle, targetPOI);

        // 5. 保存并返回
        return vehicleRepository.save(vehicle);
    }

    @Override
    public int batchResetVehiclesToPOI(List<Long> vehicleIds, Long poiId) {
        logger.info("批量重置车辆到 POI {}，车辆数量: {}", poiId, vehicleIds.size());

        // 1. 获取目标POI
        POI targetPOI = poiRepository.findById(poiId)
                .orElseThrow(() -> new RuntimeException("POI不存在，ID: " + poiId));

        // 2. 获取车辆列表
        List<Vehicle> vehicles = vehicleRepository.findAllById(vehicleIds);

        // 3. 检查车辆是否存在
        Set<Long> foundIds = vehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        List<Long> missingIds = vehicleIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            logger.warn("以下车辆ID不存在: {}", missingIds);
        }

        // 4. 批量重置
        int successCount = 0;
        for (Vehicle vehicle : vehicles) {
            try {
                if (canInitializeVehicle(vehicle.getId())) {
                    initializeSingleVehicle(vehicle, targetPOI);
                    successCount++;
                    logger.debug("车辆 {} 重置成功", vehicle.getId());
                } else {
                    logger.warn("车辆 {} 无法重置，跳过", vehicle.getId());
                }
            } catch (Exception e) {
                logger.error("车辆 {} 重置失败: {}", vehicle.getId(), e.getMessage());
            }
        }

        // 5. 批量保存
        if (successCount > 0) {
            vehicleRepository.saveAll(vehicles);
        }

        logger.info("批量重置完成: 成功 {} 辆，总数 {} 辆", successCount, vehicles.size());
        return successCount;
    }

    @Override
    public Vehicle clearVehicleLoad(Long vehicleId) {
        logger.info("清空车辆 {} 的载重", vehicleId);

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        // 检查车辆状态，只有在空闲或运输完成状态下才能清空载重
        if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.TRANSPORT_DRIVING) {
            throw new IllegalStateException("车辆正在运输中，无法清空载重");
        }

        // 清空当前载重
        vehicle.setCurrentLoad(0.0);

        // 更新操作时间
        vehicle.setDriverName(vehicle.getDriverName()); // 触发更新

        logger.info("车辆 {} 载重已清空", vehicleId);
        return vehicleRepository.save(vehicle);
    }

    @Override
    public int batchClearVehicleLoads(List<Long> vehicleIds) {
        logger.info("批量清空车辆载重，车辆数量: {}", vehicleIds.size());

        List<Vehicle> vehicles = vehicleRepository.findAllById(vehicleIds);

        int successCount = 0;
        for (Vehicle vehicle : vehicles) {
            try {
                // 跳过正在运输的车辆
                if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.TRANSPORT_DRIVING) {
                    logger.warn("车辆 {} 正在运输中，跳过清空载重", vehicle.getId());
                    continue;
                }

                vehicle.setCurrentLoad(0.0);
                successCount++;
                logger.debug("车辆 {} 载重已清空", vehicle.getId());
            } catch (Exception e) {
                logger.error("清空车辆 {} 载重失败: {}", vehicle.getId(), e.getMessage());
            }
        }

        if (successCount > 0) {
            vehicleRepository.saveAll(vehicles);
        }

        logger.info("批量清空载重完成: 成功 {} 辆", successCount);
        return successCount;
    }

    @Override
    public Vehicle setVehicleToIdle(Long vehicleId) {
        logger.info("设置车辆 {} 为空闲状态", vehicleId);

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        // 检查是否可以设置为空闲
        if (!canSetToIdle(vehicle)) {
            throw new IllegalStateException("车辆当前状态无法设置为空闲");
        }

        // 设置为空闲状态
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);

        // 如果有进行中的任务，记录日志
        if (vehicle.getCurrentAssignment() != null) {
            logger.warn("车辆 {} 有进行中的任务，但被强制设置为空闲状态", vehicleId);
        }

        logger.info("车辆 {} 已设置为空闲状态", vehicleId);
        return vehicleRepository.save(vehicle);
    }

    @Override
    public int batchSetVehiclesToIdle(List<Long> vehicleIds) {
        logger.info("批量设置车辆为空闲状态，车辆数量: {}", vehicleIds.size());

        List<Vehicle> vehicles = vehicleRepository.findAllById(vehicleIds);

        int successCount = 0;
        for (Vehicle vehicle : vehicles) {
            try {
                if (canSetToIdle(vehicle)) {
                    vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
                    successCount++;
                    logger.debug("车辆 {} 已设置为空闲状态", vehicle.getId());
                } else {
                    logger.warn("车辆 {} 无法设置为空闲状态，跳过", vehicle.getId());
                }
            } catch (Exception e) {
                logger.error("设置车辆 {} 为空闲状态失败: {}", vehicle.getId(), e.getMessage());
            }
        }

        if (successCount > 0) {
            vehicleRepository.saveAll(vehicles);
        }

        logger.info("批量设置空闲状态完成: 成功 {} 辆", successCount);
        return successCount;
    }

    @Override
    public Map<String, Object> getInitializationStats() {
        Map<String, Object> stats = new HashMap<>();

        // 获取所有车辆
        List<Vehicle> allVehicles = vehicleRepository.findAll();

        // 基础统计
        stats.put("totalVehicles", allVehicles.size());

        // 按状态统计
        Map<Vehicle.VehicleStatus, Long> statusCount = allVehicles.stream()
                .collect(Collectors.groupingBy(
                        Vehicle::getCurrentStatus,
                        Collectors.counting()
                ));
        stats.put("statusDistribution", statusCount);

        // 空闲车辆统计
        long idleCount = allVehicles.stream()
                .filter(v -> v.getCurrentStatus() == Vehicle.VehicleStatus.IDLE)
                .count();
        stats.put("idleVehicles", idleCount);

        // 有载重的车辆统计
        long loadedCount = allVehicles.stream()
                .filter(v -> v.getCurrentLoad() != null && v.getCurrentLoad() > 0)
                .count();
        stats.put("loadedVehicles", loadedCount);

        // 有任务分配的车辆统计
        long assignedCount = allVehicles.stream()
                .filter(v -> v.getCurrentAssignment() != null)
                .count();
        stats.put("assignedVehicles", assignedCount);

        // 可初始化车辆统计
        long canInitializeCount = allVehicles.stream()
                .filter(v -> canInitializeVehicle(v.getId()))
                .count();
        stats.put("canInitializeVehicles", canInitializeCount);

        // 默认POI信息
        POI defaultPOI = getDefaultPOI();
        if (defaultPOI != null) {
            Map<String, Object> defaultPoiInfo = new HashMap<>();
            defaultPoiInfo.put("id", defaultPOI.getId());
            defaultPoiInfo.put("name", defaultPOI.getName());
            defaultPoiInfo.put("type", defaultPOI.getPoiType());
            stats.put("defaultPOI", defaultPoiInfo);
        }

        logger.debug("获取初始化统计信息");
        return stats;
    }

    @Override
    public List<Long> getAvailableDefaultPOIs() {
        // 获取所有仓库类型的POI作为候选
        List<POI> warehouses = poiRepository.findByPoiType(POI.POIType.WAREHOUSE);

        // 如果没有仓库，获取配送中心
        if (warehouses.isEmpty()) {
            warehouses = poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER);
        }

        // 返回ID列表
        return warehouses.stream()
                .map(POI::getId)
                .collect(Collectors.toList());
    }

    @Override
    public void setDefaultPOI(Long poiId) {
        // 验证POI是否存在
        if (!poiRepository.existsById(poiId)) {
            throw new RuntimeException("POI不存在，ID: " + poiId);
        }

        this.defaultPoiId = poiId;
        logger.info("设置默认POI为: {}", poiId);
    }

    @Override
    public boolean canInitializeVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        if (vehicle == null) {
            return false;
        }

        // 检查车辆状态
        Vehicle.VehicleStatus status = vehicle.getCurrentStatus();

        // 以下状态不能初始化
        if (status == Vehicle.VehicleStatus.TRANSPORT_DRIVING) {
            logger.debug("车辆 {} 正在运输中，无法初始化", vehicleId);
            return false;
        }

        if (status == Vehicle.VehicleStatus.BREAKDOWN) {
            logger.debug("车辆 {} 处于事故状态，无法初始化", vehicleId);
            return false;
        }

        // 检查是否有进行中的任务
        if (vehicle.getCurrentAssignment() != null) {
            Assignment assignment = vehicle.getCurrentAssignment();
            if (assignment.isInProgress()) {
                logger.debug("车辆 {} 有进行中的任务，无法初始化", vehicleId);
                return false;
            }
        }

        return true;
    }

    @Override
    public Map<String, Object> getVehicleStatusInfo(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        Map<String, Object> info = new HashMap<>();

        // 基本信息
        info.put("id", vehicle.getId());
        info.put("licensePlate", vehicle.getLicensePlate());
        info.put("brand", vehicle.getBrand());
        info.put("modelType", vehicle.getModelType());

        // 状态信息
        info.put("currentStatus", vehicle.getCurrentStatus());
        info.put("canInitialize", canInitializeVehicle(vehicleId));

        // 载重信息
        info.put("maxLoadCapacity", vehicle.getMaxLoadCapacity());
        info.put("currentLoad", vehicle.getCurrentLoad());

        // 位置信息
        if (vehicle.getCurrentPOI() != null) {
            info.put("currentPOI", vehicle.getCurrentPOI().getName());
            info.put("currentPOIId", vehicle.getCurrentPOI().getId());
        }

        // 司机信息
        info.put("driverName", vehicle.getDriverName());

        // 任务信息
        if (vehicle.getCurrentAssignment() != null) {
            Assignment assignment = vehicle.getCurrentAssignment();
            Map<String, Object> assignmentInfo = new HashMap<>();
            assignmentInfo.put("id", assignment.getId());
            assignmentInfo.put("status", assignment.getStatus());
            assignmentInfo.put("isInProgress", assignment.isInProgress());
            info.put("currentAssignment", assignmentInfo);
        }

        // 坐标信息
        info.put("longitude", vehicle.getCurrentLongitude());
        info.put("latitude", vehicle.getCurrentLatitude());

        return info;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取默认POI
     */
    private POI getDefaultPOI() {
        // 如果已设置默认POI，使用它
        if (defaultPoiId != null) {
            return poiRepository.findById(defaultPoiId).orElse(null);
        }

        // 否则获取第一个仓库
        List<POI> warehouses = poiRepository.findByPoiType(POI.POIType.WAREHOUSE);
        if (!warehouses.isEmpty()) {
            defaultPoiId = warehouses.get(0).getId();
            return warehouses.get(0);
        }

        // 如果没有仓库，获取第一个配送中心
        List<POI> distributionCenters = poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER);
        if (!distributionCenters.isEmpty()) {
            defaultPoiId = distributionCenters.get(0).getId();
            return distributionCenters.get(0);
        }

        // 如果没有合适的POI，返回null
        return null;
    }

    /**
     * 初始化单个车辆
     */
    private void initializeSingleVehicle(Vehicle vehicle, POI targetPOI) {
        logger.debug("初始化车辆 {} (车牌: {})", vehicle.getId(), vehicle.getLicensePlate());

        // 1. 设置车辆状态为空闲
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);

        // 2. 设置车辆位置到目标POI
        vehicle.setCurrentPOI(targetPOI);

        // 3. 同步坐标
        if (targetPOI.getLongitude() != null && targetPOI.getLatitude() != null) {
            vehicle.setCurrentLongitude(targetPOI.getLongitude());
            vehicle.setCurrentLatitude(targetPOI.getLatitude());
        } else {
            logger.warn("POI {} 没有坐标信息，车辆坐标未更新", targetPOI.getId());
        }

        // 4. 清空当前载重
        vehicle.setCurrentLoad(0.0);

        // 5. 设置司机信息（如果有驾驶员关联）
        if (!vehicle.getDrivers().isEmpty()) {
            Driver driver = vehicle.getDrivers().iterator().next();
            vehicle.setDriverName(driver.getDriverName());
        } else if (vehicle.getDriverName() == null || vehicle.getDriverName().isEmpty()) {
            vehicle.setDriverName("待分配");
        }

        // 6. 记录初始化时间（可以扩展Vehicle实体添加初始化时间字段）
        logger.debug("车辆 {} 初始化完成", vehicle.getId());
    }

    /**
     * 检查车辆是否可以设置为空闲状态
     */
    private boolean canSetToIdle(Vehicle vehicle) {
        // 已经是空闲状态
        if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.IDLE) {
            return true;
        }

        // 检查是否有进行中的任务
        if (vehicle.getCurrentAssignment() != null) {
            Assignment assignment = vehicle.getCurrentAssignment();
            if (assignment.isInProgress()) {
                logger.warn("车辆 {} 有进行中的任务，不能设置为空闲", vehicle.getId());
                return false;
            }
        }

        // 事故状态的车辆不能设置为空闲
        if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.BREAKDOWN) {
            logger.warn("车辆 {} 处于事故状态，不能设置为空闲", vehicle.getId());
            return false;
        }

        return true;
    }
}