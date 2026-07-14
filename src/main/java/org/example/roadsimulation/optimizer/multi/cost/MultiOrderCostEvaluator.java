package org.example.roadsimulation.optimizer.multi.cost;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.NodeGene;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.insertion.RouteSequenceCostEstimator;
import org.example.roadsimulation.optimizer.node.AssignmentNodeSequenceValidator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 多运单启发式算法候选解评价器。
 *
 * 特点：
 * 1. 不污染全局 CostEntity。
 * 2. 对候选解进行 deltaCost 风格评价。
 * 3. 使用归一化后的软目标组合。
 * 4. 未分配运单采用中等惩罚，并随等待时间增长。
 */
@Component
public class MultiOrderCostEvaluator {

    private static final double EPS = 1e-6;

    private final AssignmentNodeSequenceValidator validator;
    private final RouteSequenceCostEstimator routeEstimator;

    public MultiOrderCostEvaluator(
            AssignmentNodeSequenceValidator validator,
            RouteSequenceCostEstimator routeEstimator
    ) {
        this.validator = validator;
        this.routeEstimator = routeEstimator;
    }

    public double evaluate(
            MultiOrderSolution solution,
            List<ShipmentItem> allPendingItems,
            List<Vehicle> vehicles,
            CostNormalizationConfig config
    ) {
        if (config == null) {
            config = new CostNormalizationConfig();
        }

        EvaluationContext context = buildContext(allPendingItems, vehicles);

        double hardPenalty = 0.0;
        double totalDistance = 0.0;
        double totalEmptyDistance = 0.0;
        double totalCapacityWaste = 0.0;
        double lowUtilPenaltyRaw = 0.0;

        int usedVehicleCount = 0;
        boolean feasible = true;

        Set<Long> seenLoadedItems = new HashSet<>();
        Set<Long> seenUnloadedItems = new HashSet<>();

        if (solution == null || solution.getVehicleRoutes() == null) {
            hardPenalty += config.getHardConstraintPenalty();
            feasible = false;
        } else {
            for (VehicleRouteGene routeGene : solution.getVehicleRoutes()) {
                if (routeGene == null || routeGene.getVehicleId() == null) {
                    continue;
                }

                Vehicle vehicle = context.vehicleMap.get(routeGene.getVehicleId());
                if (vehicle == null) {
                    hardPenalty += config.getHardConstraintPenalty();
                    feasible = false;
                    continue;
                }

                List<AssignmentNode> nodes = toAssignmentNodes(routeGene, context);
                if (nodes.isEmpty()) {
                    continue;
                }

                usedVehicleCount++;

                AssignmentNodeSequenceValidator.ValidationResult validation =
                        validator.validate(nodes, vehicle);

                if (!validation.isValid()) {
                    hardPenalty += config.getHardConstraintPenalty();
                    feasible = false;
                }

                NodeCompletenessResult completeness =
                        checkNodeCompleteness(nodes, context.itemMap);

                hardPenalty += completeness.penalty;
                if (!completeness.valid) {
                    feasible = false;
                }

                for (Long itemId : completeness.loadedItems) {
                    if (!seenLoadedItems.add(itemId)) {
                        hardPenalty += config.getDuplicateAssignmentPenalty();
                        feasible = false;
                    }
                }

                for (Long itemId : completeness.unloadedItems) {
                    if (!seenUnloadedItems.add(itemId)) {
                        hardPenalty += config.getDuplicateAssignmentPenalty();
                        feasible = false;
                    }
                }

                RouteMetrics metrics = computeRouteMetrics(vehicle, nodes);

                totalDistance += metrics.getTotalDistanceKm();
                totalEmptyDistance += metrics.getEmptyDistanceKm();
                totalCapacityWaste += metrics.getCapacityWasteTonKm();

                double utilGap = Math.max(
                        0.0,
                        config.getIdealUtilization() - metrics.getEffectiveUtilization()
                );
                lowUtilPenaltyRaw += utilGap;
            }
        }

        // 已分配集合必须 LOAD/UNLOAD 一致
        if (!seenLoadedItems.equals(seenUnloadedItems)) {
            hardPenalty += config.getMissingLoadUnloadPenalty();
            feasible = false;
        }

        // 计算未分配集合：显式 unassigned + 没有被服务到的 pendingItems
        Set<Long> unassigned = new HashSet<>();
        if (solution != null && solution.getUnassignedShipmentItemIds() != null) {
            unassigned.addAll(solution.getUnassignedShipmentItemIds());
        }

        for (ShipmentItem item : allPendingItems) {
            if (item == null || item.getId() == null) {
                continue;
            }
            if (!seenLoadedItems.contains(item.getId())) {
                unassigned.add(item.getId());
            }
        }

        // 如果某个 item 同时被服务又出现在 unassigned，需要处罚并从 unassigned 中移除，避免双算
        Iterator<Long> iterator = unassigned.iterator();
        while (iterator.hasNext()) {
            Long itemId = iterator.next();
            if (seenLoadedItems.contains(itemId)) {
                hardPenalty += config.getDuplicateAssignmentPenalty();
                feasible = false;
                iterator.remove();
            }
        }

        double unassignedPenalty = computeUnassignedPenalty(
                unassigned,
                context.itemMap,
                config
        );

        double normalizedDistance =
                normalize(totalDistance, config.getDistanceNormKm());

        double normalizedEmptyDistance =
                normalize(totalEmptyDistance, config.getEmptyDistanceNormKm());

        double normalizedCapacityWaste =
                normalize(totalCapacityWaste, config.getCapacityWasteNormTonKm());

        double normalizedVehicleCount =
                normalize(usedVehicleCount, config.getVehicleCountNorm());

        double normalizedLowUtil =
                normalize(lowUtilPenaltyRaw, config.getLowUtilNorm());

        double softCost =
                config.getDistanceWeight() * normalizedDistance
                        + config.getEmptyDistanceWeight() * normalizedEmptyDistance
                        + config.getCapacityWasteWeight() * normalizedCapacityWaste
                        + config.getVehicleCountWeight() * normalizedVehicleCount
                        + config.getLowUtilizationWeight() * normalizedLowUtil;

        double totalCost = hardPenalty + softCost + unassignedPenalty;

        solution.setCost(totalCost);
        solution.setFeasible(feasible && hardPenalty < EPS);

        return totalCost;
    }

    private EvaluationContext buildContext(
            List<ShipmentItem> items,
            List<Vehicle> vehicles
    ) {
        EvaluationContext context = new EvaluationContext();

        if (items != null) {
            for (ShipmentItem item : items) {
                if (item != null && item.getId() != null) {
                    context.itemMap.put(item.getId(), item);
                }
            }
        }

        if (vehicles != null) {
            for (Vehicle vehicle : vehicles) {
                if (vehicle != null && vehicle.getId() != null) {
                    context.vehicleMap.put(vehicle.getId(), vehicle);
                }
            }
        }

        return context;
    }

    private List<AssignmentNode> toAssignmentNodes(
            VehicleRouteGene routeGene,
            EvaluationContext context
    ) {
        List<AssignmentNode> nodes = new ArrayList<>();

        if (routeGene.getNodes() == null) {
            return nodes;
        }

        int index = 0;
        for (NodeGene gene : routeGene.getNodes()) {
            if (gene == null) {
                continue;
            }

            AssignmentNode node = new AssignmentNode();
            node.setSequenceIndex(index++);
            node.setActionType(gene.getActionType());

            ShipmentItem item = gene.getShipmentItemId() != null
                    ? context.itemMap.get(gene.getShipmentItemId())
                    : null;

            node.setShipmentItem(item);

            POI poi = resolvePoi(gene, item);
            node.setPoi(poi);

            if (gene.getActionType() == AssignmentNode.NodeActionType.LOAD) {
                node.setWeightDelta(item != null ? safe(item.getWeight()) : 0.0);
                node.setVolumeDelta(item != null ? safe(item.getVolume()) : 0.0);
            } else if (gene.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
                node.setWeightDelta(item != null ? -safe(item.getWeight()) : 0.0);
                node.setVolumeDelta(item != null ? -safe(item.getVolume()) : 0.0);
            } else {
                node.setWeightDelta(0.0);
                node.setVolumeDelta(0.0);
            }

            nodes.add(node);
        }

        return nodes;
    }

    /**
     * 优先使用 gene 中的 poiId 对应的 POI。
     * 但为了减少依赖 POIRepository，这里第一版直接从 ShipmentItem 的 Shipment 推导。
     *
     * 如果后续 NodeGene 中允许 PASS_BY 或自定义 poiId，则需要接入 POIRepository。
     */
    private POI resolvePoi(NodeGene gene, ShipmentItem item) {
        if (item == null || item.getShipment() == null || gene.getActionType() == null) {
            return null;
        }

        if (gene.getActionType() == AssignmentNode.NodeActionType.LOAD) {
            // 根据你项目实际 Shipment getter 调整
            return item.getShipment().getOriginPOI();
        }

        if (gene.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
            return item.getShipment().getDestPOI();
        }

        return null;
    }

    private NodeCompletenessResult checkNodeCompleteness(
            List<AssignmentNode> nodes,
            Map<Long, ShipmentItem> itemMap
    ) {
        NodeCompletenessResult result = new NodeCompletenessResult();

        Map<Long, Integer> loadCount = new HashMap<>();
        Map<Long, Integer> unloadCount = new HashMap<>();

        for (AssignmentNode node : nodes) {
            if (node.getActionType() == AssignmentNode.NodeActionType.PASS_BY) {
                continue;
            }

            ShipmentItem item = node.getShipmentItem();
            if (item == null || item.getId() == null || !itemMap.containsKey(item.getId())) {
                result.valid = false;
                result.penalty += 500_000.0;
                continue;
            }

            Long itemId = item.getId();

            if (node.getActionType() == AssignmentNode.NodeActionType.LOAD) {
                loadCount.merge(itemId, 1, Integer::sum);
            } else if (node.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
                unloadCount.merge(itemId, 1, Integer::sum);
            }
        }

        Set<Long> union = new HashSet<>();
        union.addAll(loadCount.keySet());
        union.addAll(unloadCount.keySet());

        for (Long itemId : union) {
            int loads = loadCount.getOrDefault(itemId, 0);
            int unloads = unloadCount.getOrDefault(itemId, 0);

            if (loads != 1 || unloads != 1) {
                result.valid = false;
                result.penalty += 500_000.0;
            }

            if (loads == 1) {
                result.loadedItems.add(itemId);
            }

            if (unloads == 1) {
                result.unloadedItems.add(itemId);
            }
        }

        return result;
    }

    private RouteMetrics computeRouteMetrics(
            Vehicle vehicle,
            List<AssignmentNode> nodes
    ) {
        RouteMetrics metrics = new RouteMetrics();

        double maxLoad = vehicle.getMaxLoadCapacity() != null
                ? vehicle.getMaxLoadCapacity()
                : 0.0;

        double maxVolume = vehicle.getCargoVolume() != null
                ? vehicle.getCargoVolume()
                : 0.0;

        metrics.setMaxLoadTon(maxLoad);
        metrics.setMaxVolumeM3(maxVolume);

        if (nodes == null || nodes.isEmpty()) {
            return metrics;
        }

        List<AssignmentNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));

        double totalDistance = routeEstimator.estimateTotalDistanceKm(vehicle, ordered);
        metrics.setTotalDistanceKm(totalDistance);

        // 空驶：车辆当前位置到第一个装卸节点
        double emptyDistance = 0.0;
        if (!ordered.isEmpty() && ordered.get(0).getPoi() != null) {
            emptyDistance = routeEstimator.distanceFromVehicleToPoi(vehicle, ordered.get(0).getPoi());
        }
        metrics.setEmptyDistanceKm(emptyDistance);

        double currentWeight = 0.0;
        double currentVolume = 0.0;
        double maxWeightOnRoute = 0.0;
        double maxVolumeOnRoute = 0.0;

        double loadedTonKm = 0.0;

        // 从车辆当前位置到第一个节点，当前货重为 0，不贡献实际运能
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

        metrics.setLoadedTonKm(loadedTonKm);
        metrics.setTheoreticalTonKm(theoreticalTonKm);
        metrics.setCapacityWasteTonKm(capacityWaste);
        metrics.setMaxWeightOnRoute(maxWeightOnRoute);
        metrics.setMaxVolumeOnRoute(maxVolumeOnRoute);
        metrics.setFinalWeight(currentWeight);
        metrics.setFinalVolume(currentVolume);

        metrics.setWeightUtilization(maxLoad > EPS ? maxWeightOnRoute / maxLoad : 0.0);
        metrics.setVolumeUtilization(maxVolume > EPS ? maxVolumeOnRoute / maxVolume : 0.0);
        metrics.setEffectiveUtilization(Math.max(
                metrics.getWeightUtilization(),
                metrics.getVolumeUtilization()
        ));

        metrics.setServedItemCount(countDistinctItems(ordered));

        return metrics;
    }

    private int countDistinctItems(List<AssignmentNode> nodes) {
        Set<Long> ids = new HashSet<>();
        for (AssignmentNode node : nodes) {
            if (node.getShipmentItem() != null && node.getShipmentItem().getId() != null) {
                ids.add(node.getShipmentItem().getId());
            }
        }
        return ids.size();
    }

    private double computeUnassignedPenalty(
            Set<Long> unassignedIds,
            Map<Long, ShipmentItem> itemMap,
            CostNormalizationConfig config
    ) {
        if (unassignedIds == null || unassignedIds.isEmpty()) {
            return 0.0;
        }

        double penalty = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (Long itemId : unassignedIds) {
            ShipmentItem item = itemMap.get(itemId);

            double itemPenalty = config.getUnassignedBasePenalty();

            double waitingHours = estimateWaitingHours(item, now);
            itemPenalty += waitingHours * config.getUnassignedWaitingHourPenalty();

            int priority = estimatePriority(item);
            itemPenalty += priority * config.getUnassignedPriorityPenalty();

            penalty += itemPenalty;
        }

        return penalty;
    }

    /**
     * 第一版等待时间估算。
     *
     */
    private double estimateWaitingHours(ShipmentItem item, LocalDateTime now) {
        if (item == null || item.getShipment() == null) {
            return 0.0;
        }

        try {
            LocalDateTime created = item.getShipment().getCreatedAt();
            if (created == null) {
                return 0.0;
            }
            return Math.max(0.0, Duration.between(created, now).toMinutes() / 60.0);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    /**
     * 第一版优先级估算。
     *
     * 如果 ShipmentItem / Shipment 后续有 priority 字段，替换这里。
     */
    private int estimatePriority(ShipmentItem item) {
        return 0;
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

    private static class EvaluationContext {
        private final Map<Long, ShipmentItem> itemMap = new HashMap<>();
        private final Map<Long, Vehicle> vehicleMap = new HashMap<>();
    }

    private static class NodeCompletenessResult {
        private boolean valid = true;
        private double penalty = 0.0;
        private final Set<Long> loadedItems = new HashSet<>();
        private final Set<Long> unloadedItems = new HashSet<>();
    }
}
