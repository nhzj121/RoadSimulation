package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentDTO {
    @Setter
    @Getter
    private Long id;
    @Setter @Getter
    private String status;
    @Setter @Getter
    private LocalDateTime createdTime;
    @Setter @Getter
    private LocalDateTime updatedTime;
    @Setter @Getter
    private LocalDateTime startTime;
    @Setter @Getter
    private LocalDateTime endTime;

    // 车辆信息
    @Setter @Getter
    private VehicleDTO vehicle;

    // 路线信息
    @Setter @Getter
    private RouteDTO route;

    // 货物清单信息
    @Setter @Getter
    private List<ShipmentItemDTO> shipmentItems;

    // 简化信息，便于前端快速访问
    @Setter @Getter
    private String goodsName;
    @Setter @Getter
    private Integer totalQuantity;
    @Setter @Getter
    private Double totalWeight;
    @Setter @Getter
    private Double totalVolume;
    @Setter @Getter
    private String shipmentRefNo;

    // 状态跟踪
    @Setter @Getter
    private Integer currentActionIndex;
    @Setter @Getter
    private Boolean isDrawn; // 是否已被前端绘制
    @Setter @Getter
    private LocalDateTime lastDrawnTime;

    // 性能统计
    @Setter @Getter
    private Double progressPercentage; // 进度百分比
    @Setter @Getter
    private Long estimatedRemainingTime; // 预计剩余时间（秒）
}