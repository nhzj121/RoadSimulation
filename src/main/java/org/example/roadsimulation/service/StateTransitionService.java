package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Vehicle;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 车辆状态转移服务接口
 *
 * 功能说明：
 * 1. 提供完整的车辆状态转移逻辑
 * 2. 支持单个和批量车辆状态转移
 * 3. 预留马尔科夫链集成接口
 *
 * ✅ 统一时间框架：所有“状态更新/批量更新”必须显式传入 simNow（仿真时间）
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
     * @param currentStatus 当前车辆状态
     * @param assignment    当前任务（可为 null）
     * @param vehicle       车辆实体
     * @return 下一个状态
     */
    Vehicle.VehicleStatus selectNextStateWithFullContext(
            Vehicle.VehicleStatus currentStatus,
            Assignment assignment,
            Vehicle vehicle
    );

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
     * @param markovMatrix  马尔科夫链概率矩阵
     * @return 下一个状态
     */
    Vehicle.VehicleStatus selectNextStateWithMarkov(
            Vehicle.VehicleStatus currentStatus,
            Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> markovMatrix
    );

    // ==========================
    // ✅ 主循环驱动的状态更新接口
    // ==========================

    /**
     * 单辆车状态更新（主循环驱动）
     *
     * @param vehicle        车辆
     * @param simNow         当前仿真时间（由 SimulationMainLoop 计算传入）
     * @param minutesPerLoop 每个 loop 对应的仿真分钟数
     */
    void updateVehicleStateWithContext(Vehicle vehicle, LocalDateTime simNow, int minutesPerLoop);

    /**
     * 批量更新所有车辆状态（主循环驱动）
     *
     * @param simNow         当前仿真时间（由 SimulationMainLoop 计算传入）
     * @param minutesPerLoop 每个 loop 对应的仿真分钟数
     */
    void batchUpdateAllVehicleStates(LocalDateTime simNow, int minutesPerLoop);

    // ==========================
    // ❌ 旧接口（保留用于定位旧调用点）
    // ==========================

    /**
     * 旧版本：无 simNow 的单车更新入口（已废弃）
     * 目的：如果项目中还有人调用旧方法，运行时会立刻抛异常方便定位并清理
     */
    @Deprecated
    default void updateVehicleStateWithContext(Vehicle vehicle) {
        throw new UnsupportedOperationException(
                "已改为 SimulationMainLoop 驱动：请调用 updateVehicleStateWithContext(vehicle, simNow, minutesPerLoop)"
        );
    }

    /**
     * 旧版本：无 simNow 的批量更新入口（已废弃）
     */
    @Deprecated
    default void batchUpdateAllVehicleStates() {
        throw new UnsupportedOperationException(
                "已改为 SimulationMainLoop 驱动：请调用 batchUpdateAllVehicleStates(simNow, minutesPerLoop)"
        );
    }
}
