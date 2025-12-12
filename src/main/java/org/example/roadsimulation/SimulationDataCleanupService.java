package org.example.roadsimulation;

import org.example.roadsimulation.entity.Enrollment;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.RouteRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

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
    private PlatformTransactionManager transactionManager;

    /**
     * 清理所有模拟数据
     */
    @Transactional
    public void cleanupAllSimulationData() {
        System.out.println("开始清理所有模拟数据...");

        long startTime = System.currentTimeMillis();

        try {
            // 删除顺序：子表 -> 父表
            // 1. ShipmentItem（最底层）
            long itemCount = shipmentItemRepository.count();
            shipmentItemRepository.deleteAll();
            System.out.println("已删除 " + itemCount + " 条ShipmentItem记录");

            // 2. Shipment
            long shipmentCount = shipmentRepository.count();
            shipmentRepository.deleteAll();
            System.out.println("已删除 " + shipmentCount + " 条Shipment记录");

            // 3. Enrollment
            long enrollmentCount = enrollmentRepository.count();
            enrollmentRepository.deleteAll();
            System.out.println("已删除 " + enrollmentCount + " 条Enrollment记录");

            // 4. Route（可选，根据需求）
            long routeCount = routeRepository.count();
            // 如果只想删除自己生成的Route，可以添加过滤条件
            // routeRepository.deleteAll(); 
            System.out.println("Route记录数: " + routeCount + "（保留不删除）");

            long endTime = System.currentTimeMillis();
            System.out.println("模拟数据清理完成，耗时 " + (endTime - startTime) + "ms");

        } catch (Exception e) {
            System.err.println("清理模拟数据时出错: " + e.getMessage());
            throw new RuntimeException("清理模拟数据失败", e);
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

            System.out.println(String.format(
                    "清理完成：ShipmentItem=%d, Shipment=%d, Enrollment=%d",
                    deletedItems, deletedShipments, deletedEnrollments
            ));

        } catch (Exception e) {
            System.err.println("清理水泥模拟数据时出错: " + e.getMessage());
            throw e;
        }
    }
}