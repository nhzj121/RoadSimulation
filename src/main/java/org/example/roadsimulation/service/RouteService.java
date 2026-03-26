package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.RouteRequestDTO;
import org.example.roadsimulation.dto.RouteResponseDTO;
import org.example.roadsimulation.entity.Route.RouteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * 路线业务服务接口
 *
 * 这个接口现在只负责：
 * 1. Route 实体的 CRUD
 * 2. Route 实体的查询
 * 3. Route 实体的状态变更
 * 4. Route 实体的统计分析
 * 5. Route 实体的批量操作
 *
 * 注意：
 * 原来放在这里的“高德实时路径规划”相关方法，
 * 现在已经迁移到 RoutePlanningService 中。
 */
public interface RouteService {

    // =========================
    // 基本 CRUD 操作
    // =========================

    RouteResponseDTO createRoute(RouteRequestDTO requestDTO);

    RouteResponseDTO getRouteById(Long id);

    RouteResponseDTO getRouteByCode(String routeCode);

    Page<RouteResponseDTO> getAllRoutes(Pageable pageable);

    RouteResponseDTO updateRoute(Long id, RouteRequestDTO requestDTO);

    void deleteRoute(Long id);

    // =========================
    // 查询操作
    // =========================

    List<RouteResponseDTO> getRoutesByStatus(RouteStatus status);

    List<RouteResponseDTO> getRoutesByType(String routeType);

    List<RouteResponseDTO> getRoutesByStartPoi(Long startPoiId);

    List<RouteResponseDTO> getRoutesByEndPoi(Long endPoiId);

    List<RouteResponseDTO> searchRoutes(String keyword);

    // =========================
    // 路线查询 / 排序相关
    // =========================

    List<RouteResponseDTO> findRoutesBetweenPois(Long startPoiId, Long endPoiId);

    List<RouteResponseDTO> findShortestRoutes(int limit);

    List<RouteResponseDTO> findFastestRoutes(int limit);

    List<RouteResponseDTO> findRoutesByDistanceRange(Double minDistance, Double maxDistance);

    // =========================
    // 业务操作
    // =========================

    RouteResponseDTO activateRoute(Long id);

    RouteResponseDTO closeRoute(Long id);

    RouteResponseDTO markRouteAsCongested(Long id);

    RouteResponseDTO markRouteUnderMaintenance(Long id);

    RouteResponseDTO calculateRouteCost(Long id, Double fuelPrice);

    // =========================
    // 统计分析
    // =========================

    Map<RouteStatus, Long> getRouteStatistics();

    List<RouteResponseDTO> getMostUsedRoutes(int limit);

    // =========================
    // 批量操作
    // =========================

    List<RouteResponseDTO> batchCreateRoutes(List<RouteRequestDTO> requestDTOs);

    void batchUpdateStatus(List<Long> routeIds, RouteStatus status);
}
