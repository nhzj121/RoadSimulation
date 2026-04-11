package org.example.roadsimulation.optimizer;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模拟退火算法（Simulated Annealing）优化器
 *
 * 邻域操作（3种，等概率随机选取）：
 *   1. singleReassign  — 随机将1票货换到另一辆车
 *   2. twoPointSwap    — 交换2票货的分配车辆
 *   3. segmentTransfer — 将同一辆车的2~5票货批量转移到另一辆车
 *
 * 超参数（中等规模调优）：
 *   T0    = 50000   初始温度
 *   ALPHA = 0.997   几何冷却系数（每轮温度乘以ALPHA）
 *   T_MIN = 0.5     终止温度
 *   L     = max(200, 10×itemCount)  每温度层迭代次数
 */
@Component
public class SimulatedAnnealingOptimizer {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAnnealingOptimizer.class);

    private static final double T0    = 50_000.0;
    private static final double ALPHA = 0.997;
    private static final double T_MIN = 0.5;

    @Autowired
    private SolutionEvaluator evaluator;

    /**
     * 执行模拟退火优化
     *
     * @param vehicles  可用车辆列表（IDLE状态）
     * @param items     待分配运单明细列表（NOT_ASSIGNED状态）
     * @param seed      随机种子
     * @return 最优方案
     */
    public MatchingSolution optimize(List<Vehicle> vehicles, List<ShipmentItem> items, long seed) {
        long start = System.currentTimeMillis();
        int S = items.size();
        int V = vehicles.size();
        int L = Math.max(200, 10 * S);   // 每层迭代次数
        Random rng = new Random(seed);

        log.info("[SA] 开始优化：{}辆车, {}票货物明细, 每层L={}", V, S, L);

        // 生成启发式初始解（轮询分配）
        MatchingSolution current = buildRoundRobinSolution(S, V);
        evaluator.evaluate(current, vehicles, items);

        MatchingSolution best = new MatchingSolution(current);
        double T = T0;
        long totalIter = 0;
        int improved = 0;

        // 退火主循环
        while (T > T_MIN) {
            for (int l = 0; l < L; l++) {
                // 生成邻域解
                MatchingSolution neighbor = generateNeighbor(current, V, rng);
                evaluator.evaluate(neighbor, vehicles, items);

                double delta = neighbor.getTotalCost() - current.getTotalCost();

                // Metropolis准则：优则接受，劣以 e^(-Δ/T) 概率接受
                if (delta < 0 || rng.nextDouble() < Math.exp(-delta / T)) {
                    current = neighbor;
                    if (current.getTotalCost() < best.getTotalCost() - 1e-6) {
                        best = new MatchingSolution(current);
                        improved++;
                    }
                }
                totalIter++;
            }
            T *= ALPHA;  // 几何冷却
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[SA] 完成，最优cost={:.2f}, feasible={}, 总迭代={}, 改善{}次, 耗时={}ms",
                best.getTotalCost(), best.isFeasible(), totalIter, improved, elapsed);
        return best;
    }

    // ── 轮询初始解 ────────────────────────────────────────────────────
    private MatchingSolution buildRoundRobinSolution(int S, int V) {
        MatchingSolution sol = new MatchingSolution(S);
        for (int i = 0; i < S; i++) sol.setAssignment(i, i % V);
        return sol;
    }

    // ── 邻域生成（随机选3种操作之一）────────────────────────────────
    private MatchingSolution generateNeighbor(MatchingSolution current, int V, Random rng) {
        MatchingSolution neighbor = new MatchingSolution(current);
        int op = rng.nextInt(3);
        switch (op) {
            case 0 -> singleReassign(neighbor, V, rng);
            case 1 -> twoPointSwap(neighbor, rng);
            case 2 -> segmentTransfer(neighbor, V, rng);
        }
        return neighbor;
    }

    /**
     * 操作1：单点重分配
     * 随机选一票货，分配给不同于当前的另一辆车
     */
    private void singleReassign(MatchingSolution sol, int V, Random rng) {
        if (V < 2) return;
        int S = sol.getItemCount();
        int idx = rng.nextInt(S);
        int oldV = sol.getAssignment(idx);
        int newV;
        do { newV = rng.nextInt(V); } while (newV == oldV);
        sol.setAssignment(idx, newV);
    }

    /**
     * 操作2：两点交换
     * 随机选2票货，互换分配的车辆
     */
    private void twoPointSwap(MatchingSolution sol, Random rng) {
        int S = sol.getItemCount();
        if (S < 2) return;
        int i = rng.nextInt(S);
        int j;
        do { j = rng.nextInt(S); } while (j == i);
        int tmp = sol.getAssignment(i);
        sol.setAssignment(i, sol.getAssignment(j));
        sol.setAssignment(j, tmp);
    }

    /**
     * 操作3：段转移
     * 随机选一辆源车中的2~5票货，统一转移到另一辆车
     * 模拟"批量重调度"场景
     */
    private void segmentTransfer(MatchingSolution sol, int V, Random rng) {
        if (V < 2) return;
        int S = sol.getItemCount();
        int srcV = rng.nextInt(V);
        int dstV;
        do { dstV = rng.nextInt(V); } while (dstV == srcV);

        // 找出当前分配给srcV的货物下标
        List<Integer> srcItems = new ArrayList<>();
        for (int i = 0; i < S; i++) {
            if (sol.getAssignment(i) == srcV) srcItems.add(i);
        }
        if (srcItems.isEmpty()) {
            // 退化为单点重分配
            singleReassign(sol, V, rng);
            return;
        }

        int count = Math.min(2 + rng.nextInt(4), srcItems.size());
        Collections.shuffle(srcItems, rng);
        for (int k = 0; k < count; k++) {
            sol.setAssignment(srcItems.get(k), dstV);
        }
    }
}
