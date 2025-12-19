package org.example.roadsimulation.service.impl;

import jakarta.transaction.Transactional;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.StateTransitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 车辆状态转移服务实现类 - 最终完整版（已移除所有报错代码）
 */
@Service
public class StateTransitionServiceImpl implements StateTransitionService {

    private static final Logger logger = LoggerFactory.getLogger(StateTransitionServiceImpl.class);
    private final Random random = new Random();

    @Autowired
    private VehicleRepository vehicleRepository;

    // 状态顺序（必须与矩阵行/列严格对应）
    private static final List<VehicleStatus> STATES = List.of(
            VehicleStatus.IDLE,
            VehicleStatus.ORDER_DRIVING,
            VehicleStatus.LOADING,
            VehicleStatus.TRANSPORT_DRIVING,
            VehicleStatus.UNLOADING,
            VehicleStatus.WAITING,
            VehicleStatus.BREAKDOWN
    );

    private double[][] transitionMatrix;

    @PostConstruct
    public void initTransitionMatrix() {
        transitionMatrix = new double[7][7];
        transitionMatrix[0] = new double[]{0.13, 0.55, 0.0,  0.0,   0.0,   0.255, 0.065};
        transitionMatrix[1] = new double[]{0.0,  0.065,0.775, 0.0,   0.0,   0.112, 0.048};
        transitionMatrix[2] = new double[]{0.0,  0.0,  0.08, 0.78,  0.055, 0.043, 0.042};
        transitionMatrix[3] = new double[]{0.0,  0.0,  0.0,  0.09,  0.778, 0.07,  0.062};
        transitionMatrix[4] = new double[]{0.637,0.125,0.0,  0.0,   0.093, 0.083, 0.062};
        transitionMatrix[5] = new double[]{0.0,  0.30, 0.225,0.138, 0.10,  0.195, 0.042};
        transitionMatrix[6] = new double[]{0.212,0.0,  0.0,  0.0,   0.0,   0.0,   0.788};
    }

    /**
     * 核心方法：简化版（完全不依赖 getActions() 和完整 Action 对象）
     */
    @Override
    public VehicleStatus selectNextStateWithFullContext(
            VehicleStatus currentStatus, Assignment assignment, Vehicle vehicle) {

        if (assignment == null) {
            return selectNextStateWithMarkov(currentStatus);
        }

        switch (assignment.getStatus()) {
            case ASSIGNED:
                return VehicleStatus.ORDER_DRIVING;  // 待接单/去接单途中
            case IN_PROGRESS:
                Integer idx = assignment.getCurrentActionIndex();
                if (idx != null && assignment.getActionLine() != null && idx < assignment.getActionLine().size()) {
                    Long actionId = assignment.getActionLine().get(idx);
                    if (actionId != null) {
                        // 根据您的实际 actionId 规则调整这里（示例：尾数决定类型）
                        long lastDigit = actionId % 10;
                        if (lastDigit == 1) return VehicleStatus.LOADING;
                        if (lastDigit == 2) return VehicleStatus.TRANSPORT_DRIVING;
                        if (lastDigit == 3) return VehicleStatus.UNLOADING;
                        if (lastDigit == 4) return VehicleStatus.WAITING;
                        if (lastDigit == 5) return VehicleStatus.BREAKDOWN; // 维护
                    }
                }
                return VehicleStatus.TRANSPORT_DRIVING;  // 默认运输中
            case COMPLETED:
            case CANCELLED:
                return VehicleStatus.IDLE;
            default:
                return selectNextStateWithMarkov(currentStatus);
        }
    }

    /**
     * 纯马尔科夫链转移（无任务时使用）
     */
    private VehicleStatus selectNextStateWithMarkov(VehicleStatus currentStatus) {
        int idx = STATES.indexOf(currentStatus);
        if (idx == -1) return VehicleStatus.IDLE;

        double[] probs = transitionMatrix[idx];
        double rand = random.nextDouble();
        double sum = 0.0;
        for (int i = 0; i < probs.length; i++) {
            sum += probs[i];
            if (rand < sum) {
                return STATES.get(i);
            }
        }
        return STATES.get(probs.length - 1);
    }

    @Override
    public VehicleStatus selectNextState(VehicleStatus currentStatus) {
        return selectNextStateWithMarkov(currentStatus);
    }

    @Override
    public Map<Long, VehicleStatus> batchSelectNextState(Map<Long, VehicleStatus> currentStates) {
        Map<Long, VehicleStatus> result = new HashMap<>();
        for (Map.Entry<Long, VehicleStatus> entry : currentStates.entrySet()) {
            result.put(entry.getKey(), selectNextState(entry.getValue()));
        }
        return result;
    }

    @Override
    public VehicleStatus selectNextStateWithMarkov(
            VehicleStatus currentStatus,
            Map<VehicleStatus, Map<VehicleStatus, Double>> markovMatrix) {
        return selectNextStateWithMarkov(currentStatus);
    }

    /**
     * 单辆车状态更新
     */
    @Transactional
    public void updateVehicleStateWithContext(Vehicle vehicle) {
        if (vehicle == null) return;

        Assignment currentAssignment = vehicle.getCurrentAssignment();
        VehicleStatus nextStatus = selectNextStateWithFullContext(
                vehicle.getCurrentStatus(), currentAssignment, vehicle);

        if (nextStatus != vehicle.getCurrentStatus()) {
            vehicle.setPreviousStatus(vehicle.getCurrentStatus());
            vehicle.setCurrentStatus(nextStatus);
            vehicle.setStatusStartTime(LocalDateTime.now());
            logger.info("车辆[{}] 状态更新: {} → {}", vehicle.getLicensePlate(),
                    vehicle.getPreviousStatus(), nextStatus);
            vehicleRepository.save(vehicle);
        }
    }

    /**
     * 批量更新所有车辆状态
     */
    @Transactional
    public void batchUpdateAllVehicleStates() {
        System.out.println("=== 开始批量更新所有车辆状态 ===");
        List<Vehicle> vehicles = vehicleRepository.findAll();
        System.out.println("当前车辆数量: " + vehicles.size());
        for (Vehicle vehicle : vehicles) {
            updateVehicleStateWithContext(vehicle);
        }
        System.out.println("=== 批量更新完成 ===");
    }
}