package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.service.StateTransitionService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 车辆状态转移服务实现类
 *
 * 功能说明：
 * 1. 基于完整业务规则实现车辆状态自动转移
 * 2. 不依赖外部马尔科夫链，独立运行
 * 3. 支持单个车辆和批量车辆状态转移
 * 4. 预留马尔科夫链集成接口
 */
@Service
public class StateTransitionServiceImpl implements StateTransitionService {

    private final Random random = new Random();

    // 预留的马尔科夫链矩阵（目前为空，供后续扩展使用）
    private Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> markovMatrix = new HashMap<>();

    @Override
    public Vehicle.VehicleStatus selectNextState(Vehicle.VehicleStatus currentStatus) {
        if (currentStatus == null) {
            throw new IllegalArgumentException("当前状态不能为null");
        }

        // 使用业务规则选择下一个状态
        return selectNextStateByBusinessRules(currentStatus);
    }

    @Override
    public Vehicle.VehicleStatus selectNextStateWithContext(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("车辆不能为null");
        }

        Vehicle.VehicleStatus currentStatus = vehicle.getCurrentStatus();
        Assignment currentAssignment = vehicle.getCurrentAssignment();

        // 基于完整上下文的决策逻辑
        return selectNextStateWithFullContext(currentStatus, currentAssignment, vehicle);
    }

    @Override
    public Map<Long, Vehicle.VehicleStatus> batchSelectNextState(Map<Long, Vehicle.VehicleStatus> currentStates) {
        Map<Long, Vehicle.VehicleStatus> nextStates = new HashMap<>();

        for (Map.Entry<Long, Vehicle.VehicleStatus> entry : currentStates.entrySet()) {
            Vehicle.VehicleStatus nextStatus = selectNextState(entry.getValue());
            nextStates.put(entry.getKey(), nextStatus);
        }

        return nextStates;
    }

    /**
     * 保留马尔科夫链的占位符 - 供队友后续集成使用
     */
    @Override
    public Vehicle.VehicleStatus selectNextStateWithMarkov(
            Vehicle.VehicleStatus currentStatus,
            Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> markovMatrix) {

        // 目前暂不实现马尔科夫链的逻辑，直接使用业务规则
        return selectNextStateByBusinessRules(currentStatus);
    }

    /**
     * 基于业务规则的状态选择 - 核心算法
     */
    private Vehicle.VehicleStatus selectNextStateByBusinessRules(Vehicle.VehicleStatus currentStatus) {
        switch (currentStatus) {
            case IDLE:
                return handleIdleState();
            case TRANSPORTING:
                return handleTransportingState();
            case UNLOADING:
                return handleUnloadingState();
            case MAINTAINING:
                return handleMaintainingState();
            case REFUELING:
                return handleRefuelingState();
            case RESTING:
                return handleRestingState();
            case ACCIDENT:
                return handleAccidentState();
            default:
                return Vehicle.VehicleStatus.IDLE;
        }
    }

    /**
     * 空闲状态处理 - 完整业务逻辑
     * 业务场景：车辆处于空闲状态，等待任务分配
     */
    private Vehicle.VehicleStatus handleIdleState() {
        double rand = random.nextDouble();

        if (rand < 0.70) {
            // 70% 接到新运输任务
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.78) {
            // 8% 定期保养检查
            return Vehicle.VehicleStatus.MAINTAINING;
        } else if (rand < 0.85) {
            // 7% 加油准备
            return Vehicle.VehicleStatus.REFUELING;
        } else if (rand < 0.92) {
            // 7% 司机换班休息
            return Vehicle.VehicleStatus.RESTING;
        } else if (rand < 0.96) {
            // 4% 车辆清洁整理
            return Vehicle.VehicleStatus.IDLE; // 保持空闲但进行其他作业
        } else {
            // 4% 等待调度指令
            return Vehicle.VehicleStatus.IDLE;
        }
    }

    /**
     * 运输中状态处理 - 完整业务逻辑
     * 业务场景：车辆正在执行运输任务
     */
    private Vehicle.VehicleStatus handleTransportingState() {
        double rand = random.nextDouble();

        if (rand < 0.75) {
            // 75% 正常到达装货点或卸货点
            return Vehicle.VehicleStatus.UNLOADING;
        } else if (rand < 0.80) {
            // 5% 途中需要加油
            return Vehicle.VehicleStatus.REFUELING;
        } else if (rand < 0.85) {
            // 5% 司机休息
            return Vehicle.VehicleStatus.RESTING;
        } else if (rand < 0.88) {
            // 3% 交通拥堵，继续运输
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.91) {
            // 3% 路线变更，继续运输
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.94) {
            // 3% 车辆故障需要保养
            return Vehicle.VehicleStatus.MAINTAINING;
        } else if (rand < 0.97) {
            // 3% 轻微事故
            return Vehicle.VehicleStatus.ACCIDENT;
        } else {
            // 3% 任务取消返回
            return Vehicle.VehicleStatus.IDLE;
        }
    }

    /**
     * 卸货状态处理 - 完整业务逻辑
     * 业务场景：车辆到达目的地正在进行卸货作业
     */
    private Vehicle.VehicleStatus handleUnloadingState() {
        double rand = random.nextDouble();

        if (rand < 0.65) {
            // 65% 卸货完成，等待新任务
            return Vehicle.VehicleStatus.IDLE;
        } else if (rand < 0.80) {
            // 15% 立即接新任务继续运输
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.85) {
            // 5% 卸货设备故障
            return Vehicle.VehicleStatus.UNLOADING; // 继续卸货
        } else if (rand < 0.90) {
            // 5% 货物验收问题
            return Vehicle.VehicleStatus.UNLOADING; // 处理问题
        } else if (rand < 0.94) {
            // 4% 卸货后需要加油
            return Vehicle.VehicleStatus.REFUELING;
        } else if (rand < 0.97) {
            // 3% 卸货后司机休息
            return Vehicle.VehicleStatus.RESTING;
        } else {
            // 3% 卸货后发现车辆问题需要保养
            return Vehicle.VehicleStatus.MAINTAINING;
        }
    }

    /**
     * 保养状态处理 - 完整业务逻辑
     * 业务场景：车辆正在进行维护保养
     */
    private Vehicle.VehicleStatus handleMaintainingState() {
        double rand = random.nextDouble();

        if (rand < 0.85) {
            // 85% 保养完成
            return Vehicle.VehicleStatus.IDLE;
        } else if (rand < 0.90) {
            // 5% 需要继续保养
            return Vehicle.VehicleStatus.MAINTAINING;
        } else if (rand < 0.94) {
            // 4% 保养后需要测试运行
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.97) {
            // 3% 等待配件
            return Vehicle.VehicleStatus.MAINTAINING;
        } else {
            // 3% 保养发现问题需要维修
            return Vehicle.VehicleStatus.MAINTAINING;
        }
    }

    /**
     * 加油状态处理 - 完整业务逻辑
     * 业务场景：车辆正在加油
     */
    private Vehicle.VehicleStatus handleRefuelingState() {
        double rand = random.nextDouble();

        if (rand < 0.90) {
            // 90% 加油完成
            return Vehicle.VehicleStatus.IDLE;
        } else if (rand < 0.94) {
            // 4% 加油后立即执行任务
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.97) {
            // 3% 加油设备故障
            return Vehicle.VehicleStatus.REFUELING;
        } else {
            // 3% 加油后发现车辆问题
            return Vehicle.VehicleStatus.MAINTAINING;
        }
    }

    /**
     * 休息状态处理 - 完整业务逻辑
     * 业务场景：司机休息中
     */
    private Vehicle.VehicleStatus handleRestingState() {
        double rand = random.nextDouble();

        if (rand < 0.80) {
            // 80% 休息结束
            return Vehicle.VehicleStatus.IDLE;
        } else if (rand < 0.90) {
            // 10% 休息后立即执行任务
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.95) {
            // 5% 延长休息
            return Vehicle.VehicleStatus.RESTING;
        } else {
            // 5% 休息期间发现身体不适需要调整
            return Vehicle.VehicleStatus.IDLE;
        }
    }

    /**
     * 事故状态处理 - 完整业务逻辑
     * 业务场景：车辆发生事故
     */
    private Vehicle.VehicleStatus handleAccidentState() {
        double rand = random.nextDouble();

        if (rand < 0.60) {
            // 60% 事故处理完成，需要保养
            return Vehicle.VehicleStatus.MAINTAINING;
        } else if (rand < 0.80) {
            // 20% 轻微事故，可直接继续
            return Vehicle.VehicleStatus.TRANSPORTING;
        } else if (rand < 0.90) {
            // 10% 事故处理中
            return Vehicle.VehicleStatus.ACCIDENT;
        } else if (rand < 0.95) {
            // 5% 事故后司机需要休息
            return Vehicle.VehicleStatus.RESTING;
        } else {
            // 5% 车辆报废，等待处理（保持事故状态）
            return Vehicle.VehicleStatus.ACCIDENT;
        }
    }

    /**
     * 基于完整上下文的状态选择
     * 预留方法：可根据车辆具体情况进行更精确决策
     */
    private Vehicle.VehicleStatus selectNextStateWithFullContext(
            Vehicle.VehicleStatus currentStatus,
            Assignment assignment,
            Vehicle vehicle) {

        // 目前先使用基础业务规则
        // 后续可根据assignment任务状态、vehicle位置等信息进行更精确决策
        return selectNextStateByBusinessRules(currentStatus);
    }
}