package org.example.roadsimulation.optimizer.multi.init;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.NodeGene;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.ga.InsertionThresholdPolicy;
import org.example.roadsimulation.optimizer.multi.ga.MutationConfig;
import org.example.roadsimulation.optimizer.multi.insertion.FeasibleInsertionService;
import org.example.roadsimulation.optimizer.multi.insertion.InsertionCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多运单-多车辆启发式算法的初始种群构造器。
 *
 * 核心思路：
 * 1. 用合法插入算子保证初始化个体满足 LIFO + 容量约束。
 * 2. 用贪心生成高质量个体。
 * 3. 用扰动和 Top-K 随机插入制造多样性。
 */
@Service
public class MultiOrderInitialPopulationBuilder {

    private final FeasibleInsertionService insertionService;

    @Autowired
    public MultiOrderInitialPopulationBuilder(FeasibleInsertionService insertionService) {
        this.insertionService = insertionService;
    }

    public List<MultiOrderSolution> buildInitialPopulation(
            List<ShipmentItem> pendingItems,
            List<Vehicle> vehicles,
            InitialPopulationConfig config,
            MutationConfig mutationConfig,
            long seed
    ) {
        if (config == null) {
            config = new InitialPopulationConfig();
        }
        if (mutationConfig == null) {
            mutationConfig = new MutationConfig();
        }

        validateInput(pendingItems, vehicles);

        Random random = new Random(seed);
        List<MultiOrderSolution> population = new ArrayList<>();

        List<ShipmentItem> canonicalOrder = sortItemsForGreedy(pendingItems);

        // 1. 贪心精英个体：同一批运单顺序，但车辆顺序可以轻微变化
        for (int i = 0; i < config.getGreedyEliteCount()
                && population.size() < config.getPopulationSize(); i++) {

            List<Vehicle> vehicleOrder = new ArrayList<>(vehicles);
            if (i > 0) {
                lightlyShuffle(vehicleOrder, random, 0.10);
            }

            MultiOrderSolution solution = buildByGreedyInsertion(
                    canonicalOrder,
                    vehicleOrder,
                    false,
                    config.getTopKInsertionChoice(),
                    mutationConfig,
                    random
            );

            population.add(solution);
        }

        // 2. 贪心扰动个体：对运单排序做小扰动
        for (int i = 0; i < config.getPerturbedGreedyCount()
                && population.size() < config.getPopulationSize(); i++) {

            List<ShipmentItem> perturbedItems = new ArrayList<>(canonicalOrder);
            perturbItemOrder(perturbedItems, random, config.getPerturbRatio());

            List<Vehicle> vehicleOrder = new ArrayList<>(vehicles);
            lightlyShuffle(vehicleOrder, random, 0.20);

            MultiOrderSolution solution = buildByGreedyInsertion(
                    perturbedItems,
                    vehicleOrder,
                    true,
                    config.getTopKInsertionChoice(),
                    mutationConfig,
                    random
            );

            population.add(solution);
        }

        // 3. 随机贪心个体：运单顺序随机，插入位置从 Top-K 合法候选中随机选
        for (int i = 0; i < config.getRandomizedGreedyCount()
                && population.size() < config.getPopulationSize(); i++) {

            List<ShipmentItem> randomizedItems = new ArrayList<>(pendingItems);
            Collections.shuffle(randomizedItems, random);

            List<Vehicle> vehicleOrder = new ArrayList<>(vehicles);
            Collections.shuffle(vehicleOrder, random);

            MultiOrderSolution solution = buildByGreedyInsertion(
                    randomizedItems,
                    vehicleOrder,
                    true,
                    config.getTopKInsertionChoice(),
                    mutationConfig,
                    random
            );

            population.add(solution);
        }

        // 4. 如果数量仍不足，继续用随机贪心补齐
        while (population.size() < config.getPopulationSize()) {
            List<ShipmentItem> randomizedItems = new ArrayList<>(pendingItems);
            Collections.shuffle(randomizedItems, random);

            List<Vehicle> vehicleOrder = new ArrayList<>(vehicles);
            Collections.shuffle(vehicleOrder, random);

            MultiOrderSolution solution = buildByGreedyInsertion(
                    randomizedItems,
                    vehicleOrder,
                    true,
                    config.getTopKInsertionChoice(),
                    mutationConfig,
                    random
            );

            population.add(solution);
        }

        return population;
    }

    /**
     * 基于合法插入算子的贪心构造。
     *
     * @param useTopKRandomChoice false 表示永远选 score 最小插入；true 表示从 Top-K 候选中随机选。
     */
    private MultiOrderSolution buildByGreedyInsertion(
            List<ShipmentItem> orderedItems,
            List<Vehicle> vehicles,
            boolean useTopKRandomChoice,
            int topK,
            MutationConfig mutationConfig,
            Random random
    ) {
        Map<Long, Vehicle> vehicleMap = new LinkedHashMap<>();
        Map<Long, List<AssignmentNode>> vehicleNodes = new LinkedHashMap<>();

        for (Vehicle vehicle : vehicles) {
            if (vehicle == null || vehicle.getId() == null) {
                continue;
            }
            vehicleMap.put(vehicle.getId(), vehicle);
            vehicleNodes.put(vehicle.getId(), new ArrayList<>());
        }

        Set<Long> unassigned = new HashSet<>();

        for (ShipmentItem item : orderedItems) {
            if (item == null || item.getId() == null) {
                continue;
            }

            List<InsertionCandidate> allCandidates = new ArrayList<>();

            for (Vehicle vehicle : vehicleMap.values()) {
                List<AssignmentNode> currentNodes = vehicleNodes.get(vehicle.getId());

                List<InsertionCandidate> candidates =
                        insertionService.findAllFeasibleInsertions(vehicle, currentNodes, item);

                allCandidates.addAll(candidates);
            }

            if (allCandidates.isEmpty()) {
                unassigned.add(item.getId());
                continue;
            }

            allCandidates = InsertionThresholdPolicy.filterAcceptable(
                    allCandidates,
                    item,
                    mutationConfig
            );

            if (allCandidates.isEmpty()) {
                unassigned.add(item.getId());
                continue;
            }

            allCandidates.sort(Comparator.comparingDouble(InsertionCandidate::getScore));

            InsertionCandidate selected;
            if (useTopKRandomChoice) {
                int bound = Math.max(1, Math.min(topK, allCandidates.size()));
                selected = allCandidates.get(random.nextInt(bound));
            } else {
                selected = allCandidates.get(0);
            }

            vehicleNodes.put(
                    selected.getVehicle().getId(),
                    selected.getNodesAfterInsertion()
            );
        }

        return toSolution(vehicleMap, vehicleNodes, unassigned);
    }

    /**
     * 将内部节点序列转换为 MultiOrderSolution 的基因结构。
     */
    private MultiOrderSolution toSolution(
            Map<Long, Vehicle> vehicleMap,
            Map<Long, List<AssignmentNode>> vehicleNodes,
            Set<Long> unassigned
    ) {
        MultiOrderSolution solution = new MultiOrderSolution();

        List<VehicleRouteGene> routes = new ArrayList<>();

        for (Long vehicleId : vehicleMap.keySet()) {
            VehicleRouteGene routeGene = new VehicleRouteGene(vehicleId);
            List<AssignmentNode> nodes = vehicleNodes.getOrDefault(vehicleId, Collections.emptyList());

            nodes.sort(Comparator.comparing(
                    AssignmentNode::getSequenceIndex,
                    Comparator.nullsLast(Integer::compareTo)
            ));

            for (AssignmentNode node : nodes) {
                Long shipmentItemId = node.getShipmentItem() != null
                        ? node.getShipmentItem().getId()
                        : null;

                Long poiId = node.getPoi() != null
                        ? node.getPoi().getId()
                        : null;

                routeGene.addNode(new NodeGene(
                        shipmentItemId,
                        node.getActionType(),
                        poiId
                ));
            }

            routes.add(routeGene);
        }

        solution.setVehicleRoutes(routes);
        solution.setUnassignedShipmentItemIds(unassigned);

        // 这里先不设置最终 cost，Step 9 的 CostEvaluator 会统一计算。
        solution.setCost(Double.MAX_VALUE);
        solution.setFeasible(false);

        return solution;
    }

    /**
     * 贪心排序：
     * 1. 重量大的优先
     * 2. 体积大的优先
     * 3. 等待时间长/优先级后续可补
     */
    private List<ShipmentItem> sortItemsForGreedy(List<ShipmentItem> items) {
        List<ShipmentItem> sorted = new ArrayList<>(items);

        sorted.sort((a, b) -> {
            double aw = safe(a.getWeight());
            double bw = safe(b.getWeight());
            int weightCmp = Double.compare(bw, aw);
            if (weightCmp != 0) {
                return weightCmp;
            }

            double av = safe(a.getVolume());
            double bv = safe(b.getVolume());
            return Double.compare(bv, av);
        });

        return sorted;
    }

    /**
     * 对运单顺序做小扰动。
     * 不是完全随机打乱，而是进行若干次 swap。
     */
    private void perturbItemOrder(List<ShipmentItem> items, Random random, double ratio) {
        if (items == null || items.size() < 2) {
            return;
        }

        int swapCount = Math.max(1, (int) Math.round(items.size() * ratio));

        for (int k = 0; k < swapCount; k++) {
            int i = random.nextInt(items.size());
            int j = random.nextInt(items.size());
            if (i != j) {
                Collections.swap(items, i, j);
            }
        }
    }

    /**
     * 对车辆顺序轻微扰动。
     */
    private void lightlyShuffle(List<Vehicle> vehicles, Random random, double ratio) {
        if (vehicles == null || vehicles.size() < 2) {
            return;
        }

        int swapCount = Math.max(1, (int) Math.round(vehicles.size() * ratio));

        for (int k = 0; k < swapCount; k++) {
            int i = random.nextInt(vehicles.size());
            int j = random.nextInt(vehicles.size());
            if (i != j) {
                Collections.swap(vehicles, i, j);
            }
        }
    }

    private void validateInput(List<ShipmentItem> pendingItems, List<Vehicle> vehicles) {
        if (pendingItems == null || pendingItems.isEmpty()) {
            throw new IllegalArgumentException("pendingItems 不能为空");
        }

        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("vehicles 不能为空");
        }
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }
}
