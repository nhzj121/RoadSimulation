package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig;
import org.example.roadsimulation.optimizer.multi.cost.MultiOrderCostEvaluator;
import org.example.roadsimulation.optimizer.multi.init.InitialPopulationConfig;
import org.example.roadsimulation.optimizer.multi.init.MultiOrderInitialPopulationBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MultiOrderGA {

    private final MultiOrderInitialPopulationBuilder initialPopulationBuilder;
    private final MultiOrderCostEvaluator costEvaluator;
    private final MultiOrderCrossoverOperator crossoverOperator;
    private final MultiOrderMutationOperator mutationOperator;

    @Autowired
    public MultiOrderGA(
            MultiOrderInitialPopulationBuilder initialPopulationBuilder,
            MultiOrderCostEvaluator costEvaluator,
            MultiOrderCrossoverOperator crossoverOperator,
            MultiOrderMutationOperator mutationOperator
    ) {
        this.initialPopulationBuilder = initialPopulationBuilder;
        this.costEvaluator = costEvaluator;
        this.crossoverOperator = crossoverOperator;
        this.mutationOperator = mutationOperator;
    }

    public MultiOrderSolution optimize(
            List<ShipmentItem> pendingItems,
            List<Vehicle> vehicles,
            MultiOrderGAConfig gaConfig,
            InitialPopulationConfig initConfig,
            CostNormalizationConfig costConfig,
            MutationConfig mutationConfig,
            long seed
    ) {
        return optimize(
                pendingItems, vehicles, gaConfig, initConfig, costConfig, mutationConfig,
                new Random(seed), new Random(seed));
    }

    public MultiOrderSolution optimize(
            List<ShipmentItem> pendingItems,
            List<Vehicle> vehicles,
            MultiOrderGAConfig gaConfig,
            InitialPopulationConfig initConfig,
            CostNormalizationConfig costConfig,
            MutationConfig mutationConfig,
            Random initialPopulationRandom,
            Random evolutionRandom
    ) {
        if (pendingItems == null || pendingItems.isEmpty()) {
            throw new IllegalArgumentException("pendingItems 不能为空");
        }

        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("vehicles 不能为空");
        }

        if (gaConfig == null) {
            gaConfig = new MultiOrderGAConfig();
        }

        if (initConfig == null) {
            initConfig = new InitialPopulationConfig();
        }

        if (costConfig == null) {
            costConfig = new CostNormalizationConfig();
        }

        if (mutationConfig == null) {
            mutationConfig = new MutationConfig();
        }
        Objects.requireNonNull(initialPopulationRandom, "initialPopulationRandom must not be null");
        Objects.requireNonNull(evolutionRandom, "evolutionRandom must not be null");

        List<ShipmentItem> orderedItems = pendingItems.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ShipmentItem::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .toList();
        List<Vehicle> orderedVehicles = vehicles.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Vehicle::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .toList();

        initConfig.setPopulationSize(gaConfig.getPopulationSize());

        List<MultiOrderSolution> population =
                initialPopulationBuilder.buildInitialPopulation(
                        orderedItems,
                        orderedVehicles,
                        initConfig,
                        mutationConfig,
                        initialPopulationRandom
                );

        // 第一处：初始化后统一评价
        evaluateAll(population, orderedItems, orderedVehicles, costConfig);

        MultiOrderSolution best = copy(bestOf(population));
        int noImprove = 0;

        for (int generation = 0; generation < gaConfig.getMaxGeneration(); generation++) {
            population.sort(this::compareSolution);

            int eliteCount = Math.max(
                    1,
                    (int) Math.round(gaConfig.getPopulationSize() * gaConfig.getEliteRatio())
            );

            List<MultiOrderSolution> next = new ArrayList<>();

            // 精英保留
            for (int i = 0; i < eliteCount && i < population.size(); i++) {
                next.add(copy(population.get(i)));
            }

            while (next.size() < gaConfig.getPopulationSize()) {
                MultiOrderSolution parent1 = tournament(population, gaConfig.getTournamentSize(), evolutionRandom);
                MultiOrderSolution parent2 = tournament(population, gaConfig.getTournamentSize(), evolutionRandom);

                MultiOrderSolution child;

                if (evolutionRandom.nextDouble() < gaConfig.getCrossoverRate()) {
                    child = crossoverOperator.crossoverBidirectional(
                            parent1,
                            parent2,
                            orderedItems,
                            orderedVehicles,
                            costConfig,
                            mutationConfig,
                            evolutionRandom
                    );

                    // 第二处：交叉后评价
                    costEvaluator.evaluate(
                            child,
                            orderedItems,
                            orderedVehicles,
                            costConfig
                    );
                } else {
                    child = copy(parent1);
                }

                if (evolutionRandom.nextDouble() < gaConfig.getMutationRate()) {
                    child = mutationOperator.mutate(
                            child,
                            orderedItems,
                            orderedVehicles,
                            mutationConfig,
                            evolutionRandom
                    );

                    // 第三处：变异后评价
                    costEvaluator.evaluate(
                            child,
                            orderedItems,
                            orderedVehicles,
                            costConfig
                    );
                }

                // 安全兜底：如果 child 仍是未评价状态，则补评价
                if (child.getCost() == Double.MAX_VALUE) {
                    costEvaluator.evaluate(
                            child,
                            orderedItems,
                            orderedVehicles,
                            costConfig
                    );
                }

                next.add(child);
            }

            population = next;
            population.sort(this::compareSolution);

            MultiOrderSolution generationBest = population.get(0);

            if (isBetter(generationBest, best)) {
                best = copy(generationBest);
                noImprove = 0;
            } else {
                noImprove++;
            }

            if (noImprove >= gaConfig.getNoImproveLimit()) {
                break;
            }
        }

        return best;
    }

    private void evaluateAll(
            List<MultiOrderSolution> population,
            List<ShipmentItem> pendingItems,
            List<Vehicle> vehicles,
            CostNormalizationConfig costConfig
    ) {
        for (MultiOrderSolution solution : population) {
            costEvaluator.evaluate(
                    solution,
                    pendingItems,
                    vehicles,
                    costConfig
            );
        }
    }

    private MultiOrderSolution tournament(
            List<MultiOrderSolution> population,
            int tournamentSize,
            Random random
    ) {
        MultiOrderSolution best = null;

        for (int i = 0; i < tournamentSize; i++) {
            MultiOrderSolution candidate =
                    population.get(random.nextInt(population.size()));

            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return best;
    }

    private MultiOrderSolution bestOf(List<MultiOrderSolution> population) {
        return population.stream()
                .min(this::compareSolution)
                .orElseThrow(() -> new IllegalStateException("种群为空"));
    }

    /**
     * 排序规则：
     * 1. feasible 优先
     * 2. cost 更低优先
     */
    private int compareSolution(MultiOrderSolution a, MultiOrderSolution b) {
        if (a.isFeasible() && !b.isFeasible()) {
            return -1;
        }

        if (!a.isFeasible() && b.isFeasible()) {
            return 1;
        }

        int costComparison = Double.compare(a.getCost(), b.getCost());
        if (costComparison != 0) {
            return costComparison;
        }
        return solutionFingerprint(a).compareTo(solutionFingerprint(b));
    }

    private String solutionFingerprint(MultiOrderSolution solution) {
        StringBuilder fingerprint = new StringBuilder();
        solution.getVehicleRoutes().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(VehicleRouteGene::getVehicleId,
                        Comparator.nullsLast(Long::compareTo)))
                .forEach(route -> {
                    fingerprint.append(route.getVehicleId()).append(':');
                    if (route.getNodes() != null) {
                        route.getNodes().forEach(node -> fingerprint
                                .append(node.getShipmentItemId()).append('/')
                                .append(node.getActionType()).append('/')
                                .append(node.getPoiId()).append(','));
                    }
                    fingerprint.append(';');
                });
        fingerprint.append('|');
        solution.getUnassignedShipmentItemIds().stream().sorted()
                .forEach(id -> fingerprint.append(id).append(','));
        return fingerprint.toString();
    }

    private boolean isBetter(MultiOrderSolution candidate, MultiOrderSolution currentBest) {
        return compareSolution(candidate, currentBest) < 0;
    }

    private MultiOrderSolution copy(MultiOrderSolution solution) {
        return new MultiOrderSolution(solution);
    }
}
