package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class VehicleStateTransitionServiceImpl implements VehicleStateTransitionService {

    private final Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> transitionMatrix;
    private final Random random;

    public VehicleStateTransitionServiceImpl() {
        this.random = new Random();
        this.transitionMatrix = initializeTransitionMatrix();
    }

    /**
     * 初始化状态转移概率矩阵
     * 基于Vehicle实体中定义的状态
     */
    private Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> initializeTransitionMatrix() {
        Map<Vehicle.VehicleStatus, Map<Vehicle.VehicleStatus, Double>> matrix = new EnumMap<>(Vehicle.VehicleStatus.class);

        // IDLE -> 空闲状态转移概率
        matrix.put(Vehicle.VehicleStatus.IDLE, createTransitionMap(
                Vehicle.VehicleStatus.ORDER_DRIVING, 0.65,   // 65%概率开始接单运输
                Vehicle.VehicleStatus.WAITING, 0.25,         // 25%概率进入等待
                Vehicle.VehicleStatus.IDLE, 0.10             // 10%概率保持空闲
        ));

        // ORDER_DRIVING -> 接单运输状态转移概率
        matrix.put(Vehicle.VehicleStatus.ORDER_DRIVING, createTransitionMap(
                Vehicle.VehicleStatus.LOADING, 0.85,         // 85%概率到达装货点
                Vehicle.VehicleStatus.WAITING, 0.10,         // 10%概率需要等待
                Vehicle.VehicleStatus.ORDER_DRIVING, 0.05    // 5%概率继续接单行驶
        ));

        // LOADING -> 装货状态转移概率
        matrix.put(Vehicle.VehicleStatus.LOADING, createTransitionMap(
                Vehicle.VehicleStatus.TRANSPORT_DRIVING, 0.90,  // 90%概率开始运货行驶
                Vehicle.VehicleStatus.UNLOADING, 0.05,          // 5%概率需要卸货（装错）
                Vehicle.VehicleStatus.WAITING, 0.03,            // 3%概率需要等待
                Vehicle.VehicleStatus.LOADING, 0.02             // 2%概率继续装货
        ));

        // TRANSPORT_DRIVING -> 运货行驶状态转移概率
        matrix.put(Vehicle.VehicleStatus.TRANSPORT_DRIVING, createTransitionMap(
                Vehicle.VehicleStatus.UNLOADING, 0.88,      // 88%概率到达卸货点
                Vehicle.VehicleStatus.WAITING, 0.07,        // 7%概率需要等待
                Vehicle.VehicleStatus.TRANSPORT_DRIVING, 0.05 // 5%概率继续运货行驶
        ));

        // UNLOADING -> 卸货状态转移概率
        matrix.put(Vehicle.VehicleStatus.UNLOADING, createTransitionMap(
                Vehicle.VehicleStatus.IDLE, 0.75,           // 75%概率回到空闲
                Vehicle.VehicleStatus.ORDER_DRIVING, 0.15,  // 15%概率直接接新单
                Vehicle.VehicleStatus.WAITING, 0.06,        // 6%概率需要等待
                Vehicle.VehicleStatus.UNLOADING, 0.04       // 4%概率继续卸货
        ));

        // WAITING -> 等待状态转移概率
        matrix.put(Vehicle.VehicleStatus.WAITING, createTransitionMap(
                Vehicle.VehicleStatus.ORDER_DRIVING, 0.35,  // 35%概率开始接单运输
                Vehicle.VehicleStatus.LOADING, 0.25,        // 25%概率开始装货
                Vehicle.VehicleStatus.TRANSPORT_DRIVING, 0.15, // 15%概率开始运货行驶
                Vehicle.VehicleStatus.UNLOADING, 0.10,      // 10%概率开始卸货
                Vehicle.VehicleStatus.WAITING, 0.15         // 15%概率继续等待
        ));

        return matrix;
    }

}