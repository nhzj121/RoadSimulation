package org.example.roadsimulation.optimizer.multi.persist;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.NodeGene;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.TransportMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MultiOrderAssignmentMaterializer {

    private final AssignmentRepository assignmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final VehicleRepository vehicleRepository;
    private final TransportMetricsService transportMetricsService;

    @Autowired
    public MultiOrderAssignmentMaterializer(
            AssignmentRepository assignmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            VehicleRepository vehicleRepository,
            TransportMetricsService transportMetricsService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.vehicleRepository = vehicleRepository;
        this.transportMetricsService = transportMetricsService;
    }

    /**
     * 将 GA 最终方案落库为 Assignment。
     *
     * 规则：
     * 1. 每辆有任务的车生成一个 Assignment。
     * 2. 一个 Assignment 包含多个 ShipmentItem。
     * 3. 一个 Assignment 包含完整有序 AssignmentNode 链。
     * 4. 未分配运单保持 NOT_ASSIGNED，不落 Assignment。
     */
    @Transactional
    public List<Assignment> materialize(
            MultiOrderSolution solution,
            List<ShipmentItem> allPendingItems,
            List<Vehicle> vehicles
    ) {
        if (solution == null) {
            throw new IllegalArgumentException("solution 不能为空");
        }

        Map<Long, ShipmentItem> itemMap = toManagedItemMap(allPendingItems);
        Map<Long, Vehicle> vehicleMap = toManagedVehicleMap(vehicles);

        List<Assignment> createdAssignments = new ArrayList<>();

        if (solution.getVehicleRoutes() == null) {
            return createdAssignments;
        }

        for (VehicleRouteGene routeGene : solution.getVehicleRoutes()) {
            if (routeGene == null
                    || routeGene.getVehicleId() == null
                    || routeGene.getNodes() == null
                    || routeGene.getNodes().isEmpty()) {
                continue;
            }

            Vehicle vehicle = vehicleMap.get(routeGene.getVehicleId());
            if (vehicle == null) {
                continue;
            }

            Set<Long> servedItemIds = extractServedItemIds(routeGene);
            if (servedItemIds.isEmpty()) {
                continue;
            }

            Assignment assignment = new Assignment();
            assignment.setAssignedVehicle(vehicle);
            assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
            assignment.setCreatedTime(LocalDateTime.now());
            assignment.setUpdatedTime(LocalDateTime.now());
            assignment.setUpdatedBy("MultiOrderGA");
            assignment.setCurrentActionIndex(0);

            POI origin = resolveFirstPoi(routeGene, itemMap);
            POI dest = resolveLastPoi(routeGene, itemMap);

            if (origin != null) {
                assignment.setOriginPOI(origin);
            }

            if (dest != null) {
                assignment.setDestPOI(dest);
            }

            // 绑定 ShipmentItems
            for (Long itemId : servedItemIds) {
                ShipmentItem item = itemMap.get(itemId);
                if (item == null) {
                    continue;
                }

                assignment.addShipmentItem(item);
                item.setStatus(ShipmentItem.ShipmentItemStatus.ASSIGNED);
            }

            // 创建 AssignmentNode 链
            List<AssignmentNode> nodes = buildAssignmentNodes(
                    assignment,
                    routeGene,
                    itemMap
            );

            for (AssignmentNode node : nodes) {
                assignment.addNode(node);
            }

            // 同步 actionLine，兼容旧的前端/推进逻辑
            assignment.setActionLine(extractPoiIds(nodes));

            Assignment saved = assignmentRepository.save(assignment);

            // 显式保存 item，确保状态和 assignment_id 更新
            for (ShipmentItem item : saved.getShipmentItems()) {
                shipmentItemRepository.save(item);
            }

            boolean routeReady = transportMetricsService.rebuildMetricsForAssignmentStrict(saved.getId());
            if (!routeReady) {
                rollbackAssignmentAllocation(saved, "Gaode route planning failed during materialization");
                continue;
            }

            vehicle.transitionToStatus(Vehicle.VehicleStatus.ORDER_DRIVING, LocalDateTime.now(), Duration.ZERO);
            vehicle.setUpdatedBy("MultiOrderGA");
            vehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(vehicle);

            createdAssignments.add(saved);
        }

        // 未分配项保持 NOT_ASSIGNED
        if (solution.getUnassignedShipmentItemIds() != null) {
            for (Long itemId : solution.getUnassignedShipmentItemIds()) {
                ShipmentItem item = itemMap.get(itemId);
                if (item != null) {
                    item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
                    shipmentItemRepository.save(item);
                }
            }
        }

        return createdAssignments;
    }

    private void rollbackAssignmentAllocation(Assignment assignment, String reason) {
        if (assignment == null) {
            return;
        }

        Set<ShipmentItem> items = assignment.getShipmentItems() == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(assignment.getShipmentItems());
        for (ShipmentItem item : items) {
            item.setAssignment(null);
            item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            shipmentItemRepository.save(item);
        }

        assignment.setStatus(Assignment.AssignmentStatus.FAILED);
        assignment.setAssignedVehicle(null);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignment.setUpdatedBy(reason);
        assignmentRepository.save(assignment);
    }

    private List<AssignmentNode> buildAssignmentNodes(
            Assignment assignment,
            VehicleRouteGene routeGene,
            Map<Long, ShipmentItem> itemMap
    ) {
        List<AssignmentNode> nodes = new ArrayList<>();

        int index = 0;

        for (NodeGene gene : routeGene.getNodes()) {
            if (gene == null || gene.getActionType() == null) {
                continue;
            }

            ShipmentItem item = gene.getShipmentItemId() != null
                    ? itemMap.get(gene.getShipmentItemId())
                    : null;

            AssignmentNode node = new AssignmentNode();
            node.setAssignment(assignment);
            node.setSequenceIndex(index++);
            node.setActionType(gene.getActionType());
            node.setShipmentItem(item);
            node.setCompleted(false);

            POI poi = resolveNodePoi(gene, item);
            node.setPoi(poi);

            if (gene.getActionType() == AssignmentNode.NodeActionType.LOAD) {
                node.setWeightDelta(item != null && item.getWeight() != null ? item.getWeight() : 0.0);
                node.setVolumeDelta(item != null && item.getVolume() != null ? item.getVolume() : 0.0);
            } else if (gene.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
                node.setWeightDelta(item != null && item.getWeight() != null ? -item.getWeight() : 0.0);
                node.setVolumeDelta(item != null && item.getVolume() != null ? -item.getVolume() : 0.0);
            } else {
                node.setWeightDelta(0.0);
                node.setVolumeDelta(0.0);
            }

            nodes.add(node);
        }

        return nodes;
    }

    private POI resolveNodePoi(NodeGene gene, ShipmentItem item) {
        if (item == null || item.getShipment() == null || gene.getActionType() == null) {
            return null;
        }

        if (gene.getActionType() == AssignmentNode.NodeActionType.LOAD) {
            return item.getShipment().getOriginPOI();
        }

        if (gene.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
            return item.getShipment().getDestPOI();
        }

        return null;
    }

    private POI resolveFirstPoi(
            VehicleRouteGene routeGene,
            Map<Long, ShipmentItem> itemMap
    ) {
        if (routeGene.getNodes() == null || routeGene.getNodes().isEmpty()) {
            return null;
        }

        for (NodeGene gene : routeGene.getNodes()) {
            ShipmentItem item = gene.getShipmentItemId() != null
                    ? itemMap.get(gene.getShipmentItemId())
                    : null;

            POI poi = resolveNodePoi(gene, item);
            if (poi != null) {
                return poi;
            }
        }

        return null;
    }

    private POI resolveLastPoi(
            VehicleRouteGene routeGene,
            Map<Long, ShipmentItem> itemMap
    ) {
        if (routeGene.getNodes() == null || routeGene.getNodes().isEmpty()) {
            return null;
        }

        List<NodeGene> nodes = routeGene.getNodes();

        for (int i = nodes.size() - 1; i >= 0; i--) {
            NodeGene gene = nodes.get(i);

            ShipmentItem item = gene.getShipmentItemId() != null
                    ? itemMap.get(gene.getShipmentItemId())
                    : null;

            POI poi = resolveNodePoi(gene, item);
            if (poi != null) {
                return poi;
            }
        }

        return null;
    }

    private Set<Long> extractServedItemIds(VehicleRouteGene routeGene) {
        Set<Long> ids = new LinkedHashSet<>();

        if (routeGene == null || routeGene.getNodes() == null) {
            return ids;
        }

        for (NodeGene node : routeGene.getNodes()) {
            if (node != null && node.getShipmentItemId() != null) {
                ids.add(node.getShipmentItemId());
            }
        }

        return ids;
    }

    private List<Long> extractPoiIds(List<AssignmentNode> nodes) {
        List<Long> poiIds = new ArrayList<>();

        for (AssignmentNode node : nodes) {
            if (node.getPoi() != null && node.getPoi().getId() != null) {
                poiIds.add(node.getPoi().getId());
            }
        }

        return poiIds;
    }

    private Map<Long, ShipmentItem> toManagedItemMap(List<ShipmentItem> allPendingItems) {
        Map<Long, ShipmentItem> map = new LinkedHashMap<>();

        if (allPendingItems == null) {
            return map;
        }

        for (ShipmentItem item : allPendingItems) {
            if (item == null || item.getId() == null) {
                continue;
            }

            ShipmentItem managed = shipmentItemRepository.findById(item.getId())
                    .orElse(item);

            map.put(managed.getId(), managed);
        }

        return map;
    }

    private Map<Long, Vehicle> toManagedVehicleMap(List<Vehicle> vehicles) {
        Map<Long, Vehicle> map = new LinkedHashMap<>();

        if (vehicles == null) {
            return map;
        }

        for (Vehicle vehicle : vehicles) {
            if (vehicle == null || vehicle.getId() == null) {
                continue;
            }

            Vehicle managed = vehicleRepository.findById(vehicle.getId())
                    .orElse(vehicle);

            map.put(managed.getId(), managed);
        }

        return map;
    }
}
