package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
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

        initConfig.setPopulationSize(gaConfig.getPopulationSize());

        Random random = new Random(seed);

        List<MultiOrderSolution> population =
                initialPopulationBuilder.buildInitialPopulation(
                        pendingItems,
                        vehicles,
                        initConfig,
                        seed
                );

        // 第一处：初始化后统一评价
        evaluateAll(population, pendingItems, vehicles, costConfig);

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
                MultiOrderSolution parent1 = tournament(population, gaConfig.getTournamentSize(), random);
                MultiOrderSolution parent2 = tournament(population, gaConfig.getTournamentSize(), random);

                MultiOrderSolution child;

                if (random.nextDouble() < gaConfig.getCrossoverRate()) {
                    child = crossoverOperator.crossoverBidirectional(
                            parent1,
                            parent2,
                            pendingItems,
                            vehicles,
                            random
                    );

                    // 第二处：交叉后评价
                    costEvaluator.evaluate(
                            child,
                            pendingItems,
                            vehicles,
                            costConfig
                    );
                } else {
                    child = copy(parent1);
                }

                if (random.nextDouble() < gaConfig.getMutationRate()) {
                    child = mutationOperator.mutate(
                            child,
                            pendingItems,
                            vehicles,
                            mutationConfig,
                            random
                    );

                    // 第三处：变异后评价
                    costEvaluator.evaluate(
                            child,
                            pendingItems,
                            vehicles,
                            costConfig
                    );
                }

                // 安全兜底：如果 child 仍是未评价状态，则补评价
                if (child.getCost() == Double.MAX_VALUE) {
                    costEvaluator.evaluate(
                            child,
                            pendingItems,
                            vehicles,
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

        return Double.compare(a.getCost(), b.getCost());
    }

    private boolean isBetter(MultiOrderSolution candidate, MultiOrderSolution currentBest) {
        return compareSolution(candidate, currentBest) < 0;
    }

    private MultiOrderSolution copy(MultiOrderSolution solution) {
        return new MultiOrderSolution(solution);
    }
}