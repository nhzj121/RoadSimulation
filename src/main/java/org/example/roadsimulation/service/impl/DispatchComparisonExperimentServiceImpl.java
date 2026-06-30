package org.example.roadsimulation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.SimulationDataCleanupService;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.core.SimulationModeGuard;
import org.example.roadsimulation.dto.DispatchComparisonOptionsDTO;
import org.example.roadsimulation.dto.DispatchComparisonPrepareRequest;
import org.example.roadsimulation.dto.DispatchComparisonScenarioDTO;
import org.example.roadsimulation.dto.DispatchComparisonVisualArrivalAckRequest;
import org.example.roadsimulation.dto.DispatchComparisonVisualRunResultDTO;
import org.example.roadsimulation.dto.DispatchComparisonVisualRunStatusDTO;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.CostEntity;
import org.example.roadsimulation.entity.DispatchComparisonCostSnapshot;
import org.example.roadsimulation.entity.DispatchComparisonExperimentRun;
import org.example.roadsimulation.entity.DispatchComparisonExperimentRun.RunStatus;
import org.example.roadsimulation.entity.DispatchComparisonStrategyRun;
import org.example.roadsimulation.entity.DispatchComparisonStrategyRun.StrategyRunStatus;
import org.example.roadsimulation.entity.Enrollment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentLegRepository;
import org.example.roadsimulation.repository.AssignmentNodeRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.DispatchComparisonCostSnapshotRepository;
import org.example.roadsimulation.repository.DispatchComparisonExperimentRunRepository;
import org.example.roadsimulation.repository.DispatchComparisonStrategyRunRepository;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.CostBaselineNormalizationService;
import org.example.roadsimulation.service.DispatchComparisonExperimentService;
import org.example.roadsimulation.service.GaodeRoutePlanningQueueService;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.POIShipmentManager;
import org.example.roadsimulation.service.VehicleInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DispatchComparisonExperimentServiceImpl implements DispatchComparisonExperimentService {

    private static final Logger log = LoggerFactory.getLogger(DispatchComparisonExperimentServiceImpl.class);

    private static final int MAX_EXPERIMENT_SHIPMENTS = 20;
    private static final int MAX_VISUAL_RUN_LOOPS = 360;
    private static final int VISUAL_COMPLETION_FALLBACK_GRACE_LOOPS = 2;
    private static final String FIXED_PLACEMENT_POLICY = "VEHICLE_ID_AND_INITIAL_POI_ID_ROUND_ROBIN";

    private final ReentrantLock visualRunLock = new ReentrantLock(true);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final SimulationContext simulationContext;
    private final SimulationModeGuard simulationModeGuard;
    private final SimulationRuntimeConfig simulationRuntimeConfig;
    private final VehicleRepository vehicleRepository;
    private final POIRepository poiRepository;
    private final GoodsRepository goodsRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentNodeRepository assignmentNodeRepository;
    private final AssignmentLegRepository assignmentLegRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DispatchComparisonExperimentRunRepository experimentRunRepository;
    private final DispatchComparisonStrategyRunRepository strategyRunRepository;
    private final DispatchComparisonCostSnapshotRepository costSnapshotRepository;
    private final VehicleInitializationService vehicleInitializationService;
    private final POIShipmentManager poiShipmentManager;
    private final DataInitializer dataInitializer;
    private final SimulationDataCleanupService cleanupService;
    private final SimulationDispatchRouter simulationDispatchRouter;
    private final StateUpdateService stateUpdateService;
    private final GetCostService getCostService;
    private final CostBaselineNormalizationService costBaselineNormalizationService;
    private final GaodeRoutePlanningQueueService routePlanningQueueService;
    private final TransactionTemplate transactionTemplate;

    private volatile DispatchComparisonScenarioDTO currentScenario;
    private volatile Long activeRunId;
    private volatile Long activeStrategyRunId;
    private volatile List<Long> activeStrategyShipmentItemIds = List.of();
    private volatile int visualCompletionFallbackStartLoop = -1;
    private final Set<Long> visualArrivedAssignmentIds = ConcurrentHashMap.newKeySet();

    public DispatchComparisonExperimentServiceImpl(
            SimulationContext simulationContext,
            SimulationModeGuard simulationModeGuard,
            SimulationRuntimeConfig simulationRuntimeConfig,
            VehicleRepository vehicleRepository,
            POIRepository poiRepository,
            GoodsRepository goodsRepository,
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            AssignmentNodeRepository assignmentNodeRepository,
            AssignmentLegRepository assignmentLegRepository,
            EnrollmentRepository enrollmentRepository,
            DispatchComparisonExperimentRunRepository experimentRunRepository,
            DispatchComparisonStrategyRunRepository strategyRunRepository,
            DispatchComparisonCostSnapshotRepository costSnapshotRepository,
            VehicleInitializationService vehicleInitializationService,
            POIShipmentManager poiShipmentManager,
            DataInitializer dataInitializer,
            SimulationDataCleanupService cleanupService,
            SimulationDispatchRouter simulationDispatchRouter,
            StateUpdateService stateUpdateService,
            GetCostService getCostService,
            CostBaselineNormalizationService costBaselineNormalizationService,
            GaodeRoutePlanningQueueService routePlanningQueueService,
            TransactionTemplate transactionTemplate
    ) {
        this.simulationContext = simulationContext;
        this.simulationModeGuard = simulationModeGuard;
        this.simulationRuntimeConfig = simulationRuntimeConfig;
        this.vehicleRepository = vehicleRepository;
        this.poiRepository = poiRepository;
        this.goodsRepository = goodsRepository;
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentNodeRepository = assignmentNodeRepository;
        this.assignmentLegRepository = assignmentLegRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.experimentRunRepository = experimentRunRepository;
        this.strategyRunRepository = strategyRunRepository;
        this.costSnapshotRepository = costSnapshotRepository;
        this.vehicleInitializationService = vehicleInitializationService;
        this.poiShipmentManager = poiShipmentManager;
        this.dataInitializer = dataInitializer;
        this.cleanupService = cleanupService;
        this.simulationDispatchRouter = simulationDispatchRouter;
        this.stateUpdateService = stateUpdateService;
        this.getCostService = getCostService;
        this.costBaselineNormalizationService = costBaselineNormalizationService;
        this.routePlanningQueueService = routePlanningQueueService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public DispatchComparisonOptionsDTO getPreparationOptions() {
        DispatchComparisonOptionsDTO options = new DispatchComparisonOptionsDTO();

        List<Vehicle> vehicles = vehicleRepository.findAll().stream()
                .sorted(Comparator.comparing(Vehicle::getId))
                .toList();
        options.setVehicleCount(vehicles.size());
        for (Vehicle vehicle : vehicles) {
            DispatchComparisonOptionsDTO.VehicleOption option = new DispatchComparisonOptionsDTO.VehicleOption();
            option.setVehicleId(vehicle.getId());
            option.setLicensePlate(vehicle.getLicensePlate());
            option.setCurrentStatus(vehicle.getCurrentStatus() == null ? "" : vehicle.getCurrentStatus().name());
            POI currentPoi = vehicle.getCurrentPOI();
            if (currentPoi != null) {
                option.setCurrentPoiId(currentPoi.getId());
                option.setCurrentPoiName(currentPoi.getName());
            }
            options.getVehicles().add(option);
        }

        options.setCandidateInitialPoiCount(getFixedInitialPoiCandidates().size());
        options.setPlacementPolicy(FIXED_PLACEMENT_POLICY);
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized DispatchComparisonScenarioDTO prepareScenario(DispatchComparisonPrepareRequest request) {
        validateScenarioCanBePrepared(request);

        List<Vehicle> allVehicles = vehicleRepository.findAll().stream()
                .sorted(Comparator.comparing(Vehicle::getId))
                .toList();
        List<POI> initialPoiCandidates = getFixedInitialPoiCandidates();

        DispatchComparisonScenarioDTO scenario = new DispatchComparisonScenarioDTO();
        scenario.setExperimentId(buildExperimentId());
        scenario.setStatus("PREPARED");
        scenario.setPreparedAt(LocalDateTime.now());
        scenario.setShipmentCount(request.getShipmentCount());
        scenario.setVehicleCount(allVehicles.size());

        for (int i = 0; i < allVehicles.size(); i++) {
            Vehicle vehicle = allVehicles.get(i);
            POI poi = initialPoiCandidates.get(i % initialPoiCandidates.size());
            Vehicle resetVehicle = vehicleInitializationService.resetVehicleToPOI(vehicle.getId(), poi.getId());
            resetVehicle.setCurrentVolumn(0.0);
            vehicleRepository.save(resetVehicle);
            scenario.getVehicleInitialPositions().add(toVehicleSummary(resetVehicle, poi));
        }

        List<ExperimentShipmentTemplate> selectedTemplates = selectTemplates(request.getShipmentCount());
        int index = 0;
        for (ExperimentShipmentTemplate template : selectedTemplates) {
            scenario.getShipments().add(createShipmentFromTemplate(scenario.getExperimentId(), index++, template));
        }

        currentScenario = scenario;
        return scenario;
    }

    @Override
    public DispatchComparisonScenarioDTO getCurrentScenario() {
        return currentScenario;
    }

    @Override
    public synchronized void clearCurrentScenario() {
        if (isVisualRunActive()) {
            throw new IllegalStateException("dispatch comparison experiment is running");
        }
        currentScenario = null;
    }

    @Override
    public DispatchComparisonVisualRunStatusDTO startVisualRun() {
        visualRunLock.lock();
        try {
            if (currentScenario == null || currentScenario.getExperimentId() == null) {
                throw new IllegalStateException("dispatch comparison scenario is not prepared");
            }
            if (simulationContext.isRunning() && !simulationModeGuard.isDispatchComparisonExperimentActive()) {
                throw new IllegalStateException("ordinary simulation is running");
            }
            if (isVisualRunActive()) {
                throw new IllegalStateException("dispatch comparison experiment is already active");
            }

            DispatchComparisonExperimentRun run = transactionTemplate.execute(status -> {
                DispatchComparisonExperimentRun newRun = new DispatchComparisonExperimentRun();
                newRun.setExperimentId(currentScenario.getExperimentId());
                newRun.setStatus(RunStatus.PREPARED);
                newRun.setScenarioJson(writeScenarioJson(currentScenario));
                newRun.setShipmentCount(currentScenario.getShipmentCount());
                newRun.setVehicleCount(currentScenario.getVehicleCount());
                newRun.setTotalItems(currentScenario.getShipmentCount());
                newRun.setMaxLoops(MAX_VISUAL_RUN_LOOPS);
                newRun.setStartedAt(LocalDateTime.now());
                return experimentRunRepository.save(newRun);
            });

            activeRunId = run.getId();
            simulationModeGuard.markDispatchComparisonExperimentActive();
            prepareStrategyRun(run, DispatchStrategy.ORIGINAL);
            return getVisualRunStatus();
        } finally {
            visualRunLock.unlock();
        }
    }

    @Override
    public DispatchComparisonVisualRunStatusDTO pauseVisualRun() {
        visualRunLock.lock();
        try {
            DispatchComparisonExperimentRun run = activeRunOrThrow();
            if (run.getStatus() == RunStatus.RUNNING_ORIGINAL) {
                run.setStatus(RunStatus.PAUSED_ORIGINAL);
            } else if (run.getStatus() == RunStatus.RUNNING_HEURISTIC) {
                run.setStatus(RunStatus.PAUSED_HEURISTIC);
            } else {
                return toStatusDTO(run, null);
            }
            transactionTemplate.executeWithoutResult(status -> {
                experimentRunRepository.save(run);
                updateActiveStrategyStatus(StrategyRunStatus.PAUSED);
            });
            simulationContext.setRunning(false);
            routePlanningQueueService.pauseAndCancelPending();
            return getVisualRunStatus();
        } finally {
            visualRunLock.unlock();
        }
    }

    @Override
    public DispatchComparisonVisualRunStatusDTO resumeVisualRun() {
        visualRunLock.lock();
        try {
            DispatchComparisonExperimentRun run = activeRunOrThrow();
            if (run.getStatus() == RunStatus.PAUSED_ORIGINAL) {
                run.setStatus(RunStatus.RUNNING_ORIGINAL);
            } else if (run.getStatus() == RunStatus.PAUSED_HEURISTIC) {
                run.setStatus(RunStatus.RUNNING_HEURISTIC);
            } else {
                return toStatusDTO(run, null);
            }
            transactionTemplate.executeWithoutResult(status -> {
                experimentRunRepository.save(run);
                updateActiveStrategyStatus(StrategyRunStatus.RUNNING);
            });
            simulationModeGuard.markDispatchComparisonExperimentActive();
            simulationContext.setRunning(true);
            routePlanningQueueService.resume();
            return getVisualRunStatus();
        } finally {
            visualRunLock.unlock();
        }
    }

    @Override
    public DispatchComparisonVisualRunStatusDTO abortVisualRun() {
        visualRunLock.lock();
        try {
            DispatchComparisonExperimentRun run = activeRunOrThrow();
            transactionTemplate.executeWithoutResult(status -> {
                failOrCloseRun(run, RunStatus.ABORTED, "aborted by user");
                updateActiveStrategyStatus(StrategyRunStatus.ABORTED);
            });
            cleanupAfterExperimentStops(run);
            return toStatusDTO(run, latestActiveStrategyRun());
        } finally {
            visualRunLock.unlock();
        }
    }

    @Override
    public DispatchComparisonVisualRunStatusDTO acknowledgeVisualArrival(DispatchComparisonVisualArrivalAckRequest request) {
        if (request == null || request.getAssignmentId() == null) {
            throw new IllegalArgumentException("assignmentId is required");
        }
        visualRunLock.lock();
        try {
            DispatchComparisonExperimentRun run = activeRunOrThrow();
            if (!isRunningStatus(run.getStatus())) {
                return toStatusDTO(run, latestActiveStrategyRun());
            }
            if (currentStrategyAssignmentIds().contains(request.getAssignmentId())) {
                visualArrivedAssignmentIds.add(request.getAssignmentId());
            }
            return toStatusDTO(run, latestActiveStrategyRun());
        } finally {
            visualRunLock.unlock();
        }
    }

    @Override
    public DispatchComparisonVisualRunStatusDTO getVisualRunStatus() {
        DispatchComparisonExperimentRun run = activeRunOrLatest();
        if (run == null) {
            DispatchComparisonVisualRunStatusDTO dto = new DispatchComparisonVisualRunStatusDTO();
            dto.setStatus(currentScenario == null ? "IDLE" : "PREPARED");
            dto.setExperimentId(currentScenario == null ? null : currentScenario.getExperimentId());
            dto.setTotalItems(currentScenario == null ? 0 : currentScenario.getShipmentCount());
            dto.setCompletedItems(0);
            return dto;
        }
        return toStatusDTO(run, latestStrategyForStatus(run));
    }

    @Override
    public DispatchComparisonVisualRunResultDTO getLatestVisualRunResult() {
        DispatchComparisonExperimentRun run = experimentRunRepository.findTopByOrderByCreatedAtDesc().orElse(null);
        if (run == null || run.getStatus() != RunStatus.COMPLETED) {
            return null;
        }

        DispatchComparisonVisualRunResultDTO result = new DispatchComparisonVisualRunResultDTO();
        result.setRunId(run.getId());
        result.setExperimentId(run.getExperimentId());
        result.setStatus(run.getStatus().name());
        result.setStartedAt(run.getStartedAt());
        result.setEndedAt(run.getEndedAt());

        List<DispatchComparisonStrategyRun> strategyRuns =
                strategyRunRepository.findByExperimentRunIdOrderByStartedAtAsc(run.getId());
        for (DispatchComparisonStrategyRun strategyRun : strategyRuns) {
            DispatchComparisonVisualRunResultDTO.StrategyResult strategyResult = toStrategyResult(strategyRun);
            if (DispatchStrategy.ORIGINAL.name().equals(strategyRun.getStrategy())) {
                result.setOriginal(strategyResult);
            } else if (DispatchStrategy.HEURISTIC.name().equals(strategyRun.getStrategy())) {
                result.setHeuristic(strategyResult);
            }
        }
        return result;
    }

    @Scheduled(fixedRate = 4000)
    public void executeVisualRunLoop() {
        if (!simulationModeGuard.isDispatchComparisonExperimentActive()) {
            return;
        }
        if (!visualRunLock.tryLock()) {
            return;
        }
        try {
            DispatchComparisonExperimentRun run = activeRunOrLatest();
            if (run == null || !isRunningStatus(run.getStatus())) {
                return;
            }
            runOneExperimentLoop(run);
        } catch (Exception ex) {
            DispatchComparisonExperimentRun run = activeRunOrLatest();
            if (run != null) {
                log.error(
                        "[DispatchComparisonExperiment] visual run loop failed. runId={}, status={}, strategy={}, loop={}, completedItems={}, totalItems={}, assignmentCount={}, visualAckCount={}, runtimeActiveAssignmentCount={}",
                        run.getId(),
                        run.getStatus(),
                        run.getCurrentStrategy(),
                        run.getCurrentLoop(),
                        run.getCompletedItems(),
                        run.getTotalItems(),
                        safeCurrentStrategyAssignmentCount(),
                        visualArrivedAssignmentIds.size(),
                        safeCurrentStrategyRuntimeActiveAssignmentCount(),
                        ex
                );
                failOrCloseRun(run, RunStatus.FAILED, ex.getMessage());
                cleanupAfterExperimentStops(run);
            }
        } finally {
            visualRunLock.unlock();
        }
    }

    private void runOneExperimentLoop(DispatchComparisonExperimentRun run) {
        DispatchComparisonStrategyRun strategyRun = latestActiveStrategyRun();
        if (strategyRun == null) {
            throw new IllegalStateException("active strategy run is missing");
        }

        int loop = simulationContext.getLoopCount();
        if (loop > safeInt(run.getMaxLoops())) {
            finalizeStrategy(strategyRun, StrategyRunStatus.FAILED);
            failOrCloseRun(run, RunStatus.FAILED, "max loop exceeded");
            cleanupAfterExperimentStops(run);
            return;
        }

        LocalDateTime simNow = simulationContext.getCurrentSimTime();
        if (loop != 0 && loop % 3 == 0) {
            simulationDispatchRouter.dispatch();
            recordCostNormalizationDispatchSnapshot();
        }

        stateUpdateService.tick(simNow, 30, loop);

        int completed = countCompletedActiveItems();
        int total = activeStrategyShipmentItemIds.size();
        RuntimeCostDTO costs = currentRuntimeCosts();
        costBaselineNormalizationService.applyLatest(costs);
        saveCostSnapshot(strategyRun, loop, simNow, completed, total, costs);

        run.setCurrentLoop(loop);
        run.setCompletedItems(completed);
        run.setTotalItems(total);

        strategyRun.setLoopCount(loop);
        strategyRun.setCompletedItems(completed);
        strategyRun.setTotalItems(total);
        transactionTemplate.executeWithoutResult(status -> {
            experimentRunRepository.save(run);
            strategyRunRepository.save(strategyRun);
        });

        if (isCurrentStrategyComplete(completed, total)) {
            finalizeStrategy(strategyRun, StrategyRunStatus.COMPLETED);
            if (DispatchStrategy.ORIGINAL.name().equals(strategyRun.getStrategy())) {
                run.setStatus(RunStatus.REBUILDING_FOR_HEURISTIC);
                transactionTemplate.executeWithoutResult(status -> experimentRunRepository.save(run));
                prepareStrategyRun(run, DispatchStrategy.HEURISTIC);
            } else {
                run.setStatus(RunStatus.COMPLETED);
                run.setEndedAt(LocalDateTime.now());
                transactionTemplate.executeWithoutResult(status -> experimentRunRepository.save(run));
                cleanupAfterExperimentStops(run);
            }
            return;
        }

        simulationContext.incrementLoop();
    }

    private void prepareStrategyRun(DispatchComparisonExperimentRun run, DispatchStrategy strategy) {
        DispatchComparisonScenarioDTO scenario = readScenario(run.getScenarioJson());
        cleanupRuntimeDataForExperiment();
        restoreVehiclesToScenario(scenario);
        List<Long> itemIds = rebuildShipmentsForStrategy(scenario, strategy, run.getId());

        simulationContext.finishReset();
        simulationContext.reset();
        CostEntity.reset();
        costBaselineNormalizationService.reset();
        simulationRuntimeConfig.setDispatchStrategy(strategy);
        routePlanningQueueService.resume();
        simulationContext.setRunning(true);
        stateUpdateService.resetWindowsOnce(simulationContext.getCurrentSimTime(), 30);

        DispatchComparisonStrategyRun strategyRun = new DispatchComparisonStrategyRun();
        strategyRun.setExperimentRun(run);
        strategyRun.setStrategy(strategy.name());
        strategyRun.setStatus(StrategyRunStatus.RUNNING);
        strategyRun.setTotalItems(itemIds.size());
        strategyRun = strategyRunRepository.save(strategyRun);

        activeRunId = run.getId();
        activeStrategyRunId = strategyRun.getId();
        activeStrategyShipmentItemIds = List.copyOf(itemIds);
        visualArrivedAssignmentIds.clear();
        visualCompletionFallbackStartLoop = -1;

        run.setCurrentStrategy(strategy.name());
        run.setCurrentLoop(0);
        run.setCompletedItems(0);
        run.setTotalItems(itemIds.size());
        run.setStatus(strategy == DispatchStrategy.ORIGINAL ? RunStatus.RUNNING_ORIGINAL : RunStatus.RUNNING_HEURISTIC);
        experimentRunRepository.save(run);
    }

    private void finalizeStrategy(DispatchComparisonStrategyRun strategyRun, StrategyRunStatus status) {
        RuntimeCostDTO costs = currentRuntimeCosts();
        costBaselineNormalizationService.applyLatest(costs);
        strategyRun.setStatus(status);
        strategyRun.setEndedAt(LocalDateTime.now());
        strategyRun.setLoopCount(simulationContext.getLoopCount());
        strategyRun.setCompletedItems(countCompletedActiveItems());
        strategyRun.setTotalItems(activeStrategyShipmentItemIds.size());
        strategyRun.setAssignmentCount((int) assignmentRepository.count());
        strategyRun.setVehicleUsedCount(countUsedVehicles());
        copyCosts(strategyRun, costs);
        strategyRunRepository.save(strategyRun);
    }

    private void cleanupAfterExperimentStops(DispatchComparisonExperimentRun run) {
        simulationContext.setRunning(false);
        routePlanningQueueService.reset();
        cleanupRuntimeDataForExperiment();
        restoreVehiclesToScenario(readScenario(run.getScenarioJson()));
        simulationContext.reset();
        CostEntity.reset();
        costBaselineNormalizationService.reset();
        activeRunId = null;
        activeStrategyRunId = null;
        activeStrategyShipmentItemIds = List.of();
        visualArrivedAssignmentIds.clear();
        visualCompletionFallbackStartLoop = -1;
        simulationModeGuard.clearDispatchComparisonExperimentActive();
    }

    private void cleanupRuntimeDataForExperiment() {
        simulationContext.beginReset();
        routePlanningQueueService.reset();
        dataInitializer.clearExperimentRuntimeCaches();
        cleanupService.cleanupAllSimulationData();
        simulationContext.reset();
        simulationContext.finishReset();
    }

    private void restoreVehiclesToScenario(DispatchComparisonScenarioDTO scenario) {
        transactionTemplate.executeWithoutResult(status -> restoreVehiclesToScenarioInTransaction(scenario));
    }

    private void restoreVehiclesToScenarioInTransaction(DispatchComparisonScenarioDTO scenario) {
        LocalDateTime now = LocalDateTime.now();
        for (DispatchComparisonScenarioDTO.VehiclePositionSummary position : scenario.getVehicleInitialPositions()) {
            Vehicle vehicle = vehicleRepository.findById(position.getVehicleId())
                    .orElseThrow(() -> new IllegalStateException("vehicle not found: " + position.getVehicleId()));
            POI poi = poiRepository.findById(position.getPoiId())
                    .orElseThrow(() -> new IllegalStateException("poi not found: " + position.getPoiId()));
            vehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, now, Duration.ZERO);
            vehicle.setPreviousStatus(null);
            vehicle.setCurrentPOI(poi);
            if (poi.getLongitude() != null && poi.getLatitude() != null) {
                vehicle.setCurrentLongitude(poi.getLongitude());
                vehicle.setCurrentLatitude(poi.getLatitude());
            }
            vehicle.setCurrentLoad(0.0);
            vehicle.setCurrentVolumn(0.0);
            vehicle.setUpdatedBy("DispatchComparisonExperiment");
            vehicle.setUpdatedTime(now);
            vehicleRepository.save(vehicle);
        }
    }

    private List<Long> rebuildShipmentsForStrategy(
            DispatchComparisonScenarioDTO scenario,
            DispatchStrategy strategy,
            Long runId
    ) {
        List<Long> itemIds = new ArrayList<>();
        int index = 0;
        for (DispatchComparisonScenarioDTO.ExperimentShipmentSummary source : scenario.getShipments()) {
            POI origin = poiRepository.findById(source.getOriginPoiId())
                    .orElseThrow(() -> new IllegalStateException("origin poi not found: " + source.getOriginPoiId()));
            POI destination = poiRepository.findById(source.getDestinationPoiId())
                    .orElseThrow(() -> new IllegalStateException("destination poi not found: " + source.getDestinationPoiId()));
            Goods goods = resolveGoods(source);
            String refNo = "VIS_" + runId + "_" + strategy.name() + "_" + String.format("%02d", ++index)
                    + "_" + source.getTemplateCode();

            Shipment shipment = new Shipment(refNo, origin, destination, source.getTotalWeight(), source.getTotalVolume());
            shipment.setStatus(Shipment.ShipmentStatus.CREATED);
            shipment.setUpdatedBy("DispatchComparisonExperiment");
            Shipment savedShipment = shipmentRepository.save(shipment);

            ShipmentItem item = new ShipmentItem(
                    savedShipment,
                    source.getGoodsName() == null ? goods.getName() : source.getGoodsName(),
                    source.getQuantity(),
                    source.getGoodsSku(),
                    source.getTotalWeight(),
                    source.getTotalVolume()
            );
            item.setCreatedTime(simulationContext.getCurrentSimTime());
            item.setGoods(goods);
            item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            item.setUpdatedBy("DispatchComparisonExperiment");
            ShipmentItem savedItem = shipmentItemRepository.save(item);
            ensureOriginEnrollment(origin, goods, source.getQuantity());
            poiShipmentManager.registerShipment(origin, destination, savedShipment);
            itemIds.add(savedItem.getId());
        }
        return itemIds;
    }

    private Goods resolveGoods(DispatchComparisonScenarioDTO.ExperimentShipmentSummary source) {
        if (source.getGoodsId() != null) {
            return goodsRepository.findById(source.getGoodsId())
                    .orElseThrow(() -> new IllegalStateException("goods not found: " + source.getGoodsId()));
        }
        return goodsRepository.findBySku(source.getGoodsSku())
                .orElseThrow(() -> new IllegalStateException("goods sku not found: " + source.getGoodsSku()));
    }

    private void validateScenarioCanBePrepared(DispatchComparisonPrepareRequest request) {
        if (simulationModeGuard.isDispatchComparisonExperimentActive()) {
            throw new IllegalStateException("dispatch comparison experiment is active");
        }
        if (simulationContext.isRunning()) {
            throw new IllegalStateException("ordinary simulation is running");
        }
        if (currentScenario != null) {
            throw new IllegalStateException("experiment scenario already exists");
        }
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Integer shipmentCount = request.getShipmentCount();
        if (shipmentCount == null || shipmentCount < 1 || shipmentCount > MAX_EXPERIMENT_SHIPMENTS) {
            throw new IllegalArgumentException("shipmentCount must be between 1 and 20");
        }
        rejectIfRuntimeDataExists();
        validateFixedInitialPoiCandidates();
    }

    private void rejectIfRuntimeDataExists() {
        if (shipmentRepository.count() > 0
                || shipmentItemRepository.count() > 0
                || assignmentRepository.count() > 0
                || assignmentNodeRepository.count() > 0
                || assignmentLegRepository.count() > 0) {
            throw new IllegalStateException("runtime data exists, please reset ordinary simulation first");
        }
    }

    private void validateFixedInitialPoiCandidates() {
        if (vehicleRepository.count() == 0) {
            throw new IllegalStateException("no vehicles available for experiment");
        }
        if (getFixedInitialPoiCandidates().isEmpty()) {
            throw new IllegalStateException("no warehouse or distribution center poi with coordinates");
        }
    }

    private List<POI> getFixedInitialPoiCandidates() {
        List<POI> candidates = new ArrayList<>();
        candidates.addAll(poiRepository.findByPoiType(POI.POIType.WAREHOUSE));
        candidates.addAll(poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER));
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getLongitude() != null && poi.getLatitude() != null)
                .sorted(Comparator.comparing(POI::getId))
                .toList();
    }

    private DispatchComparisonScenarioDTO.VehiclePositionSummary toVehicleSummary(Vehicle vehicle, POI poi) {
        DispatchComparisonScenarioDTO.VehiclePositionSummary summary =
                new DispatchComparisonScenarioDTO.VehiclePositionSummary();
        summary.setVehicleId(vehicle.getId());
        summary.setLicensePlate(vehicle.getLicensePlate());
        summary.setPoiId(poi.getId());
        summary.setPoiName(poi.getName());
        summary.setPoiType(poi.getPoiType() == null ? "" : poi.getPoiType().name());
        return summary;
    }

    private List<ExperimentShipmentTemplate> selectTemplates(int count) {
        List<ExperimentShipmentTemplate> templates = new ArrayList<>(templates());
        Collections.shuffle(templates);
        return templates.subList(0, count);
    }

    private DispatchComparisonScenarioDTO.ExperimentShipmentSummary createShipmentFromTemplate(
            String experimentId,
            int index,
            ExperimentShipmentTemplate template
    ) {
        POI origin = resolvePoi(template.originType(), template.originOffset(), template.code(), "origin");
        POI destination = resolvePoi(template.destinationType(), template.destinationOffset(), template.code(), "destination");
        Goods goods = goodsRepository.findBySku(template.goodsSku())
                .orElseThrow(() -> new IllegalStateException("missing goods sku: " + template.goodsSku()));
        if (goods.getWeightPerUnit() == null || goods.getVolumePerUnit() == null) {
            throw new IllegalStateException("goods missing weight or volume: " + goods.getSku());
        }

        Integer quantity = template.quantity();
        double totalWeight = quantity * goods.getWeightPerUnit();
        double totalVolume = quantity * goods.getVolumePerUnit();
        String refNo = "EXP_" + experimentId + "_" + String.format("%02d", index + 1) + "_" + template.code();

        Shipment shipment = new Shipment(refNo, origin, destination, totalWeight, totalVolume);
        shipment.setStatus(Shipment.ShipmentStatus.CREATED);
        Shipment savedShipment = shipmentRepository.save(shipment);

        ShipmentItem item = new ShipmentItem(savedShipment, goods.getName(), quantity, goods.getSku(), totalWeight, totalVolume);
        item.setGoods(goods);
        item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
        ShipmentItem savedItem = shipmentItemRepository.save(item);

        ensureOriginEnrollment(origin, goods, quantity);
        poiShipmentManager.registerShipment(origin, destination, savedShipment);

        DispatchComparisonScenarioDTO.ExperimentShipmentSummary summary =
                new DispatchComparisonScenarioDTO.ExperimentShipmentSummary();
        summary.setTemplateCode(template.code());
        summary.setShipmentId(savedShipment.getId());
        summary.setShipmentItemId(savedItem.getId());
        summary.setOriginPoiId(origin.getId());
        summary.setOriginPoiName(origin.getName());
        summary.setDestinationPoiId(destination.getId());
        summary.setDestinationPoiName(destination.getName());
        summary.setGoodsId(goods.getId());
        summary.setGoodsSku(goods.getSku());
        summary.setGoodsName(goods.getName());
        summary.setQuantity(quantity);
        summary.setTotalWeight(totalWeight);
        summary.setTotalVolume(totalVolume);
        return summary;
    }

    private POI resolvePoi(POI.POIType type, int offset, String templateCode, String role) {
        List<POI> pois = poiRepository.findByPoiType(type).stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getLongitude() != null && poi.getLatitude() != null)
                .sorted(Comparator.comparing(POI::getId))
                .toList();
        if (pois.isEmpty()) {
            throw new IllegalStateException("missing " + role + " poi type: " + type + ", template=" + templateCode);
        }
        return pois.get(Math.floorMod(offset, pois.size()));
    }

    private void ensureOriginEnrollment(POI origin, Goods goods, Integer quantity) {
        Enrollment enrollment = enrollmentRepository.findByPoiAndGoods(origin, goods)
                .orElseGet(() -> new Enrollment(origin, goods, 0));
        enrollment.setQuantity((enrollment.getQuantity() == null ? 0 : enrollment.getQuantity()) + quantity);
        enrollment.setUpdatedBy("DispatchComparisonExperiment");
        enrollment.setUpdatedTime(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
    }

    private void recordCostNormalizationDispatchSnapshot() {
        RuntimeCostDTO costs = currentRuntimeCosts();
        costBaselineNormalizationService.recordDispatchSnapshot(
                costs,
                activeStrategyShipmentItemIds.size(),
                shipmentItemRepository.findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED).size()
        );
    }

    private RuntimeCostDTO currentRuntimeCosts() {
        return getCostService.calculateRuntimeCosts(
                vehicleRepository.findAll(),
                assignmentRepository.findRuntimeActiveAssignments()
        );
    }

    private void saveCostSnapshot(
            DispatchComparisonStrategyRun strategyRun,
            int loop,
            LocalDateTime simNow,
            int completed,
            int total,
            RuntimeCostDTO costs
    ) {
        DispatchComparisonCostSnapshot snapshot = new DispatchComparisonCostSnapshot();
        snapshot.setStrategyRun(strategyRun);
        snapshot.setLoopCount(loop);
        snapshot.setSimTime(simNow);
        snapshot.setCompletedItems(completed);
        snapshot.setTotalItems(total);
        snapshot.setCostA(costs.getCostA());
        snapshot.setCostB(costs.getCostB());
        snapshot.setCostC(costs.getCostC());
        snapshot.setCostD(costs.getCostD());
        snapshot.setCostE(costs.getCostE());
        snapshot.setAllCost(costs.getAllCost());
        snapshot.setNormalizedAllCost(costs.getNormalizedAllCost());
        costSnapshotRepository.save(snapshot);
    }

    private int countCompletedActiveItems() {
        if (activeStrategyShipmentItemIds.isEmpty()) {
            return 0;
        }
        return (int) shipmentItemRepository.findAllById(activeStrategyShipmentItemIds).stream()
                .filter(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED)
                .count();
    }

    private boolean isCurrentStrategyComplete(int completed, int total) {
        if (total <= 0 || completed < total) {
            visualCompletionFallbackStartLoop = -1;
            return false;
        }
        List<Long> assignmentIds = currentStrategyAssignmentIds();
        List<Long> activeAssignmentIds = currentStrategyRuntimeActiveAssignmentIds();

        if (!assignmentIds.isEmpty()
                && visualArrivedAssignmentIds.containsAll(assignmentIds)
                && activeAssignmentIds.isEmpty()) {
            visualCompletionFallbackStartLoop = -1;
            return true;
        }

        if (activeAssignmentIds.isEmpty()) {
            int loop = simulationContext.getLoopCount();
            if (visualCompletionFallbackStartLoop < 0) {
                visualCompletionFallbackStartLoop = loop;
                return false;
            }
            return loop - visualCompletionFallbackStartLoop >= VISUAL_COMPLETION_FALLBACK_GRACE_LOOPS;
        }

        visualCompletionFallbackStartLoop = -1;
        return false;
    }

    private List<Long> currentStrategyAssignmentIds() {
        if (activeStrategyShipmentItemIds.isEmpty()) {
            return List.of();
        }
        return assignmentRepository.findAssignmentIdsByShipmentItemIds(activeStrategyShipmentItemIds);
    }

    private List<Long> currentStrategyRuntimeActiveAssignmentIds() {
        if (activeStrategyShipmentItemIds.isEmpty()) {
            return List.of();
        }
        return assignmentRepository.findRuntimeActiveAssignmentIdsByShipmentItemIds(activeStrategyShipmentItemIds);
    }

    private int safeCurrentStrategyAssignmentCount() {
        try {
            return currentStrategyAssignmentIds().size();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int safeCurrentStrategyRuntimeActiveAssignmentCount() {
        try {
            return currentStrategyRuntimeActiveAssignmentIds().size();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int countUsedVehicles() {
        Set<Long> vehicleIds = new HashSet<>();
        for (Assignment assignment : assignmentRepository.findAll()) {
            if (assignment.getAssignedVehicle() != null && assignment.getAssignedVehicle().getId() != null) {
                vehicleIds.add(assignment.getAssignedVehicle().getId());
            }
        }
        return vehicleIds.size();
    }

    private void copyCosts(DispatchComparisonStrategyRun strategyRun, RuntimeCostDTO costs) {
        strategyRun.setCostA(costs.getCostA());
        strategyRun.setCostB(costs.getCostB());
        strategyRun.setCostC(costs.getCostC());
        strategyRun.setCostD(costs.getCostD());
        strategyRun.setCostE(costs.getCostE());
        strategyRun.setAllCost(costs.getAllCost());
        strategyRun.setNormalizedCostA(costs.getNormalizedCostA());
        strategyRun.setNormalizedCostB(costs.getNormalizedCostB());
        strategyRun.setNormalizedCostC(costs.getNormalizedCostC());
        strategyRun.setNormalizedCostD(costs.getNormalizedCostD());
        strategyRun.setNormalizedCostE(costs.getNormalizedCostE());
        strategyRun.setNormalizedAllCost(costs.getNormalizedAllCost());
    }

    private void failOrCloseRun(DispatchComparisonExperimentRun run, RunStatus status, String reason) {
        run.setStatus(status);
        run.setEndedAt(LocalDateTime.now());
        run.setFailureReason(reason);
        simulationContext.setRunning(false);
        experimentRunRepository.save(run);
    }

    private void updateActiveStrategyStatus(StrategyRunStatus status) {
        DispatchComparisonStrategyRun strategyRun = latestActiveStrategyRun();
        if (strategyRun != null) {
            strategyRun.setStatus(status);
            if (status == StrategyRunStatus.ABORTED || status == StrategyRunStatus.FAILED) {
                strategyRun.setEndedAt(LocalDateTime.now());
            }
            strategyRunRepository.save(strategyRun);
        }
    }

    private DispatchComparisonExperimentRun activeRunOrThrow() {
        DispatchComparisonExperimentRun run = activeRunOrLatest();
        if (run == null || isTerminalStatus(run.getStatus())) {
            throw new IllegalStateException("no active visual experiment run");
        }
        return run;
    }

    private DispatchComparisonExperimentRun activeRunOrLatest() {
        if (activeRunId != null) {
            return experimentRunRepository.findById(activeRunId).orElse(null);
        }
        Collection<RunStatus> statuses = List.of(
                RunStatus.RUNNING_ORIGINAL,
                RunStatus.PAUSED_ORIGINAL,
                RunStatus.REBUILDING_FOR_HEURISTIC,
                RunStatus.RUNNING_HEURISTIC,
                RunStatus.PAUSED_HEURISTIC
        );
        return experimentRunRepository.findTopByStatusInOrderByCreatedAtDesc(statuses).orElse(null);
    }

    private DispatchComparisonStrategyRun latestActiveStrategyRun() {
        if (activeStrategyRunId != null) {
            return strategyRunRepository.findById(activeStrategyRunId).orElse(null);
        }
        DispatchComparisonExperimentRun run = activeRunOrLatest();
        if (run == null || run.getCurrentStrategy() == null) {
            return null;
        }
        return strategyRunRepository
                .findTopByExperimentRunIdAndStrategyOrderByStartedAtDesc(run.getId(), run.getCurrentStrategy())
                .orElse(null);
    }

    private DispatchComparisonStrategyRun latestStrategyForStatus(DispatchComparisonExperimentRun run) {
        if (run == null || run.getCurrentStrategy() == null) {
            return null;
        }
        return strategyRunRepository
                .findTopByExperimentRunIdAndStrategyOrderByStartedAtDesc(run.getId(), run.getCurrentStrategy())
                .orElse(null);
    }

    private boolean isVisualRunActive() {
        DispatchComparisonExperimentRun run = activeRunOrLatest();
        return run != null && !isTerminalStatus(run.getStatus());
    }

    private boolean isRunningStatus(RunStatus status) {
        return status == RunStatus.RUNNING_ORIGINAL || status == RunStatus.RUNNING_HEURISTIC;
    }

    private boolean isTerminalStatus(RunStatus status) {
        return status == RunStatus.COMPLETED || status == RunStatus.ABORTED || status == RunStatus.FAILED;
    }

    private DispatchComparisonVisualRunStatusDTO toStatusDTO(
            DispatchComparisonExperimentRun run,
            DispatchComparisonStrategyRun strategyRun
    ) {
        DispatchComparisonVisualRunStatusDTO dto = new DispatchComparisonVisualRunStatusDTO();
        dto.setRunId(run.getId());
        dto.setExperimentId(run.getExperimentId());
        dto.setStatus(run.getStatus().name());
        dto.setCurrentStrategy(run.getCurrentStrategy());
        dto.setCurrentLoop(run.getCurrentLoop());
        dto.setMaxLoops(run.getMaxLoops());
        dto.setCompletedItems(run.getCompletedItems());
        dto.setTotalItems(run.getTotalItems());
        dto.setMessage(run.getFailureReason());
        dto.setStartedAt(run.getStartedAt());
        dto.setEndedAt(run.getEndedAt());
        List<Long> assignmentIds = currentStrategyAssignmentIds();
        List<Long> activeAssignmentIds = currentStrategyRuntimeActiveAssignmentIds();
        List<Long> missingVisualArrivalAssignmentIds = assignmentIds.stream()
                .filter(id -> !visualArrivedAssignmentIds.contains(id))
                .toList();
        dto.setAssignmentCount(assignmentIds.size());
        dto.setVisualArrivedAssignmentCount(assignmentIds.size() - missingVisualArrivalAssignmentIds.size());
        dto.setRuntimeActiveAssignmentCount(activeAssignmentIds.size());
        dto.setMissingVisualArrivalAssignmentIds(missingVisualArrivalAssignmentIds);
        if (strategyRun != null) {
            dto.setLatestAllCost(strategyRun.getAllCost());
            dto.setLatestNormalizedAllCost(strategyRun.getNormalizedAllCost());
        }
        return dto;
    }

    private DispatchComparisonVisualRunResultDTO.StrategyResult toStrategyResult(
            DispatchComparisonStrategyRun strategyRun
    ) {
        DispatchComparisonVisualRunResultDTO.StrategyResult result =
                new DispatchComparisonVisualRunResultDTO.StrategyResult();
        result.setStrategy(strategyRun.getStrategy());
        result.setStatus(strategyRun.getStatus().name());
        result.setStartedAt(strategyRun.getStartedAt());
        result.setEndedAt(strategyRun.getEndedAt());
        result.setLoopCount(strategyRun.getLoopCount());
        result.setCompletedItems(strategyRun.getCompletedItems());
        result.setTotalItems(strategyRun.getTotalItems());
        result.setVehicleUsedCount(strategyRun.getVehicleUsedCount());
        result.setAssignmentCount(strategyRun.getAssignmentCount());
        result.setCostA(strategyRun.getCostA());
        result.setCostB(strategyRun.getCostB());
        result.setCostC(strategyRun.getCostC());
        result.setCostD(strategyRun.getCostD());
        result.setCostE(strategyRun.getCostE());
        result.setAllCost(strategyRun.getAllCost());
        result.setNormalizedCostA(strategyRun.getNormalizedCostA());
        result.setNormalizedCostB(strategyRun.getNormalizedCostB());
        result.setNormalizedCostC(strategyRun.getNormalizedCostC());
        result.setNormalizedCostD(strategyRun.getNormalizedCostD());
        result.setNormalizedCostE(strategyRun.getNormalizedCostE());
        result.setNormalizedAllCost(strategyRun.getNormalizedAllCost());

        for (DispatchComparisonCostSnapshot snapshot :
                costSnapshotRepository.findByStrategyRunIdOrderByLoopCountAsc(strategyRun.getId())) {
            DispatchComparisonVisualRunResultDTO.CostPoint point =
                    new DispatchComparisonVisualRunResultDTO.CostPoint();
            point.setLoopCount(snapshot.getLoopCount());
            point.setSimTime(snapshot.getSimTime());
            point.setCompletedItems(snapshot.getCompletedItems());
            point.setTotalItems(snapshot.getTotalItems());
            point.setAllCost(snapshot.getAllCost());
            point.setNormalizedAllCost(snapshot.getNormalizedAllCost());
            result.getCostTrend().add(point);
        }
        return result;
    }

    private String writeScenarioJson(DispatchComparisonScenarioDTO scenario) {
        try {
            return objectMapper.writeValueAsString(scenario);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize experiment scenario", e);
        }
    }

    private DispatchComparisonScenarioDTO readScenario(String scenarioJson) {
        try {
            return objectMapper.readValue(scenarioJson, DispatchComparisonScenarioDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to read experiment scenario", e);
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String buildExperimentId() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<ExperimentShipmentTemplate> templates() {
        return List.of(
                new ExperimentShipmentTemplate("EXP-LOG-01", POI.POIType.TIMBER_YARD, 0, POI.POIType.SAWMILL, 0, "LOG", 8),
                new ExperimentShipmentTemplate("EXP-LOG-02", POI.POIType.TIMBER_YARD, 1, POI.POIType.SAWMILL, 1, "LOG", 10),
                new ExperimentShipmentTemplate("EXP-PLANK-01", POI.POIType.SAWMILL, 0, POI.POIType.BOARD_FACTORY, 0, "PLANK", 10),
                new ExperimentShipmentTemplate("EXP-PLANK-02", POI.POIType.SAWMILL, 1, POI.POIType.BOARD_FACTORY, 1, "PLANK", 12),
                new ExperimentShipmentTemplate("EXP-PANEL-01", POI.POIType.BOARD_FACTORY, 0, POI.POIType.FURNITURE_FACTORY, 0, "PANEL", 12),
                new ExperimentShipmentTemplate("EXP-PANEL-02", POI.POIType.BOARD_FACTORY, 1, POI.POIType.FURNITURE_FACTORY, 1, "PANEL", 14),
                new ExperimentShipmentTemplate("EXP-IRON-ORE-01", POI.POIType.IRON_MINE, 0, POI.POIType.STEEL_MILL, 0, "IRON_ORE", 10),
                new ExperimentShipmentTemplate("EXP-IRON-ORE-02", POI.POIType.IRON_MINE, 1, POI.POIType.STEEL_MILL, 1, "IRON_ORE", 12),
                new ExperimentShipmentTemplate("EXP-STEEL-BILLET-01", POI.POIType.STEEL_MILL, 0, POI.POIType.STEEL_PROCESSING_PLANT, 0, "STEEL_BILLET", 10),
                new ExperimentShipmentTemplate("EXP-STEEL-BILLET-02", POI.POIType.STEEL_MILL, 1, POI.POIType.STEEL_PROCESSING_PLANT, 1, "STEEL_BILLET", 12),
                new ExperimentShipmentTemplate("EXP-STEEL-PRODUCT-01", POI.POIType.STEEL_PROCESSING_PLANT, 0, POI.POIType.WAREHOUSE, 0, "STEEL_PRODUCT", 10),
                new ExperimentShipmentTemplate("EXP-STEEL-PRODUCT-02", POI.POIType.STEEL_PROCESSING_PLANT, 1, POI.POIType.WAREHOUSE, 1, "STEEL_PRODUCT", 12),
                new ExperimentShipmentTemplate("EXP-RUBBER-RAW-01", POI.POIType.WAREHOUSE, 2, POI.POIType.RUBBER_PROCESSING_PLANT, 0, "RUBBER_RAW", 10),
                new ExperimentShipmentTemplate("EXP-RUBBER-RAW-02", POI.POIType.WAREHOUSE, 3, POI.POIType.RUBBER_PROCESSING_PLANT, 1, "RUBBER_RAW", 12),
                new ExperimentShipmentTemplate("EXP-RUBBER-SEMI-01", POI.POIType.RUBBER_PROCESSING_PLANT, 0, POI.POIType.TIRE_MANUFACTURING_PLANT, 0, "RUBBER_SEMI", 10),
                new ExperimentShipmentTemplate("EXP-RUBBER-SEMI-02", POI.POIType.RUBBER_PROCESSING_PLANT, 1, POI.POIType.TIRE_MANUFACTURING_PLANT, 1, "RUBBER_SEMI", 12),
                new ExperimentShipmentTemplate("EXP-TIRE-01", POI.POIType.TIRE_MANUFACTURING_PLANT, 0, POI.POIType.AUTO_ASSEMBLY_PLANT, 0, "TIRE", 10),
                new ExperimentShipmentTemplate("EXP-TIRE-02", POI.POIType.TIRE_MANUFACTURING_PLANT, 1, POI.POIType.AUTO_ASSEMBLY_PLANT, 1, "TIRE", 12),
                new ExperimentShipmentTemplate("EXP-MIX-WOOD-01", POI.POIType.TIMBER_YARD, 2, POI.POIType.BOARD_FACTORY, 2, "LOG", 14),
                new ExperimentShipmentTemplate("EXP-MIX-METAL-01", POI.POIType.IRON_MINE, 2, POI.POIType.STEEL_PROCESSING_PLANT, 2, "IRON_ORE", 14)
        );
    }

    private record ExperimentShipmentTemplate(
            String code,
            POI.POIType originType,
            int originOffset,
            POI.POIType destinationType,
            int destinationOffset,
            String goodsSku,
            Integer quantity
    ) {
    }
}
