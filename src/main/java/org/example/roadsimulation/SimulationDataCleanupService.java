package org.example.roadsimulation;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import jakarta.persistence.EntityManager;
import org.example.roadsimulation.service.TransportLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

// SimulationDataCleanupService.java
@Component
public class SimulationDataCleanupService {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private POIRepository poiRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentNodeRepository assignmentNodeRepository;

    @Autowired
    private AssignmentLegRepository assignmentLegRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransportLifecycleService transportLifecycleService;

    /**
     * 清理所有模拟数据
     */
    @Transactional
    public void cleanupAllSimulationData() {
        System.out.println("开始清理所有模拟数据...");

        long startTime = System.currentTimeMillis();

        try {
            // 删除顺序按实际外键依赖从叶子节点向业务主数据回退：
            // assignment_leg -> assignment_nodes -> shipment_item -> assignment -> shipment -> enrollment
            long assignmentLegCount = assignmentLegRepository.count();
            assignmentLegRepository.deleteAllInBatch();
            assignmentLegRepository.flush();
            System.out.println("Deleted " + assignmentLegCount + " assignment_leg records");
            clearPersistenceContext();

            long assignmentNodeCount = assignmentNodeRepository.count();
            assignmentNodeRepository.deleteAllInBatch();
            assignmentNodeRepository.flush();
            System.out.println("已删除 " + assignmentNodeCount + " 条AssignmentNode记录");
            clearPersistenceContext();

            long itemCount = shipmentItemRepository.count();
            shipmentItemRepository.deleteAllInBatch();
            shipmentItemRepository.flush();
            System.out.println("已删除 " + itemCount + " 条ShipmentItem记录");
            clearPersistenceContext();

            long assignmentCount = assignmentRepository.count();
            assignmentRepository.deleteAllInBatch();
            assignmentRepository.flush();
            System.out.println("已删除 " + assignmentCount + " 条Assignment记录");
            clearPersistenceContext();

            clearShipmentUpstreamRelationsIfPresent();

            long shipmentCount = shipmentRepository.count();
            shipmentRepository.deleteAllInBatch();
            shipmentRepository.flush();
            System.out.println("已删除 " + shipmentCount + " 条Shipment记录");
            clearPersistenceContext();

            long enrollmentCount = enrollmentRepository.count();
            enrollmentRepository.deleteAllInBatch();
            enrollmentRepository.flush();
            System.out.println("已删除 " + enrollmentCount + " 条Enrollment记录");
            clearPersistenceContext();

            long endTime = System.currentTimeMillis();
            System.out.println("模拟数据清理完成，耗时 " + (endTime - startTime) + "ms");

        } catch (Exception e) {
            System.err.println("清理模拟数据时出错: " + e.getMessage());
            throw new RuntimeException("清理模拟数据失败", e);
        }
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private void clearShipmentUpstreamRelationsIfPresent() {
        try {
            int deleted = entityManager
                    .createNativeQuery("DELETE FROM shipment_upstream_relations")
                    .executeUpdate();
            System.out.println("已删除 " + deleted + " 条shipment_upstream_relations记录");
            clearPersistenceContext();
        } catch (Exception e) {
            System.out.println("shipment_upstream_relations无需清理或不存在: " + e.getMessage());
            entityManager.clear();
        }
    }

    /**
     * 只清理特定货物的模拟数据
     */
    @Transactional
    public void cleanupCementSimulationData() {
        System.out.println("开始清理水泥相关模拟数据...");

        try {
            int deletedItems = 0;
            int deletedShipments = 0;
            int deletedEnrollments = 0;

            // 1. 删除水泥相关的ShipmentItem
            List<ShipmentItem> allItems = shipmentItemRepository.findAll();
            for (ShipmentItem item : allItems) {
                if (item.getSku() != null && item.getSku().contains("CEMENT")) {
                    shipmentItemRepository.delete(item);
                    deletedItems++;
                }
            }

            // 2. 删除水泥相关的Shipment
            List<Shipment> allShipments = shipmentRepository.findAll();
            for (Shipment shipment : allShipments) {
                // 检查是否包含水泥货物
                boolean hasCement = shipment.getItems().stream()
                        .anyMatch(item -> item.getSku() != null && item.getSku().contains("CEMENT"));

                if (hasCement) {
                    shipmentRepository.delete(shipment);
                    deletedShipments++;
                }
            }

            // 3. 删除水泥相关的Enrollment
            List<Enrollment> allEnrollments = enrollmentRepository.findAll();
            for (Enrollment enrollment : allEnrollments) {
                if (enrollment.getGoods() != null && enrollment.getGoods().getSku().equals("CEMENT")) {
                    enrollmentRepository.delete(enrollment);
                    deletedEnrollments++;
                }
            }

            System.out.printf(
                    "清理完成：ShipmentItem=%d, Shipment=%d, Enrollment=%d%n",
                    deletedItems, deletedShipments, deletedEnrollments
            );

        } catch (Exception e) {
            System.err.println("清理水泥模拟数据时出错: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 重置所有车辆到指定POI
     */
    @Transactional
    public void resetAllVehiclesToPOI(Long poiId) {
        System.out.println("开始重置所有车辆到POI ID: " + poiId + "...");

        try {
            POI targetPOI = poiRepository.findById(poiId)
                    .orElseThrow(() -> new RuntimeException("未找到POI ID为 " + poiId + " 的POI"));

            List<Vehicle> allVehicles = vehicleRepository.findAll();
            int resetCount = 0;

            for (Vehicle vehicle : allVehicles) {
                try {
                    resetSingleVehicleToPOI(vehicle, targetPOI);

                    vehicleRepository.save(vehicle);
                    resetCount++;

                    System.out.println("已重置车辆: " + vehicle.getLicensePlate() +
                            " 到POI: " + targetPOI.getName());

                } catch (Exception e) {
                    System.err.println("重置车辆 " + vehicle.getLicensePlate() + " 时出错: " + e.getMessage());
                }
            }

            System.out.println("车辆重置完成，共重置 " + resetCount + " 辆车辆到POI ID: " + poiId);

        } catch (Exception e) {
            System.err.println("重置车辆到POI时出错: " + e.getMessage());
            throw new RuntimeException("重置车辆失败", e);
        }
    }

    /**
     * 重置所有车辆到随机仓库或配送中心。
     */
    @Transactional
    public void resetAllVehiclesToRandomInitializationPOIs() {
        System.out.println("开始随机重置所有车辆到仓库或配送中心...");

        try {
            List<POI> initializationPOIs = getVehicleInitializationPOIs();
            if (initializationPOIs.isEmpty()) {
                throw new IllegalStateException("没有可用的仓库或配送中心POI，无法重置车辆位置");
            }

            List<Vehicle> allVehicles = vehicleRepository.findAll();
            int resetCount = 0;

            for (Vehicle vehicle : allVehicles) {
                try {
                    POI targetPOI = selectRandomPOI(initializationPOIs);
                    resetSingleVehicleToPOI(vehicle, targetPOI);

                    vehicleRepository.save(vehicle);
                    resetCount++;

                    System.out.println("已随机重置车辆: " + vehicle.getLicensePlate() +
                            " 到POI: " + targetPOI.getName() + " (" + targetPOI.getPoiType() + ")");

                } catch (Exception e) {
                    System.err.println("随机重置车辆 " + vehicle.getLicensePlate() + " 时出错: " + e.getMessage());
                }
            }

            System.out.println("车辆随机重置完成，共重置 " + resetCount + " 辆车辆");

        } catch (Exception e) {
            System.err.println("随机重置车辆位置时出错: " + e.getMessage());
            throw new RuntimeException("随机重置车辆失败", e);
        }
    }

    private void resetSingleVehicleToPOI(Vehicle vehicle, POI targetPOI) {
        List<Assignment> vehicleAssignments = new ArrayList<>(vehicle.getAssignments());
        for (Assignment assignment : vehicleAssignments) {
            transportLifecycleService.cancelAssignment(
                    assignment,
                    "Vehicle reset",
                    LocalDateTime.now(),
                    "SimulationDataCleanupService"
            );
        }

        vehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, LocalDateTime.now(), Duration.ZERO);
        vehicle.setPreviousStatus(null);
        vehicle.setLoopCount(0);

        vehicle.setCurrentPOI(targetPOI);
        if (targetPOI.getLongitude() != null && targetPOI.getLatitude() != null) {
            vehicle.setCurrentLongitude(targetPOI.getLongitude());
            vehicle.setCurrentLatitude(targetPOI.getLatitude());
        }

        vehicle.setCurrentLoad(0.0);
        vehicle.setUpdatedBy("System - Vehicle Reset");
        vehicle.setUpdatedTime(LocalDateTime.now());
    }

    private List<POI> getVehicleInitializationPOIs() {
        List<POI> candidates = new ArrayList<>();
        candidates.addAll(poiRepository.findByPoiType(POI.POIType.WAREHOUSE));
        candidates.addAll(poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER));

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getLongitude() != null && poi.getLatitude() != null)
                .toList();
    }

    private POI selectRandomPOI(List<POI> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("没有可用的仓库或配送中心POI");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
