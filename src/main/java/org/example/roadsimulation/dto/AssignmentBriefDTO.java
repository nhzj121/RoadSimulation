package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Assignment 简要信息DTO（用于列表和状态同步）
 */
@Data
public class AssignmentBriefDTO {
    @Setter @Getter
    private Long assignmentId;
    @Setter @Getter
    private String status;
    @Setter @Getter
    private LocalDateTime createdTime;
    @Setter @Getter
    private LocalDateTime startTime;

    // 车辆信息
    @Setter @Getter
    private Long vehicleId;
    @Setter @Getter
    private String licensePlate;
    @Setter @Getter
    private String vehicleStatus;
    // 车辆起始位置（用于在地图上绘制车辆图标）
    @Setter @Getter
    private Double vehicleStartLng;
    @Setter @Getter
    private Double vehicleStartLat;
    @Setter @Getter
    private Double vehicleCurrentLon;
    @Setter @Getter
    private Double vehicleCurrentLat;

    // 路线信息
    @Setter @Getter
    private Long routeId;
    @Setter @Getter
    private String routeName;

    // 起点信息
    @Setter @Getter
    private Long startPOIId;
    @Setter @Getter
    private String startPOIName;
    @Setter @Getter
    private BigDecimal startLng;
    @Setter @Getter
    private BigDecimal startLat;

    // 终点信息
    @Setter @Getter
    private Long endPOIId;
    @Setter @Getter
    private String endPOIName;
    @Setter @Getter
    private BigDecimal endLng;
    @Setter @Getter
    private BigDecimal endLat;

    // 载重信息
    @Setter @Getter
    private Double currentLoad;
    @Setter @Getter
    private Double maxLoadCapacity;

    // 载容信息
    private Double currentVolume;
    private Double maxVolumeCapacity;

    // 货物信息
    @Setter @Getter
    private String goodsName;
    @Setter @Getter
    private Integer quantity;
    @Setter @Getter
    private String shipmentRefNo;
    @Setter @Getter
    private Double goodsWeightPerUnit;
    @Setter @Getter
    private Double goodsVolumePerUnit;

    // 状态跟踪
    @Setter @Getter
    private boolean isDrawn;
    @Setter @Getter
    private LocalDateTime lastDrawnTime;

    // 用于前端快速访问的字段
    @Setter @Getter
    private String pairId; // 兼容旧格式: startPOIId_endPOIId
}