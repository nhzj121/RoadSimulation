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
}
