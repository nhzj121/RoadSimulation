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
 * 多运单 GA 的 route-level crossover。
 *
 * 思路：
 * 1. 从 parentA 继承一条优秀车辆路线。
 * 2. 从 parentB 中读取剩余未覆盖运单顺序。
 * 3. 使用合法插入算子将剩余运单插入 child。
 */
@Component
public class MultiOrderCrossoverOperator {

    private final RouteGeneSelector routeGeneSelector;
    private final FeasibleInsertionService insertionService;

    @Autowired
    public MultiOrderCrossoverOperator(
            RouteGeneSelector routeGeneSelector,
            FeasibleInsertionService insertionService
    ) {
        this.routeGeneSelector = routeGeneSelector;
        this.insertionService = insertionService;
    }

    public MultiOrderSolution crossover(
            MultiOrderSolution parentA,
            MultiOrderSolution parentB,
            List<ShipmentItem> allItems,
            List<Vehicle> vehicles,
            Random random
    ) {
        if (random == null) {
            random = new Random();
        }

        Map<Long, Vehicle> vehicleMap = toVehicleMap(vehicles);
        Map<Long, ShipmentItem> itemMap = toItemMap(allItems);

        // 1. 初始化 child，每辆车先给空路线
        MultiOrderSolution child = createEmptyChild(vehicles);

        Map<Long, List<AssignmentNode>> childVehicleNodes = initEmptyVehicleNodes(vehicles);

        Set<Long> alreadyAssigned = new HashSet<>();

        // 2. 从 parentA 继承一条优秀路线
        Optional<VehicleRouteGene> inheritedRouteOpt =
                routeGeneSelector.selectBestNonEmptyRoute(parentA, vehicles, allItems);

        if (inheritedRouteOpt.isPresent()) {
            VehicleRouteGene inheritedRoute = inheritedRouteOpt.get();

            List<AssignmentNode> inheritedNodes =
                    toAssignmentNodes(inheritedRoute, itemMap);

            childVehicleNodes.put(inheritedRoute.getVehicleId(), inheritedNodes);

            alreadyAssigned.addAll(extractServedItemIds(inheritedRoute));
        }

        // 3. 从 parentB 中抽取剩余运单顺序
        List<Long> remainingOrder = extractItemOrder(parentB);
        remainingOrder.removeIf(alreadyAssigned::contains);

        // 如果 parentB 没有覆盖全部 item，则补上
        for (ShipmentItem item : allItems) {
            if (item != null && item.getId() != null
                    && !alreadyAssigned.contains(item.getId())
                    && !remainingOrder.contains(item.getId())) {
                remainingOrder.add(item.getId());
            }
        }

        // 4. 用合法插入算子修复剩余运单
        Set<Long> unassigned = new HashSet<>();

        for (Long itemId : remainingOrder) {
            ShipmentItem item = itemMap.get(itemId);
            if (item == null) {
                continue;
            }

            List<InsertionCandidate> allCandidates = new ArrayList<>();

            for (Vehicle vehicle : vehicleMap.values()) {
                List<AssignmentNode> currentNodes =
                        childVehicleNodes.getOrDefault(vehicle.getId(), new ArrayList<>());

                List<InsertionCandidate> candidates =
                        insertionService.findAllFeasibleInsertions(vehicle, currentNodes, item);

                allCandidates.addAll(candidates);
            }

            if (allCandidates.isEmpty()) {
                unassigned.add(itemId);
                continue;
            }

            allCandidates.sort(Comparator.comparingDouble(InsertionCandidate::getScore));

            // 交叉修复阶段建议多数时候选最优，少量随机增强多样性
            InsertionCandidate selected;
            if (random.nextDouble() < 0.85) {
                selected = allCandidates.get(0);
            } else {
                int bound = Math.min(3, allCandidates.size());
                selected = allCandidates.get(random.nextInt(bound));
            }

            childVehicleNodes.put(
                    selected.getVehicle().getId(),
                    selected.getNodesAfterInsertion()
            );

            alreadyAssigned.add(itemId);
        }

        child.setVehicleRoutes(toRouteGenes(childVehicleNodes));
        child.setUnassignedShipmentItemIds(unassigned);

        // 这里仍不评价，交给 Step 9 的 evaluator。
        child.setCost(Double.MAX_VALUE);
        child.setFeasible(false);

        return child;
    }

    /**
     * 随机决定父代方向。
     * 用于 GA 中避免总是从 parentA 继承路线。
     */
    public MultiOrderSolution crossoverBidirectional(
            MultiOrderSolution p1,
            MultiOrderSolution p2,
            List<ShipmentItem> allItems,
            List<Vehicle> vehicles,
            Random random
    ) {
        if (random == null) {
            random = new Random();
        }

        if (random.nextBoolean()) {
            return crossover(p1, p2, allItems, vehicles, random);
        } else {
            return crossover(p2, p1, allItems, vehicles, random);
        }
    }

    private MultiOrderSolution createEmptyChild(List<Vehicle> vehicles) {
        MultiOrderSolution child = new MultiOrderSolution();
        List<VehicleRouteGene> routes = new ArrayList<>();

        if (vehicles != null) {
            for (Vehicle vehicle : vehicles) {
                if (vehicle != null && vehicle.getId() != null) {
                    routes.add(new VehicleRouteGene(vehicle.getId()));
                }
            }
        }

        child.setVehicleRoutes(routes);
        return child;
    }

    private Map<Long, List<AssignmentNode>> initEmptyVehicleNodes(List<Vehicle> vehicles) {
        Map<Long, List<AssignmentNode>> map = new LinkedHashMap<>();

        if (vehicles != null) {
            for (Vehicle vehicle : vehicles) {
                if (vehicle != null && vehicle.getId() != null) {
                    map.put(vehicle.getId(), new ArrayList<>());
                }
            }
        }

        return map;
    }

    private List<Long> extractItemOrder(MultiOrderSolution parent) {
        List<Long> order = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        if (parent == null || parent.getVehicleRoutes() == null) {
            return order;
        }

        for (VehicleRouteGene route : parent.getVehicleRoutes()) {
            if (route == null || route.getNodes() == null) {
                continue;
            }

            for (NodeGene node : route.getNodes()) {
                if (node == null || node.getShipmentItemId() == null) {
                    continue;
                }

                Long itemId = node.getShipmentItemId();
                if (seen.add(itemId)) {
                    order.add(itemId);
                }
            }
        }

        return order;
    }

    private Set<Long> extractServedItemIds(VehicleRouteGene route) {
        Set<Long> ids = new HashSet<>();

        if (route == null || route.getNodes() == null) {
            return ids;
        }

        for (NodeGene node : route.getNodes()) {
            if (node != null && node.getShipmentItemId() != null) {
                ids.add(node.getShipmentItemId());
            }
        }

        return ids;
    }

    private List<AssignmentNode> toAssignmentNodes(
            VehicleRouteGene route,
            Map<Long, ShipmentItem> itemMap
    ) {
        List<AssignmentNode> nodes = new ArrayList<>();

        if (route == null || route.getNodes() == null) {
            return nodes;
        }

        int index = 0;
        for (NodeGene gene : route.getNodes()) {
            AssignmentNode node = new AssignmentNode();
            node.setSequenceIndex(index++);
            node.setActionType(gene.getActionType());

            ShipmentItem item = gene.getShipmentItemId() != null
                    ? itemMap.get(gene.getShipmentItemId())
                    : null;

            node.setShipmentItem(item);

            if (item != null && item.getShipment() != null) {
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
            }

            nodes.add(node);
        }

        return nodes;
    }

    private List<VehicleRouteGene> toRouteGenes(
            Map<Long, List<AssignmentNode>> vehicleNodes
    ) {
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

        return routes;
    }

    private Map<Long, Vehicle> toVehicleMap(List<Vehicle> vehicles) {
        Map<Long, Vehicle> map = new LinkedHashMap<>();

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
        Map<Long, ShipmentItem> map = new LinkedHashMap<>();

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