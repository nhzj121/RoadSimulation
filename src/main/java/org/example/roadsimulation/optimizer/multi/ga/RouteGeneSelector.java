package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.NodeGene;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig;
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
            List<ShipmentItem> items,
            CostNormalizationConfig costConfig
    ) {
        if (parent == null || parent.getVehicleRoutes() == null) {
            return Optional.empty();
        }

        if (costConfig == null) {
            costConfig = new CostNormalizationConfig();
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
            double routeCost = computeRouteTransportCost(vehicle, nodes, costConfig);
            double score = routeCost / Math.max(servedCount, EPS);

            if (score < bestScore) {
                bestScore = score;
                bestRoute = route;
            }
        }

        return bestRoute == null
                ? Optional.empty()
                : Optional.of(new VehicleRouteGene(bestRoute));
    }

    private double computeRouteTransportCost(
            Vehicle vehicle,
            List<AssignmentNode> nodes,
            CostNormalizationConfig config
    ) {
        if (vehicle == null || nodes == null || nodes.isEmpty()) {
            return 0.0;
        }

        List<AssignmentNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));

        double totalDistance = routeEstimator.estimateTotalDistanceKm(vehicle, ordered);

        double emptyDistance = 0.0;
        if (!ordered.isEmpty() && ordered.get(0).getPoi() != null) {
            emptyDistance = routeEstimator.distanceFromVehicleToPoi(
                    vehicle,
                    ordered.get(0).getPoi()
            );
        }

        double maxLoad = safe(vehicle.getMaxLoadCapacity());
        double maxVolume = safe(vehicle.getCargoVolume());

        double currentWeight = 0.0;
        double currentVolume = 0.0;
        double maxWeightOnRoute = 0.0;
        double maxVolumeOnRoute = 0.0;
        double loadedTonKm = 0.0;

        for (int i = 0; i < ordered.size(); i++) {
            AssignmentNode node = ordered.get(i);

            currentWeight += safe(node.getWeightDelta());
            currentVolume += safe(node.getVolumeDelta());

            maxWeightOnRoute = Math.max(maxWeightOnRoute, currentWeight);
            maxVolumeOnRoute = Math.max(maxVolumeOnRoute, currentVolume);

            if (i < ordered.size() - 1) {
                double segmentKm = routeEstimator.distanceBetweenPois(
                        ordered.get(i).getPoi(),
                        ordered.get(i + 1).getPoi()
                );
                loadedTonKm += Math.max(0.0, currentWeight) * segmentKm;
            }
        }

        double theoreticalTonKm = maxLoad * totalDistance;
        double capacityWaste = Math.max(0.0, theoreticalTonKm - loadedTonKm);

        double weightUtilization = maxLoad > EPS ? maxWeightOnRoute / maxLoad : 0.0;
        double volumeUtilization = maxVolume > EPS ? maxVolumeOnRoute / maxVolume : 0.0;
        double effectiveUtilization = Math.max(weightUtilization, volumeUtilization);
        double lowUtilPenalty = Math.max(0.0, config.getIdealUtilization() - effectiveUtilization);

        return config.getDistanceWeight() * normalize(totalDistance, config.getDistanceNormKm())
                + config.getEmptyDistanceWeight() * normalize(emptyDistance, config.getEmptyDistanceNormKm())
                + config.getCapacityWasteWeight() * normalize(capacityWaste, config.getCapacityWasteNormTonKm())
                + config.getLowUtilizationWeight() * normalize(lowUtilPenalty, config.getLowUtilNorm());
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

    private double normalize(double value, double norm) {
        if (norm <= EPS) {
            return value;
        }
        return value / norm;
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
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
