package org.example.roadsimulation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VehicleDTO {

    private Long id;

    private String licensePlate;

    private String brand;

    private String modelType;

    private String vehicleType;

    private Double maxLoadCapacity;

    private Double currentLoad;

    private String suitableGoods;

    // 状态信息
    private String currentStatus;

    private String previousStatus;

    private LocalDateTime statusStartTime;

    private Long statusDurationSeconds;

    // 位置信息
    private Long currentPOIId;

    private String currentPOIName;

    private BigDecimal currentLongitude;

    private BigDecimal currentLatitude;

    // 驾驶员信息
    private String driverName;

    private Long driverId;

    // 任务信息
    private Long currentAssignmentId;

    private Boolean hasActiveAssignment;

    // 元数据
    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private String updatedBy;

    // 用于前端显示
    private String statusText;

    private String statusColor;

    // 车辆维度统计信息

    // 起始地等待时间（单位：秒）
    private Long loadingWaitTime;

    // 空驶时间（单位：秒）
    private Long emptyDrivingTime;

    // 空驶距离（单位：米）
    private Double emptyDrivingDistance;

    // 总行驶时间（单位：秒）
    private Long totalDrivingTime;

    // 总行驶距离（单位：米）
    private Double totalDrivingDistance;

}
