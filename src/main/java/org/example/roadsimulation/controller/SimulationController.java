package org.example.roadsimulation.controller;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.RuntimeCostDTO;
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
import org.example.roadsimulation.service.GaodeRoutePlanningQueueService;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.impl.VehicleInitializationServiceImpl;
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
    private GaodeRoutePlanningQueueService gaodeRoutePlanningQueueService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Value("${app.simulation.startup-pre-generation.enabled:false}")
    private boolean startupPreGenerationEnabled;

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startSimulation(
            @RequestBody(required = false) StartSimulationRequest request
    ) {
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
        dataInitializer.clearFrontendRuntimeAssignments();
        return ApiResponse.success("simulation stopped");
    }

    @PostMapping("/reset")
    public ApiResponse<String> resetSimulation() {
        simulationMainLoop.reset();
        gaodeRoutePlanningQueueService.reset();
        dataInitializer.resetSimulationRuntimeData();
        return ApiResponse.success("simulation reset");
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

    @PostMapping("/vehicle-arrived")
    public ResponseEntity<Void> handleVehicleArrived(@RequestBody VehicleArrivedRequest request) {
        try {
            Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new RuntimeException("Assignment not found: " + request.getAssignmentId()));

            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle == null) {
                throw new RuntimeException("No vehicle assigned to assignment: " + request.getAssignmentId());
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
        return getCostService.calculateRuntimeCosts(vehicleRepository.findAll());
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
}
