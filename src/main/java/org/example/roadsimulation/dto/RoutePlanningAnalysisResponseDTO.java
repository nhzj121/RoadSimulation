package org.example.roadsimulation.dto;

import lombok.Data;

/**
 * 路径规划 + 复杂度分析 联合返回 DTO
 *
 * 作用：
 * 1. 返回高德路径规划原始结果
 * 2. 返回我们系统自己算出来的复杂度分析结果
 */
@Data
public class RoutePlanningAnalysisResponseDTO {

    /**
     * 高德路径规划结果
     */
    private GaodeRouteResponse routeResponse;

    /**
     * 推荐路径的复杂度分析结果
     */
    private RouteComplexityAnalysisDTO complexityAnalysis;
}
