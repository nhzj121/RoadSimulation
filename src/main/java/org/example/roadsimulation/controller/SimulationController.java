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
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.CostBaselineNormalizationService;
import org.example.roadsimulation.service.GaodeRoutePlanningQueueService;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.TransportLifecycleService;
import org.example.roadsimulation.service.TransportMonitorService;
import org.example.roadsimulation.service.TransportRandomEventService;
import org.example.roadsimulation.service.impl.VehicleInitializationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(VehicleInitializationServiceImpl.class);

    @Autowired
    private SimulationMainLoop simulationMainLoop;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private POIRepository poiRepository;

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
    private TransportLifecycleService transportLifecycleService;

    @Autowired
    private TransportRandomEventService transportRandomEventService;
    @Autowired private org.example.roadsimulation.service.WeatherEnvironmentService weatherEnvironmentService;
    @Autowired private org.example.roadsimulation.service.DrivingProgressService drivingProgressService;
    @Autowired private org.example.roadsimulation.service.StateTransitionService stateTransitionService;

    @Autowired
    private GaodeRoutePlanningQueueService gaodeRoutePlanningQueueService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private SimulationModeGuard simulationModeGuard;

    @Value("${app.simulation.startup-pre-generation.enabled:false}")
    private boolean startupPreGenerationEnabled;

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startSimulation(
            @RequestBody(required = false) StartSimulationRequest request
    ) {
        if (simulationModeGuard.isDispatchComparisonExperimentActive()) {
            return ApiResponse.error("dispatch comparison experiment is active");
        }
        DispatchStrategy dispatchStrategy = resolveDispatchStrategy(request);
        try {
            simulationMainLoop.startWithWeather(request == null ? null : request.getScenarioId(),
                    request == null ? null : request.getExternalExperimentId(), () -> {
                        simulationRuntimeConfig.setDispatchStrategy(dispatchStrategy);
                        gaodeRoutePlanningQueueService.resume();
                    });
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
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
            if (request == null) {
                throw new IllegalArgumentException("request body is required");
            }

            TransportLifecycleService.LoadingCompletionResult result =
                    transportLifecycleService.markFrontendLoadingCompleted(
                            request.getAssignmentId(),
                            request.getVehicleId(),
                            simulationMainLoop.getCurrentSimTime(),
                            "Frontend assignment-loaded"
                    );

            AssignmentLoadedResponse response = new AssignmentLoadedResponse(
                    result.assignmentId(),
                    result.vehicleId(),
                    result.currentLoad(),
                    result.currentVolume()
            );
            return ResponseEntity.ok(ApiResponse.success("assignment loaded", response));
        } catch (TransportRandomEventService.TransitionBlockedException e) {
            logger.info("Assignment loaded rejected while random event is active: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
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
    @Transactional
    public ResponseEntity<Void> handleVehicleArrived(@RequestBody VehicleArrivedRequest request) {
        try {
            Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new RuntimeException("Assignment not found: " + request.getAssignmentId()));

            if (assignment.getStatus() == Assignment.AssignmentStatus.COMPLETED
                    || assignment.getStatus() == Assignment.AssignmentStatus.CANCELLED
                    || assignment.getStatus() == Assignment.AssignmentStatus.FAILED) {
                logger.info("Vehicle arrival ignored for closed assignment: id={}, status={}",
                        assignment.getId(), assignment.getStatus());
                return ResponseEntity.ok().build();
            }

            Vehicle assignedVehicle = assignment.getAssignedVehicle();
            if (assignedVehicle == null || assignedVehicle.getId() == null) {
                throw new RuntimeException("No vehicle assigned to assignment: " + request.getAssignmentId());
            }
            Vehicle vehicle = vehicleRepository.findByIdForUpdate(assignedVehicle.getId())
                    .orElseThrow(() -> new RuntimeException("Vehicle not found: " + assignedVehicle.getId()));
            if (request.getVehicleId() != null && !request.getVehicleId().equals(vehicle.getId()))
                return ResponseEntity.badRequest().build();
            if (drivingProgressService != null && drivingProgressService.enabled()) {
                var now = simulationMainLoop.getCurrentSimTime();
                var nodes = assignment.getNodes();
                if (nodes != null && !nodes.isEmpty()) {
                    if (request.getLegIndex() == null || request.getPhaseKey() == null)
                        return ResponseEntity.badRequest().build();
                    var node = nodes.stream().filter(n -> java.util.Objects.equals(n.getSequenceIndex(), request.getLegIndex())).findFirst().orElse(null);
                    if (node == null || !java.util.Objects.equals(node.getPoi().getId(), request.getEndPOIId()))
                        return ResponseEntity.badRequest().build();
                    if (node.isCompleted()) return ResponseEntity.ok().build();
                    var p = drivingProgressService.latest(vehicle.getId());
                    if (p == null || !p.getPhaseKey().equals(request.getPhaseKey())
                            || !java.util.Objects.equals(p.getAssignmentId(), assignment.getId())
                            || !java.util.Objects.equals(p.getLegIndex(), request.getLegIndex()))
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    if (!drivingProgressService.canAdvance(vehicle, now)) return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    if (org.example.roadsimulation.service.DrivingProgressService.driving(vehicle.getCurrentStatus()))
                        stateTransitionService.updateVehicleStateWithContext(vehicle, now, 30);
                    return ResponseEntity.ok().build();
                }
                if (vehicle.getCurrentStatus() != Vehicle.VehicleStatus.TRANSPORT_DRIVING
                        && vehicle.getCurrentStatus() != Vehicle.VehicleStatus.UNLOADING)
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                var p = drivingProgressService.settle(vehicle, now);
                if (p == null) p = drivingProgressService.latest(vehicle.getId());
                if (p == null || !java.util.Objects.equals(p.getAssignmentId(), assignment.getId())
                        || p.getDrivingStatus() != Vehicle.VehicleStatus.TRANSPORT_DRIVING
                        || p.getRemainingWorkSeconds() > 1e-7
                        || (request.getPhaseKey() != null && !request.getPhaseKey().equals(p.getPhaseKey())))
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                Long destination = assignment.getDestPOI() != null ? assignment.getDestPOI().getId()
                        : assignment.getRoute() != null && assignment.getRoute().getEndPOI() != null
                        ? assignment.getRoute().getEndPOI().getId() : null;
                if (!java.util.Objects.equals(destination, request.getEndPOIId())) return ResponseEntity.badRequest().build();
                if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.TRANSPORT_DRIVING)
                    stateTransitionService.updateVehicleStateWithContext(vehicle, now, 30);
                return ResponseEntity.ok().build();
            }
            if (transportRandomEventService.isTransitionBlocked(
                    vehicle.getId(), simulationMainLoop.getCurrentSimTime())) {
                logger.info("Vehicle arrival rejected while random event is active: vehicleId={}", vehicle.getId());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            POI endPOI = poiRepository.findById(request.getEndPOIId())
                    .orElseThrow(() -> new RuntimeException("End POI not found: " + request.getEndPOIId()));

            if (assignment.getNodes() != null && !assignment.getNodes().isEmpty()) {
                dataInitializer.processVrpVehicleDelivery(assignment, vehicle, endPOI);
                logger.info("VRP vehicle delivery processed: vehicle={}", vehicle.getLicensePlate());
            } else {
                Route route = assignment.getRoute();
                POI startPOI = route.getStartPOI();
                dataInitializer.processVehicleDelivery(startPOI, vehicle, endPOI);
                logger.info("Single-route vehicle delivery processed: vehicle={}", vehicle.getLicensePlate());
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Vehicle arrival processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        private Long scenarioId;
        private String externalExperimentId;
        public Long getScenarioId() { return scenarioId; }
        public void setScenarioId(Long value) { scenarioId = value; }
        public String getExternalExperimentId() { return externalExperimentId; }
        public void setExternalExperimentId(String value) { externalExperimentId = value; }
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
        private Integer legIndex;
        private String phaseKey;
        public Integer getLegIndex() { return legIndex; }
        public void setLegIndex(Integer value) { legIndex = value; }
        public String getPhaseKey() { return phaseKey; }
        public void setPhaseKey(String value) { phaseKey = value; }
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
