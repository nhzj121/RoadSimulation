package org.example.roadsimulation.optimizer;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模拟退火（SA）- 货物数量分配优化
 *
 * 邻域操作（随机选一种）：
 *   1. transfer  — 从一辆车转移若干件给另一辆车
 *   2. swap      — 两辆车的分配数量互换
 *   3. rebalance — 随机重新平衡两辆车的总量分配
 *
 * 超参数（中等规模）：
 *   T0    = 30000
 *   ALPHA = 0.995
 *   T_MIN = 0.1
 *   L     = max(150, 8×vehicleCount)  每温度层迭代次数
 */
@Component
public class QuantitySA {

    private static final Logger log = LoggerFactory.getLogger(QuantitySA.class);

    private static final double T0    = 30_000.0;
    private static final double ALPHA = 0.995;
    private static final double T_MIN = 0.1;

    @Autowired
    private QuantityEvaluator evaluator;

    /**
     * 执行SA优化
     *
     * @param vehicles   候选车辆列表
     * @param goods      货物信息
     * @param totalQty   需要运输的总件数
     * @param seed       随机种子
     * @return 最优数量分配方案
     */
    public QuantitySolution optimize(List<Vehicle> vehicles, Goods goods, int totalQty, long seed) {
        int V = vehicles.size();
        int L = Math.max(150, 8 * V);
        Random rng = new Random(seed);
        long start = System.currentTimeMillis();

        log.info("[SA] 开始优化：{}辆候选车, {}件货物 ({}), 每层L={}", V, totalQty, goods.getName(), L);

        // 贪心初始解
        QuantitySolution current = greedyInit(V, totalQty, vehicles, goods);
        evaluator.evaluate(current, vehicles, goods, totalQty);
        QuantitySolution best = new QuantitySolution(current);

        double T = T0;
        long totalIter = 0;
        int improved = 0;

        while (T > T_MIN) {
            for (int l = 0; l < L; l++) {
                QuantitySolution neighbor = generateNeighbor(current, totalQty, rng);
                evaluator.evaluate(neighbor, vehicles, goods, totalQty);

                double delta = neighbor.getCost() - current.getCost();
                if (delta < 0 || rng.nextDouble() < Math.exp(-delta / T)) {
                    current = neighbor;
                    if (current.getCost() < best.getCost() - 1e-6) {
                        best = new QuantitySolution(current);
                        improved++;
                    }
                }
                totalIter++;
            }
            T *= ALPHA;
        }

        log.info("[SA] 完成 cost={:.1f} feasible={} 用{}辆 总迭代={} 改善{}次 耗时={}ms",
                best.getCost(), best.isFeasible(), best.usedVehicleCount(),
                totalIter, improved, System.currentTimeMillis() - start);
        return best;
    }

    // ── 贪心初始解 ────────────────────────────────────────────────────
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

    // ── 邻域生成 ──────────────────────────────────────────────────────
    private QuantitySolution generateNeighbor(QuantitySolution current, int totalQty, Random rng) {
        QuantitySolution neighbor = new QuantitySolution(current);
        int op = rng.nextInt(3);
        switch (op) {
            case 0 -> transfer(neighbor, rng);
            case 1 -> swap(neighbor, rng);
            case 2 -> rebalance(neighbor, totalQty, rng);
        }
        return neighbor;
    }

    /** 操作1：将src车的若干件转移给dst车 */
    private void transfer(QuantitySolution sol, Random rng) {
        int V = sol.getVehicleCount();
        if (V < 2) return;
        int src = rng.nextInt(V);
        int srcQty = sol.getQuantity(src);
        if (srcQty == 0) return;
        int dst;
        do { dst = rng.nextInt(V); } while (dst == src);
        int t = 1 + rng.nextInt(srcQty);
        sol.setQuantity(src, srcQty - t);
        sol.setQuantity(dst, sol.getQuantity(dst) + t);
    }

    /** 操作2：两辆车数量互换 */
    private void swap(QuantitySolution sol, Random rng) {
        int V = sol.getVehicleCount();
        if (V < 2) return;
        int i = rng.nextInt(V);
        int j;
        do { j = rng.nextInt(V); } while (j == i);
        int tmp = sol.getQuantity(i);
        sol.setQuantity(i, sol.getQuantity(j));
        sol.setQuantity(j, tmp);
    }

    /** 操作3：随机重新平衡两辆车的总量 */
    private void rebalance(QuantitySolution sol, int totalQty, Random rng) {
        int V = sol.getVehicleCount();
        if (V < 2) return;
        int i = rng.nextInt(V);
        int j;
        do { j = rng.nextInt(V); } while (j == i);
        int combined = sol.getQuantity(i) + sol.getQuantity(j);
        int newI = rng.nextInt(combined + 1);
        sol.setQuantity(i, newI);
        sol.setQuantity(j, combined - newI);
    }
}
