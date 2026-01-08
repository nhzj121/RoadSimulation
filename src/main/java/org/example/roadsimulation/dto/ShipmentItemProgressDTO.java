package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 运单项进度数据传输对象
 */
@Data
public class ShipmentItemProgressDTO {

    @Setter @Getter
    private Long id;

    @Setter @Getter
    private String name;

    @Setter @Getter
    private String sku;

    @Setter @Getter
    private Integer qty;

    @Setter @Getter
    private Double weight;

    @Setter @Getter
    private Double volume;

    // 状态信息
    @Setter @Getter
    private String status; // 运单项状态：NOT_ASSIGNED, ASSIGNED, IN_TRANSIT, DELIVERED

    @Setter @Getter
    private String statusText;

    @Setter @Getter
    private LocalDateTime assignedTime;

    @Setter @Getter
    private LocalDateTime loadedTime;

    @Setter @Getter
    private LocalDateTime deliveredTime;

    // 关联的Assignment信息
    @Setter @Getter
    private Long assignmentId;

    @Setter @Getter
    private String assignmentStatus;

    // 关联的车辆信息
    @Setter @Getter
    private Long vehicleId;

    @Setter @Getter
    private String vehicleLicensePlate;

    @Setter @Getter
    private String vehicleStatus;

    // 获取状态显示文本
    public String getStatusDisplayText() {
        if (status == null) return "未分配";

        switch (status) {
            case "NOT_ASSIGNED":
                return "未分配";
            case "ASSIGNED":
                return "已分配";
            case "LOADED":
                return "已装货";
            case "IN_TRANSIT":
                return "运输中";
            case "DELIVERED":
                return "已送达";
            default:
                return status;
        }
    }

    // 获取状态颜色
    public String getStatusColor() {
        if (status == null) return "#ccc";

        switch (status) {
            case "NOT_ASSIGNED":
                return "#bfbfbf"; // 灰色
            case "ASSIGNED":
                return "#1890ff"; // 蓝色
            case "LOADED":
                return "#fa8c16"; // 橙色
            case "IN_TRANSIT":
                return "#52c41a"; // 绿色
            case "DELIVERED":
                return "#722ed1"; // 紫色
            default:
                return "#ccc";
        }
    }

    // 检查是否已完成
    public boolean isCompleted() {
        return "DELIVERED".equals(status);
    }

    // 检查是否在运输中
    public boolean isInTransit() {
        return "IN_TRANSIT".equals(status) || "LOADED".equals(status);
    }
}