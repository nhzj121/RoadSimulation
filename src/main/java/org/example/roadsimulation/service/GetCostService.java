package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.CostEntity;
import org.springframework.stereotype.Service;


/**
 * 时间的计量单位为 小时
 * 距离的计量单位为 千米
 * 运能的计量单位为 吨千米
 */
@Service
public class GetCostService {

    private Double VehicleType = 1.0;

    /**
     * 直接成本
     * A ： costA = 0.5 * <所有车辆等待时间> + 0.5 * <所有车辆空驶里程>
     */
    public Double getCostByAllWaitingTimeAndMileageWithoutGoods(){
        return 0.5 * CostEntity.totalWaitingTime + 0.5 * CostEntity.totalMileage;
    }

    /**
     * 效率 + 关注最差情况
     * B：costB = 0.4 * <总空驶里程/总里程> + 0.5 * <总等待时间/总运输时间> + 0.1 * <(最差情况)>
     */
    public Double getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(){
        double mileageRatio = CostEntity.totalMileage == 0.0 ? 0.0 : (CostEntity.totalMileageWithoutThings / CostEntity.totalMileage);
        double timeRatio = CostEntity.totalTransportTime == 0.0 ? 0.0 : (CostEntity.totalWaitingTime / CostEntity.totalTransportTime);

        return 0.4 * mileageRatio
                + 0.5 * timeRatio
                + 0.1 * CostEntity.WorstWaitingTransportTime;
    }

    /**
     * 运能
     * C: costC = 0.9 * <总理论运能 - 总实际运能> + 0.1 * <(最差情况)>
     */
    public Double getCostByAllEffectiveTransportCapacityWithWorst(){
        return 0.9 * (CostEntity.totalTheoryCapacity - CostEntity.totalRealityCapacity)
                + 0.1 * CostEntity.WorstTheoryRealityCapacity;
    }

    /**
     * 经济收益 （这里需要预设油耗，固定损耗）
     * D： costD = 0.5 * <运输油耗> + 0.3 * <固定损耗> + 0.2 * <最差情况>
     */
    public Double getCostByALlOilAndFixedConsumptionWithWorst(){
        double capacityRatio = CostEntity.totalTheoryCapacity == 0.0 ? 0.0 : (CostEntity.totalRealityCapacity / CostEntity.totalTheoryCapacity);

        return 0.5 * VehicleType * capacityRatio
                + 0.3 * (VehicleType * CostEntity.totalWaitingTime + VehicleType * CostEntity.totalTransportTime)
                + 0.2 * CostEntity.WorstLoss;
    }

    /**
     * =========================================================================
     * 新增：边际成本预估器 (Cost Estimator) —— 专供 VRP 启发式算法使用
     * =========================================================================
     * 作用：在不污染全局 CostEntity 的前提下，模拟“如果增加这笔订单”，总成本会发生什么变化。
     * * @param deltaMileage 新增的总行驶里程 (千米)
     * @param deltaMileageWithoutThings 新增的空驶里程 (千米)
     * @param deltaTransportTime 新增的运输时间 (小时)
     * @param deltaWaitingTime 新增的等待时间 (小时)
     * @param deltaTheoryCapacity 新增的理论运能 (车辆最大载重 * 里程)
     * @param deltaRealityCapacity 新增的实际运能 (货物实际重量 * 里程)
     * @param worstTheoryRealityCapacity 模拟的单次最差运能比 (用于更新最差情况记录，可传当前单次的计算结果)
     * @param worstWaitingTransportTime 模拟的单次最差等待比
     * @param worstLoss 模拟的单次最差损耗
     * @return 模拟后的综合预测成本 (数值越小代表越值得拼车)
     */
    public Double estimateMarginalCost(
            Double deltaMileage,
            Double deltaMileageWithoutThings,
            Double deltaTransportTime,
            Double deltaWaitingTime,
            Double deltaTheoryCapacity,
            Double deltaRealityCapacity,
            Double worstTheoryRealityCapacity,
            Double worstWaitingTransportTime,
            Double worstLoss) {

        // 1. 模拟未来的全局状态 (What-If)
        double simTotalMileage = CostEntity.totalMileage + deltaMileage;
        double simTotalMileageWithoutThings = CostEntity.totalMileageWithoutThings + deltaMileageWithoutThings;
        double simTotalTransportTime = CostEntity.totalTransportTime + deltaTransportTime;
        double simTotalWaitingTime = CostEntity.totalWaitingTime + deltaWaitingTime;
        double simTotalTheoryCapacity = CostEntity.totalTheoryCapacity + deltaTheoryCapacity;
        double simTotalRealityCapacity = CostEntity.totalRealityCapacity + deltaRealityCapacity;

        // 模拟最差情况 (如果新订单引发了更差的情况，则用新的，否则保持原有)
        double simWorstTheoryRealityCapacity = Math.max(CostEntity.WorstTheoryRealityCapacity, worstTheoryRealityCapacity);
        double simWorstWaitingTransportTime = Math.max(CostEntity.WorstWaitingTransportTime, worstWaitingTransportTime);
        double simWorstLoss = Math.max(CostEntity.WorstLoss, worstLoss);

        // 2. 预估 Cost A (直接成本)
        double simCostA = 0.5 * simTotalWaitingTime + 0.5 * simTotalMileage;

        // 3. 预估 Cost B (效率)
        double simMileageRatio = simTotalMileage == 0.0 ? 0.0 : (simTotalMileageWithoutThings / simTotalMileage);
        double simTimeRatio = simTotalTransportTime == 0.0 ? 0.0 : (simTotalWaitingTime / simTotalTransportTime);
        double simCostB = 0.4 * simMileageRatio + 0.5 * simTimeRatio + 0.1 * simWorstWaitingTransportTime;

        // 4. 预估 Cost C (运能)
        double simCostC = 0.9 * (simTotalTheoryCapacity - simTotalRealityCapacity) + 0.1 * simWorstTheoryRealityCapacity;

        // 5. 预估 Cost D (经济)
        // 注意：在你原有的公式中，Cost D 的 capacityRatio 是加项。如果是成本惩罚，这里通常是减去收益。
        // 但为了与你现有的计算逻辑保持绝对一致，这里沿用你的公式结构。
        double simCapacityRatio = simTotalTheoryCapacity == 0.0 ? 0.0 : (simTotalRealityCapacity / simTotalTheoryCapacity);
        double simCostD = 0.5 * VehicleType * simCapacityRatio
                + 0.3 * (VehicleType * simTotalWaitingTime + VehicleType * simTotalTransportTime)
                + 0.2 * simWorstLoss;

        // 6. 计算总预测代价（你可以根据业务侧重点给 A、B、C、D 赋予不同的外层权重）
        // 这里默认它们权重为 1:1:1:1
        return simCostA + simCostB + simCostC + simCostD;
    }
}
