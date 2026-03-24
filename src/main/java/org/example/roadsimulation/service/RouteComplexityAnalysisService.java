package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.dto.RouteComplexityAnalysisDTO;

/**
 * 路线复杂度分析服务接口
 *
 * 职责：
 * 1. 根据高德路径规划结果提取路线结构特征
 * 2. 计算道路复杂度分数
 * 3. 输出复杂度等级和分析结果
 */
public interface RouteComplexityAnalysisService {

    /**
     * 对单条路径进行复杂度分析
     *
     * @param path 高德返回的某一条路径
     * @return 复杂度分析结果
     */
    RouteComplexityAnalysisDTO analyzeRoutePath(GaodeRouteResponse.RoutePath path);

    /**
     * 对整个高德规划结果做复杂度分析
     *
     * 默认取第一条路径（推荐路径）进行分析
     *
     * @param response 高德路径规划响应
     * @return 复杂度分析结果
     */
    RouteComplexityAnalysisDTO analyzeRouteResponse(GaodeRouteResponse response);
}
