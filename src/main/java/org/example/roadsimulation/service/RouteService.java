package org.example.roadsimulation.service;

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
}