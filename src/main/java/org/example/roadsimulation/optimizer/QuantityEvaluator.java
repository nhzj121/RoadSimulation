package org.example.roadsimulation.optimizer;

import lombok.extern.slf4j.Slf4j;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.util.List;

// ToDo 预计需要大范围修改，这里具体的成本计算需要和实际结合着进行修改

/**
 * 分配方案成本评估器
 *
 * 成本组成：
 *   硬约束（违反则方案不可行，施加大额惩罚）：
 *     - 超重惩罚         = OVERLOAD_PENALTY × 超载比例
 *     - 货物不适配惩罚   = INCOMPATIBLE_PENALTY（车辆suitableGoods与货物不匹配）
 *     - 未分配完惩罚     = UNASSIGNED_PENALTY × 未分配件数（强制分完）
 *
 *   软目标（优化方向）：
 *     - 派车固定成本     = VEHICLE_FIXED_COST × 使用车辆数（少派车）
 *     - 低利用率惩罚     = LOW_UTIL_PENALTY × max(0, IDEAL_UTIL - 实际利用率)（不浪费大车）
 */
@Slf4j
@Component
public class QuantityEvaluator {

    // ── 惩罚系数 ─────────────────────────────────────────────────────
    private static final double UNASSIGNED_PENALTY    = 100_000.0;  // 每件非法未分配货物的惩罚
    private static final double OVERLOAD_PENALTY      = 200_000.0;  // 超载惩罚基数
    private static final double INCOMPATIBLE_PENALTY  = 150_000.0;  // 货物不适配惩罚
    private static final double VEHICLE_FIXED_COST    = 8_000.0;    // 每辆车固定派车成本
    private static final double LOW_UTIL_PENALTY      = 5_000.0;    // 低利用率惩罚系数
    private static final double IDEAL_UTIL            = 0.5;        // 理想利用率下限（50%）

    /**
     * 评估方案成本，结果写回 solution 对象
     *
     * @param solution   待评估方案
     * @param vehicles   候选车辆列表（下标与 solution.quantities 对应）
     * @param goods      运输的货物
     * @param totalQty   需要运输的总件数
     * @return 总成本（越低越好）
     */
    public double evaluate(QuantitySolution solution,
                           List<Vehicle> vehicles,
                           Goods goods,
                           int totalQty) {

        double penalty = 0.0;
        boolean feasible = true;
        int assigned = solution.totalAssigned();

        // ── 1. 未分配完惩罚 ───────────────────────────────────────────
        int unassigned = totalQty - assigned;
        if (unassigned > 0) {
            // 计算未分配货物的总重量和总体积
            double unassignedWeight = goods.getWeightPerUnit() != null ? unassigned * goods.getWeightPerUnit() : 0.0;
            double unassignedVolume = goods.getVolumePerUnit() != null ? unassigned * goods.getVolumePerUnit() : 0.0;

            boolean isLegalTailGood = true; // 默认假设它是合法尾货

            // 探测：这些未分配的货，如果硬塞给目前剩下的空车，能不能达到 60% 装载率？
            for (Vehicle v : vehicles) {
                if (!isCompatible(v, goods)) continue;

                Double maxLoad = v.getMaxLoadCapacity();
                Double maxVolume = v.getCargoVolume();

                if (maxLoad != null && maxLoad > 0 && maxVolume != null && maxVolume > 0) {
                    double expectedWeightFactor = unassignedWeight / maxLoad;
                    double expectedVolumeFactor = unassignedVolume / maxVolume;
                    double bestLoadFactor = Math.max(expectedWeightFactor, expectedVolumeFactor);

                    // 如果随便找一辆适配的车，装载率都能达到 60% 以上，说明这根本不是尾货！
                    // 是 GA 算法没有尽力分配，属于“非法未分配”
                    if (bestLoadFactor >= 0.60) {
                        isLegalTailGood = false;
                        break;
                    }
                }
            }

            if (isLegalTailGood) {
                // 【关键】合法尾货处理：
                // 1. 不将其标记为不可行 (feasible 保持为 true)
                // 2. 给予一个轻微的惩罚（模拟 VRP 拼载的预计边缘成本），而不是 10万的死刑惩罚
                // 比如假设每件货走 VRP 的成本是 50 块
                penalty += unassigned * 50.0;
                log.debug("[Evaluator] 鉴定为合法尾货: {}件，转入VRP预期成本计算", unassigned);
            } else {
                // 非法未分配（大头货没分完）：维持原有的死刑惩罚
                penalty += UNASSIGNED_PENALTY * unassigned;
                feasible = false;
            }
        }
        // 超分配也不行（分配的总量超过实际货物数）
        if (assigned > totalQty) {
            penalty += UNASSIGNED_PENALTY * (assigned - totalQty);
            feasible = false;
        }

        // ── 2. 逐车检查 ───────────────────────────────────────────────
        for (int j = 0; j < vehicles.size(); j++) {
            int qty = solution.getQuantity(j);
            if (qty <= 0) continue;   // 未使用的车辆跳过

            Vehicle vehicle = vehicles.get(j);

            // 2a. 货物适配检查：suitableGoods 需包含货物 SKU 或名称
            if (!isCompatible(vehicle, goods)) {
                penalty += INCOMPATIBLE_PENALTY;
                feasible = false;
            }

            // 2b. 载重约束检查
            Double maxLoad = vehicle.getMaxLoadCapacity();
            if (maxLoad != null && maxLoad > 0 && goods.getWeightPerUnit() != null) {
                double assignedWeight = qty * goods.getWeightPerUnit();
                if (assignedWeight > maxLoad) {
                    double overRatio = (assignedWeight - maxLoad) / maxLoad;
                    penalty += OVERLOAD_PENALTY * overRatio;
                    feasible = false;
                } else {
                    // 2c. 低利用率软惩罚
                    double util = assignedWeight / maxLoad;
                    if (util < IDEAL_UTIL) {
                        penalty += LOW_UTIL_PENALTY * (IDEAL_UTIL - util);
                    }
                }
            }

            // 2d. 派车固定成本
            penalty += VEHICLE_FIXED_COST;
        }

        solution.setCost(penalty);
        solution.setFeasible(feasible);
        return penalty;
    }

    /**
     * 检查车辆是否适合运输该货物
     * 判断逻辑：vehicle.suitableGoods 包含货物的 SKU 或 名称（忽略大小写）
     */
    public boolean isCompatible(Vehicle vehicle, Goods goods) {
        if (vehicle == null || goods == null) {
            return false;
        }

        String suitableVehicle = goods.getVehicleFit();
        String vehicleType = vehicle.getVehicleType();
        if(suitableVehicle == null || suitableVehicle.isBlank()) return false;
        if(vehicleType == null || vehicleType.isBlank()) return false;

        return suitableVehicle.strip().equals(vehicleType.strip());
    }
}
