package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.RouteRequestDTO;
import org.example.roadsimulation.dto.RouteResponseDTO;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Route.RouteStatus;
import org.example.roadsimulation.repository.RouteRepository;
import org.example.roadsimulation.service.POIService;
import org.example.roadsimulation.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 路线业务服务实现类
 *
 * 这个类现在只负责：
 * 1. Route 实体的 CRUD
 * 2. Route 实体的查询
 * 3. Route 实体的状态变更
 * 4. Route 实体的统计分析
 * 5. Route 实体的批量操作
 *
 * 注意：
 * 原来放在这里的高德路径规划逻辑已经迁移到 RoutePlanningServiceImpl。
 */
@Service
@Transactional
public class RouteServiceImpl implements RouteService {

    /**
     * 路线仓库
     */
    @Autowired
    private RouteRepository routeRepository;

    /**
     * POI 服务
     */
    @Autowired
    private POIService poiService;

    // =========================
    // 基本 CRUD 操作
    // =========================

    @Override
    @Transactional
    public RouteResponseDTO createRoute(RouteRequestDTO requestDTO) {
        // 校验路线编号唯一性
        if (routeRepository.findByRouteCode(requestDTO.getRouteCode()).isPresent()) {
            throw new IllegalArgumentException("路线编号已存在: " + requestDTO.getRouteCode());
        }

        // 校验起点和终点 POI 是否存在
        validatePOIExists(requestDTO.getStartPoiId(), "起点POI");
        validatePOIExists(requestDTO.getEndPoiId(), "终点POI");
        // Phase 1：创建时必须在旧单位或规范单位中各提供一种，且禁止同一量同时提供两种单位。
        validateRouteMeasureInputs(requestDTO, true);

        // 创建路线实体
        Route route = new Route();
        updateRouteFromDTO(route, requestDTO);

        // 设置关联的 POI
        setRoutePOIs(route, requestDTO);

        Route savedRoute = routeRepository.save(route);
        return convertToDTO(savedRoute);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponseDTO getRouteById(Long id) {
        Route route = findRouteById(id);
        return convertToDTO(route);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponseDTO getRouteByCode(String routeCode) {
        Route route = routeRepository.findByRouteCode(routeCode)
                .orElseThrow(() -> new RuntimeException("未找到路线编号为 " + routeCode + " 的路线"));
        return convertToDTO(route);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponseDTO> getAllRoutes(Pageable pageable) {
        Page<Route> routes = routeRepository.findAll(pageable);
        return routes.map(this::convertToDTO);
    }

    @Override
    @Transactional
    public RouteResponseDTO updateRoute(Long id, RouteRequestDTO requestDTO) {
        Route route = findRouteById(id);

        // Phase 1：更新允许不修改距离/时间，但一旦提供就不能混用旧单位和规范单位。
        validateRouteMeasureInputs(requestDTO, false);

        // 校验路线编号唯一性（排除当前路线）
        if (requestDTO.getRouteCode() != null
                && !requestDTO.getRouteCode().equals(route.getRouteCode())
                && routeRepository.existsByRouteCodeAndIdNot(requestDTO.getRouteCode(), id)) {
            throw new IllegalArgumentException("路线编号已存在: " + requestDTO.getRouteCode());
        }

        // 更新字段
        updateRouteFromDTO(route, requestDTO);

        // 更新关联的 POI
        if (requestDTO.getStartPoiId() != null || requestDTO.getEndPoiId() != null) {
            setRoutePOIs(route, requestDTO);
        }

        Route updatedRoute = routeRepository.save(route);
        return convertToDTO(updatedRoute);
    }

    @Override
    @Transactional
    public void deleteRoute(Long id) {
        Route route = findRouteById(id);

        // 检查是否有关联任务
        if (!route.getAssignments().isEmpty()) {
            throw new IllegalStateException("该路线有关联的任务，无法删除");
        }

        routeRepository.deleteById(id);
    }

    // =========================
    // 查询操作
    // =========================

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getRoutesByStatus(RouteStatus status) {
        List<Route> routes = routeRepository.findByStatus(status);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getRoutesByType(String routeType) {
        List<Route> routes = routeRepository.findByRouteType(routeType);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getRoutesByStartPoi(Long startPoiId) {
        List<Route> routes = routeRepository.findByStartPOIId(startPoiId);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getRoutesByEndPoi(Long endPoiId) {
        List<Route> routes = routeRepository.findByEndPOIId(endPoiId);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> searchRoutes(String keyword) {
        List<Route> routes = routeRepository.searchByNameOrCode(keyword);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> findRoutesBetweenPois(Long startPoiId, Long endPoiId) {
        List<Route> routes = routeRepository.findByStartPOIIdAndEndPOIId(startPoiId, endPoiId);
        return routes.stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparingDouble(RouteResponseDTO::getDistance))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> findShortestRoutes(int limit) {
        Pageable pageable = Pageable.ofSize(limit);
        List<Route> routes = routeRepository.findShortestRoutes(pageable);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> findFastestRoutes(int limit) {
        Pageable pageable = Pageable.ofSize(limit);
        List<Route> routes = routeRepository.findFastestRoutes(pageable);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> findRoutesByDistanceRange(Double minDistance, Double maxDistance) {
        List<Route> routes = routeRepository.findByDistanceBetween(minDistance, maxDistance);
        return routes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // =========================
    // 业务操作
    // =========================

    @Override
    @Transactional
    public RouteResponseDTO activateRoute(Long id) {
        Route route = findRouteById(id);
        route.setStatus(RouteStatus.ACTIVE);
        Route updatedRoute = routeRepository.save(route);
        return convertToDTO(updatedRoute);
    }

    @Override
    @Transactional
    public RouteResponseDTO closeRoute(Long id) {
        Route route = findRouteById(id);
        route.setStatus(RouteStatus.CLOSED);
        Route updatedRoute = routeRepository.save(route);
        return convertToDTO(updatedRoute);
    }

    @Override
    @Transactional
    public RouteResponseDTO markRouteAsCongested(Long id) {
        Route route = findRouteById(id);
        route.setStatus(RouteStatus.CONGESTED);

        // Phase 1：拥堵调整在规范秒域完成，再由 Route 写回兼容小时列。
        Long baseSeconds = route.getEstimatedDrivingSeconds();
        if (baseSeconds != null) {
            route.setEstimatedDrivingSeconds(Math.round(baseSeconds * 1.2));
        }

        Route updatedRoute = routeRepository.save(route);
        return convertToDTO(updatedRoute);
    }

    @Override
    @Transactional
    public RouteResponseDTO markRouteUnderMaintenance(Long id) {
        Route route = findRouteById(id);
        route.setStatus(RouteStatus.UNDER_MAINTENANCE);
        Route updatedRoute = routeRepository.save(route);
        return convertToDTO(updatedRoute);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponseDTO calculateRouteCost(Long id, Double fuelPrice) {
        Route route = findRouteById(id);
        RouteResponseDTO dto = convertToDTO(route);
        dto.setTotalCost(route.calculateTotalCost(fuelPrice));
        return dto;
    }

    // =========================
    // 统计分析
    // =========================

    @Override
    @Transactional(readOnly = true)
    public Map<RouteStatus, Long> getRouteStatistics() {
        List<Object[]> results = routeRepository.countRoutesByStatus();
        return results.stream()
                .collect(Collectors.toMap(
                        result -> (RouteStatus) result[0],
                        result -> (Long) result[1]
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getMostUsedRoutes(int limit) {
        List<Route> allRoutes = routeRepository.findAll();
        return allRoutes.stream()
                .map(this::convertToDTO)
                .sorted((r1, r2) -> r2.getAssignmentCount().compareTo(r1.getAssignmentCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // =========================
    // 批量操作
    // =========================

    @Override
    @Transactional
    public List<RouteResponseDTO> batchCreateRoutes(List<RouteRequestDTO> requestDTOs) {
        List<Route> routes = new ArrayList<>();

        for (RouteRequestDTO dto : requestDTOs) {
            // 校验路线编号唯一性
            if (routeRepository.findByRouteCode(dto.getRouteCode()).isPresent()) {
                throw new IllegalArgumentException("路线编号已存在: " + dto.getRouteCode());
            }

            // Phase 1：批量创建与单条创建使用相同的单位互斥及必填规则。
            validateRouteMeasureInputs(dto, true);

            Route route = new Route();
            updateRouteFromDTO(route, dto);
            setRoutePOIs(route, dto);
            routes.add(route);
        }

        List<Route> savedRoutes = routeRepository.saveAll(routes);
        return savedRoutes.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void batchUpdateStatus(List<Long> routeIds, RouteStatus status) {
        List<Route> routes = routeRepository.findAllById(routeIds);
        for (Route route : routes) {
            route.setStatus(status);
        }
        routeRepository.saveAll(routes);
    }

    // =========================
    // 私有辅助方法
    // =========================

    private Route findRouteById(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到ID为 " + id + " 的路线"));
    }

    private void validatePOIExists(Long poiId, String fieldName) {
        if (poiId != null && !poiService.existsById(poiId)) {
            throw new IllegalArgumentException(fieldName + "不存在: " + poiId);
        }
    }

    private void updateRouteFromDTO(Route route, RouteRequestDTO dto) {
        if (dto.getRouteCode() != null) route.setRouteCode(dto.getRouteCode());
        if (dto.getName() != null) route.setName(dto.getName());
        // Phase 1：服务内部优先处理明确米制字段；旧公里字段只作为兼容输入。
        if (dto.getDistanceMeters() != null) {
            route.setDistanceMeters(dto.getDistanceMeters());
        } else if (dto.getDistance() != null) {
            route.setDistanceKilometers(dto.getDistance());
        }
        // Phase 1：服务内部优先处理明确秒制字段；旧小时字段只作为兼容输入。
        if (dto.getEstimatedDrivingSeconds() != null) {
            route.setEstimatedDrivingSeconds(dto.getEstimatedDrivingSeconds());
        } else if (dto.getEstimatedTime() != null) {
            route.setEstimatedTimeHours(dto.getEstimatedTime());
        }
        if (dto.getDescription() != null) route.setDescription(dto.getDescription());
        if (dto.getStatus() != null) route.setStatus(dto.getStatus());
        if (dto.getRouteType() != null) route.setRouteType(dto.getRouteType());
        if (dto.getTollCost() != null) route.setTollCost(dto.getTollCost());
        if (dto.getFuelConsumption() != null) route.setFuelConsumption(dto.getFuelConsumption());
    }

    private void setRoutePOIs(Route route, RouteRequestDTO dto) {
        if (dto.getStartPoiId() != null) {
            POI startPOI = poiService.getPOIEntityById(dto.getStartPoiId());
            route.setStartPOI(startPOI);
        }
        if (dto.getEndPoiId() != null) {
            POI endPOI = poiService.getPOIEntityById(dto.getEndPoiId());
            route.setEndPOI(endPOI);
        }
    }

    private RouteResponseDTO convertToDTO(Route route) {
        RouteResponseDTO dto = new RouteResponseDTO();
        dto.setId(route.getId());
        dto.setRouteCode(route.getRouteCode());
        dto.setName(route.getName());
        // Phase 1：旧响应字段继续输出公里/小时；DTO 会额外派生米/秒字段。
        dto.setDistance(route.getDistanceKilometers());
        dto.setEstimatedTime(route.getEstimatedTimeHours());
        dto.setDescription(route.getDescription());
        dto.setStatus(route.getStatus());
        dto.setRouteType(route.getRouteType());
        dto.setTollCost(route.getTollCost());
        dto.setFuelConsumption(route.getFuelConsumption());

        // 设置起点 POI 信息
        if (route.getStartPOI() != null) {
            dto.setStartPoiId(route.getStartPOI().getId());
            dto.setStartPoiName(route.getStartPOI().getName());
        }

        // 设置终点 POI 信息
        if (route.getEndPOI() != null) {
            dto.setEndPoiId(route.getEndPOI().getId());
            dto.setEndPoiName(route.getEndPOI().getName());
        }

        // 设置关联任务数量
        dto.setAssignmentCount(route.getAssignments().size());

        // 默认油价 8.0
        dto.setTotalCost(route.calculateTotalCost(8.0));

        return dto;
    }

    /**
     * Phase 1：校验方案 A 的双入口契约。
     * 同一个物理量只能选择旧单位或规范单位，避免出现“哪个字段生效”的隐式优先级。
     */
    private void validateRouteMeasureInputs(RouteRequestDTO dto, boolean required) {
        if (dto == null) {
            throw new IllegalArgumentException("路线请求不能为空");
        }

        boolean hasLegacyDistance = dto.getDistance() != null;
        boolean hasCanonicalDistance = dto.getDistanceMeters() != null;
        if (hasLegacyDistance && hasCanonicalDistance) {
            throw new IllegalArgumentException("distance（公里）与 distanceMeters（米）不能同时提供");
        }
        if (required && !hasLegacyDistance && !hasCanonicalDistance) {
            throw new IllegalArgumentException("必须提供 distance（公里）或 distanceMeters（米）");
        }

        boolean hasLegacyDuration = dto.getEstimatedTime() != null;
        boolean hasCanonicalDuration = dto.getEstimatedDrivingSeconds() != null;
        if (hasLegacyDuration && hasCanonicalDuration) {
            throw new IllegalArgumentException(
                    "estimatedTime（小时）与 estimatedDrivingSeconds（秒）不能同时提供"
            );
        }
        if (required && !hasLegacyDuration && !hasCanonicalDuration) {
            throw new IllegalArgumentException(
                    "必须提供 estimatedTime（小时）或 estimatedDrivingSeconds（秒）"
            );
        }
    }
}
