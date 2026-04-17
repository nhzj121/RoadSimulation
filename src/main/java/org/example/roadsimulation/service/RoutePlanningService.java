package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;

import java.util.List;

/**
 * 路径规划服务接口
 *
 * 这个接口的职责：
 * 1. 对外提供“实时路径规划”能力
 * 2. 协调 POI、坐标校验、高德适配层
 * 3. 不负责 Route 实体 CRUD
 *
 * 注意：
 * 这个接口和 RouteService 要分清：
 *
 * RouteService：
 * - 管理数据库里的 Route 实体
 * - 做 CRUD、状态变更、统计、成本计算
 *
 * RoutePlanningService：
 * - 管理“实时计算出来的路径规划结果”
 * - 调用高德 API
 * - 根据 POI 转坐标后做规划
 * - 批量规划
 */
public interface RoutePlanningService {

    /**
     * 直接根据起点和终点坐标做驾车路径规划
     *
     * 使用场景：
     * - 前端已经拿到了经纬度
     * - 直接传 origin / destination 给后端
     *
     * @param request 路径规划请求参数
     * @return 高德路径规划响应结果
     */
    GaodeRouteResponse planDrivingRoute(GaodeRouteRequest request);

    /**
     * 根据两个 POI 做驾车路径规划
     *
     * 使用场景：
     * - 前端只有起点 POI ID 和终点 POI ID
     * - 后端先查 POI 坐标，再调用高德路径规划
     *
     * @param startPoiId 起点 POI ID
     * @param endPoiId 终点 POI ID
     * @param strategy 路线策略
     * @return 高德路径规划响应结果
     */
    GaodeRouteResponse planDrivingRouteByPois(Long startPoiId, Long endPoiId, String strategy);

    /**
     * 批量驾车路径规划
     *
     * 使用场景：
     * - 一次性规划多条路线
     * - 批量任务预计算
     *
     * @param requests 多个路径规划请求
     * @return 多个路径规划响应结果
     */
    List<GaodeRouteResponse> batchPlanDrivingRoutes(List<GaodeRouteRequest> requests);

    /**
     * 根据 POI ID 获取坐标
     *
     * 返回格式统一为：
     * 经度,纬度
     *
     * 例如：
     * 116.397428,39.90923
     *
     * @param poiId POI ID
     * @return 坐标字符串；如果查不到则返回 null
     */
    String getPoiLocation(Long poiId);

    /**
     * 使用高德地图API规划驾车路线
     * @param request 高德路线规划请求
     * @return 高德路线规划响应
     */
    GaodeRouteResponse planRouteWithGaode(GaodeRouteRequest request);

    GaodeRouteResponse planMultiPointRoute(List<Long> poiIds, String strategy);
}
