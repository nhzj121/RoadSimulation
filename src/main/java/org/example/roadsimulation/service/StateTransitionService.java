package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import java.util.Map;

/**
 * 车辆状态转移服务接口
 *
 * 功能说明：
 * 1. 提供完整的车辆状态转移逻辑，不进行任何简化
 * 2. 支持单个和批量车辆状态转移
 * 3. 预留马尔科夫链集成接口
 */
public interface StateTransitionService {

    /**
     * 状态选择函数 - 核心方法
     * 基于完整业务规则进行状态转移决策
     *
     * @param currentStatus 当前车辆状态
     * @return 下一个状态
     */
    Vehicle.VehicleStatus selectNextState(Vehicle.VehicleStatus currentStatus);

    /**
     * 带任务上下文的状态选择
     * 考虑车辆当前任务情况进行状态转移
     *
     * @param vehicle 车辆实体（包含任务信息）
     * @return 下一个状态
     */
    Vehicle.VehicleStatus selectNextStateWithContext(Vehicle vehicle);

    /**
     * 批量状态选择
     * 高效处理多个车辆的状态转移
     *
     * @param currentStates 车辆ID与状态的映射
     * @return 车辆ID与下一个状态的映射
     */
    Map<Long, Vehicle.VehicleStatus> batchSelectNextState(Map<Long, Vehicle.VehicleStatus> currentStates);

    /**
     * 预留接口：与马尔科夫链集成
     *
     * @param currentStatus 当前状态
     * @param markovMatrix 马尔科夫链概率矩阵
     * @return 下一个状态
     */
    Vehicle.VehicleStatus selectNextStateWithMarkov(
            Vehicle.VehicleStatus currentStatus,
            Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> markovMatrix);
}