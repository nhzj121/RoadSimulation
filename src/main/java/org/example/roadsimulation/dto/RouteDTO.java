package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RouteDTO {
    @Setter
    @Getter
    private Long id;
    @Setter @Getter
    private String routeCode;
    @Setter @Getter
    private String name;
    @Setter @Getter
    private Double distance; // 公里
    @Setter @Getter
    private Double estimatedTime; // 小时
    @Setter @Getter
    private String routeType;
    @Setter @Getter
    private String status;
    @Setter @Getter
    private String description;

    // 起点信息
    @Setter @Getter
    private Long startPOIId;
    @Setter @Getter
    private String startPOIName;
    @Setter @Getter
    private BigDecimal startLng;
    @Setter @Getter
    private BigDecimal startLat;
    @Setter @Getter
    private String startPOIType;

    // 终点信息
    @Setter @Getter
    private Long endPOIId;
    @Setter @Getter
    private String endPOIName;
    @Setter @Getter
    private BigDecimal endLng;
    @Setter @Getter
    private BigDecimal endLat;
    @Setter @Getter
    private String endPOIType;

    // 路径点（用于高德地图路径）
    @Setter @Getter
    private List<BigDecimal[]> pathPoints; // [[lng, lat], ...]格式

    // 成本信息
    @Setter @Getter
    private Double tollCost;
    @Setter @Getter
    private Double fuelConsumption;
    @Setter @Getter
    private Double estimatedTotalCost;

    // 元数据
    @Setter @Getter
    private LocalDateTime createdTime;
    @Setter @Getter
    private LocalDateTime updatedTime;

    // 关联信息
    @Setter @Getter
    private Integer activeAssignmentsCount; // 当前活跃任务数
}