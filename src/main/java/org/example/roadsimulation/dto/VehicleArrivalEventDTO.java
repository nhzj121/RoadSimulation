package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆到达POI点事件数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleArrivalEventDTO {
    /**
     * 车辆ID
     */
    private Long vehicleId;

    /**
     * POI点ID
     */
    private Long poiId;

    /**
     * 事件发生时间
     */
    private LocalDateTime arrivalTime;

    /**
     * 车辆到达时的实际经度
     */
    private BigDecimal actualLongitude;

    /**
     * 车辆到达时的实际纬度
     */
    private BigDecimal actualLatitude;

    /**
     * 触发半径（米）- 前端计算的到达阈值
     */
    private BigDecimal triggerRadius;
}