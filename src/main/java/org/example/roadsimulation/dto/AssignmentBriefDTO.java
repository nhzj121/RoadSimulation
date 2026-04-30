package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    // ==================== VRP 一车多装新增扩展字段 ====================
    @Setter @Getter
    private boolean isVrp = false; // 标识该任务是否为多点拼载(VRP)任务

    @Setter @Getter
    private List<NodeDTO> nodes; // 动作节点列表（有序）

    /**
     * 内部静态类：用于向前端传递每个途径节点的详细动作
     */
    @Data
    public static class NodeDTO {
        private Integer sequenceIndex; // 执行顺序
        private Long poiId;            // 途径点ID
        private String poiName;        // 途径点名称
        private BigDecimal lng;        // 经度
        private BigDecimal lat;        // 纬度
        private String actionType;     // 动作类型 (LOAD / UNLOAD)
        private String poiType;        // POI类型
        private Double weightDelta;    // 载重变化量 (正为装，负为卸)
        private Double volumeDelta;    // 体积变化量
    }
}