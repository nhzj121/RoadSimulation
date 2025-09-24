package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.RouteRequestDTO;
import org.example.roadsimulation.dto.RouteResponseDTO;

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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class RouteServiceImpl implements RouteService {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private POIService poiService; // 假设存在POI服务

    @Override
    @Transactional
    public RouteResponseDTO createRoute(RouteRequestDTO requestDTO) {
        // 验证路线编号唯一性
        if (routeRepository.findByRouteCode(requestDTO.getRouteCode()).isPresent()) {
            throw new IllegalArgumentException("路线编号已存在: " + requestDTO.getRouteCode());
        }

        // 验证起点和终点POI
        validatePOIExists(requestDTO.getStartPoiId(), "起点POI");
        validatePOIExists(requestDTO.getEndPoiId(), "终点POI");

        // 创建路线实体
        Route route = new Route();
        updateRouteFromDTO(route, requestDTO);

        // 设置关联的POI实体
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

        // 验证路线编号唯一性（排除当前路线）
        if (requestDTO.getRouteCode() != null &&
                !requestDTO.getRouteCode().equals(route.getRouteCode()) &&
                routeRepository.existsByRouteCodeAndIdNot(requestDTO.getRouteCode(), id)) {
            throw new IllegalArgumentException("路线编号已存在: " + requestDTO.getRouteCode());
        }

        // 更新字段
        updateRouteFromDTO(route, requestDTO);

        // 更新关联的POI
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

        // 检查是否有任务关联此路线
        if (!route.getAssignments().isEmpty()) {
            throw new IllegalStateException("该路线有关联的任务，无法删除");
        }

        routeRepository.deleteById(id);
    }

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
        // 可以自动调整预计时间（增加20%）
        route.setEstimatedTime(route.getEstimatedTime() * 1.2);
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
        // 需要扩展Repository来支持按使用次数排序
        // 这里简化实现，返回所有路线按任务数量排序
        List<Route> allRoutes = routeRepository.findAll();
        return allRoutes.stream()
                .map(this::convertToDTO)
                .sorted((r1, r2) -> r2.getAssignmentCount().compareTo(r1.getAssignmentCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<RouteResponseDTO> batchCreateRoutes(List<RouteRequestDTO> requestDTOs) {
        List<Route> routes = new ArrayList<>();

        for (RouteRequestDTO dto : requestDTOs) {
            // 验证路线编号唯一性
            if (routeRepository.findByRouteCode(dto.getRouteCode()).isPresent()) {
                throw new IllegalArgumentException("路线编号已存在: " + dto.getRouteCode());
            }

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

    // 私有辅助方法
    private Route findRouteById(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到ID为 " + id + " 的路线"));
    }

    private void validatePOIExists(Long poiId, String fieldName) {
        if (poiId != null) {
            // 这里需要调用POI服务验证POI存在
            if (!poiService.existsById(poiId)) {
                throw new IllegalArgumentException(fieldName + "不存在: " + poiId);
            }
        }
    }

    private void updateRouteFromDTO(Route route, RouteRequestDTO dto) {
        if (dto.getRouteCode() != null) route.setRouteCode(dto.getRouteCode());
        if (dto.getName() != null) route.setName(dto.getName());
        if (dto.getDistance() != null) route.setDistance(dto.getDistance());
        if (dto.getEstimatedTime() != null) route.setEstimatedTime(dto.getEstimatedTime());
        if (dto.getDescription() != null) route.setDescription(dto.getDescription());
        if (dto.getStatus() != null) route.setStatus(dto.getStatus());
        if (dto.getRouteType() != null) route.setRouteType(dto.getRouteType());
        if (dto.getTollCost() != null) route.setTollCost(dto.getTollCost());
        if (dto.getFuelConsumption() != null) route.setFuelConsumption(dto.getFuelConsumption());
    }

    private void setRoutePOIs(Route route, RouteRequestDTO dto) {
        if (dto.getStartPoiId() != null) {
            // POI startPOI = poiService.getPOIEntityById(dto.getStartPoiId());
            // route.setStartPOI(startPOI);
        }
        if (dto.getEndPoiId() != null) {
            // POI endPOI = poiService.getPOIEntityById(dto.getEndPoiId());
            // route.setEndPOI(endPOI);
        }
    }

    private RouteResponseDTO convertToDTO(Route route) {
        RouteResponseDTO dto = new RouteResponseDTO();
        dto.setId(route.getId());
        dto.setRouteCode(route.getRouteCode());
        dto.setName(route.getName());
        dto.setDistance(route.getDistance());
        dto.setEstimatedTime(route.getEstimatedTime());
        dto.setDescription(route.getDescription());
        dto.setStatus(route.getStatus());
        dto.setRouteType(route.getRouteType());
        dto.setTollCost(route.getTollCost());
        dto.setFuelConsumption(route.getFuelConsumption());

        // 设置POI信息
        if (route.getStartPOI() != null) {
            dto.setStartPoiId(route.getStartPOI().getId());
            dto.setStartPoiName(route.getStartPOI().getName()); // 假设POI有name字段
        }

        if (route.getEndPOI() != null) {
            dto.setEndPoiId(route.getEndPOI().getId());
            dto.setEndPoiName(route.getEndPOI().getName());
        }

        // 设置关联任务数量
        dto.setAssignmentCount(route.getAssignments().size());

        // 计算总成本（使用默认油价）
        dto.setTotalCost(route.calculateTotalCost(8.0)); // 假设油价8元/升

        return dto;
    }
}
