package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 运单进度数据传输对象
 * 用于前端展示运单的运输进度
 */
@Data
public class ShipmentProgressDTO {

    // 运单基本信息
    @Setter @Getter
    private Long shipmentId;

    @Setter @Getter
    private String refNo; // 运单号

    @Setter @Getter
    private String cargoType; // 货类

    @Setter @Getter
    private Double totalWeight; // 总重量

    @Setter @Getter
    private Double totalVolume; // 总体积

    // 起点和终点
    @Setter @Getter
    private Long originPOIId;

    @Setter @Getter
    private String originPOIName;

    @Setter @Getter
    private Long destPOIId;

    @Setter @Getter
    private String destPOIName;

    // 状态信息
    @Setter @Getter
    private String status;

    @Setter @Getter
    private String statusText;

    // 进度信息
    @Setter @Getter
    private Integer totalItems; // 总运单项数

    @Setter @Getter
    private Integer completedItems; // 已完成运单项数

    @Setter @Getter
    private Integer inProgressItems; // 运输中运单项数

    @Setter @Getter
    private Integer waitingItems; // 等待中运单项数

    @Setter @Getter
    private Double progressPercentage; // 进度百分比

    // 货物量统计（已完成）
    @Setter @Getter
    private Double completedWeight; // 已完成重量

    @Setter @Getter
    private Double completedVolume; // 已完成体积

    @Setter @Getter
    private Double completedWeightPercentage; // 重量完成百分比

    @Setter @Getter
    private Double completedVolumePercentage; // 体积完成百分比

    // 时间信息
    @Setter @Getter
    private LocalDateTime createdAt;

    @Setter @Getter
    private LocalDateTime updatedAt;

    @Setter @Getter
    private LocalDateTime expectedCompletionTime;

    // 关联信息
    @Setter @Getter
    private List<ShipmentItemProgressDTO> items; // 运单项进度列表

    @Setter @Getter
    private List<AssignmentBriefDTO> assignments; // 关联的任务分配

    @Setter @Getter
    private List<VehicleDTO> vehicles; // 关联的车辆

    // 构造函数
    public ShipmentProgressDTO() {
        this.items = new ArrayList<>();
        this.assignments = new ArrayList<>();
        this.vehicles = new ArrayList<>();
    }

    // 计算进度百分比
    public void calculateProgress() {
        if (totalItems > 0) {
            this.progressPercentage = (double) completedItems / totalItems * 100;
        } else {
            this.progressPercentage = 0.0;
        }

        if (totalWeight != null && totalWeight > 0) {
            this.completedWeightPercentage = completedWeight != null ?
                    (completedWeight / totalWeight) * 100 : 0.0;
        }

        if (totalVolume != null && totalVolume > 0) {
            this.completedVolumePercentage = completedVolume != null ?
                    (completedVolume / totalVolume) * 100 : 0.0;
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

    // 获取进度状态颜色
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
}