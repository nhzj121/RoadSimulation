package org.example.roadsimulation.optimizer;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 遗传算法（Genetic Algorithm）优化器
 *
 * 编码：整数数组，chromosome[i] = vehicleIdx
 *
 * 超参数（针对中等规模 10-50辆车 / 50-500票货调优）：
 *   种群大小  POPULATION_SIZE = 150
 *   最大代数  MAX_GENERATIONS  = 300
 *   早停代数  NO_IMPROVE_LIMIT = 50（连续50代无改善则终止）
 *   锦标赛规模 TOURNAMENT_SIZE  = 5
 *   交叉率    CROSSOVER_RATE   = 0.85
 *   变异率    MUTATION_RATE    = 0.12
 *   精英比例  ELITE_RATIO      = 0.10
 */
@Component
public class GeneticAlgorithmOptimizer {

    private static final Logger log = LoggerFactory.getLogger(GeneticAlgorithmOptimizer.class);

    private static final int    POPULATION_SIZE   = 150;
    private static final int    MAX_GENERATIONS   = 300;
    private static final int    NO_IMPROVE_LIMIT  = 50;
    private static final int    TOURNAMENT_SIZE   = 5;
    private static final double CROSSOVER_RATE    = 0.85;
    private static final double MUTATION_RATE     = 0.12;
    private static final double ELITE_RATIO       = 0.10;

    @Autowired
    private SolutionEvaluator evaluator;

    /**
     * 执行遗传算法优化
     *
     * @param vehicles  可用车辆列表（IDLE状态）
     * @param items     待分配运单明细列表（NOT_ASSIGNED状态）
     * @param seed      随机种子（相同seed可复现结果）
     * @return 最优方案
     */
    public MatchingSolution optimize(List<Vehicle> vehicles, List<ShipmentItem> items, long seed) {
        long start = System.currentTimeMillis();
        int S = items.size();
        int V = vehicles.size();
        Random rng = new Random(seed);

        log.info("[GA] 开始优化：{}辆车, {}票货物明细", V, S);

        // Step1：初始化种群
        List<MatchingSolution> population = initPopulation(S, V, rng);
        evalAll(population, vehicles, items);

        MatchingSolution globalBest = deepCopyBest(population);
        int noImproveGen = 0;

        // Step2：迭代进化
        for (int gen = 0; gen < MAX_GENERATIONS; gen++) {

            int eliteCount = Math.max(1, (int)(POPULATION_SIZE * ELITE_RATIO));
            population.sort(Comparator.comparingDouble(MatchingSolution::getTotalCost));

            // 精英直接保留
            List<MatchingSolution> nextGen = new ArrayList<>();
            for (int e = 0; e < eliteCount; e++) {
                nextGen.add(new MatchingSolution(population.get(e)));
            }

            // 填充子代
            while (nextGen.size() < POPULATION_SIZE) {
                MatchingSolution p1 = tournamentSelect(population, rng);
                MatchingSolution p2 = tournamentSelect(population, rng);
                MatchingSolution child = (rng.nextDouble() < CROSSOVER_RATE)
                        ? uniformCrossover(p1, p2, rng)
                        : new MatchingSolution(p1);
                if (rng.nextDouble() < MUTATION_RATE) {
                    mutate(child, V, rng);
                }
                nextGen.add(child);
            }

            evalAll(nextGen, vehicles, items);
            population = nextGen;

            // 更新全局最优
            MatchingSolution genBest = deepCopyBest(population);
            if (genBest.getTotalCost() < globalBest.getTotalCost() - 1e-6) {
                globalBest = genBest;
                noImproveGen = 0;
                log.debug("[GA] gen={} 新最优 cost={:.2f}", gen, globalBest.getTotalCost());
            } else {
                noImproveGen++;
            }

            // 早停
            if (noImproveGen >= NO_IMPROVE_LIMIT) {
                log.info("[GA] 连续{}代无改善，提前终止于第{}代", NO_IMPROVE_LIMIT, gen);
                break;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[GA] 完成，最优cost={:.2f}, feasible={}, 耗时={}ms",
                globalBest.getTotalCost(), globalBest.isFeasible(), elapsed);
        return globalBest;
    }

    // ── 初始化种群 ────────────────────────────────────────────────────
    private List<MatchingSolution> initPopulation(int S, int V, Random rng) {
        List<MatchingSolution> pop = new ArrayList<>(POPULATION_SIZE);

        // 前20%使用轮询初始化（启发式，提升初代质量）
        int hCount = Math.max(1, POPULATION_SIZE / 5);
        for (int k = 0; k < hCount; k++) {
            MatchingSolution sol = new MatchingSolution(S);
            for (int i = 0; i < S; i++) sol.setAssignment(i, i % V);
            pop.add(sol);
        }

        // 剩余完全随机
        while (pop.size() < POPULATION_SIZE) {
            MatchingSolution sol = new MatchingSolution(S);
            for (int i = 0; i < S; i++) sol.setAssignment(i, rng.nextInt(V));
            pop.add(sol);
        }
        return pop;
    }

    // ── 锦标赛选择 ────────────────────────────────────────────────────
    private MatchingSolution tournamentSelect(List<MatchingSolution> pop, Random rng) {
        MatchingSolution best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            MatchingSolution c = pop.get(rng.nextInt(pop.size()));
            if (best == null || c.getTotalCost() < best.getTotalCost()) best = c;
        }
        return best;
    }

    // ── 均匀交叉 ──────────────────────────────────────────────────────
    private MatchingSolution uniformCrossover(MatchingSolution p1, MatchingSolution p2, Random rng) {
        int S = p1.getItemCount();
        MatchingSolution child = new MatchingSolution(S);
        for (int i = 0; i < S; i++) {
            child.setAssignment(i, rng.nextBoolean() ? p1.getAssignment(i) : p2.getAssignment(i));
        }
        return child;
    }

    // ── 变异：随机1-3个位置重新分配 ──────────────────────────────────
    private void mutate(MatchingSolution sol, int V, Random rng) {
        int S = sol.getItemCount();
        int pts = 1 + rng.nextInt(Math.min(3, S));
        for (int m = 0; m < pts; m++) {
            int idx = rng.nextInt(S);
            int oldV = sol.getAssignment(idx);
            int newV;
            do { newV = rng.nextInt(V); } while (V > 1 && newV == oldV);
            sol.setAssignment(idx, newV);
        }
    }

    private void evalAll(List<MatchingSolution> pop, List<Vehicle> vehicles, List<ShipmentItem> items) {
        for (MatchingSolution s : pop) evaluator.evaluate(s, vehicles, items);
    }

    private MatchingSolution deepCopyBest(List<MatchingSolution> pop) {
        return pop.stream()
                .min(Comparator.comparingDouble(MatchingSolution::getTotalCost))
                .map(MatchingSolution::new)
                .orElseThrow(() -> new RuntimeException("种群为空"));
    }
}
