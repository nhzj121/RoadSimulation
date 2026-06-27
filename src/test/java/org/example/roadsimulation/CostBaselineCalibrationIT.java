package org.example.roadsimulation;

import jakarta.persistence.EntityManager;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.config.TimeModuleConfig;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.example.roadsimulation.entity.CostEntity;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingStageRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.impl.VehicleDataImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@EnabledIfSystemProperty(
        named = "baseline.enabled",
        matches = "true",
        disabledReason = "Cost baseline calibration is an opt-in experiment runner."
)
@SpringBootTest(
        classes = CostBaselineCalibrationIT.BaselineCalibrationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class CostBaselineCalibrationIT {

    private static final String BASELINE_VERSION = "dispatch-window-v1";
    private static final int DEFAULT_RUNS = 5;
    private static final int DEFAULT_TOTAL_LOOP_INDEX = 60;
    private static final Path DEFAULT_OUTPUT_DIR = Paths.get("target", "cost-baseline");

    @Autowired
    private SimulationMainLoop simulationMainLoop;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private SimulationContext simulationContext;

    @Autowired
    private SimulationRuntimeConfig simulationRuntimeConfig;

    @Autowired
    private GetCostService getCostService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private POIRepository poiRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private ProcessingChainRepository processingChainRepository;

    @Autowired
    private ProcessingStageRepository processingStageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void calibrateDispatchWindowP90AndP95() throws Exception {
        int runs = positiveIntProperty("baseline.runs", DEFAULT_RUNS);
        int totalLoopIndex = nonNegativeIntProperty("baseline.totalLoops", DEFAULT_TOTAL_LOOP_INDEX);
        Path outputDir = outputDirProperty();

        BaseCounts baseCountsBefore = captureBaseCounts();
        Map<Long, VehicleSnapshot> originalVehicles = snapshotVehicles();
        assertFalse(originalVehicles.isEmpty(), "baseline calibration requires existing vehicle base data");

        List<WindowSample> samples = new ArrayList<>();
        Map<DispatchStrategy, List<Long>> runStartEpochs = new EnumMap<>(DispatchStrategy.class);

        try {
            for (DispatchStrategy strategy : List.of(DispatchStrategy.ORIGINAL, DispatchStrategy.HEURISTIC)) {
                runStartEpochs.put(strategy, new ArrayList<>());
                for (int runIndex = 1; runIndex <= runs; runIndex++) {
                    long runStartEpochMillis = System.currentTimeMillis();
                    runStartEpochs.get(strategy).add(runStartEpochMillis);
                    runCalibration(strategy, runIndex, runStartEpochMillis, totalLoopIndex, samples);
                }
            }

            Map<DispatchStrategy, StrategyBaseline> baselines = computeBaselines(samples);
            writeOutputs(outputDir, runs, totalLoopIndex, samples, baselines, runStartEpochs);

            assertFalse(samples.isEmpty(), "baseline calibration produced no dispatch-window samples");
            assertFalse(baselines.get(DispatchStrategy.ORIGINAL).samples().isEmpty(),
                    "ORIGINAL baseline produced no samples");
            assertFalse(baselines.get(DispatchStrategy.HEURISTIC).samples().isEmpty(),
                    "HEURISTIC baseline produced no samples");
        } finally {
            cleanupRuntimeDataAndRestoreVehicles(originalVehicles);
            assertEquals(baseCountsBefore, captureBaseCounts(),
                    "baseline calibration must not add or remove base data records");
            assertEquals(0L, assignmentRepository.count(),
                    "baseline calibration should leave Assignment runtime data clean");
            assertEquals(0L, shipmentItemRepository.count(),
                    "baseline calibration should leave ShipmentItem runtime data clean");
        }
    }

    private void runCalibration(
            DispatchStrategy strategy,
            int runIndex,
            long runStartEpochMillis,
            int totalLoopIndex,
            List<WindowSample> samples
    ) {
        prepareRun(strategy);

        CostSnapshot previousDispatchSnapshot = null;
        int previousDispatchLoop = -1;
        int windowIndex = 0;

        while (simulationContext.getLoopCount() <= totalLoopIndex) {
            int loopBeforeExecute = simulationContext.getLoopCount();
            simulationMainLoop.executeMainLoop();

            if (isDispatchLoop(loopBeforeExecute)) {
                CostSnapshot currentSnapshot = currentCostSnapshot();
                if (previousDispatchSnapshot != null) {
                    windowIndex++;
                    samples.add(WindowSample.from(
                            strategy,
                            runIndex,
                            runStartEpochMillis,
                            windowIndex,
                            previousDispatchLoop,
                            loopBeforeExecute,
                            previousDispatchSnapshot,
                            currentSnapshot
                    ));
                }
                previousDispatchSnapshot = currentSnapshot;
                previousDispatchLoop = loopBeforeExecute;
            }
        }

        simulationContext.setRunning(false);
    }

    private void prepareRun(DispatchStrategy strategy) {
        simulationMainLoop.awaitLoopIdleAndResetContext();
        dataInitializer.resetSimulationRuntimeData();
        try {
            dataInitializer.run();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize baseline run data", e);
        }
        simulationRuntimeConfig.setDispatchStrategy(strategy);
        simulationContext.finishReset();
        simulationContext.setRunning(true);
    }

    private boolean isDispatchLoop(int loop) {
        return loop != 0 && loop % 3 == 0;
    }

    private CostSnapshot currentCostSnapshot() {
        RuntimeCostDTO costs = getCostService.calculateRuntimeCosts(
                vehicleRepository.findAll(),
                assignmentRepository.findRuntimeActiveAssignments()
        );
        return new CostSnapshot(
                safe(costs.getCostA()),
                safe(costs.getCostB()),
                safe(costs.getCostC()),
                safe(costs.getCostD()),
                safe(costs.getCostE()),
                shipmentItemRepository.count(),
                shipmentItemRepository.findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED).size()
        );
    }

    private Map<Long, VehicleSnapshot> snapshotVehicles() {
        Map<Long, VehicleSnapshot> snapshots = new LinkedHashMap<>();
        for (Vehicle vehicle : vehicleRepository.findAll()) {
            if (vehicle.getId() != null) {
                snapshots.put(vehicle.getId(), VehicleSnapshot.from(vehicle));
            }
        }
        return snapshots;
    }

    private void cleanupRuntimeDataAndRestoreVehicles(Map<Long, VehicleSnapshot> vehicleSnapshots) {
        try {
            simulationContext.setRunning(false);
            simulationMainLoop.awaitLoopIdleAndResetContext();
            dataInitializer.resetSimulationRuntimeData();
        } finally {
            restoreVehicles(vehicleSnapshots);
            CostEntity.reset();
            simulationMainLoop.awaitLoopIdleAndResetContext();
        }
    }

    private void restoreVehicles(Map<Long, VehicleSnapshot> vehicleSnapshots) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            for (VehicleSnapshot snapshot : vehicleSnapshots.values()) {
                Vehicle vehicle = vehicleRepository.findById(snapshot.id())
                        .orElseThrow(() -> new IllegalStateException(
                                "Vehicle missing after baseline calibration: " + snapshot.id()));
                snapshot.applyTo(vehicle, poiRepository);
                vehicleRepository.save(vehicle);
            }
            vehicleRepository.flush();
            entityManager.flush();

            for (VehicleSnapshot snapshot : vehicleSnapshots.values()) {
                entityManager.createNativeQuery("UPDATE vehicle SET updated_time = :updatedTime WHERE id = :id")
                        .setParameter("updatedTime", snapshot.updatedTime())
                        .setParameter("id", snapshot.id())
                        .executeUpdate();
            }
            entityManager.flush();
            entityManager.clear();
        });
    }

    private BaseCounts captureBaseCounts() {
        return new BaseCounts(
                vehicleRepository.count(),
                goodsRepository.count(),
                poiRepository.count(),
                processingChainRepository.count(),
                processingStageRepository.count()
        );
    }

    private Map<DispatchStrategy, StrategyBaseline> computeBaselines(List<WindowSample> samples) {
        Map<DispatchStrategy, StrategyBaseline> baselines = new EnumMap<>(DispatchStrategy.class);
        for (DispatchStrategy strategy : List.of(DispatchStrategy.ORIGINAL, DispatchStrategy.HEURISTIC)) {
            List<WindowSample> strategySamples = samples.stream()
                    .filter(sample -> sample.strategy() == strategy)
                    .toList();
            baselines.put(strategy, new StrategyBaseline(
                    strategySamples,
                    percentileSet(strategySamples, 0.90),
                    percentileSet(strategySamples, 0.95)
            ));
        }
        return baselines;
    }

    private PercentileSet percentileSet(List<WindowSample> samples, double percentile) {
        return new PercentileSet(
                nearestRank(samples.stream().map(WindowSample::unitA).toList(), percentile),
                nearestRank(samples.stream().map(WindowSample::unitB).toList(), percentile),
                nearestRank(samples.stream().map(WindowSample::unitC).toList(), percentile),
                nearestRank(samples.stream().map(WindowSample::unitD).toList(), percentile),
                nearestRank(samples.stream().map(WindowSample::unitE).toList(), percentile)
        );
    }

    private double nearestRank(List<Double> values, double percentile) {
        List<Double> sorted = values.stream()
                .filter(value -> value != null && Double.isFinite(value))
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private void writeOutputs(
            Path outputDir,
            int runs,
            int totalLoopIndex,
            List<WindowSample> samples,
            Map<DispatchStrategy, StrategyBaseline> baselines,
            Map<DispatchStrategy, List<Long>> runStartEpochs
    ) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(
                outputDir.resolve("baseline-window-samples.csv"),
                csv(samples),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                outputDir.resolve("baseline-result.json"),
                json(runs, totalLoopIndex, baselines, runStartEpochs),
                StandardCharsets.UTF_8
        );
    }

    private String csv(List<WindowSample> samples) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",",
                "strategy",
                "runIndex",
                "runStartEpochMillis",
                "windowIndex",
                "startLoop",
                "endLoop",
                "taskScale",
                "costAStart",
                "costAEnd",
                "unitA",
                "costBStart",
                "costBEnd",
                "unitB",
                "costCStart",
                "costCEnd",
                "unitC",
                "costDStart",
                "costDEnd",
                "unitD",
                "costEStart",
                "costEEnd",
                "unitE"
        )).append(System.lineSeparator());

        samples.stream()
                .sorted(Comparator
                        .comparing(WindowSample::strategy)
                        .thenComparingInt(WindowSample::runIndex)
                        .thenComparingInt(WindowSample::windowIndex))
                .forEach(sample -> builder.append(sample.toCsv()).append(System.lineSeparator()));
        return builder.toString();
    }

    private String json(
            int runs,
            int totalLoopIndex,
            Map<DispatchStrategy, StrategyBaseline> baselines,
            Map<DispatchStrategy, List<Long>> runStartEpochs
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"baselineVersion\": \"").append(BASELINE_VERSION).append("\",\n");
        builder.append("  \"runs\": ").append(runs).append(",\n");
        builder.append("  \"totalLoops\": ").append(totalLoopIndex).append(",\n");
        builder.append("  \"loopRange\": \"0..").append(totalLoopIndex).append("\",\n");
        builder.append("  \"strategies\": {\n");
        appendStrategyJson(builder, DispatchStrategy.ORIGINAL, baselines, runStartEpochs, true);
        appendStrategyJson(builder, DispatchStrategy.HEURISTIC, baselines, runStartEpochs, false);
        builder.append("  }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private void appendStrategyJson(
            StringBuilder builder,
            DispatchStrategy strategy,
            Map<DispatchStrategy, StrategyBaseline> baselines,
            Map<DispatchStrategy, List<Long>> runStartEpochs,
            boolean appendComma
    ) {
        StrategyBaseline baseline = baselines.get(strategy);
        builder.append("    \"").append(strategy.name()).append("\": {\n");
        builder.append("      \"sampleCount\": ").append(baseline.samples().size()).append(",\n");
        builder.append("      \"runStartEpochMillis\": ").append(longArray(runStartEpochs.get(strategy))).append(",\n");
        builder.append("      \"p90\": ").append(baseline.p90().toJson()).append(",\n");
        builder.append("      \"p95\": ").append(baseline.p95().toJson()).append("\n");
        builder.append("    }");
        if (appendComma) {
            builder.append(",");
        }
        builder.append("\n");
    }

    private String longArray(List<Long> values) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (Long value : values == null ? List.<Long>of() : values) {
            joiner.add(String.valueOf(value));
        }
        return joiner.toString();
    }

    private int positiveIntProperty(String name, int defaultValue) {
        int value = intProperty(name, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private int nonNegativeIntProperty(String name, int defaultValue) {
        int value = intProperty(name, defaultValue);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private Path outputDirProperty() {
        String value = System.getProperty("baseline.outputDir");
        if (value == null || value.isBlank()) {
            return DEFAULT_OUTPUT_DIR;
        }
        return Paths.get(value.trim());
    }

    private double safe(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return value;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.10f", value);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "org.example.roadsimulation",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            RoadSimulationApplication.class,
                            TimeModuleConfig.class,
                            VehicleDataImportServiceImpl.class
                    }
            )
    )
    static class BaselineCalibrationApplication {
    }

    private record CostSnapshot(
            double costA,
            double costB,
            double costC,
            double costD,
            double costE,
            long totalShipmentItems,
            long notAssignedItems
    ) {
    }

    private record WindowSample(
            DispatchStrategy strategy,
            int runIndex,
            long runStartEpochMillis,
            int windowIndex,
            int startLoop,
            int endLoop,
            long taskScale,
            double costAStart,
            double costAEnd,
            double unitA,
            double costBStart,
            double costBEnd,
            double unitB,
            double costCStart,
            double costCEnd,
            double unitC,
            double costDStart,
            double costDEnd,
            double unitD,
            double costEStart,
            double costEEnd,
            double unitE
    ) {
        static WindowSample from(
                DispatchStrategy strategy,
                int runIndex,
                long runStartEpochMillis,
                int windowIndex,
                int startLoop,
                int endLoop,
                CostSnapshot start,
                CostSnapshot end
        ) {
            long taskScale = Math.max(
                    1L,
                    start.notAssignedItems() + Math.max(0L, end.totalShipmentItems() - start.totalShipmentItems())
            );
            double costAEnd = end.costA();
            double costBEnd = end.costB();
            double costCEnd = end.costC();
            double costDEnd = end.costD();
            double costEEnd = end.costE();
            return new WindowSample(
                    strategy,
                    runIndex,
                    runStartEpochMillis,
                    windowIndex,
                    startLoop,
                    endLoop,
                    taskScale,
                    start.costA(),
                    costAEnd,
                    Math.max(0.0, costAEnd - start.costA()) / taskScale,
                    start.costB(),
                    costBEnd,
                    (start.costB() + costBEnd) / 2.0,
                    start.costC(),
                    costCEnd,
                    Math.max(0.0, costCEnd - start.costC()) / taskScale,
                    start.costD(),
                    costDEnd,
                    Math.max(0.0, costDEnd - start.costD()) / taskScale,
                    start.costE(),
                    costEEnd,
                    (start.costE() + costEEnd) / 2.0
            );
        }

        String toCsv() {
            return String.join(",",
                    strategy.name(),
                    String.valueOf(runIndex),
                    String.valueOf(runStartEpochMillis),
                    String.valueOf(windowIndex),
                    String.valueOf(startLoop),
                    String.valueOf(endLoop),
                    String.valueOf(taskScale),
                    format(costAStart),
                    format(costAEnd),
                    format(unitA),
                    format(costBStart),
                    format(costBEnd),
                    format(unitB),
                    format(costCStart),
                    format(costCEnd),
                    format(unitC),
                    format(costDStart),
                    format(costDEnd),
                    format(unitD),
                    format(costEStart),
                    format(costEEnd),
                    format(unitE)
            );
        }
    }

    private record StrategyBaseline(
            List<WindowSample> samples,
            PercentileSet p90,
            PercentileSet p95
    ) {
    }

    private record PercentileSet(double a, double b, double c, double d, double e) {
        String toJson() {
            return "{ \"a\": " + format(a)
                    + ", \"b\": " + format(b)
                    + ", \"c\": " + format(c)
                    + ", \"d\": " + format(d)
                    + ", \"e\": " + format(e)
                    + " }";
        }
    }

    private record BaseCounts(
            long vehicles,
            long goods,
            long pois,
            long processingChains,
            long processingStages
    ) {
    }

    private record VehicleSnapshot(
            Long id,
            Vehicle.VehicleStatus currentStatus,
            Vehicle.VehicleStatus previousStatus,
            LocalDateTime statusStartTime,
            Long statusDurationSeconds,
            Double currentLoad,
            Double currentVolumn,
            Long currentPoiId,
            BigDecimal currentLongitude,
            BigDecimal currentLatitude,
            Integer loopCount,
            Long loadingWaitTime,
            Long unloadingWaitTime,
            Long waitingAssignmentTime,
            Long emptyDrivingTime,
            Double emptyDrivingDistance,
            Long totalDrivingTime,
            Double totalDrivingDistance,
            Double emptyDistanceMeters,
            Double loadedDistanceMeters,
            Double totalDistanceMeters,
            Long emptyDrivingSeconds,
            Long loadedDrivingSeconds,
            Long totalDrivingSeconds,
            Long loadingWaitSeconds,
            Long unloadingWaitSeconds,
            Long waitingAssignmentSeconds,
            String updatedBy,
            LocalDateTime updatedTime
    ) {
        static VehicleSnapshot from(Vehicle vehicle) {
            return new VehicleSnapshot(
                    vehicle.getId(),
                    vehicle.getCurrentStatus(),
                    vehicle.getPreviousStatus(),
                    vehicle.getStatusStartTime(),
                    vehicle.getStatusDurationSeconds(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCurrentVolumn(),
                    vehicle.getCurrentPOI() == null ? null : vehicle.getCurrentPOI().getId(),
                    vehicle.getCurrentLongitude(),
                    vehicle.getCurrentLatitude(),
                    vehicle.getLoopCount(),
                    vehicle.getLoadingWaitTime(),
                    vehicle.getUnloadingWaitTime(),
                    vehicle.getWaitingAssignmentTime(),
                    vehicle.getEmptyDrivingTime(),
                    vehicle.getEmptyDrivingDistance(),
                    vehicle.getTotalDrivingTime(),
                    vehicle.getTotalDrivingDistance(),
                    vehicle.getEmptyDistanceMeters(),
                    vehicle.getLoadedDistanceMeters(),
                    vehicle.getTotalDistanceMeters(),
                    vehicle.getEmptyDrivingSeconds(),
                    vehicle.getLoadedDrivingSeconds(),
                    vehicle.getTotalDrivingSeconds(),
                    vehicle.getLoadingWaitSeconds(),
                    vehicle.getUnloadingWaitSeconds(),
                    vehicle.getWaitingAssignmentSeconds(),
                    vehicle.getUpdatedBy(),
                    vehicle.getUpdatedTime()
            );
        }

        void applyTo(Vehicle vehicle, POIRepository poiRepository) {
            vehicle.setCurrentStatus(currentStatus);
            vehicle.setPreviousStatus(previousStatus);
            vehicle.setStatusStartTime(statusStartTime);
            vehicle.setStatusDurationSeconds(statusDurationSeconds);
            vehicle.setCurrentLoad(currentLoad);
            vehicle.setCurrentVolumn(currentVolumn);
            POI poi = currentPoiId == null
                    ? null
                    : poiRepository.findById(currentPoiId)
                    .orElseThrow(() -> new IllegalStateException("POI missing after baseline calibration: " + currentPoiId));
            vehicle.setCurrentPOI(poi);
            vehicle.setCurrentLongitude(currentLongitude);
            vehicle.setCurrentLatitude(currentLatitude);
            vehicle.setLoopCount(loopCount);
            vehicle.setLoadingWaitTime(loadingWaitTime);
            vehicle.setUnloadingWaitTime(unloadingWaitTime);
            vehicle.setWaitingAssignmentTime(waitingAssignmentTime);
            vehicle.setEmptyDrivingTime(emptyDrivingTime);
            vehicle.setEmptyDrivingDistance(emptyDrivingDistance);
            vehicle.setTotalDrivingTime(totalDrivingTime);
            vehicle.setTotalDrivingDistance(totalDrivingDistance);
            vehicle.setEmptyDistanceMeters(emptyDistanceMeters);
            vehicle.setLoadedDistanceMeters(loadedDistanceMeters);
            vehicle.setTotalDistanceMeters(totalDistanceMeters);
            vehicle.setEmptyDrivingSeconds(emptyDrivingSeconds);
            vehicle.setLoadedDrivingSeconds(loadedDrivingSeconds);
            vehicle.setTotalDrivingSeconds(totalDrivingSeconds);
            vehicle.setLoadingWaitSeconds(loadingWaitSeconds);
            vehicle.setUnloadingWaitSeconds(unloadingWaitSeconds);
            vehicle.setWaitingAssignmentSeconds(waitingAssignmentSeconds);
            vehicle.setUpdatedBy(updatedBy);
            vehicle.setUpdatedTime(updatedTime);
        }
    }
}
