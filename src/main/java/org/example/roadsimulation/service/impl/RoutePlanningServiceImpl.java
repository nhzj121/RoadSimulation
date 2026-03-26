package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.service.GaodeMapService;
import org.example.roadsimulation.service.POIService;
import org.example.roadsimulation.service.RoutePlanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 路径规划服务实现类
 */
@Service
public class RoutePlanningServiceImpl implements RoutePlanningService {

    private static final Logger logger = LoggerFactory.getLogger(RoutePlanningServiceImpl.class);

    private final POIService poiService;
    private final GaodeMapService gaodeMapService;

    public RoutePlanningServiceImpl(POIService poiService, GaodeMapService gaodeMapService) {
        this.poiService = poiService;
        this.gaodeMapService = gaodeMapService;
    }

    @Override
    public GaodeRouteResponse planDrivingRoute(GaodeRouteRequest request) {
        // 校验起点是否为空
        if (request.getOrigin() == null || request.getOrigin().trim().isEmpty()) {
            return GaodeRouteResponse.error("起点坐标不能为空");
        }

        // 校验终点是否为空
        if (request.getDestination() == null || request.getDestination().trim().isEmpty()) {
            return GaodeRouteResponse.error("终点坐标不能为空");
        }

        // 校验坐标格式是否合法
        if (!isValidCoordinate(request.getOrigin()) || !isValidCoordinate(request.getDestination())) {
            return GaodeRouteResponse.error("坐标格式不正确，应为：经度,纬度");
        }

        logger.info("驾车路径规划: {} -> {}", request.getOrigin(), request.getDestination());

        // 调用高德底层适配层
        return gaodeMapService.planDrivingRoute(request);
    }

    @Override
    public GaodeRouteResponse planDrivingRouteByPois(Long startPoiId, Long endPoiId, String strategy) {
        // 校验 POI 是否存在
        validatePOIExists(startPoiId, "起点POI");
        validatePOIExists(endPoiId, "终点POI");

        // 获取 POI 坐标
        String startLocation = getPoiLocation(startPoiId);
        String endLocation = getPoiLocation(endPoiId);

        if (startLocation == null || endLocation == null) {
            return GaodeRouteResponse.error("起点或终点POI坐标不存在");
        }

        // 构建请求对象
        GaodeRouteRequest request = new GaodeRouteRequest(startLocation, endLocation);

        // 如果没有传递策略，则使用默认策略
        request.setStrategy(strategy != null ? strategy : "32");

        logger.info("根据 POI 规划路径: startPoiId={}, endPoiId={}, strategy={}", startPoiId, endPoiId, request.getStrategy());

        // 调用高德服务层进行路径规划
        GaodeRouteResponse response = gaodeMapService.planDrivingRoute(request);

        // 计算复杂度
        if (response != null && response.isSuccess()) {
            calculateComplexityScore(response);
        }

        return response;
    }

    @Override
    public List<GaodeRouteResponse> batchPlanDrivingRoutes(List<GaodeRouteRequest> requests) {
        List<GaodeRouteResponse> responses = new ArrayList<>();

        if (requests == null || requests.isEmpty()) {
            return responses;
        }

        for (GaodeRouteRequest request : requests) {
            try {
                GaodeRouteResponse response = planDrivingRoute(request);
                responses.add(response);
            } catch (Exception e) {
                logger.error("批量路径规划失败，请求参数={}", request, e);
                responses.add(GaodeRouteResponse.error("路线规划失败: " + e.getMessage()));
            }
        }

        return responses;
    }

    @Override
    public String getPoiLocation(Long poiId) {
        try {
            POI poi = poiService.getPOIEntityById(poiId);
            if (poi == null) {
                logger.warn("POI 不存在, poiId={}", poiId);
                return null;
            }

            BigDecimal longitude = poi.getLongitude();
            BigDecimal latitude = poi.getLatitude();

            if (longitude == null || latitude == null) {
                logger.warn("POI 坐标不完整, poiId={}", poiId);
                return null;
            }

            return String.format("%.6f,%.6f", longitude, latitude);

        } catch (Exception e) {
            logger.error("获取 POI 坐标失败, poiId={}", poiId, e);
            return null;
        }
    }

    private void validatePOIExists(Long poiId, String fieldName) {
        if (poiId != null && !poiService.existsById(poiId)) {
            throw new IllegalArgumentException(fieldName + "不存在: " + poiId);
        }
    }

    private boolean isValidCoordinate(String coordinate) {
        if (coordinate == null) {
            return false;
        }

        String[] parts = coordinate.split(",");
        if (parts.length != 2) {
            return false;
        }

        try {
            Double.parseDouble(parts[0].trim()); // 经度
            Double.parseDouble(parts[1].trim()); // 纬度
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 计算复杂度分数
    private void calculateComplexityScore(GaodeRouteResponse response) {
        if (response == null || response.getData() == null) {
            return;
        }

        List<GaodeRouteResponse.RoutePath> paths = response.getData().getPaths();
        if (paths != null && !paths.isEmpty()) {
            for (GaodeRouteResponse.RoutePath path : paths) {
                double complexityScore = 0;
                complexityScore += calculateDirectionChangeScore(path);
                complexityScore += calculateRampCountScore(path);
                complexityScore += calculateShortStepScore(path);

                // 设置复杂度得分
                response.getData().setComplexityScore(complexityScore);

                // 根据得分设置复杂度级别
                String complexityLevel = resolveComplexityLevel(complexityScore);
                response.getData().setComplexityLevel(complexityLevel);

                // 设置总结和解释
                response.getData().setSummary(buildSummary(response.getData()));
                response.getData().setExplanation(buildExplanation(response.getData()));
            }
        }
    }

    // 计算方向变化得分
    private double calculateDirectionChangeScore(GaodeRouteResponse.RoutePath path) {
        int directionChangeCount = path.getSteps().size() - 1;
        return directionChangeCount * 10.0;  // 示例计算，按方向变化的步数来计算得分
    }

    // 计算匝道得分
    private double calculateRampCountScore(GaodeRouteResponse.RoutePath path) {
        long rampCount = path.getSteps().stream()
                .filter(step -> step.getRoad() != null && step.getRoad().contains("匝道"))
                .count();
        return rampCount * 15.0;  // 每个匝道增加15分
    }

    // 计算短步骤得分
    private double calculateShortStepScore(GaodeRouteResponse.RoutePath path) {
        int shortStepCount = (int) path.getSteps().stream().filter(step -> step.getDistance() < 1000).count();
        return shortStepCount * 5.0;  // 每个短步骤增加5分
    }

    // 根据复杂度得分计算复杂度级别
    private String resolveComplexityLevel(double complexityScore) {
        if (complexityScore < 30) {
            return "低复杂度";
        } else if (complexityScore < 60) {
            return "中复杂度";
        } else {
            return "高复杂度";
        }
    }

    // 生成总结信息
    private String buildSummary(GaodeRouteResponse.GaodeRouteData data) {
        return String.format("路线包含 %d 个步骤，其中短步骤 %.2f 个（占比 %.2f），左转 %d 次，右转 %d 次，调头 %d 次，匝道 %d 次，环岛 %d 次，方向变化 %d 次（其中大幅变化 %d 次），综合复杂度为 %.2f，等级：%s。",
                data.getStepCount(),
                data.getShortStepCount(), data.getShortStepRatio(),
                data.getLeftTurnCount(), data.getRightTurnCount(),
                data.getUturnCount(), data.getRampCount(),
                data.getRoundaboutCount(), data.getDirectionChangeCount(),
                data.getMajorDirectionChangeCount(),
                data.getComplexityScore(),
                data.getComplexityLevel());
    }

    // 生成解释信息
    private String buildExplanation(GaodeRouteResponse.GaodeRouteData data) {
        return String.format("该路线的综合复杂度为 %.2f，等级为 %s。其中影响最大的因素是 %s。各维度贡献分别为：路段碎片 %.2f，转向操作 %.2f，方向变化 %.2f，路线形态 %.2f。",
                data.getComplexityScore(),
                data.getComplexityLevel(),
                data.getDominantFactor(),
                data.getFragmentContribution(),
                data.getManeuverContribution(),
                data.getDirectionChangeContribution(),
                data.getShapeContribution());
    }
}
