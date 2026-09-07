package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.roadsimulation.core.TransportUnits;

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
    // Phase 1（兼容字段）：旧前端继续读取公里；新运输展示应读取 distanceMeters。
    private Double distance; // 公里
    @Setter @Getter
    // Phase 1（兼容字段）：旧前端继续读取小时；新运输展示应读取 estimatedDrivingSeconds。
    private Double estimatedTime; // 小时

    /**
     * Phase 1：RouteDTO 的规范距离投影，单位为米，不保存第二份可能失真的状态。
     */
    public Double getDistanceMeters() {
        return distance == null ? null : TransportUnits.kilometersToMeters(distance);
    }

    /**
     * Phase 1：接受米制输入时同步写回旧公里字段，保持方案 A 的 JSON 兼容性。
     */
    public void setDistanceMeters(Double distanceMeters) {
        this.distance = distanceMeters == null ? null : TransportUnits.metersToKilometers(distanceMeters);
    }

    /**
     * Phase 1：RouteDTO 的规范计划耗时投影，单位为秒。
     */
    public Long getEstimatedDrivingSeconds() {
        return estimatedTime == null ? null : TransportUnits.hoursToSeconds(estimatedTime);
    }

    /**
     * Phase 1：接受秒制输入时同步写回旧小时字段，保持方案 A 的 JSON 兼容性。
     */
    public void setEstimatedDrivingSeconds(Long seconds) {
        this.estimatedTime = seconds == null ? null : TransportUnits.secondsToHours(seconds);
    }
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
