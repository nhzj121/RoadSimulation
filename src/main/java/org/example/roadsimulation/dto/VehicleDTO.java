package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VehicleDTO {
    @Setter
    @Getter
    private Long id;
    @Setter @Getter
    private String licensePlate;
    @Setter @Getter
    private String brand;
    @Setter @Getter
    private String modelType;
    @Setter @Getter
    private String vehicleType;
    @Setter @Getter
    private Double maxLoadCapacity;
    @Setter @Getter
    private Double currentLoad;
    @Setter @Getter
    private String suitableGoods;

    // 状态信息
    @Setter @Getter
    private String currentStatus;
    @Setter @Getter
    private String previousStatus;
    @Setter @Getter
    private LocalDateTime statusStartTime;
    @Setter @Getter
    private Long statusDurationSeconds;

    // 位置信息
    @Setter @Getter
    private Long currentPOIId;
    @Setter @Getter
    private String currentPOIName;
    @Setter @Getter
    private BigDecimal currentLongitude;
    @Setter @Getter
    private BigDecimal currentLatitude;

    // 驾驶员信息
    @Setter @Getter
    private String driverName;
    @Setter @Getter
    private Long driverId;

    // 任务信息
    @Setter @Getter
    private Long currentAssignmentId;
    @Setter @Getter
    private Boolean hasActiveAssignment;

    // 元数据
    @Setter @Getter
    private LocalDateTime createdTime;
    @Setter @Getter
    private LocalDateTime updatedTime;
    @Setter @Getter
    private String updatedBy;

    // 用于前端显示
    @Setter @Getter
    private String statusText;
    @Setter @Getter
    private String statusColor;
}