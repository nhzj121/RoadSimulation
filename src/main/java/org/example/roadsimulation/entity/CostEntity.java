package org.example.roadsimulation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CostEntity {
    public static Double totalTransportTime = 0.0; // 总运输时间
    public static Double totalMileage = 0.0; // 总里程

    public static Double totalMileageWithoutThings = 0.0; // 总空驶里程
    public static Double totalWaitingTime = 0.0; // 总等待时间

    public static Double totalTheoryCapacity = 0.0; // 总理论运能
    public static Double totalRealityCapacity = 0.0; // 总实际运能

    public static Double WorstTheoryRealityCapacity = 0.0; // 运能最差情况
    public static Double WorstWaitingTransportTime = 0.0; // 最差等待时间
    public static Double WorstLoss = 0.0;

}
