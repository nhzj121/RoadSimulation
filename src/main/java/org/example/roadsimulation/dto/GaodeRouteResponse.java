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
        private Double totalDistance;  // 总距离(米)，改为Double完美接收各种精度
        private Double totalDuration;  // 总时间(秒)

        // 新增字段，用于存储复杂度分析结果
        private Double complexityScore;  // 复杂度得分
        private String complexityLevel;  // 复杂度等级

        // 其他字段
        private Integer stepCount;           // 步骤数
        private Integer shortStepCount;      // 短步骤数
        private Double shortStepRatio;       // 短步骤占比
        private Integer leftTurnCount;       // 左转次数
        private Integer rightTurnCount;      // 右转次数
        private Integer uturnCount;          // 调头次数
        private Integer rampCount;           // 匝道数
        private Integer roundaboutCount;     // 环岛数
        private Integer directionChangeCount; // 方向变化数
        private Integer majorDirectionChangeCount; // 大幅方向变化数
        private Double fragmentContribution; // 路段碎片复杂度贡献
        private Double maneuverContribution; // 转向操作贡献
        private Double directionChangeContribution; // 方向变化贡献
        private Double shapeContribution;   // 路线形态贡献
        private String dominantFactor;      // 主要影响因素
        private String summary;             // 路线总结
        private String explanation;         // 复杂度解释

        // ================== 智能距离提取逻辑开始 ==================

        // 外部直接获取 Double 类型的距离，保留小数点精度
        public Double getTotalDistance() {
            // 如果高德原生 JSON 解析到了根节点的 totalDistance，直接返回
            if (this.totalDistance != null) {
                return this.totalDistance;
            }
            // 兜底逻辑：自动去 paths 数组里提取真实规划路线的距离
            if (this.paths != null && !this.paths.isEmpty() && this.paths.get(0).getDistance() != null) {
                return this.paths.get(0).getDistance();
            }
            return null;
        }

        public Double getTotalDuration() {
            if (this.totalDuration != null) {
                return this.totalDuration;
            }
            if (this.paths != null && !this.paths.isEmpty() && this.paths.get(0).getDuration() != null) {
                return this.paths.get(0).getDuration();
            }
            return null;
        }

        // 注：Lombok 的 @Data 会自动生成接收 Double 的 setter，专门供 JSON 框架反序列化时平滑注入。
        // ================== 智能距离提取逻辑结束 ==================

        // 添加set方法
        public void setComplexityScore(Double complexityScore) {
            this.complexityScore = complexityScore;
        }

        public void setComplexityLevel(String complexityLevel) {
            this.complexityLevel = complexityLevel;
        }

        // 添加获取步骤数的getter方法
        public Integer getStepCount() {
            return paths != null ? paths.size() : 0;
        }

        public Integer getShortStepCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getDistance() < 1000)
                    .count();
        }

        public Double getShortStepRatio() {
            if (getStepCount() == 0) return 0.0;
            return (double) getShortStepCount() / getStepCount();
        }

        public Integer getLeftTurnCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getAction() != null && step.getAction().equals("左转"))
                    .count();
        }

        public Integer getRightTurnCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getAction() != null && step.getAction().equals("右转"))
                    .count();
        }

        public Integer getUturnCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getAction() != null && step.getAction().equals("调头"))
                    .count();
        }

        public Integer getRampCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getRoad() != null && step.getRoad().contains("匝道"))
                    .count();
        }

        public Integer getRoundaboutCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getRoad() != null && step.getRoad().contains("环岛"))
                    .count();
        }

        public Integer getDirectionChangeCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getAction() != null && !step.getAction().equals("直行"))
                    .count();
        }

        public Integer getMajorDirectionChangeCount() {
            return (int) paths.stream()
                    .flatMap(path -> path.getSteps().stream())
                    .filter(step -> step.getAction() != null && step.getAction().equals("大幅变化"))
                    .count();
        }
    }

    @Data
    public static class RoutePath {
        private Double distance;
        private Double duration;
        private String strategy;
        private Double tolls;
        private Double tollDistance;
        private List<Step> steps;
        private String polyline;
    }

    @Data
    public static class Step {
        private String instruction;
        private String road;
        private Double distance;
        private Double duration;
        private String polyline;
        private String action;
        private String orientation;
    }
}