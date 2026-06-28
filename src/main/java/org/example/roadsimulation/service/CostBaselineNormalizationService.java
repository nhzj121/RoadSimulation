package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.dto.RuntimeCostDetailDTO;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class CostBaselineNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(CostBaselineNormalizationService.class);

    private static final double RUNTIME_WEIGHT_A = 0.20;
    private static final double RUNTIME_WEIGHT_B = 0.20;
    private static final double RUNTIME_WEIGHT_C = 0.20;
    private static final double RUNTIME_WEIGHT_D = 0.20;
    private static final double RUNTIME_WEIGHT_E = 0.20;
    private static final double RUNTIME_WEIGHT_SUM = RUNTIME_WEIGHT_A
            + RUNTIME_WEIGHT_B
            + RUNTIME_WEIGHT_C
            + RUNTIME_WEIGHT_D
            + RUNTIME_WEIGHT_E;

    private final SimulationRuntimeConfig simulationRuntimeConfig;
    private final ObjectMapper objectMapper;

    @Value("${simulation.cost-baseline.enabled:true}")
    private boolean enabled;

    @Value("${simulation.cost-baseline.path:target/cost-baseline/baseline-result.json}")
    private String baselinePath;

    @Value("${simulation.cost-baseline.percentile:P95}")
    private String baselinePercentile;

    private BaselineCatalog cachedCatalog;
    private Path cachedPath;
    private long cachedLastModifiedMillis = Long.MIN_VALUE;
    private String lastWarningKey;

    private WindowSnapshot previousSnapshot;
    private NormalizedWindow latestWindow;
    private long nextWindowSequence = 1L;

    public CostBaselineNormalizationService(SimulationRuntimeConfig simulationRuntimeConfig) {
        this.simulationRuntimeConfig = simulationRuntimeConfig;
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void recordDispatchSnapshot(
            RuntimeCostDTO currentCosts,
            long totalShipmentItems,
            long notAssignedItems
    ) {
        if (!enabled || currentCosts == null) {
            return;
        }

        DispatchStrategy strategy = currentStrategy();
        WindowSnapshot current = new WindowSnapshot(
                strategy,
                LocalDateTime.now(),
                safe(currentCosts.getCostA()),
                safe(currentCosts.getCostB()),
                safe(currentCosts.getCostC()),
                safe(currentCosts.getCostD()),
                safe(currentCosts.getCostE()),
                Math.max(0L, totalShipmentItems),
                Math.max(0L, notAssignedItems)
        );

        if (previousSnapshot == null || previousSnapshot.strategy() != strategy) {
            previousSnapshot = current;
            latestWindow = null;
            return;
        }

        BaselineValues baseline = findBaseline(strategy);
        if (baseline == null || !baseline.isValid()) {
            latestWindow = null;
            previousSnapshot = current;
            warnOnce("invalid-baseline-" + strategy + "-" + normalizedPercentile(),
                    "Cost baseline normalization skipped: missing or invalid "
                            + normalizedPercentile() + " baseline for " + strategy);
            return;
        }

        latestWindow = calculateNormalizedWindow(previousSnapshot, current, baseline, normalizedPercentile());
        previousSnapshot = current;
    }

    public void applyLatest(RuntimeCostDTO dto) {
        if (dto == null || !enabled) {
            return;
        }

        NormalizedWindow window = latestWindow;
        if (window == null || window.strategy() != currentStrategy()) {
            return;
        }

        dto.setNormalizedCostA(window.normalizedA());
        dto.setNormalizedCostB(window.normalizedB());
        dto.setNormalizedCostC(window.normalizedC());
        dto.setNormalizedCostD(window.normalizedD());
        dto.setNormalizedCostE(window.normalizedE());
        dto.setNormalizedAllCost(window.normalizedAllCost());
        dto.setBaselinePercentile(window.percentile());
        dto.setBaselineStrategy(window.strategy().name());
    }

    public synchronized RuntimeCostDetailDTO.WindowDetail exportLatestWindowDetail() {
        NormalizedWindow window = latestWindow;
        if (!enabled || window == null || window.strategy() != currentStrategy()) {
            return null;
        }

        RuntimeCostDetailDTO.WindowDetail detail = new RuntimeCostDetailDTO.WindowDetail();
        detail.setWindowId(window.windowId());
        detail.setStrategy(window.strategy().name());
        detail.setWindowStartTime(window.windowStartTime());
        detail.setWindowEndTime(window.windowEndTime());
        detail.setTaskScale(window.taskScale());
        detail.setGeneratedShipmentItems(window.generatedShipmentItems());
        detail.setNotAssignedItemsAtStart(window.notAssignedItemsAtStart());
        detail.setStartCostA(window.startCostA());
        detail.setStartCostB(window.startCostB());
        detail.setStartCostC(window.startCostC());
        detail.setStartCostD(window.startCostD());
        detail.setStartCostE(window.startCostE());
        detail.setEndCostA(window.endCostA());
        detail.setEndCostB(window.endCostB());
        detail.setEndCostC(window.endCostC());
        detail.setEndCostD(window.endCostD());
        detail.setEndCostE(window.endCostE());
        detail.setUnitCostA(window.unitA());
        detail.setUnitCostB(window.unitB());
        detail.setUnitCostC(window.unitC());
        detail.setUnitCostD(window.unitD());
        detail.setUnitCostE(window.unitE());
        return detail;
    }

    public synchronized RuntimeCostDetailDTO.BaselineDetail exportCurrentBaselineDetail() {
        if (!enabled) {
            return null;
        }

        DispatchStrategy strategy = currentStrategy();
        String percentile = normalizedPercentile();
        BaselineValues baseline = findBaseline(strategy);

        RuntimeCostDetailDTO.BaselineDetail detail = new RuntimeCostDetailDTO.BaselineDetail();
        detail.setBaselinePercentile(percentile);
        detail.setBaselineStrategy(strategy.name());
        detail.setRuntimeWeightA(RUNTIME_WEIGHT_A);
        detail.setRuntimeWeightB(RUNTIME_WEIGHT_B);
        detail.setRuntimeWeightC(RUNTIME_WEIGHT_C);
        detail.setRuntimeWeightD(RUNTIME_WEIGHT_D);
        detail.setRuntimeWeightE(RUNTIME_WEIGHT_E);

        if (baseline != null && baseline.isValid()) {
            detail.setBaselineCostA(baseline.a());
            detail.setBaselineCostB(baseline.b());
            detail.setBaselineCostC(baseline.c());
            detail.setBaselineCostD(baseline.d());
            detail.setBaselineCostE(baseline.e());
        }
        return detail;
    }

    public synchronized void reset() {
        previousSnapshot = null;
        latestWindow = null;
        nextWindowSequence = 1L;
    }

    void configureForTest(boolean enabled, String baselinePath, String baselinePercentile) {
        this.enabled = enabled;
        this.baselinePath = baselinePath;
        this.baselinePercentile = baselinePercentile;
        this.cachedCatalog = null;
        this.cachedPath = null;
        this.cachedLastModifiedMillis = Long.MIN_VALUE;
        this.lastWarningKey = null;
        this.nextWindowSequence = 1L;
        reset();
    }

    private NormalizedWindow calculateNormalizedWindow(
            WindowSnapshot start,
            WindowSnapshot end,
            BaselineValues baseline,
            String percentile
    ) {
        long generatedItems = Math.max(0L, end.totalShipmentItems() - start.totalShipmentItems());
        double taskScale = Math.max(1L, start.notAssignedItems() + generatedItems);

        double unitA = Math.max(0.0, end.costA() - start.costA()) / taskScale;
        double unitB = (start.costB() + end.costB()) / 2.0;
        double unitC = Math.max(0.0, end.costC() - start.costC()) / taskScale;
        double unitD = Math.max(0.0, end.costD() - start.costD()) / taskScale;
        double unitE = (start.costE() + end.costE()) / 2.0;

        double normalizedA = unitA / baseline.a();
        double normalizedB = unitB / baseline.b();
        double normalizedC = unitC / baseline.c();
        double normalizedD = unitD / baseline.d();
        double normalizedE = unitE / baseline.e();
        double normalizedAllCost = (RUNTIME_WEIGHT_A * normalizedA
                + RUNTIME_WEIGHT_B * normalizedB
                + RUNTIME_WEIGHT_C * normalizedC
                + RUNTIME_WEIGHT_D * normalizedD
                + RUNTIME_WEIGHT_E * normalizedE) / RUNTIME_WEIGHT_SUM;

        return new NormalizedWindow(
                "runtime-cost-window-" + nextWindowSequence++,
                end.strategy(),
                percentile,
                start.recordedAt(),
                end.recordedAt(),
                generatedItems,
                start.notAssignedItems(),
                taskScale,
                start.costA(),
                start.costB(),
                start.costC(),
                start.costD(),
                start.costE(),
                end.costA(),
                end.costB(),
                end.costC(),
                end.costD(),
                end.costE(),
                unitA,
                unitB,
                unitC,
                unitD,
                unitE,
                normalizedA,
                normalizedB,
                normalizedC,
                normalizedD,
                normalizedE,
                normalizedAllCost
        );
    }

    private BaselineValues findBaseline(DispatchStrategy strategy) {
        BaselineCatalog catalog = loadBaselineCatalog();
        if (catalog == null) {
            return null;
        }
        Map<String, BaselineValues> strategyBaselines = catalog.values().get(strategy);
        if (strategyBaselines == null) {
            return null;
        }
        return strategyBaselines.get(normalizedPercentile().toLowerCase(Locale.ROOT));
    }

    private synchronized BaselineCatalog loadBaselineCatalog() {
        Path path = Path.of(baselinePath);
        if (!Files.exists(path)) {
            warnOnce("missing-" + path.toAbsolutePath(),
                    "Cost baseline normalization skipped: baseline file not found: "
                            + path.toAbsolutePath());
            return null;
        }

        try {
            long lastModifiedMillis = Files.getLastModifiedTime(path).toMillis();
            if (cachedCatalog != null
                    && path.equals(cachedPath)
                    && lastModifiedMillis == cachedLastModifiedMillis) {
                return cachedCatalog;
            }

            JsonNode root = objectMapper.readTree(path.toFile());
            JsonNode strategies = root.path("strategies");
            Map<DispatchStrategy, Map<String, BaselineValues>> values = new EnumMap<>(DispatchStrategy.class);
            for (DispatchStrategy strategy : DispatchStrategy.values()) {
                JsonNode strategyNode = strategies.path(strategy.name());
                Map<String, BaselineValues> percentiles = new HashMap<>();
                percentiles.put("p90", parseBaselineValues(strategyNode.path("p90")));
                percentiles.put("p95", parseBaselineValues(strategyNode.path("p95")));
                values.put(strategy, percentiles);
            }

            cachedCatalog = new BaselineCatalog(values);
            cachedPath = path;
            cachedLastModifiedMillis = lastModifiedMillis;
            return cachedCatalog;
        } catch (IOException | RuntimeException ex) {
            warnOnce("read-failed-" + path.toAbsolutePath() + "-" + ex.getClass().getName(),
                    "Cost baseline normalization skipped: failed to read baseline file "
                            + path.toAbsolutePath() + ", reason=" + ex.getMessage());
            return null;
        }
    }

    private BaselineValues parseBaselineValues(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return BaselineValues.invalid();
        }
        return new BaselineValues(
                node.path("a").asDouble(Double.NaN),
                node.path("b").asDouble(Double.NaN),
                node.path("c").asDouble(Double.NaN),
                node.path("d").asDouble(Double.NaN),
                node.path("e").asDouble(Double.NaN)
        );
    }

    private DispatchStrategy currentStrategy() {
        if (simulationRuntimeConfig == null || simulationRuntimeConfig.getDispatchStrategy() == null) {
            return DispatchStrategy.ORIGINAL;
        }
        return simulationRuntimeConfig.getDispatchStrategy();
    }

    private String normalizedPercentile() {
        if (baselinePercentile == null || baselinePercentile.isBlank()) {
            return "P95";
        }
        return baselinePercentile.trim().toUpperCase(Locale.ROOT);
    }

    private void warnOnce(String key, String message) {
        if (key.equals(lastWarningKey)) {
            return;
        }
        lastWarningKey = key;
        log.warn(message);
    }

    private double safe(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return value;
    }

    private record BaselineCatalog(Map<DispatchStrategy, Map<String, BaselineValues>> values) {
    }

    private record BaselineValues(double a, double b, double c, double d, double e) {

        static BaselineValues invalid() {
            return new BaselineValues(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        boolean isValid() {
            return isPositiveFinite(a)
                    && isPositiveFinite(b)
                    && isPositiveFinite(c)
                    && isPositiveFinite(d)
                    && isPositiveFinite(e);
        }

        private static boolean isPositiveFinite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
        }
    }

    private record WindowSnapshot(
            DispatchStrategy strategy,
            LocalDateTime recordedAt,
            double costA,
            double costB,
            double costC,
            double costD,
            double costE,
            long totalShipmentItems,
            long notAssignedItems
    ) {
    }

    private record NormalizedWindow(
            String windowId,
            DispatchStrategy strategy,
            String percentile,
            LocalDateTime windowStartTime,
            LocalDateTime windowEndTime,
            long generatedShipmentItems,
            long notAssignedItemsAtStart,
            double taskScale,
            double startCostA,
            double startCostB,
            double startCostC,
            double startCostD,
            double startCostE,
            double endCostA,
            double endCostB,
            double endCostC,
            double endCostD,
            double endCostE,
            double unitA,
            double unitB,
            double unitC,
            double unitD,
            double unitE,
            double normalizedA,
            double normalizedB,
            double normalizedC,
            double normalizedD,
            double normalizedE,
            double normalizedAllCost
    ) {
    }
}
