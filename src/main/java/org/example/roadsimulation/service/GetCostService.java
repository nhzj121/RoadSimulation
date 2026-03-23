package org.example.roadsimulation.service;

import org.springframework.stereotype.Service;

@Service
public class GetCostService {

    /**
     * 直接成本
     * A ： costA = 0.5 * <所有车辆等待时间> + 0.5 * <所有车辆空驶里程>
     */
    public Double getCostByAllWaitingTimeAndMileageWithoutGoods(){

        return 0.0;
    }

    /**
     * 效率 + 关注最差情况
     * B：costB = 0.4 * <总空驶里程/总里程> + 0.5 * <总等待时间/总运输时间> + 0.1 * <(最差情况)>
     */
    public Double getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(){

        return 0.0;
    }

    /**
     * 运能
     * C: costC = 0.9 * <总理论运能 - 总实际运能> + 0.1 * <(最差情况)>
     */
    public Double getCostByAllEffectiveTransportCapacityWithWorse(){

        return 0.0;
    }

    /**
     *
     */
}
