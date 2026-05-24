package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.NodeGene;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.insertion.RouteSequenceCostEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RouteGeneSelector {

    private static final double EPS = 1e-6;

    private final RouteSequenceCostEstimator routeEstimator;

    @Autowired
    public RouteGeneSelector(RouteSequenceCostEstimator routeEstimator) {
        this.routeEstimator = routeEstimator;
    }

    public Optional<VehicleRouteGene> selectBestNonEmptyRoute(
            MultiOrderSolution parent,
            List<Vehicle> vehicles,
            List<ShipmentItem> items
    ) {
        if (parent == null || parent.getVehicleRoutes() == null) {
            return Optional.empty();
        }

        Map<Long, Vehicle> vehicleMap = toVehicleMap(vehicles);
        Map<Long, ShipmentItem> itemMap = toItemMap(items);

        VehicleRouteGene bestRoute = null;
        double bestScore = Double.MAX_VALUE;

        for (VehicleRouteGene route : parent.getVehicleRoutes()) {
            if (route == null || route.getVehicleId() == null
                    || route.getNodes() == null || route.getNodes().isEmpty()) {
                continue;
            }

            int servedCount = countServedItems(route);
            if (servedCount <= 0) {
                continue;
            }

            Vehicle vehicle = vehicleMap.get(route.getVehicleId());
            if (vehicle == null) {
                continue;
            }

            List<AssignmentNode> nodes = toTempNodes(route, itemMap);
            double distance = routeEstimator.estimateTotalDistanceKm(vehicle, nodes);

            double score = distance / Math.max(servedCount, EPS);

            if (score < bestScore) {
                bestScore = score;
                bestRoute = route;
            }
        }

        return bestRoute == null
                ? Optional.empty()
                : Optional.of(new VehicleRouteGene(bestRoute));
    }

    private int countServedItems(VehicleRouteGene route) {
        Set<Long> ids = new HashSet<>();
        for (NodeGene node : route.getNodes()) {
            if (node.getShipmentItemId() != null) {
                ids.add(node.getShipmentItemId());
            }
        }
        return ids.size();
    }

    private List<AssignmentNode> toTempNodes(
            VehicleRouteGene route,
            Map<Long, ShipmentItem> itemMap
    ) {
        List<AssignmentNode> nodes = new ArrayList<>();
        int index = 0;

        for (NodeGene gene : route.getNodes()) {
            AssignmentNode node = new AssignmentNode();
            node.setSequenceIndex(index++);
            node.setActionType(gene.getActionType());

            ShipmentItem item = gene.getShipmentItemId() != null
                    ? itemMap.get(gene.getShipmentItemId())
                    : null;

            node.setShipmentItem(item);
            node.setPoi(resolvePoi(gene, item));

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

    private POI resolvePoi(NodeGene gene, ShipmentItem item) {
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

    private Map<Long, Vehicle> toVehicleMap(List<Vehicle> vehicles) {
        Map<Long, Vehicle> map = new HashMap<>();
        if (vehicles == null) {
            return map;
        }

        for (Vehicle vehicle : vehicles) {
            if (vehicle != null && vehicle.getId() != null) {
                map.put(vehicle.getId(), vehicle);
            }
        }
        return map;
    }

    private Map<Long, ShipmentItem> toItemMap(List<ShipmentItem> items) {
        Map<Long, ShipmentItem> map = new HashMap<>();
        if (items == null) {
            return map;
        }

        for (ShipmentItem item : items) {
            if (item != null && item.getId() != null) {
                map.put(item.getId(), item);
            }
        }
        return map;
    }
}