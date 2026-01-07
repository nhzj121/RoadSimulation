// SimulationPairService.java
package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.dto.POIPairDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SimulationPairService {

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    /**
     * 从活跃配对中获取POI对
     */
    public List<POIPairDTO> getCurrentPOIPairs() {
        return dataInitializer.getCurrentPOIPairs();
    }

    /**
     * 获取新增的POI配对
     */
    public List<POIPairDTO> getNewPOIPairs() {
        return dataInitializer.getNewPOIPairs();
    }

    /**
     * 标记配对为已绘制
     */
    public void markPairAsDrawn(String pairId) {
        dataInitializer.markPairAsDrawn(pairId);
    }

    /**
     * 获取已完成运输的配对ID
     */
    public List<String> getCompletedPairIds() {
        // 这里可以扩展为从数据库查询已完成运输的配对
        // 目前暂时返回空列表，后续可以完善
        return new ArrayList<>();
    }

    /**
     * 获取POI配对并填充完整的运输任务信息
     */
    public POIPairDTO enrichPOIPairWithTransportInfo(POIPairDTO basicPair) {
        try {
            // 1. 根据配对ID查找对应的Assignment
            // pairId格式是 "startPOIId_endPOIId"
            String[] ids = basicPair.getPairId().split("_");
            Long startPOIId = Long.parseLong(ids[0]);
            Long endPOIId = Long.parseLong(ids[1]);

            // 2. 查找对应的Assignment（通过起点和终点POI）
            // ToDo 完善Assignment和POI之间的关系
            List<Route> routes = routeRepository.findByStartPOIIdAndEndPOIId(startPOIId,endPOIId);
            List<Assignment> assignments = null;
            for(Route route : routes){
                List<Assignment> suitableAssignments = assignmentRepository.findByRouteId(route.getId());
                for(Assignment assignment : suitableAssignments){
                    assignments.add(assignment);
                }
            }

            if (!assignments.isEmpty()) {
                Assignment assignment = assignments.get(0); // 假设一个配对对应一个任务

                // 填充Assignment信息
                basicPair.setAssignmentId(assignment.getId());
                basicPair.setAssignmentStatus(assignment.getStatus().toString());
                basicPair.setAssignmentCurrentActionIndex(assignment.getCurrentActionIndex());
                basicPair.setAssignmentCreatedTime(assignment.getCreatedTime());
                basicPair.setAssignmentStartTime(assignment.getStartTime());
                basicPair.setAssignmentEndTime(assignment.getEndTime());

                // 3. 获取Vehicle信息
                Vehicle vehicle = assignment.getAssignedVehicle();
                if (vehicle != null) {
                    basicPair.setVehicleId(vehicle.getId());
                    basicPair.setVehicleLicensePlate(vehicle.getLicensePlate());
                    basicPair.setVehicleBrand(vehicle.getBrand());
                    basicPair.setVehicleModelType(vehicle.getModelType());
                    basicPair.setVehicleCurrentLoad(vehicle.getCurrentLoad());
                    basicPair.setVehicleMaxLoadCapacity(vehicle.getMaxLoadCapacity());
                    basicPair.setVehicleStatus(vehicle.getCurrentStatus() != null ?
                            vehicle.getCurrentStatus().toString() : null);
                    basicPair.setVehicleLongitude(vehicle.getCurrentLongitude());
                    basicPair.setVehicleLatitude(vehicle.getCurrentLatitude());
                }

                // 4. 获取Shipment信息（通过ShipmentItem）
                Set<ShipmentItem> shipmentItems = assignment.getShipmentItems();
                if (shipmentItems != null && !shipmentItems.isEmpty()) {
                    ShipmentItem shipmentItem = shipmentItems.iterator().next();

                    // 填充ShipmentItem信息
                    basicPair.setShipmentItemId(shipmentItem.getId());
                    basicPair.setShipmentItemName(shipmentItem.getName());
                    basicPair.setShipmentItemQuantity(shipmentItem.getQty());
                    basicPair.setShipmentItemSku(shipmentItem.getSku());
                    basicPair.setShipmentItemTotalWeight(shipmentItem.getWeight() * shipmentItem.getQty());
                    basicPair.setShipmentItemTotalVolume(shipmentItem.getVolume() * shipmentItem.getQty());

                    // 获取Shipment信息
                    Shipment shipment = shipmentItem.getShipment();
                    if (shipment != null) {
                        basicPair.setShipmentId(shipment.getId());
                        basicPair.setShipmentRefNo(shipment.getRefNo());
                        basicPair.setShipmentTotalWeight(shipment.getTotalWeight());
                        basicPair.setShipmentTotalVolume(shipment.getTotalVolume());
                        basicPair.setShipmentStatus(shipment.getStatus() != null ?
                                shipment.getStatus().toString() : null);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("填充POI配对运输信息失败: " + e);
        }

        return basicPair;
    }

}