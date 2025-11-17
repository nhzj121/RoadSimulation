package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.RouteRequestDTO;
import org.example.roadsimulation.dto.RouteResponseDTO;
import org.example.roadsimulation.entity.Route.RouteStatus;
import org.example.roadsimulation.service.RouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;

import java.util.List;
import java.util.Map;

/**
 * Route Controller
 *
 * 功能：
 * 1. 提供完整的 RESTful API 用于路线管理
 * 2. 支持 CRUD 操作、分页查询、条件查询
 * 3. 支持路线规划、成本计算、统计分析等高级功能
 * 4. 统一的响应格式和异常处理
 */
@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RouteController {

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);

    private final RouteService routeService;

    @Autowired
    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * 创建新路线
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RouteResponseDTO>> createRoute(@RequestBody RouteRequestDTO requestDTO) {
        logger.info("创建新路线，路线编号: {}", requestDTO.getRouteCode());

        try {
            RouteResponseDTO route = routeService.createRoute(requestDTO);
            logger.info("路线创建成功，ID: {}, 编号: {}", route.getId(), route.getRouteCode());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("路线创建成功", route));
        } catch (IllegalArgumentException e) {
            logger.error("路线创建失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 根据ID获取路线详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> getRouteById(@PathVariable Long id) {
        logger.info("查询路线详情，ID: {}", id);

        try {
            RouteResponseDTO route = routeService.getRouteById(id);
            logger.info("路线查询成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("查询成功", route));
        } catch (RuntimeException e) {
            logger.warn("路线不存在，ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 根据路线编号获取路线详情
     */
    @GetMapping("/code/{routeCode}")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> getRouteByCode(@PathVariable String routeCode) {
        logger.info("根据路线编号查询，编号: {}", routeCode);

        try {
            RouteResponseDTO route = routeService.getRouteByCode(routeCode);
            logger.info("路线查询成功，编号: {}", routeCode);
            return ResponseEntity.ok(ApiResponse.success("查询成功", route));
        } catch (RuntimeException e) {
            logger.warn("路线不存在，编号: {}", routeCode);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 更新路线信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> updateRoute(
            @PathVariable Long id,
            @RequestBody RouteRequestDTO requestDTO) {
        logger.info("更新路线信息，ID: {}", id);

        try {
            RouteResponseDTO updatedRoute = routeService.updateRoute(id, requestDTO);
            logger.info("路线信息更新成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("路线信息更新成功", updatedRoute));
        } catch (IllegalArgumentException e) {
            logger.error("路线信息更新失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("路线信息更新失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除路线
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(@PathVariable Long id) {
        logger.info("删除路线，ID: {}", id);

        try {
            routeService.deleteRoute(id);
            logger.info("路线删除成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("路线删除成功", null));
        } catch (IllegalStateException e) {
            logger.error("路线删除失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("路线删除失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取所有路线列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> getAllRoutes() {
        logger.info("获取所有路线列表");

        // 使用分页获取所有数据
        Page<RouteResponseDTO> routesPage = routeService.getAllRoutes(Pageable.unpaged());
        List<RouteResponseDTO> routes = routesPage.getContent();
        logger.info("获取到 {} 条路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 分页查询路线
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<RouteResponseDTO>>> getRoutesByPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.info("分页查询路线，页码: {}, 每页大小: {}, 排序: {}, 方向: {}", page, size, sortBy, direction);

        Sort sort = direction.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<RouteResponseDTO> routePage = routeService.getAllRoutes(pageable);
        logger.info("分页查询成功，总记录数: {}, 总页数: {}", routePage.getTotalElements(), routePage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success("分页查询成功", routePage));
    }

    /**
     * 根据状态查询路线
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> getRoutesByStatus(@PathVariable RouteStatus status) {
        logger.info("根据状态查询路线: {}", status);

        List<RouteResponseDTO> routes = routeService.getRoutesByStatus(status);
        logger.info("状态查询成功，找到 {} 条 {} 状态的路线", routes.size(), status);
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 根据类型查询路线
     */
    @GetMapping("/type/{routeType}")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> getRoutesByType(@PathVariable String routeType) {
        logger.info("根据类型查询路线: {}", routeType);

        List<RouteResponseDTO> routes = routeService.getRoutesByType(routeType);
        logger.info("类型查询成功，找到 {} 条 {} 类型的路线", routes.size(), routeType);
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 根据起点POI查询路线
     */
    @GetMapping("/start-poi/{startPoiId}")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> getRoutesByStartPoi(@PathVariable Long startPoiId) {
        logger.info("根据起点POI查询路线，起点POI ID: {}", startPoiId);

        List<RouteResponseDTO> routes = routeService.getRoutesByStartPoi(startPoiId);
        logger.info("起点POI查询成功，找到 {} 条路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 根据终点POI查询路线
     */
    @GetMapping("/end-poi/{endPoiId}")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> getRoutesByEndPoi(@PathVariable Long endPoiId) {
        logger.info("根据终点POI查询路线，终点POI ID: {}", endPoiId);

        List<RouteResponseDTO> routes = routeService.getRoutesByEndPoi(endPoiId);
        logger.info("终点POI查询成功，找到 {} 条路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 搜索路线（按名称或编号）
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> searchRoutes(@RequestParam String keyword) {
        logger.info("搜索路线，关键词: {}", keyword);

        List<RouteResponseDTO> routes = routeService.searchRoutes(keyword);
        logger.info("搜索成功，找到 {} 条路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("搜索成功", routes));
    }

    /**
     * 查找两点之间的路线
     */
    @GetMapping("/between-pois")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> findRoutesBetweenPois(
            @RequestParam Long startPoiId,
            @RequestParam Long endPoiId) {
        logger.info("查找两点之间的路线，起点POI ID: {}, 终点POI ID: {}", startPoiId, endPoiId);

        List<RouteResponseDTO> routes = routeService.findRoutesBetweenPois(startPoiId, endPoiId);
        logger.info("找到 {} 条路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 查找最短路线
     */
    @GetMapping("/shortest")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> findShortestRoutes(
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("查找最短路线，限制: {}", limit);

        List<RouteResponseDTO> routes = routeService.findShortestRoutes(limit);
        logger.info("找到 {} 条最短路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 查找最快路线
     */
    @GetMapping("/fastest")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> findFastestRoutes(
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("查找最快路线，限制: {}", limit);

        List<RouteResponseDTO> routes = routeService.findFastestRoutes(limit);
        logger.info("找到 {} 条最快路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 根据距离范围查询路线
     */
    @GetMapping("/distance-range")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> findRoutesByDistanceRange(
            @RequestParam Double minDistance,
            @RequestParam Double maxDistance) {
        logger.info("根据距离范围查询路线，最小距离: {}, 最大距离: {}", minDistance, maxDistance);

        List<RouteResponseDTO> routes = routeService.findRoutesByDistanceRange(minDistance, maxDistance);
        logger.info("找到 {} 条路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 激活路线
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> activateRoute(@PathVariable Long id) {
        logger.info("激活路线，ID: {}", id);

        try {
            RouteResponseDTO route = routeService.activateRoute(id);
            logger.info("路线激活成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("路线激活成功", route));
        } catch (RuntimeException e) {
            logger.error("路线激活失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 关闭路线
     */
    @PatchMapping("/{id}/close")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> closeRoute(@PathVariable Long id) {
        logger.info("关闭路线，ID: {}", id);

        try {
            RouteResponseDTO route = routeService.closeRoute(id);
            logger.info("路线关闭成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("路线关闭成功", route));
        } catch (RuntimeException e) {
            logger.error("路线关闭失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 标记路线为拥堵
     */
    @PatchMapping("/{id}/mark-congested")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> markRouteAsCongested(@PathVariable Long id) {
        logger.info("标记路线为拥堵，ID: {}", id);

        try {
            RouteResponseDTO route = routeService.markRouteAsCongested(id);
            logger.info("路线标记为拥堵成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("路线标记为拥堵成功", route));
        } catch (RuntimeException e) {
            logger.error("路线标记为拥堵失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 标记路线为维护中
     */
    @PatchMapping("/{id}/mark-maintenance")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> markRouteUnderMaintenance(@PathVariable Long id) {
        logger.info("标记路线为维护中，ID: {}", id);

        try {
            RouteResponseDTO route = routeService.markRouteUnderMaintenance(id);
            logger.info("路线标记为维护中成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("路线标记为维护中成功", route));
        } catch (RuntimeException e) {
            logger.error("路线标记为维护中失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 计算路线成本
     */
    @GetMapping("/{id}/calculate-cost")
    public ResponseEntity<ApiResponse<RouteResponseDTO>> calculateRouteCost(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8.0") Double fuelPrice) {
        logger.info("计算路线成本，ID: {}, 油价: {}", id, fuelPrice);

        try {
            RouteResponseDTO route = routeService.calculateRouteCost(id, fuelPrice);
            logger.info("路线成本计算成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("成本计算成功", route));
        } catch (RuntimeException e) {
            logger.error("路线成本计算失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取路线统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<RouteStatus, Long>>> getRouteStatistics() {
        logger.info("获取路线统计信息");

        Map<RouteStatus, Long> statistics = routeService.getRouteStatistics();
        logger.info("路线统计获取成功");
        return ResponseEntity.ok(ApiResponse.success("统计查询成功", statistics));
    }

    /**
     * 获取最常用路线
     */
    @GetMapping("/most-used")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> getMostUsedRoutes(
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("获取最常用路线，限制: {}", limit);

        List<RouteResponseDTO> routes = routeService.getMostUsedRoutes(limit);
        logger.info("找到 {} 条最常用路线", routes.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", routes));
    }

    /**
     * 批量创建路线
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<RouteResponseDTO>>> batchCreateRoutes(@RequestBody List<RouteRequestDTO> requestDTOs) {
        logger.info("批量创建路线，数量: {}", requestDTOs.size());

        try {
            List<RouteResponseDTO> routes = routeService.batchCreateRoutes(requestDTOs);
            logger.info("批量创建路线成功，数量: {}", routes.size());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("批量创建路线成功", routes));
        } catch (IllegalArgumentException e) {
            logger.error("批量创建路线失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 批量更新路线状态
     */
    @PatchMapping("/batch/update-status")
    public ResponseEntity<ApiResponse<Void>> batchUpdateStatus(
            @RequestParam List<Long> routeIds,
            @RequestParam RouteStatus status) {
        logger.info("批量更新路线状态，路线数量: {}, 新状态: {}", routeIds.size(), status);

        try {
            routeService.batchUpdateStatus(routeIds, status);
            logger.info("批量更新路线状态成功");
            return ResponseEntity.ok(ApiResponse.success("批量更新状态成功", null));
        } catch (Exception e) {
            logger.error("批量更新路线状态失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量更新失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有路线状态枚举值
     */
    @GetMapping("/statuses")
    public ResponseEntity<ApiResponse<RouteStatus[]>> getRouteStatuses() {
        logger.info("获取路线状态枚举值");

        RouteStatus[] statuses = RouteStatus.values();
        return ResponseEntity.ok(ApiResponse.success("获取状态成功", statuses));
    }

    /**
     * 统一 API 响应格式
     */
    public static class ApiResponse<T> {
        private final boolean success;
        private final String message;
        private final T data;
        private final long timestamp;

        private ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public T getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 全局异常处理
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.error("业务异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("服务器内部错误: " + e.getMessage()));
    }



    /**
     * 高德地图路线规划
     */
    @PostMapping("/gaode/plan")
    public ResponseEntity<ApiResponse<GaodeRouteResponse>> planRouteWithGaode(@RequestBody GaodeRouteRequest request) {
        logger.info("高德路线规划: {} -> {}", request.getOrigin(), request.getDestination());

        try {
            GaodeRouteResponse response = routeService.planRouteWithGaode(request);
            if (response.isSuccess()) {
                logger.info("高德路线规划成功");
                return ResponseEntity.ok(ApiResponse.success("路线规划成功", response));
            } else {
                logger.warn("高德路线规划失败: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }
        } catch (Exception e) {
            logger.error("高德路线规划异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路线规划服务异常"));
        }
    }

    /**
     * 根据POI坐标规划路线
     */
    @GetMapping("/gaode/plan-by-pois")
    public ResponseEntity<ApiResponse<GaodeRouteResponse>> planRouteByPois(
            @RequestParam Long startPoiId,
            @RequestParam Long endPoiId,
            @RequestParam(defaultValue = "0") String strategy) {

        logger.info("根据POI坐标规划路线: {} -> {}, 策略: {}", startPoiId, endPoiId, strategy);

        try {
            GaodeRouteResponse response = routeService.planRouteBetweenPois(startPoiId, endPoiId, strategy);
            if (response.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success("路线规划成功", response));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }
        } catch (Exception e) {
            logger.error("POI坐标路线规划异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路线规划服务异常"));
        }
    }

    /**
     * 直接使用坐标规划路线
     */
    // 前端最可能使用的方法。
    @GetMapping("/gaode/plan-by-coordinates")
    public ResponseEntity<ApiResponse<GaodeRouteResponse>> planRouteByCoordinates(
            @RequestParam String startLon,
            @RequestParam String startLat,
            @RequestParam String endLon,
            @RequestParam String endLat,
            @RequestParam(defaultValue = "0") String strategy) {

        String startLocation = startLon + "," + startLat;
        String endLocation = endLon + "," + endLat;

        logger.info("坐标路线规划: {} -> {}", startLocation, endLocation);

        try {
            GaodeRouteRequest request = new GaodeRouteRequest(startLocation, endLocation);
            request.setStrategy(strategy);

            GaodeRouteResponse response = routeService.planRouteWithGaode(request);
            if (response.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success("路线规划成功", response));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }
        } catch (Exception e) {
            logger.error("坐标路线规划异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路线规划服务异常"));
        }
    }

    /**
     * 批量规划路线
     */
    @PostMapping("/gaode/batch-plan")
    public ResponseEntity<ApiResponse<List<GaodeRouteResponse>>> batchPlanRoutes(
            @RequestBody List<GaodeRouteRequest> requests) {

        logger.info("批量规划路线, 数量: {}", requests.size());

        try {
            List<GaodeRouteResponse> responses = routeService.batchPlanRoutes(requests);
            return ResponseEntity.ok(ApiResponse.success("批量规划成功", responses));
        } catch (Exception e) {
            logger.error("批量规划路线异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量规划异常"));
        }
    }
}