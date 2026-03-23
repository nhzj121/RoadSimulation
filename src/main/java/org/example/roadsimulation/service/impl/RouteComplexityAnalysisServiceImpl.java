package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.dto.RouteComplexityAnalysisDTO;
import org.example.roadsimulation.service.RouteComplexityAnalysisService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static org.apache.commons.lang3.CharSetUtils.containsAny;

/**
 * 路线复杂度分析服务实现类
 *
 * 当前复杂度算法版本：
 * - 采用 4 维规则评分模型
 *
 * 4 个维度：
 * 1. 路段碎片复杂度
 * 2. 转向操作复杂度
 * 3. 收费结构复杂度
 * 4. 路线形态复杂度
 *
 * 总分：
 * complexityScore =
 * 0.35 * fragmentScore
 * + 0.35 * maneuverScore
 * + 0.15 * tollStructureScore
 * + 0.15 * shapeScore
 */
@Service
public class RouteComplexityAnalysisServiceImpl implements RouteComplexityAnalysisService {

    @Override
    public RouteComplexityAnalysisDTO analyzeRoutePath(GaodeRouteResponse.RoutePath path) {
        RouteComplexityAnalysisDTO result = new RouteComplexityAnalysisDTO();

        if (path == null) {
            result.setComplexityScore(0.0);
            result.setComplexityLevel("低复杂度");
            result.setSummary("无可分析路径");
            return result;
        }

        // =========================
        // 1. 基础数据提取
        // =========================
        List<GaodeRouteResponse.Step> steps = path.getSteps();
        int stepCount = steps == null ? 0 : steps.size();

        double totalDistance = path.getDistance() == null ? 0.0 : path.getDistance();


        double averageStepDistance = stepCount > 0 ? totalDistance / stepCount : 0.0;
        int shortStepCount = 0;


        int polylinePointCount = countPolylinePoints(path.getPolyline());
        double distanceKm = totalDistance > 0 ? totalDistance / 1000.0 : 0.0;
        double polylineDensity = distanceKm > 0 ? polylinePointCount / distanceKm : polylinePointCount;

        result.setStepCount(stepCount);
        result.setAverageStepDistance(averageStepDistance);
        result.setPolylinePointCount(polylinePointCount);
        result.setPolylineDensity(polylineDensity);

        // =========================
// 2. 统计转向动作
// =========================
        int leftTurnCount = 0;
        int rightTurnCount = 0;
        int uTurnCount = 0;
        int rampCount = 0;
        int roundaboutCount = 0;

        double maneuverRawScore = 0.0;

        int directionChangeCount = 0;
        int majorDirectionChangeCount = 0;
        String previousOrientation = null;


        if (steps != null) {
            for (GaodeRouteResponse.Step step : steps) {
                // 高德 v5 当前返回里 action 可能为空，所以优先使用 instruction 做识别
                String instruction = step.getInstruction() == null ? "" : step.getInstruction();
                String action = step.getAction() == null ? "" : step.getAction();

                // 合并分析文本：优先依赖 instruction，action 作为补充
                String text = instruction + " " + action;

                if (containsAny(text, "左转")) {
                    leftTurnCount++;
                    maneuverRawScore += 1.0;
                }



                if (containsAny(text, "右转", "向右前方")) {
                    rightTurnCount++;
                    maneuverRawScore += 1.0;
                }

                if (containsAny(text, "调头", "掉头")) {
                    uTurnCount++;
                    maneuverRawScore += 2.0;
                }

                if (containsAny(text, "进入辅路", "驶入辅路", "进入匝道", "驶入匝道", "进入主路", "驶入主路")) {
                    rampCount++;
                    maneuverRawScore += 1.5;
                }


                if (containsAny(text, "环岛")) {
                    roundaboutCount++;
                    maneuverRawScore += 1.5;
                }



                // 统计方向变化复杂度
                String currentOrientation = step.getOrientation() == null ? "" : step.getOrientation().trim();

                if (!currentOrientation.isEmpty()) {
                    if (previousOrientation != null && !previousOrientation.isEmpty()
                            && !previousOrientation.equals(currentOrientation)) {

                        directionChangeCount++;

                        if (isMajorDirectionChange(previousOrientation, currentOrientation)) {
                            majorDirectionChangeCount++;
                        }
                    }

                    previousOrientation = currentOrientation;
                }

                // 统计短 step（小于 300 米）
                if (step.getDistance() != null && step.getDistance() < 300) {
                    shortStepCount++;
                }

            }
        }
        double shortStepRatio = stepCount > 0 ? (double) shortStepCount / stepCount : 0.0;

        result.setLeftTurnCount(leftTurnCount);
        result.setRightTurnCount(rightTurnCount);
        result.setUTurnCount(uTurnCount);
        result.setRampCount(rampCount);
        result.setRoundaboutCount(roundaboutCount);
        result.setManeuverRawScore(maneuverRawScore);
        result.setDirectionChangeCount(directionChangeCount);
        result.setMajorDirectionChangeCount(majorDirectionChangeCount);
        result.setShortStepCount(shortStepCount);
        result.setShortStepRatio(shortStepRatio);



        // =========================
        // 3. 计算四个子维度分数
        // =========================
        double fragmentScore = calculateFragmentScore(stepCount, averageStepDistance, shortStepRatio);
        double maneuverScore = calculateManeuverScore(maneuverRawScore);
        double directionChangeScore = calculateDirectionChangeScore(directionChangeCount, majorDirectionChangeCount);
        double shapeScore = calculateShapeScore(polylineDensity);
        double fragmentContribution = roundTwoDecimals(0.34 * fragmentScore);
        double maneuverContribution = roundTwoDecimals(0.33 * maneuverScore);
        double directionChangeContribution = roundTwoDecimals(0.23 * directionChangeScore);
        double tollStructureContribution = 0.0; // 不再参与道路复杂度计算
        double shapeContribution = roundTwoDecimals(0.10 * shapeScore);



        result.setDirectionChangeScore(directionChangeScore);
        result.setFragmentScore(fragmentScore);
        result.setManeuverScore(maneuverScore);
        result.setShapeScore(shapeScore);
        result.setFragmentContribution(fragmentContribution);
        result.setManeuverContribution(maneuverContribution);
        result.setDirectionChangeContribution(directionChangeContribution);
        result.setShapeContribution(shapeContribution);


        // =========================
        // 4. 计算综合复杂度总分
        // =========================
        double complexityScore =
                fragmentContribution
                        + maneuverContribution
                        + directionChangeContribution
                        + shapeContribution;

        complexityScore = roundTwoDecimals(complexityScore);

        result.setComplexityScore(complexityScore);
        result.setComplexityLevel(resolveComplexityLevel(complexityScore));

        String dominantFactor = resolveDominantFactor(
                fragmentContribution,
                maneuverContribution,
                directionChangeContribution,
                shapeContribution
        );

        result.setDominantFactor(dominantFactor);

        result.setSummary(buildSummary(result));
        result.setExplanation(buildExplanation(result));

        return result;


    }

    @Override
    public RouteComplexityAnalysisDTO analyzeRouteResponse(GaodeRouteResponse response) {
        if (response == null || !response.isSuccess() || response.getData() == null
                || response.getData().getPaths() == null || response.getData().getPaths().isEmpty()) {
            RouteComplexityAnalysisDTO result = new RouteComplexityAnalysisDTO();
            result.setComplexityScore(0.0);
            result.setComplexityLevel("低复杂度");
            result.setSummary("未获取到可分析的路径数据");
            return result;
        }

        // 默认分析第一条推荐路径
        GaodeRouteResponse.RoutePath firstPath = response.getData().getPaths().get(0);
        return analyzeRoutePath(firstPath);
    }

    /**
     * 计算路段碎片复杂度
     *
     * 组成：
     * 1. step 数量越多，复杂度越高
     * 2. 平均每段距离越短，复杂度越高
     */
    private double calculateFragmentScore(int stepCount, double averageStepDistance, double shortStepRatio) {
        // 1. step 数量连续评分
        // 25 个 step 左右视为高复杂，超过则封顶
        double stepCountScore = Math.min(stepCount / 25.0, 1.0) * 100.0;

        // 2. 平均 step 距离修正
        // 平均每段越短，说明路线越碎
        double avgDistanceScore;
        if (averageStepDistance <= 0) {
            avgDistanceScore = 0;
        } else {
            // 300m 及以下视为很碎，得分趋近 100
            // 2000m 及以上视为较不碎，得分趋近 0
            double normalized = (2000.0 - averageStepDistance) / 1700.0;
            avgDistanceScore = Math.max(0, Math.min(normalized, 1.0)) * 100.0;
        }

        // 3. 短 step 占比评分
        double shortStepScore = shortStepRatio * 100.0;

        // 4. 综合碎片复杂度
        double fragmentScore =
                0.45 * stepCountScore
                        + 0.25 * avgDistanceScore
                        + 0.30 * shortStepScore;

        return roundTwoDecimals(Math.min(fragmentScore, 100.0));
    }


    /**
     * 计算转向操作复杂度
     *
     * 根据加权转向原始分数分段计分
     */
    private double calculateManeuverScore(double rawScore) {
        if (rawScore <= 5) {
            return 20;
        } else if (rawScore <= 10) {
            return 40;
        } else if (rawScore <= 20) {
            return 60;
        } else if (rawScore <= 30) {
            return 80;
        } else {
            return 100;
        }
    }

    /**
     * 计算收费结构复杂度
     *
     * 以收费路段占比为主，收费金额做少量修正
     */
    private double calculateTollStructureScore(double tolls, double tollDistanceRatio) {
        double ratioScore;

        if (tollDistanceRatio <= 0) {
            ratioScore = 0;
        } else if (tollDistanceRatio <= 0.2) {
            ratioScore = 20;
        } else if (tollDistanceRatio <= 0.5) {
            ratioScore = 50;
        } else if (tollDistanceRatio <= 0.8) {
            ratioScore = 80;
        } else {
            ratioScore = 100;
        }

        double adjustment = 0;
        if (tolls > 50) {
            adjustment = 10;
        } else if (tolls > 20) {
            adjustment = 5;
        }

        return Math.min(100, ratioScore + adjustment);
    }

    /**
     * 计算路线形态复杂度
     *
     * 基于折线点密度
     */
    private double calculateShapeScore(double polylineDensity) {
        if (polylineDensity <= 20) {
            return 20;
        } else if (polylineDensity <= 40) {
            return 40;
        } else if (polylineDensity <= 70) {
            return 60;
        } else if (polylineDensity <= 100) {
            return 80;
        } else {
            return 100;
        }
    }

    /**
     * 复杂度等级
     */
    private String resolveComplexityLevel(double score) {
        if (score < 30) {
            return "低复杂度";
        } else if (score < 60) {
            return "中复杂度";
        } else if (score < 80) {
            return "高复杂度";
        } else {
            return "极高复杂度";
        }
    }

    /**
     * 计算方向变化复杂度
     *
     * 规则：
     * - 普通方向变化：1 次计 1 分
     * - 大幅方向变化：额外加权
     */
    private double calculateDirectionChangeScore(int directionChangeCount, int majorDirectionChangeCount) {
        // 普通方向变化记 1 分，大幅方向变化额外增加权重
        double rawScore = directionChangeCount + majorDirectionChangeCount * 1.5;

        // 8 分左右视为方向变化非常频繁，超过则封顶
        double score = Math.min(rawScore / 8.0, 1.0) * 100.0;

        return roundTwoDecimals(score);
    }


    /**
     * 判断是否属于大幅方向变化
     *
     * 例如：
     * 北 -> 南
     * 东 -> 西
     * 东北 -> 西南
     * 西北 -> 东南
     */
    private boolean isMajorDirectionChange(String from, String to) {
        String pair = from + "->" + to;

        return pair.equals("北->南")
                || pair.equals("南->北")
                || pair.equals("东->西")
                || pair.equals("西->东")
                || pair.equals("东北->西南")
                || pair.equals("西南->东北")
                || pair.equals("东南->西北")
                || pair.equals("西北->东南");
    }

    private String buildExplanation(RouteComplexityAnalysisDTO dto) {
        return String.format(
                "该路线的综合复杂度为 %.2f，等级为%s。其中影响最大的因素是%s。各维度贡献分别为：路段碎片 %.2f，转向操作 %.2f，方向变化 %.2f，路线形态 %.2f。",
                safeDouble(dto.getComplexityScore()),
                dto.getComplexityLevel(),
                dto.getDominantFactor(),
                safeDouble(dto.getFragmentContribution()),
                safeDouble(dto.getManeuverContribution()),
                safeDouble(dto.getDirectionChangeContribution()),
                safeDouble(dto.getShapeContribution())
        );
    }



    private String resolveDominantFactor(double fragmentContribution,
                                         double maneuverContribution,
                                         double directionChangeContribution,
                                         double shapeContribution) {

        double max = fragmentContribution;
        String factor = "路段碎片复杂度";

        if (maneuverContribution > max) {
            max = maneuverContribution;
            factor = "转向操作复杂度";
        }

        if (directionChangeContribution > max) {
            max = directionChangeContribution;
            factor = "方向变化复杂度";
        }

        if (shapeContribution > max) {
            factor = "路线形态复杂度";
        }

        return factor;
    }



    /**
     * 生成简要说明
     */
    private String buildSummary(RouteComplexityAnalysisDTO dto) {
        return String.format(
                "路线包含 %d 个步骤，其中短步骤 %d 个（占比 %.2f），左转 %d 次，右转 %d 次，调头 %d 次，匝道 %d 次，环岛 %d 次，方向变化 %d 次（其中大幅变化 %d 次），综合复杂度为 %.2f，等级：%s。",
                safeInt(dto.getStepCount()),
                safeInt(dto.getShortStepCount()),
                safeDouble(dto.getShortStepRatio()),
                safeInt(dto.getLeftTurnCount()),
                safeInt(dto.getRightTurnCount()),
                safeInt(dto.getUTurnCount()),
                safeInt(dto.getRampCount()),
                safeInt(dto.getRoundaboutCount()),
                safeInt(dto.getDirectionChangeCount()),
                safeInt(dto.getMajorDirectionChangeCount()),
                safeDouble(dto.getComplexityScore()),
                dto.getComplexityLevel()
        );
    }



    /**
     * 统计 polyline 点数
     */
    private int countPolylinePoints(String polyline) {
        if (polyline == null || polyline.trim().isEmpty()) {
            return 0;
        }
        String[] points = polyline.split(";");
        return points.length;
    }

    /**
     * 判断字符串是否包含任一关键词
     */
    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }



    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
