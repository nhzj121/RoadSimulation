package org.example.roadsimulation.dto;

import lombok.Data;
import java.util.List;

@Data
public class GaodeRouteResponse {
    private boolean success;
    private String message;
    private GaodeRouteData data;
    private long timestamp;

    public GaodeRouteResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public static GaodeRouteResponse success(String message, GaodeRouteData data) {
        GaodeRouteResponse response = new GaodeRouteResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static GaodeRouteResponse error(String message) {
        GaodeRouteResponse response = new GaodeRouteResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    @Data
    public static class GaodeRouteData {
        private List<RoutePath> paths;
        private String origin;
        private String destination;
        private Double taxiCost;
        private Integer totalDistance;  // 总距离(米)
        private Integer totalDuration;  // 总时间(秒)
    }

    @Data
    public static class RoutePath {
        private Double distance;      // 总距离(米)
        private Double duration;      // 总时间(秒)
        private String strategy;      // 策略
        private Double tolls;         // 收费(元)
        private Double tollDistance;  // 收费路段距离
        private List<Step> steps;     // 路径步骤
        private String polyline;      // 整体轨迹坐标
    }

    @Data
    public static class Step {
        private String instruction;   // 行驶指示
        private String road;          // 道路名称
        private Double distance;      // 步骤距离
        private Double duration;      // 步骤时间
        private String polyline;      // 轨迹坐标
        private String action;        // 动作
        private String orientation;   // 方向
    }
}