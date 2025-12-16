package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.StateTransitionService;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 车辆状态转移服务实现类（马尔科夫版）
 *
 * 职责：
 * 1. 基于马尔科夫链的状态转移矩阵，为车辆计算下一状态
 * 2. 提供单个 / 批量 / 带上下文 的状态选择方法
 *
 * 使用前提：
 * - Vehicle.VehicleStatus 中包含：
 *   IDLE, ORDER_DRIVING, LOADING, TRANSPORT_DRIVING,
 *   UNLOADING, WAITING, BREAKDOWN 等状态
 */
@Service
public class StateTransitionServiceImpl implements StateTransitionService {

    private final Random random = new Random();

    /**
     * 默认使用的小型货车马尔科夫矩阵
     * （以后如果需要，可以按车型扩展多套矩阵）
     */
    private final Map<Vehicle.VehicleStatus,
            Map<Vehicle.VehicleStatus, Double>> lightTruckMatrix;

    public StateTransitionServiceImpl() {
        this.lightTruckMatrix = createLightTruckMatrix();
    }

    // ============================================================
    //                    对外接口实现
    // ============================================================

    /**
     * 不带上下文，仅根据当前状态和默认矩阵选择下一状态
     */
    @Override
    public Vehicle.VehicleStatus selectNextState(Vehicle.VehicleStatus currentStatus) {
        return selectNextStateWithMarkov(currentStatus, lightTruckMatrix);
    }

    /**
     * 带车辆上下文（类型、任务等），目前先简单使用默认矩阵，
     * 以后你可以在这里根据 vehicleType 选择不同矩阵。
     */
    @Override
    public Vehicle.VehicleStatus selectNextStateWithContext(Vehicle vehicle) {
        if (vehicle == null) {
            return Vehicle.VehicleStatus.IDLE;
        }
        Vehicle.VehicleStatus currentStatus = vehicle.getCurrentStatus();

        // TODO: 如果以后按车型区分矩阵，可以在这里做：
        // Map<VehicleStatus, Map<VehicleStatus, Double>> matrix = resolveMatrixByVehicle(vehicle);
        Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> matrix = this.lightTruckMatrix;

        return selectNextStateWithMarkov(currentStatus, matrix);
    }

    /**
     * 批量计算下一状态
     */
    @Override
    public Map<Long, Vehicle.VehicleStatus> batchSelectNextState(
            Map<Long, Vehicle.VehicleStatus> currentStates) {

        Map<Long, Vehicle.VehicleStatus> result = new HashMap<>();
        if (currentStates == null || currentStates.isEmpty()) {
            return result;
        }

        for (Map.Entry<Long, Vehicle.VehicleStatus> entry : currentStates.entrySet()) {
            Vehicle.VehicleStatus next = selectNextState(entry.getValue());
            result.put(entry.getKey(), next);
        }
        return result;
    }

    /**
     * 核心：使用指定的马尔科夫矩阵，从当前状态随机抽样一个下一状态
     */
    @Override
    public Vehicle.VehicleStatus selectNextStateWithMarkov(
            Vehicle.VehicleStatus currentStatus,
            Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> markovMatrix) {

        if (currentStatus == null) {
            return Vehicle.VehicleStatus.IDLE;
        }

        Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> matrix =
                (markovMatrix != null ? markovMatrix : this.lightTruckMatrix);

        if (matrix == null || matrix.isEmpty()) {
            return currentStatus;
        }

        Map<Vehicle.VehicleStatus, Double> row = matrix.get(currentStatus);

        // 如果这一行没有配置，默认保持当前状态
        if (row == null || row.isEmpty()) {
            return currentStatus;
        }

        double r = random.nextDouble(); // [0,1)
        double cumulative = 0.0;
        Vehicle.VehicleStatus last = currentStatus;

        for (Map.Entry<Vehicle.VehicleStatus, Double> entry : row.entrySet()) {
            last = entry.getKey();
            Double p = entry.getValue();
            if (p == null || p <= 0) {
                continue;
            }
            cumulative += p;
            if (r <= cumulative) {
                return entry.getKey();
            }
        }

        // 兜底：浮点误差导致没命中时，返回最后一个状态
        return last;
    }

    // ============================================================
    //                  内部：马尔科夫矩阵构造
    // ============================================================

    /**
     * 小型货车的马尔科夫状态转移矩阵。
     * 概率参考 mc.java 思路，可以根据老师的表格调整。
     */
    private Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> createLightTruckMatrix() {
        Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> matrix =
                new EnumMap<>(Vehicle.VehicleStatus.class);

        // 1. IDLE -> 空闲状态
        // 70% 接到新订单开始接单行驶，22% 继续等待，5% 保持空闲，3% 发生故障
        matrix.put(Vehicle.VehicleStatus.IDLE, row(
                Vehicle.VehicleStatus.ORDER_DRIVING, 0.70,
                Vehicle.VehicleStatus.WAITING,       0.22,
                Vehicle.VehicleStatus.IDLE,          0.05,
                Vehicle.VehicleStatus.BREAKDOWN,     0.03
        ));

        // 2. ORDER_DRIVING -> 接单行驶（去装货点）
        // 80% 到达装货地开始装货，10% 进入等待，7% 继续行驶，3% 故障
        matrix.put(Vehicle.VehicleStatus.ORDER_DRIVING, row(
                Vehicle.VehicleStatus.LOADING,        0.80,
                Vehicle.VehicleStatus.WAITING,        0.10,
                Vehicle.VehicleStatus.ORDER_DRIVING,  0.07,
                Vehicle.VehicleStatus.BREAKDOWN,      0.03
        ));

        // 3. LOADING -> 装货
        // 80% 装完开始运货行驶，5% 装错/直接进入卸货，5% 等待，3% 继续装货，7% 故障
        matrix.put(Vehicle.VehicleStatus.LOADING, row(
                Vehicle.VehicleStatus.TRANSPORT_DRIVING, 0.80,
                Vehicle.VehicleStatus.UNLOADING,         0.05,
                Vehicle.VehicleStatus.WAITING,           0.05,
                Vehicle.VehicleStatus.LOADING,           0.03,
                Vehicle.VehicleStatus.BREAKDOWN,         0.07
        ));

        // 4. TRANSPORT_DRIVING -> 运货行驶
        // 86% 到达目的地卸货，8% 等待（排队等卸），3% 继续行驶，3% 故障
        matrix.put(Vehicle.VehicleStatus.TRANSPORT_DRIVING, row(
                Vehicle.VehicleStatus.UNLOADING,         0.86,
                Vehicle.VehicleStatus.WAITING,           0.08,
                Vehicle.VehicleStatus.TRANSPORT_DRIVING, 0.03,
                Vehicle.VehicleStatus.BREAKDOWN,         0.03
        ));

        // 5. UNLOADING -> 卸货
        // 75% 卸完回空闲，18% 直接接新单，3% 等待，2% 继续卸货，2% 故障
        matrix.put(Vehicle.VehicleStatus.UNLOADING, row(
                Vehicle.VehicleStatus.IDLE,              0.75,
                Vehicle.VehicleStatus.ORDER_DRIVING,     0.18,
                Vehicle.VehicleStatus.WAITING,           0.03,
                Vehicle.VehicleStatus.UNLOADING,         0.02,
                Vehicle.VehicleStatus.BREAKDOWN,         0.02
        ));

        // 6. WAITING -> 等待
        // 40% 开始接单行驶，25% 进入装货，15% 再次运输行驶，15% 跳到卸货，5% 故障
        matrix.put(Vehicle.VehicleStatus.WAITING, row(
                Vehicle.VehicleStatus.ORDER_DRIVING,     0.40,
                Vehicle.VehicleStatus.LOADING,           0.25,
                Vehicle.VehicleStatus.TRANSPORT_DRIVING, 0.15,
                Vehicle.VehicleStatus.UNLOADING,         0.15,
                Vehicle.VehicleStatus.BREAKDOWN,         0.05
        ));

        // 7. BREAKDOWN -> 故障
        // 70% 修好变为空闲，30% 继续故障
        matrix.put(Vehicle.VehicleStatus.BREAKDOWN, row(
                Vehicle.VehicleStatus.IDLE,              0.70,
                Vehicle.VehicleStatus.BREAKDOWN,         0.30
        ));

        return matrix;
    }

    /**
     * 小工具：把 (状态1, 概率1, 状态2, 概率2, ...) 变成一行转移概率 Map
     */
    private Map<Vehicle.VehicleStatus, Double> row(Object... args) {
        Map<Vehicle.VehicleStatus, Double> map = new EnumMap<>(Vehicle.VehicleStatus.class);
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("row(...) 参数必须是 成对的 状态, 概率");
        }
        for (int i = 0; i < args.length; i += 2) {
            Vehicle.VehicleStatus status = (Vehicle.VehicleStatus) args[i];
            Double prob = (Double) args[i + 1];
            map.put(status, prob);
        }
        return map;
    }

    // ============================================================
    //            （可选）更复杂上下文的扩展点示例
    // ============================================================

    /**
     * 示例：如果你以后想根据 assignment / 车辆类型做更复杂决策，
     * 可以在这里引入上下文逻辑，目前只是示例未被直接调用。
     */
    @SuppressWarnings("unused")
    private Vehicle.VehicleStatus selectNextStateWithFullContext(
            Vehicle.VehicleStatus currentStatus,
            Assignment assignment,
            Vehicle vehicle) {

        // 目前仍然只是简单使用马尔科夫矩阵
        return selectNextStateWithMarkov(currentStatus, lightTruckMatrix);
    }
}
