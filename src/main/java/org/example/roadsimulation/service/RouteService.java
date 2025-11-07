package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.dto.RouteRequestDTO;
import org.example.roadsimulation.dto.RouteResponseDTO;
import org.example.roadsimulation.entity.Route.RouteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface RouteService {

    // 基本CRUD操作
    RouteResponseDTO createRoute(RouteRequestDTO requestDTO);
    RouteResponseDTO getRouteById(Long id);
    RouteResponseDTO getRouteByCode(String routeCode);
    Page<RouteResponseDTO> getAllRoutes(Pageable pageable);
    RouteResponseDTO updateRoute(Long id, RouteRequestDTO requestDTO);
    void deleteRoute(Long id);

    // 查询操作
    List<RouteResponseDTO> getRoutesByStatus(RouteStatus status);
    List<RouteResponseDTO> getRoutesByType(String routeType);
    List<RouteResponseDTO> getRoutesByStartPoi(Long startPoiId);
    List<RouteResponseDTO> getRoutesByEndPoi(Long endPoiId);
    List<RouteResponseDTO> searchRoutes(String keyword);

    // 路线规划相关
    List<RouteResponseDTO> findRoutesBetweenPois(Long startPoiId, Long endPoiId);
    List<RouteResponseDTO> findShortestRoutes(int limit);
    List<RouteResponseDTO> findFastestRoutes(int limit);
    List<RouteResponseDTO> findRoutesByDistanceRange(Double minDistance, Double maxDistance);

    // 业务操作
    RouteResponseDTO activateRoute(Long id);
    RouteResponseDTO closeRoute(Long id);
    RouteResponseDTO markRouteAsCongested(Long id);
    RouteResponseDTO markRouteUnderMaintenance(Long id);
    RouteResponseDTO calculateRouteCost(Long id, Double fuelPrice);

    // 统计分析
    Map<RouteStatus, Long> getRouteStatistics();
    List<RouteResponseDTO> getMostUsedRoutes(int limit);

    // 批量操作
    List<RouteResponseDTO> batchCreateRoutes(List<RouteRequestDTO> requestDTOs);
    void batchUpdateStatus(List<Long> routeIds, RouteStatus status);


    /**
     * 使用高德地图API规划驾车路线
     * @param request 高德路线规划请求
     * @return 高德路线规划响应
     */
    GaodeRouteResponse planRouteWithGaode(GaodeRouteRequest request);

    /**
     * 根据POI坐标规划路线
     * @param startPoiId 起点POI ID
     * @param endPoiId 终点POI ID
     * @param strategy 路线策略：0-最快 1-最经济 2-最短 3-避开高速
     * @return 高德路线规划响应
     */
    GaodeRouteResponse planRouteBetweenPois(Long startPoiId, Long endPoiId, String strategy);

    /**
     * 批量规划路线
     * @param requests 高德路线规划请求列表
     * @return 高德路线规划响应列表
     */
    List<GaodeRouteResponse> batchPlanRoutes(List<GaodeRouteRequest> requests);

    /**
     * 获取POI坐标信息（用于路线规划）
     * @param poiId POI ID
     * @return POI坐标，格式："经度,纬度"
     */
    String getPoiLocation(Long poiId);
}