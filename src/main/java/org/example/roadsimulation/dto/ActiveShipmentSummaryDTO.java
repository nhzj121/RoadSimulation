package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活跃运单概览数据传输对象
 * 用于列表展示，包含简要信息
 */
@Data
public class ActiveShipmentSummaryDTO {

    @Setter @Getter
    private Long shipmentId;

    @Setter @Getter
    private String refNo;

    @Setter @Getter
    private String cargoType;

    // 起点和终点
    @Setter @Getter
    private String originPOIName;

    @Setter @Getter
    private String destPOIName;

    // 状态
    @Setter @Getter
    private String status;

    @Setter @Getter
    private String statusText;

    // 进度信息
    @Setter @Getter
    private Integer totalItems;

    @Setter @Getter
    private Integer completedItems;

    @Setter @Getter
    private Double progressPercentage;

    // 时间信息
    @Setter @Getter
    private LocalDateTime createdAt;

    @Setter @Getter
    private LocalDateTime updatedAt;

    @Setter @Getter
    private LocalDateTime latestActivityTime; // 最新活动时间

    // 获取进度颜色
    public String getProgressColor() {
        if (progressPercentage == null) return "#ccc";

        if (progressPercentage >= 100) {
            return "#52c41a"; // 完成 - 绿色
        } else if (progressPercentage >= 70) {
            return "#1890ff"; // 高进度 - 蓝色
        } else if (progressPercentage >= 30) {
            return "#faad14"; // 中等进度 - 橙色
        } else {
            return "#f5222d"; // 低进度 - 红色
        }
    }

    // 获取状态显示文本
    public String getStatusDisplayText() {
        if (status == null) return "未知";

        switch (status) {
            case "CREATED":
                return "已创建";
            case "PLANNED":
                return "已规划";
            case "PICKED_UP":
                return "已提货";
            case "IN_TRANSIT":
                return "运输中";
            case "DELIVERED":
                return "已送达";
            case "CANCELLED":
                return "已取消";
            default:
                return status;
        }
    }
}