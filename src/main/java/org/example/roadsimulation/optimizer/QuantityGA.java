package org.example.roadsimulation.optimizer;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 遗传算法（GA）- 货物数量分配优化
 *
 * 编码：int[] quantities，quantities[j] = 第j辆车承运的货物件数
 *
 * 超参数（针对中等规模：10-50辆车 / 单批50-600件货物）：
 *   种群大小    = 120
 *   最大代数    = 200
 *   早停代数    = 40（连续40代无改善则终止）
 *   锦标赛规模  = 5
 *   交叉率      = 0.85
 *   变异率      = 0.15
 *   精英比例    = 0.10
 */
@Component
public class QuantityGA {

    private static final Logger log = LoggerFactory.getLogger(QuantityGA.class);

    private static final int    POP_SIZE         = 120;
    private static final int    MAX_GEN          = 200;
    private static final int    NO_IMPROVE_LIMIT = 40;
    private static final int    TOURNAMENT_SIZE  = 5;
    private static final double CROSSOVER_RATE   = 0.85;
    private static final double MUTATION_RATE    = 0.15;
    private static final double ELITE_RATIO      = 0.10;

    @Autowired
    private QuantityEvaluator evaluator;

    /**
     * 执行GA优化
     *
     * @param vehicles   适配该货物的候选车辆（已按距离预筛选）
     * @param goods      货物信息
     * @param totalQty   需要运输的总件数
     * @param seed       随机种子
     * @return 最优数量分配方案
     */
    public QuantitySolution optimize(List<Vehicle> vehicles, Goods goods, int totalQty, long seed) {
        int V = vehicles.size();
        Random rng = new Random(seed);
        long start = System.currentTimeMillis();

        log.info("[GA] 开始优化：{}辆候选车, {}件货物 ({})", V, totalQty, goods.getName());

        // 初始化种群
        List<QuantitySolution> pop = initPopulation(V, totalQty, vehicles, goods, rng);
        evalAll(pop, vehicles, goods, totalQty);

        QuantitySolution best = deepCopy(bestOf(pop));
        int noImprove = 0;

        for (int gen = 0; gen < MAX_GEN; gen++) {
            int eliteN = Math.max(1, (int)(POP_SIZE * ELITE_RATIO));
            pop.sort(Comparator.comparingDouble(QuantitySolution::getCost));

            List<QuantitySolution> next = new ArrayList<>();
            // 精英保留
            for (int e = 0; e < eliteN; e++) next.add(new QuantitySolution(pop.get(e)));

            // 填充子代
            while (next.size() < POP_SIZE) {
                QuantitySolution p1 = tournament(pop, rng);
                QuantitySolution p2 = tournament(pop, rng);
                QuantitySolution child = rng.nextDouble() < CROSSOVER_RATE
                        ? crossover(p1, p2, totalQty, rng)
                        : new QuantitySolution(p1);
                if (rng.nextDouble() < MUTATION_RATE) mutate(child, totalQty, vehicles, goods, rng);
                next.add(child);
            }

            evalAll(next, vehicles, goods, totalQty);
            pop = next;

            QuantitySolution genBest = deepCopy(bestOf(pop));
            if (genBest.getCost() < best.getCost() - 1e-6) {
                best = genBest;
                noImprove = 0;
                log.debug("[GA] gen={} 新最优 cost={:.1f} feasible={}", gen, best.getCost(), best.isFeasible());
            } else {
                noImprove++;
            }
            if (noImprove >= NO_IMPROVE_LIMIT) {
                log.info("[GA] 早停于第{}代", gen);
                break;
            }
        }

        log.info("[GA] 完成 cost={:.1f} feasible={} 用{}辆 耗时={}ms",
                best.getCost(), best.isFeasible(), best.usedVehicleCount(),
                System.currentTimeMillis() - start);
        return best;
    }

    // ── 初始化种群 ────────────────────────────────────────────────────
    private List<QuantitySolution> initPopulation(int V, int totalQty,
                                                   List<Vehicle> vehicles, Goods goods, Random rng) {
        List<QuantitySolution> pop = new ArrayList<>(POP_SIZE);

        // 前20%：贪心分配（按车辆最大载重从大到小分配）
        int greedyN = Math.max(1, POP_SIZE / 5);
        for (int k = 0; k < greedyN; k++) {
            pop.add(greedyInit(V, totalQty, vehicles, goods));
        }

        // 其余：随机分配（保证总量合法）
        while (pop.size() < POP_SIZE) {
            pop.add(randomInit(V, totalQty, rng));
        }
        return pop;
    }

    /** 贪心初始化：按载重比例分配，适配车辆才参与 */
    private QuantitySolution greedyInit(int V, int totalQty, List<Vehicle> vehicles, Goods goods) {
        QuantitySolution sol = new QuantitySolution(V);
        if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() <= 0) return sol;

        int remaining = totalQty;
        for (int j = 0; j < V && remaining > 0; j++) {
            Vehicle v = vehicles.get(j);
            if (!evaluator.isCompatible(v, goods)) continue;
            Double maxLoad = v.getMaxLoadCapacity();
            if (maxLoad == null || maxLoad <= 0) continue;
            int cap = (int) Math.floor(maxLoad / goods.getWeightPerUnit());
            int assign = Math.min(cap, remaining);
            if (assign > 0) {
                sol.setQuantity(j, assign);
                remaining -= assign;
            }
        }
        return sol;
    }

    /** 随机初始化：将 totalQty 随机分配给各车辆（可能部分为0） */
    private QuantitySolution randomInit(int V, int totalQty, Random rng) {
        QuantitySolution sol = new QuantitySolution(V);
        int remaining = totalQty;
        // 随机打乱车辆顺序，防止前几辆总是被优先分配
        List<Integer> order = new ArrayList<>();
        for (int j = 0; j < V; j++) order.add(j);
        Collections.shuffle(order, rng);

        for (int i = 0; i < order.size() - 1 && remaining > 0; i++) {
            int give = rng.nextInt(remaining + 1);
            sol.setQuantity(order.get(i), give);
            remaining -= give;
        }
        if (remaining > 0) sol.setQuantity(order.get(order.size() - 1), remaining);
        return sol;
    }

    // ── 锦标赛选择 ────────────────────────────────────────────────────
    private QuantitySolution tournament(List<QuantitySolution> pop, Random rng) {
        QuantitySolution best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            QuantitySolution c = pop.get(rng.nextInt(pop.size()));
            if (best == null || c.getCost() < best.getCost()) best = c;
        }
        return best;
    }

    /**
     * 算术交叉：子代 = α×p1 + (1-α)×p2，然后修正使总量=totalQty
     */
    private QuantitySolution crossover(QuantitySolution p1, QuantitySolution p2, int totalQty, Random rng) {
        int V = p1.getVehicleCount();
        QuantitySolution child = new QuantitySolution(V);
        double alpha = rng.nextDouble();
        int sum = 0;
        for (int j = 0; j < V; j++) {
            int q = (int) Math.round(alpha * p1.getQuantity(j) + (1 - alpha) * p2.getQuantity(j));
            q = Math.max(0, q);
            child.setQuantity(j, q);
            sum += q;
        }
        repair(child, totalQty, rng);
        return child;
    }

    /**
     * 变异：随机将一辆车的部分数量转移给另一辆车
     */
    private void mutate(QuantitySolution sol, int totalQty, List<Vehicle> vehicles, Goods goods, Random rng) {
        int V = sol.getVehicleCount();
        if (V < 2) return;

        int src = rng.nextInt(V);
        int dst;
        do { dst = rng.nextInt(V); } while (dst == src);

        int srcQty = sol.getQuantity(src);
        if (srcQty == 0) return;

        int transfer = 1 + rng.nextInt(srcQty);
        sol.setQuantity(src, srcQty - transfer);
        sol.setQuantity(dst, sol.getQuantity(dst) + transfer);
    }

    /**
     * 修复方案：调整总量到 totalQty
     */
    private void repair(QuantitySolution sol, int totalQty, Random rng) {
        int V = sol.getVehicleCount();
        int diff = sol.totalAssigned() - totalQty;
        while (diff > 0) {
            // 随机减少某辆车的数量
            int j = rng.nextInt(V);
            int q = sol.getQuantity(j);
            if (q > 0) {
                int reduce = Math.min(diff, q);
                sol.setQuantity(j, q - reduce);
                diff -= reduce;
            }
        }
        while (diff < 0) {
            int j = rng.nextInt(V);
            sol.setQuantity(j, sol.getQuantity(j) + 1);
            diff++;
        }
    }

    private void evalAll(List<QuantitySolution> pop, List<Vehicle> vehicles, Goods goods, int totalQty) {
        for (QuantitySolution s : pop) evaluator.evaluate(s, vehicles, goods, totalQty);
    }

    private QuantitySolution bestOf(List<QuantitySolution> pop) {
        return pop.stream().min(Comparator.comparingDouble(QuantitySolution::getCost))
                .orElseThrow();
    }

    private QuantitySolution deepCopy(QuantitySolution s) {
        return new QuantitySolution(s);
    }
}
