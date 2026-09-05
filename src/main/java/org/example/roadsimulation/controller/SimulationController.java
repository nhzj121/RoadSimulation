package org.example.roadsimulation.controller;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationModeGuard;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.RuntimeCostDetailDTO;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.example.roadsimulation.dto.TransportMonitorDTO;
import org.example.roadsimulation.dto.VehicleCostSummaryDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.CostBaselineNormalizationService;
import org.example.roadsimulation.service.GaodeRoutePlanningQueueService;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.TransportMonitorService;
import org.example.roadsimulation.service.TransportRuntimePreflightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    // Phase 4：兼容 ACK 与启动前检日志归属当前 Controller，避免误标为车辆初始化服务。
    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);

    @Autowired
    private SimulationMainLoop simulationMainLoop;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private SimulationRuntimeConfig simulationRuntimeConfig;

    @Autowired
    private GetCostService getCostService;

    @Autowired
    private CostBaselineNormalizationService costBaselineNormalizationService;

    @Autowired
    private TransportMonitorService transportMonitorService;

    @Autowired
    private GaodeRoutePlanningQueueService gaodeRoutePlanningQueueService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private SimulationModeGuard simulationModeGuard;

    // Phase 4：以只读一致性检查替代“每次启动无条件 reset”。
    @Autowired
    private TransportRuntimePreflightService transportRuntimePreflightService;

    @Value("${app.simulation.startup-pre-generation.enabled:false}")
    private boolean startupPreGenerationEnabled;

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startSimulation(
            @RequestBody(required = false) StartSimulationRequest request
    ) {
        if (simulationModeGuard.isDispatchComparisonExperimentActive()) {
            return ApiResponse.error("dispatch comparison experiment is active");
        }
        try {
            // Phase 4：正常关闭后数据已清理则直接通过；仅在检出遗留语义冲突时提示 reset。
            transportRuntimePreflightService.assertReadyToStart(simulationMainLoop.getLoopCount());
        } catch (IllegalStateException ex) {
            logger.warn("Phase 4 simulation start preflight rejected: {}", ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
        DispatchStrategy dispatchStrategy = resolveDispatchStrategy(request);
        simulationRuntimeConfig.setDispatchStrategy(dispatchStrategy);
        gaodeRoutePlanningQueueService.resume();

        simulationMainLoop.start();

        DataInitializer.StartupShipmentGenerationResult startupShipmentResult =
                new DataInitializer.StartupShipmentGenerationResult(15);
        if (startupPreGenerationEnabled) {
            startupShipmentResult = dataInitializer.generateStartupProcessingShipments(15);
        } else {
            startupShipmentResult.addFailureReason("startup processing pre-generation is disabled by configuration");
            logger.info("Startup processing pre-generation is disabled. dispatchStrategy={}", dispatchStrategy);
        }

        Map<String, Object> response = buildRuntimeConfigResponse();

        logger.info("Startup processing shipments generated: shipments={}, dispatchStrategy={}",
                startupShipmentResult.getGeneratedCount(),
                dispatchStrategy);
        response.put("startupPreGenerationEnabled", startupPreGenerationEnabled);
        response.put("startupProcessingShipments", startupShipmentResult);
        response.put("startupProcessingShipmentsGenerated",
                startupShipmentResult.getGeneratedCount() > 0);
        response.put("startupProcessingAssignmentsGenerated", false);
        return ApiResponse.success("simulation started", response);
    }

    @PostMapping("/stop")
    public ApiResponse<String> stopSimulation() {
        simulationMainLoop.stop();
        gaodeRoutePlanningQueueService.pauseAndCancelPending();
        return ApiResponse.success("simulation stopped");
    }

    @PostMapping("/reset")
    public ApiResponse<String> resetSimulation() {
        simulationMainLoop.stopForReset();
        try {
            gaodeRoutePlanningQueueService.reset();
            simulationMainLoop.awaitLoopIdleAndResetContext();
            dataInitializer.resetSimulationRuntimeData();
            return ApiResponse.success("simulation reset");
        } finally {
            simulationMainLoop.completeResetLifecycle();
        }
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getSimulationConfig() {
        return ApiResponse.success(buildRuntimeConfigResponse());
    }

    @PostMapping("/config/dispatch-strategy")
    public ApiResponse<Map<String, Object>> updateDispatchStrategy(
            @RequestBody(required = false) StartSimulationRequest request
    ) {
        DispatchStrategy dispatchStrategy = resolveDispatchStrategy(request);
        simulationRuntimeConfig.setDispatchStrategy(dispatchStrategy);
        return ApiResponse.success("dispatch strategy updated", buildRuntimeConfigResponse());
    }

    @PostMapping("/assignment-loaded")
    public ResponseEntity<ApiResponse<AssignmentLoadedResponse>> handleAssignmentLoaded(
            @RequestBody AssignmentLoadedRequest request
    ) {
        try {
            if (request == null || request.getAssignmentId() == null || request.getVehicleId() == null) {
                throw new IllegalArgumentException("request body is required");
            }

            Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Assignment not found: " + request.getAssignmentId()
                    ));
            Vehicle vehicle = requireMatchingAssignmentVehicle(assignment, request.getVehicleId());

            // Phase 4：迁移期兼容确认只读取后端快照，不再完成装货、推进动作索引或改车辆状态。
            logger.info(
                    "[Phase4 Compatibility ACK] assignment-loaded assignmentId={}, vehicleId={}, backendStatus={}",
                    assignment.getId(),
                    vehicle.getId(),
                    assignment.getStatus()
            );

            // Phase 4：前端可能在后端 LOADING 窗口结束前上报；响应使用只读计划载重回退，
            // 避免把当前 0 载重错当成“已装货但为空”，同时不修改任何持久化状态。
            double[] compatibilityLoad = resolveCompatibilityLoadedSnapshot(assignment, vehicle);

            AssignmentLoadedResponse response = new AssignmentLoadedResponse(
                    assignment.getId(),
                    vehicle.getId(),
                    compatibilityLoad[0],
                    compatibilityLoad[1]
            );
            return ResponseEntity.ok(ApiResponse.success("assignment-loaded compatibility acknowledged", response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Assignment loaded request rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Assignment loaded processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("assignment loaded processing failed"));
        }
    }

    @PostMapping("/vehicle-arrived")
    public ResponseEntity<Void> handleVehicleArrived(@RequestBody VehicleArrivedRequest request) {
        try {
            if (request == null || request.getAssignmentId() == null
                    || request.getVehicleId() == null || request.getEndPOIId() == null) {
                throw new IllegalArgumentException("assignmentId, vehicleId and endPOIId are required");
            }
            Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Assignment not found: " + request.getAssignmentId()
                    ));
            Vehicle vehicle = requireMatchingAssignmentVehicle(assignment, request.getVehicleId());

            // Phase 4：前端到达事件只作兼容 ACK；即使后端尚未完成，也不允许它代替路段+卸货链路结算。
            logger.info(
                    "[Phase4 Compatibility ACK] vehicle-arrived assignmentId={}, vehicleId={}, endPOIId={}, backendStatus={}",
                    assignment.getId(),
                    vehicle.getId(),
                    request.getEndPOIId(),
                    assignment.getStatus()
            );

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Vehicle arrival compatibility request rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Vehicle arrival compatibility acknowledgement failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Phase 4：两个兼容端点共用只读车辆归属校验，不包含任何业务状态写入。
     */
    private Vehicle requireMatchingAssignmentVehicle(Assignment assignment, Long vehicleId) {
        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle == null || vehicle.getId() == null) {
            throw new IllegalStateException("No vehicle assigned to assignment: " + assignment.getId());
        }
        if (!vehicle.getId().equals(vehicleId)) {
            throw new IllegalArgumentException("Vehicle does not match assignment: " + vehicleId);
        }
        return vehicle;
    }

    /**
     * Phase 4：只读计算兼容载重。车辆已有后端运行时载重时优先使用，否则汇总本任务未取消货物项。
     */
    private double[] resolveCompatibilityLoadedSnapshot(Assignment assignment, Vehicle vehicle) {
        double currentLoad = vehicle.getCurrentLoadTonnes() == null ? 0.0 : vehicle.getCurrentLoadTonnes();
        double currentVolume = vehicle.getCurrentVolumn() == null ? 0.0 : vehicle.getCurrentVolumn();
        if (currentLoad > 0.0 || currentVolume > 0.0) {
            return new double[]{currentLoad, currentVolume};
        }

        double plannedLoad = 0.0;
        double plannedVolume = 0.0;
        if (assignment.getShipmentItems() != null) {
            for (ShipmentItem item : assignment.getShipmentItems()) {
                if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED
                        || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                    continue;
                }
                plannedLoad += item.getWeightTonnes() == null ? 0.0 : item.getWeightTonnes();
                plannedVolume += item.getVolume() == null ? 0.0 : item.getVolume();
            }
        }
        return new double[]{Math.max(0.0, plannedLoad), Math.max(0.0, plannedVolume)};
    }

    @GetMapping("/costs")
    public RuntimeCostDTO getCurrentCosts() {
        RuntimeCostDTO costs = getCostService.calculateRuntimeCosts(
                vehicleRepository.findAll(),
                assignmentRepository.findRuntimeActiveAssignments()
        );
        costBaselineNormalizationService.applyLatest(costs);
        return costs;
    }

    @GetMapping("/costs/detail")
    public RuntimeCostDetailDTO getCurrentCostDetail() {
        RuntimeCostDetailDTO detail = getCostService.calculateRuntimeCostDetail(
                vehicleRepository.findAll(),
                assignmentRepository.findRuntimeActiveAssignments()
        );
        costBaselineNormalizationService.applyLatest(detail.getSummary());
        detail.setWindow(costBaselineNormalizationService.exportLatestWindowDetail());
        detail.setBaseline(costBaselineNormalizationService.exportCurrentBaselineDetail());
        return detail;
    }

    @GetMapping("/monitor/active")
    public TransportMonitorDTO getActiveTransportMonitor() {
        return transportMonitorService.getActiveMonitor();
    }

    @GetMapping("/vehicle-costs")
    public VehicleCostSummaryDTO getVehicleCosts() {
        long totalTaskCount = shipmentItemRepository.count();
        long unassignedTaskCount = shipmentItemRepository.findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED).size();
        return getCostService.calculateVehicleCostSummary(
                vehicleRepository.findAll(),
                assignmentRepository.findAll(),
                totalTaskCount,
                unassignedTaskCount
        );
    }

    private DispatchStrategy resolveDispatchStrategy(StartSimulationRequest request) {
        if (request == null) {
            return DispatchStrategy.ORIGINAL;
        }

        if (Boolean.TRUE.equals(request.getUseHeuristic())) {
            return DispatchStrategy.HEURISTIC;
        }

        String strategy = request.getStrategy();
        if (strategy == null || strategy.isBlank()) {
            return DispatchStrategy.ORIGINAL;
        }

        try {
            return DispatchStrategy.valueOf(strategy.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DispatchStrategy.ORIGINAL;
        }
    }

    private Map<String, Object> buildRuntimeConfigResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("dispatchStrategy", simulationRuntimeConfig.getDispatchStrategy().name());
        response.put("useHeuristic", simulationRuntimeConfig.useHeuristic());
        response.put("running", simulationMainLoop.isRunning());
        response.put("loopCount", simulationMainLoop.getLoopCount());
        response.put("simNow", simulationMainLoop.getCurrentSimTime());
        response.put("routeQueueSize", gaodeRoutePlanningQueueService.getQueueSize());
        response.put("routeQueuePaused", gaodeRoutePlanningQueueService.isPaused());
        response.put("routeQueueGeneration", gaodeRoutePlanningQueueService.getGeneration());
        response.put("startupPreGenerationEnabled", startupPreGenerationEnabled);
        response.put("startupProcessingShipmentsGenerated", dataInitializer.isStartupProcessingShipmentsGenerated());
        return response;
    }

    public static class StartSimulationRequest {
        private Boolean useHeuristic;
        private String strategy;

        public Boolean getUseHeuristic() {
            return useHeuristic;
        }

        public void setUseHeuristic(Boolean useHeuristic) {
            this.useHeuristic = useHeuristic;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
    }

    public static class VehicleArrivedRequest {
        private Long assignmentId;
        private Long vehicleId;
        private Long endPOIId;

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public Long getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(Long vehicleId) {
            this.vehicleId = vehicleId;
        }

        public Long getEndPOIId() {
            return endPOIId;
        }

        public void setEndPOIId(Long endPOIId) {
            this.endPOIId = endPOIId;
        }
    }

    public static class AssignmentLoadedRequest {
        private Long assignmentId;
        private Long vehicleId;

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public Long getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(Long vehicleId) {
            this.vehicleId = vehicleId;
        }
    }

    public static class AssignmentLoadedResponse {
        private final Long assignmentId;
        private final Long vehicleId;
        private final Double currentLoad;
        private final Double currentVolume;

        public AssignmentLoadedResponse(
                Long assignmentId,
                Long vehicleId,
                Double currentLoad,
                Double currentVolume
        ) {
            this.assignmentId = assignmentId;
            this.vehicleId = vehicleId;
            this.currentLoad = currentLoad;
            this.currentVolume = currentVolume;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public Long getVehicleId() {
            return vehicleId;
        }

        public Double getCurrentLoad() {
            return currentLoad;
        }

        public Double getCurrentVolume() {
            return currentVolume;
        }
    }
}
