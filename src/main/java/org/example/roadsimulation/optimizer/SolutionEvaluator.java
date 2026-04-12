package org.example.roadsimulation.optimizer;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 方案成本评估器（GA的适应度函数 / SA的能量函数）
 *
 * 成本组成（所有金额单位均与系统一致，纯数值评分）：
 *
 *   硬约束惩罚（违反则方案不可行）：
 *     - 超重惩罚      = OVERLOAD_PENALTY × (超载量 / 最大载重)
 *     - 超体积惩罚    = OVERVOLUME_PENALTY × (超量 / 最大容积)
 *     - 温控不适配    = TEMP_PENALTY（货物需要温控但车辆suitableGoods不包含"冷链"/"refriger"/"temp"等关键字）
 *     - 危险品不适配  = HAZMAT_PENALTY（货物有hazmatLevel但车辆suitableGoods不包含"危"/"hazmat"等）
 *     - 未分配        = UNASSIGNED_PENALTY（最高惩罚，强制算法尽量分配所有货物）
 *
 *   软目标（优化方向）：
 *     - 启用车辆数固定成本  = VEHICLE_FIXED_COST × 实际用车数量（减少派车）
 *     - 低利用率惩罚        = LOW_UTIL_PENALTY × max(0, IDEAL_UTIL_MIN - 利用率)（避免大车拉小货）
 */
@Component
public class SolutionEvaluator {

    // ── 惩罚系数（可根据业务调权） ──────────────────────────────────
    private static final double UNASSIGNED_PENALTY   = 500_000.0;
    private static final double OVERLOAD_PENALTY     = 200_000.0;
    private static final double OVERVOLUME_PENALTY   = 200_000.0;
    private static final double TEMP_PENALTY         = 100_000.0;
    private static final double HAZMAT_PENALTY       = 100_000.0;
    private static final double VEHICLE_FIXED_COST   = 5_000.0;   // 每辆被使用的车的固定成本
    private static final double LOW_UTIL_PENALTY     = 8_000.0;   // 低利用率软惩罚系数
    private static final double IDEAL_UTIL_MIN       = 0.4;       // 理想最低利用率（40%以下视为资源浪费）

    /**
     * 评估方案总成本，结果写回 solution 对象
     *
     * @param solution  待评估方案
     * @param vehicles  IDLE车辆列表（下标与assignment对应）
     * @param items     NOT_ASSIGNED运单明细列表（下标与assignment对应）
     * @return 总成本
     */
    public double evaluate(MatchingSolution solution,
                           List<Vehicle> vehicles,
                           List<ShipmentItem> items) {

        int V = vehicles.size();
        int S = items.size();

        // 每辆车的累计重量和体积
        double[] vehicleWeight = new double[V];
        double[] vehicleVolume = new double[V];
        boolean[] vehicleUsed  = new boolean[V];

        double penalty  = 0.0;
        boolean feasible = true;

        for (int i = 0; i < S; i++) {
            int vIdx = solution.getAssignment(i);
            ShipmentItem item = items.get(i);

            // ── 1. 未分配惩罚 ─────────────────────────────────────────
            if (vIdx < 0 || vIdx >= V) {
                penalty += UNASSIGNED_PENALTY;
                feasible = false;
                continue;
            }

            Vehicle vehicle = vehicles.get(vIdx);
            vehicleUsed[vIdx] = true;

            // ── 2. 温控适配检查 ───────────────────────────────────────
            //    Goods.requireTemp=true 要求车辆 suitableGoods 包含温控关键字
            Goods goods = item.getGoods();
            if (goods != null && Boolean.TRUE.equals(goods.getRequireTemp())) {
                if (!vehicleSupportsTempControl(vehicle)) {
                    penalty += TEMP_PENALTY;
                    feasible = false;
                }
            }

            // ── 3. 危险品适配检查 ─────────────────────────────────────
            if (goods != null && goods.getHazmatLevel() != null && !goods.getHazmatLevel().isBlank()) {
                if (!vehicleSupportsHazmat(vehicle)) {
                    penalty += HAZMAT_PENALTY;
                    feasible = false;
                }
            }

            // ── 4. 累计载重 & 体积 ────────────────────────────────────
            vehicleWeight[vIdx] += safeDouble(item.getWeight());
            vehicleVolume[vIdx] += safeDouble(item.getVolume());
        }

        // ── 5. 超重 / 超体积惩罚（按车汇总后检查） ───────────────────
        for (int v = 0; v < V; v++) {
            if (!vehicleUsed[v]) continue;

            Vehicle vehicle = vehicles.get(v);

            // 超重
            Double maxLoad = vehicle.getMaxLoadCapacity();
            if (maxLoad != null && maxLoad > 0 && vehicleWeight[v] > maxLoad) {
                double overRatio = (vehicleWeight[v] - maxLoad) / maxLoad;
                penalty += OVERLOAD_PENALTY * overRatio;
                feasible = false;
            }

            // 超体积
            Double maxVol = vehicle.getCargoVolume();
            if (maxVol != null && maxVol > 0 && vehicleVolume[v] > maxVol) {
                double overRatio = (vehicleVolume[v] - maxVol) / maxVol;
                penalty += OVERVOLUME_PENALTY * overRatio;
                feasible = false;
            }

            // ── 6. 低利用率软惩罚（优化目标：不派大车拉小货） ────────
            if (maxLoad != null && maxLoad > 0) {
                double util = vehicleWeight[v] / maxLoad;
                if (util < IDEAL_UTIL_MIN) {
                    penalty += LOW_UTIL_PENALTY * (IDEAL_UTIL_MIN - util);
                }
            }
        }

        // ── 7. 派车固定成本 ───────────────────────────────────────────
        long usedCount = countTrue(vehicleUsed);
        double fixedCost = usedCount * VEHICLE_FIXED_COST;

        double totalCost = fixedCost + penalty;
        solution.setTotalCost(totalCost);
        solution.setFeasible(feasible);
        return totalCost;
    }

    // ── 温控判断：vehicle.suitableGoods 包含冷链/温控关键字 ──────────
    private boolean vehicleSupportsTempControl(Vehicle vehicle) {
        String sg = vehicle.getSuitableGoods();
        if (sg == null || sg.isBlank()) return false;
        String lower = sg.toLowerCase();
        return lower.contains("冷链") || lower.contains("冷藏") || lower.contains("温控")
                || lower.contains("refriger") || lower.contains("temp");
    }

    // ── 危险品判断：vehicle.suitableGoods 包含危险品关键字 ───────────
    private boolean vehicleSupportsHazmat(Vehicle vehicle) {
        String sg = vehicle.getSuitableGoods();
        if (sg == null || sg.isBlank()) return false;
        String lower = sg.toLowerCase();
        return lower.contains("危") || lower.contains("hazmat") || lower.contains("dangerous");
    }

    private double safeDouble(Double v) { return v == null ? 0.0 : v; }

    private long countTrue(boolean[] arr) {
        long c = 0;
        for (boolean b : arr) if (b) c++;
        return c;
    }
}
