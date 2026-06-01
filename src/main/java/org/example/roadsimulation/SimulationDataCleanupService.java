package org.example.roadsimulation;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
     * 重置所有车辆到指定POI（成都市中心）
     */
    @Transactional
    public void resetAllVehiclesToPOI(Long poiId) {
        System.out.println("开始重置所有车辆到POI ID: " + poiId + " (成都市中心)...");

        try {
            // 获取目标POI（成都市中心）
            POI chengduCenter = poiRepository.findById(poiId)
                    .orElseThrow(() -> new RuntimeException("未找到POI ID为 " + poiId + " 的POI（成都市中心）"));

            // 获取所有车辆
            List<Vehicle> allVehicles = vehicleRepository.findAll();
            int resetCount = 0;

            for (Vehicle vehicle : allVehicles) {
                try {
                    // 1. 解除所有任务的关联
                    List<Assignment> vehicleAssignments = new ArrayList<>(vehicle.getAssignments());
                    for (Assignment assignment : vehicleAssignments) {
                        vehicle.removeAssignment(assignment);
                        assignment.setAssignedVehicle(null);
                        assignmentRepository.save(assignment);
                    }

                    // 2. 重置车辆状态
                    vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
                    vehicle.setPreviousStatus(null);
                    vehicle.setStatusStartTime(LocalDateTime.now());
                    vehicle.setStatusDurationSeconds(0L);
                    vehicle.setLoopCount(0);

                    // 3. 重置位置到成都市中心
                    vehicle.setCurrentPOI(chengduCenter);
                    if (chengduCenter.getLongitude() != null && chengduCenter.getLatitude() != null) {
                        vehicle.setCurrentLongitude(chengduCenter.getLongitude());
                        vehicle.setCurrentLatitude(chengduCenter.getLatitude());
                    }

                    // 4. 重置当前负载
                    vehicle.setCurrentLoad(0.0);

                    // 5. 清除司机关联（如果需要）
                    // vehicle.getDrivers().clear(); // 根据业务需求决定是否清除司机关联

                    // 6. 更新四元组信息
                    vehicle.setUpdatedBy("System - Vehicle Reset");
                    vehicle.setUpdatedTime(LocalDateTime.now());

                    vehicleRepository.save(vehicle);
                    resetCount++;

                    System.out.println("已重置车辆: " + vehicle.getLicensePlate() +
                            " 到POI: " + chengduCenter.getName());

                } catch (Exception e) {
                    System.err.println("重置车辆 " + vehicle.getLicensePlate() + " 时出错: " + e.getMessage());
                }
            }

            System.out.println("车辆重置完成，共重置 " + resetCount + " 辆车辆到成都市中心");

        } catch (Exception e) {
            System.err.println("重置车辆到POI时出错: " + e.getMessage());
            throw new RuntimeException("重置车辆失败", e);
        }
    }

    /**
     * 重置所有车辆到成都市中心（POI ID: 3466）
     */
    @Transactional
    public void resetAllVehiclesToChengduCenter() {
        resetAllVehiclesToPOI(3466L);
    }
}
