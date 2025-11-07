package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import java.util.Map;

public interface VehicleStateTransitionService {

    /**
     * 根据当前状态选择下一个状态
     */
    Vehicle.VehicleStatus selectNextState(Vehicle.VehicleStatus currentState);

    /**
     * 获取状态转移概率
     */
    Map<Vehicle.VehicleStatus, Double> getTransitionProbabilities(Vehicle.VehicleStatus currentState);

    /**
     * 打印状态转移表
     */
    void printTransitionTable();
}