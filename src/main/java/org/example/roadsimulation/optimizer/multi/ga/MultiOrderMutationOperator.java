package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.NodeGene;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.insertion.FeasibleInsertionService;
import org.example.roadsimulation.optimizer.multi.insertion.InsertionCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 多运单 GA 变异算子。
 *
 * 支持：
 * 1. 单运单 remove-and-reinsert
 * 2. 多运单 destroy-repair
 * 3. 单车路线 route-shake
 *
 * 变异后不直接评价 cost，由 GA 主流程调用 MultiOrderCostEvaluator。
 */
@Component
public class MultiOrderMutationOperator {

    private final FeasibleInsertionService insertionService;

    @Autowired
    public MultiOrderMutationOperator(FeasibleInsertionService insertionService) {
        this.insertionService = insertionService;
    }

    public MultiOrderSolution mutate(
            MultiOrderSolution source,
            List<ShipmentItem> allItems,
            List<Vehicle> vehicles,
            MutationConfig config,
            Random random
    ) {
        if (source == null) {
            throw new IllegalArgumentException("source solution 不能为空");
        }

        if (config == null) {
            config = new MutationConfig();
        }

        if (random == null) {
            random = new Random();
        }

        double r = random.nextDouble();

        double p1 = config.getSingleReinsertProbability();
        double p2 = p1 + config.getDestroyRepairProbability();

        if (r < p1) {
            return mutateSingleReinsert(source, allItems, vehicles, config, random);
        }

        if (r < p2) {
            return mutateDestroyRepair(source, allItems, vehicles, config, random);
        }

        return mutateRouteShake(source, allItems, vehicles, config, random);
    }

    /**
     * 变异一：随机取出一个运单，然后重新插入。
     */
    public MultiOrderSolution mutateSingleReinsert(
            MultiOrderSolution source,
            List<ShipmentItem> allItems,
            List<Vehicle> vehicles,
            MutationConfig config,
            Random random
    ) {
        WorkingSolution working = WorkingSolution.from(source, allItems, vehicles);

        List<Long> assignedItems = working.getAssignedItemIds();
        if (assignedItems.isEmpty()) {
            return new MultiOrderSolution(source);
        }

        Long selectedItemId = assignedItems.get(random.nextInt(assignedItems.size()));

        working.removeItemFromRoutes(selectedItemId);
        working.unassignedItemIds.remove(selectedItemId);

        reinsertItems(
                working,
                Collections.singletonList(selectedItemId),
                config,
                random
        );

        return working.toSolution();
    }

    /**
     * 变异二：随机移除多个运单，然后按贪心修复。
     */
    public MultiOrderSolution mutateDestroyRepair(
            MultiOrderSolution source,
            List<ShipmentItem> allItems,
            List<Vehicle> vehicles,
            MutationConfig config,
            Random random
    ) {
        WorkingSolution working = WorkingSolution.from(source, allItems, vehicles);

        List<Long> assignedItems = working.getAssignedItemIds();
        if (assignedItems.isEmpty()) {
            return new MultiOrderSolution(source);
        }

        Collections.shuffle(assignedItems, random);

        int destroyCount = 1 + random.nextInt(
                Math.max(1, Math.min(config.getMaxDestroyCount(), assignedItems.size()))
        );

        List<Long> removed = new ArrayList<>(assignedItems.subList(0, destroyCount));

        for (Long itemId : removed) {
            working.removeItemFromRoutes(itemId);
            working.unassignedItemIds.remove(itemId);
        }

        // 大件优先重插，更容易提高修复成功率
        removed.sort((a, b) -> {
            ShipmentItem ia = working.itemMap.get(a);
            ShipmentItem ib = working.itemMap.get(b);
            double wa = ia != null && ia.getWeight() != null ? ia.getWeight() : 0.0;
            double wb = ib != null && ib.getWeight() != null ? ib.getWeight() : 0.0;
            return Double.compare(wb, wa);
        });

        reinsertItems(working, removed, config, random);

        return working.toSolution();
    }

    /**
     * 变异三：随机选择一辆车，移除该车上的若干运单，再重插。
     *
     * 这个算子用于打散某条路线局部结构，帮助跳出局部最优。
     */
    public MultiOrderSolution mutateRouteShake(
            MultiOrderSolution source,
            List<ShipmentItem> allItems,
            List<Vehicle> vehicles,
            MutationConfig config,
            Random random
    ) {
        WorkingSolution working = WorkingSolution.from(source, allItems, vehicles);

        List<Long> nonEmptyVehicleIds = new ArrayList<>();
        for (Map.Entry<Long, List<AssignmentNode>> entry : working.vehicleNodes.entrySet()) {
            if (!extractServedItemIds(entry.getValue()).isEmpty()) {
                nonEmptyVehicleIds.add(entry.getKey());
            }
        }

        if (nonEmptyVehicleIds.isEmpty()) {
            return new MultiOrderSolution(source);
        }

        Long selectedVehicleId =
                nonEmptyVehicleIds.get(random.nextInt(nonEmptyVehicleIds.size()));

        List<Long> routeItems =
                new ArrayList<>(extractServedItemIds(working.vehicleNodes.get(selectedVehicleId)));

        if (routeItems.isEmpty()) {
            return new MultiOrderSolution(source);
        }

        Collections.shuffle(routeItems, random);

        int removeCount = 1 + random.nextInt(
                Math.max(1, Math.min(config.getMaxRouteShakeCount(), routeItems.size()))
        );

        List<Long> removed = new ArrayList<>(routeItems.subList(0, removeCount));

        for (Long itemId : removed) {
            working.removeItemFromRoutes(itemId);
            working.unassignedItemIds.remove(itemId);
        }

        reinsertItems(working, removed, config, random);

        return working.toSolution();
    }

    /**
     * 对一批 item 执行合法重插。
     */
    private void reinsertItems(
            WorkingSolution working,
            List<Long> itemIds,
            MutationConfig config,
            Random random
    ) {
        for (Long itemId : itemIds) {
            ShipmentItem item = working.itemMap.get(itemId);
            if (item == null) {
                continue;
            }

            List<InsertionCandidate> allCandidates = new ArrayList<>();

            for (Vehicle vehicle : working.vehicleMap.values()) {
                List<AssignmentNode> currentNodes =
                        working.vehicleNodes.getOrDefault(vehicle.getId(), new ArrayList<>());

                List<InsertionCandidate> candidates =
                        insertionService.findAllFeasibleInsertions(vehicle, currentNodes, item);

                allCandidates.addAll(candidates);
            }

            if (allCandidates.isEmpty()) {
                working.unassignedItemIds.add(itemId);
                continue;
            }

            allCandidates = InsertionThresholdPolicy.filterAcceptable(
                    allCandidates,
                    item,
                    config
            );

            if (allCandidates.isEmpty()) {
                working.unassignedItemIds.add(itemId);
                continue;
            }

            allCandidates.sort(Comparator.comparingDouble(InsertionCandidate::getScore));

            InsertionCandidate selected = chooseCandidate(allCandidates, config, random);

            working.vehicleNodes.put(
                    selected.getVehicle().getId(),
                    selected.getNodesAfterInsertion()
            );

            working.unassignedItemIds.remove(itemId);
        }
    }

    private InsertionCandidate chooseCandidate(
            List<InsertionCandidate> candidates,
            MutationConfig config,
            Random random
    ) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates 不能为空");
        }

        if (random.nextDouble() < config.getBestInsertionProbability()) {
            return candidates.get(0);
        }

        int bound = Math.max(1, Math.min(config.getTopKInsertionChoice(), candidates.size()));
        return candidates.get(random.nextInt(bound));
    }

    private static Set<Long> extractServedItemIds(List<AssignmentNode> nodes) {
        Set<Long> ids = new LinkedHashSet<>();

        if (nodes == null) {
            return ids;
        }

        for (AssignmentNode node : nodes) {
            if (node != null
                    && node.getShipmentItem() != null
                    && node.getShipmentItem().getId() != null) {
                ids.add(node.getShipmentItem().getId());
            }
        }

        return ids;
    }

    /**
     * 内部工作解，用于把 MultiOrderSolution 转成便于操作的 vehicle -> nodes。
     */
    private static class WorkingSolution {

        private final Map<Long, Vehicle> vehicleMap = new LinkedHashMap<>();
        private final Map<Long, ShipmentItem> itemMap = new LinkedHashMap<>();
        private final Map<Long, List<AssignmentNode>> vehicleNodes = new LinkedHashMap<>();
        private final Set<Long> unassignedItemIds = new LinkedHashSet<>();

        static WorkingSolution from(
                MultiOrderSolution solution,
                List<ShipmentItem> allItems,
                List<Vehicle> vehicles
        ) {
            WorkingSolution working = new WorkingSolution();

            if (vehicles != null) {
                for (Vehicle vehicle : vehicles) {
                    if (vehicle != null && vehicle.getId() != null) {
                        working.vehicleMap.put(vehicle.getId(), vehicle);
                        working.vehicleNodes.put(vehicle.getId(), new ArrayList<>());
                    }
                }
            }

            if (allItems != null) {
                for (ShipmentItem item : allItems) {
                    if (item != null && item.getId() != null) {
                        working.itemMap.put(item.getId(), item);
                    }
                }
            }

            if (solution.getUnassignedShipmentItemIds() != null) {
                working.unassignedItemIds.addAll(solution.getUnassignedShipmentItemIds());
            }

            if (solution.getVehicleRoutes() != null) {
                for (VehicleRouteGene route : solution.getVehicleRoutes()) {
                    if (route == null || route.getVehicleId() == null) {
                        continue;
                    }

                    List<AssignmentNode> nodes =
                            toAssignmentNodes(route, working.itemMap);

                    working.vehicleNodes.put(route.getVehicleId(), nodes);
                }
            }

            return working;
        }

        List<Long> getAssignedItemIds() {
            Set<Long> ids = new LinkedHashSet<>();

            for (List<AssignmentNode> nodes : vehicleNodes.values()) {
                ids.addAll(extractServedItemIds(nodes));
            }

            return new ArrayList<>(ids);
        }

        void removeItemFromRoutes(Long itemId) {
            if (itemId == null) {
                return;
            }

            for (Map.Entry<Long, List<AssignmentNode>> entry : vehicleNodes.entrySet()) {
                List<AssignmentNode> nodes = entry.getValue();
                if (nodes == null || nodes.isEmpty()) {
                    continue;
                }

                nodes.removeIf(node ->
                        node != null
                                && node.getShipmentItem() != null
                                && Objects.equals(node.getShipmentItem().getId(), itemId)
                );

                normalizeSequence(nodes);
            }
        }

        MultiOrderSolution toSolution() {
            MultiOrderSolution solution = new MultiOrderSolution();

            List<VehicleRouteGene> routes = new ArrayList<>();

            for (Map.Entry<Long, List<AssignmentNode>> entry : vehicleNodes.entrySet()) {
                Long vehicleId = entry.getKey();
                List<AssignmentNode> nodes = entry.getValue();

                VehicleRouteGene routeGene = new VehicleRouteGene(vehicleId);

                if (nodes != null) {
                    nodes.sort(Comparator.comparing(
                            AssignmentNode::getSequenceIndex,
                            Comparator.nullsLast(Integer::compareTo)
                    ));

                    for (AssignmentNode node : nodes) {
                        Long itemId = node.getShipmentItem() != null
                                ? node.getShipmentItem().getId()
                                : null;

                        Long poiId = node.getPoi() != null
                                ? node.getPoi().getId()
                                : null;

                        routeGene.addNode(new NodeGene(
                                itemId,
                                node.getActionType(),
                                poiId
                        ));
                    }
                }

                routes.add(routeGene);
            }

            solution.setVehicleRoutes(routes);
            solution.setUnassignedShipmentItemIds(unassignedItemIds);

            // 变异后必须重新评价
            solution.setCost(Double.MAX_VALUE);
            solution.setFeasible(false);

            return solution;
        }

        private static List<AssignmentNode> toAssignmentNodes(
                VehicleRouteGene route,
                Map<Long, ShipmentItem> itemMap
        ) {
            List<AssignmentNode> nodes = new ArrayList<>();

            if (route.getNodes() == null) {
                return nodes;
            }

            int index = 0;

            for (NodeGene gene : route.getNodes()) {
                if (gene == null) {
                    continue;
                }

                AssignmentNode node = new AssignmentNode();
                node.setSequenceIndex(index++);
                node.setActionType(gene.getActionType());

                ShipmentItem item = gene.getShipmentItemId() != null
                        ? itemMap.get(gene.getShipmentItemId())
                        : null;

                node.setShipmentItem(item);

                if (item != null && item.getShipment() != null && gene.getActionType() != null) {
                    if (gene.getActionType() == AssignmentNode.NodeActionType.LOAD) {
                        node.setPoi(item.getShipment().getOriginPOI());
                        node.setWeightDelta(item.getWeight() != null ? item.getWeight() : 0.0);
                        node.setVolumeDelta(item.getVolume() != null ? item.getVolume() : 0.0);
                    } else if (gene.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
                        node.setPoi(item.getShipment().getDestPOI());
                        node.setWeightDelta(item.getWeight() != null ? -item.getWeight() : 0.0);
                        node.setVolumeDelta(item.getVolume() != null ? -item.getVolume() : 0.0);
                    } else {
                        node.setWeightDelta(0.0);
                        node.setVolumeDelta(0.0);
                    }
                } else {
                    node.setWeightDelta(0.0);
                    node.setVolumeDelta(0.0);
                }

                nodes.add(node);
            }

            normalizeSequence(nodes);
            return nodes;
        }

        private static void normalizeSequence(List<AssignmentNode> nodes) {
            if (nodes == null) {
                return;
            }

            for (int i = 0; i < nodes.size(); i++) {
                nodes.get(i).setSequenceIndex(i);
            }
        }
    }
}
