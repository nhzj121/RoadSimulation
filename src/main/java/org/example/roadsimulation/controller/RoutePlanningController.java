package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.dto.RouteComplexityAnalysisDTO;
import org.example.roadsimulation.dto.RoutePlanningAnalysisResponseDTO;
import org.example.roadsimulation.service.RouteComplexityAnalysisService;
import org.example.roadsimulation.service.RoutePlanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 路径规划控制器
 *
 * 职责：
 * 1. 对外暴露实时路径规划接口
 * 2. 接收前端传入的路径规划参数
 * 3. 调用 RoutePlanningService 完成路径规划
 * 4. 调用 RouteComplexityAnalysisService 完成道路复杂度分析
 */
@RestController
@RequestMapping("/api/route-planning")
@CrossOrigin(
        origins = {"http://localhost:5173", "http://127.0.0.1:5173"},
        maxAge = 3600,
        allowedHeaders = "*",
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.DELETE,
                RequestMethod.OPTIONS
        }
)
public class RoutePlanningController {

    private static final Logger logger = LoggerFactory.getLogger(RoutePlanningController.class);

    private final RoutePlanningService routePlanningService;
    private final RouteComplexityAnalysisService routeComplexityAnalysisService;

    public RoutePlanningController(RoutePlanningService routePlanningService,
                                   RouteComplexityAnalysisService routeComplexityAnalysisService) {
        this.routePlanningService = routePlanningService;
        this.routeComplexityAnalysisService = routeComplexityAnalysisService;
    }

    /**
     * 直接按坐标做驾车路径规划
     *
     * POST /api/route-planning/driving
     */
    @PostMapping("/driving")
    public ResponseEntity<ApiResponse<GaodeRouteResponse>> planDrivingRoute(@RequestBody GaodeRouteRequest request) {
        try {
            logger.info("收到驾车路径规划请求: {} -> {}", request.getOrigin(), request.getDestination());

            GaodeRouteResponse response = routePlanningService.planDrivingRoute(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }

        } catch (Exception e) {
            logger.error("驾车路径规划异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路线规划服务异常"));
        }
    }

    /**
     * 直接按坐标做驾车路径规划，并分析推荐路线复杂度
     *
     * POST /api/route-planning/driving/analyze
     */
    @PostMapping("/driving/analyze")
    public ResponseEntity<ApiResponse<RoutePlanningAnalysisResponseDTO>> analyzeDrivingRoute(
            @RequestBody GaodeRouteRequest request) {
        try {
            logger.info("收到驾车路径复杂度分析请求: {} -> {}", request.getOrigin(), request.getDestination());

            GaodeRouteResponse response = routePlanningService.planDrivingRoute(request);

            if (!response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }

            RouteComplexityAnalysisDTO analysis =
                    routeComplexityAnalysisService.analyzeRouteResponse(response);

            RoutePlanningAnalysisResponseDTO result = new RoutePlanningAnalysisResponseDTO();
            result.setRouteResponse(response);
            result.setComplexityAnalysis(analysis);

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("驾车路径复杂度分析异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路径复杂度分析服务异常"));
        }
    }

    /**
     * 根据两个 POI 进行驾车路径规划
     *
     * POST /api/route-planning/driving/by-pois?startPoiId=1&endPoiId=2&strategy=32
     */
    @PostMapping("/driving/by-pois")
    public ResponseEntity<ApiResponse<GaodeRouteResponse>> planDrivingRouteByPois(
            @RequestParam Long startPoiId,
            @RequestParam Long endPoiId,
            @RequestParam(defaultValue = "32") String strategy) {
        try {
            logger.info("收到基于 POI 的路径规划请求: startPoiId={}, endPoiId={}, strategy={}",
                    startPoiId, endPoiId, strategy);

            GaodeRouteResponse response =
                    routePlanningService.planDrivingRouteByPois(startPoiId, endPoiId, strategy);

            if (response.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }

        } catch (Exception e) {
            logger.error("根据 POI 进行路径规划异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路线规划服务异常"));
        }
    }

    /**
     * 根据两个 POI 进行驾车路径规划，并分析推荐路线复杂度
     *
     * POST /api/route-planning/driving/by-pois/analyze?startPoiId=1&endPoiId=2&strategy=32
     */
    @PostMapping("/driving/by-pois/analyze")
    public ResponseEntity<ApiResponse<RoutePlanningAnalysisResponseDTO>> analyzeDrivingRouteByPois(
            @RequestParam Long startPoiId,
            @RequestParam Long endPoiId,
            @RequestParam(defaultValue = "32") String strategy) {
        try {
            logger.info("收到基于 POI 的路径复杂度分析请求: startPoiId={}, endPoiId={}, strategy={}",
                    startPoiId, endPoiId, strategy);

            GaodeRouteResponse response =
                    routePlanningService.planDrivingRouteByPois(startPoiId, endPoiId, strategy);

            if (!response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(response.getMessage()));
            }

            RouteComplexityAnalysisDTO analysis =
                    routeComplexityAnalysisService.analyzeRouteResponse(response);

            RoutePlanningAnalysisResponseDTO result = new RoutePlanningAnalysisResponseDTO();
            result.setRouteResponse(response);
            result.setComplexityAnalysis(analysis);

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("根据 POI 的路径复杂度分析异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("路径复杂度分析服务异常"));
        }
    }

    /**
     * 批量驾车路径规划
     *
     * POST /api/route-planning/driving/batch
     */
    @PostMapping("/driving/batch")
    public ResponseEntity<ApiResponse<List<GaodeRouteResponse>>> batchPlanDrivingRoutes(
            @RequestBody List<GaodeRouteRequest> requests) {
        try {
            logger.info("收到批量驾车路径规划请求, 数量={}", requests == null ? 0 : requests.size());

            List<GaodeRouteResponse> responses = routePlanningService.batchPlanDrivingRoutes(requests);

            return ResponseEntity.ok(ApiResponse.success(responses));

        } catch (Exception e) {
            logger.error("批量路径规划异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量路线规划异常"));
        }
    }

    /**
     * 根据 POI ID 获取坐标
     *
     * GET /api/route-planning/poi-location?poiId=1
     */
    @GetMapping("/poi-location")
    public ResponseEntity<ApiResponse<String>> getPoiLocation(@RequestParam Long poiId) {
        try {
            logger.info("查询 POI 坐标, poiId={}", poiId);

            String location = routePlanningService.getPoiLocation(poiId);

            if (location == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("POI 坐标不存在"));
            }

            return ResponseEntity.ok(ApiResponse.success(location));

        } catch (Exception e) {
            logger.error("查询 POI 坐标异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询 POI 坐标异常"));
        }
    }
}
