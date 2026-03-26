package org.example.roadsimulation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 高德驾车路径规划请求对象
 *
 * 这个类的作用：
 * 1. 统一接收前端传入的路径规划参数
 * 2. 统一给 service 层和高德适配层使用
 * 3. 为后续扩展更多规划能力预留字段
 */
@Data
public class GaodeRouteRequest {

    /**
     * 起点坐标
     * 格式：经度,纬度
     */
    @NotBlank(message = "起点坐标不能为空")
    private String origin;

    /**
     * 终点坐标
     * 格式：经度,纬度
     */
    @NotBlank(message = "终点坐标不能为空")
    private String destination;

    /**
     * 路线策略
     * 默认为32
     */
    private String strategy = "32";

    /**
     * 途经点
     * 多个点用英文分号;分隔
     */
    private String waypoints;

    /**
     * 避让区域
     * 区域内多个点用分号;分隔
     */
    private String avoidpolygons;

    /**
     * 车牌号
     * 给高德判断限行
     */
    private String plate;

    /**
     * 车辆类型
     * 0 - 普通燃油车，1 - 纯电动车，2 - 插电混动车
     */
    private Integer cartype;

    /**
     * 是否允许轮渡
     * 0 - 允许，1 - 不允许
     */
    private Integer ferry;

    /**
     * 最大方向变化数（用于复杂度计算）
     */
    private Integer maxDirectionChangeCount;

    /**
     * 最大匝道数（用于复杂度计算）
     */
    private Integer maxRampCount;

    /**
     * 无参构造
     */
    public GaodeRouteRequest() {
    }

    /**
     * 构造：起点 + 终点
     */
    public GaodeRouteRequest(String origin, String destination) {
        this.origin = origin;
        this.destination = destination;
    }

    /**
     * 构造：起点 + 终点 + 策略
     */
    public GaodeRouteRequest(String origin, String destination, String strategy) {
        this.origin = origin;
        this.destination = destination;
        this.strategy = strategy;
    }

    /**
     * 构造：起点 + 终点 + 策略 + 最大方向变化数 + 最大匝道数
     */
    public GaodeRouteRequest(String origin, String destination, String strategy, Integer maxDirectionChangeCount, Integer maxRampCount) {
        this.origin = origin;
        this.destination = destination;
        this.strategy = strategy;
        this.maxDirectionChangeCount = maxDirectionChangeCount;
        this.maxRampCount = maxRampCount;
    }
}
