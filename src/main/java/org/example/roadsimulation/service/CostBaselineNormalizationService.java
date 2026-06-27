package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public synchronized void reset() {
        previousSnapshot = null;
        latestWindow = null;
    }

    void configureForTest(boolean enabled, String baselinePath, String baselinePercentile) {
        this.enabled = enabled;
        this.baselinePath = baselinePath;
        this.baselinePercentile = baselinePercentile;
        this.cachedCatalog = null;
        this.cachedPath = null;
        this.cachedLastModifiedMillis = Long.MIN_VALUE;
        this.lastWarningKey = null;
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
                end.strategy(),
                percentile,
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
            DispatchStrategy strategy,
            String percentile,
            double normalizedA,
            double normalizedB,
            double normalizedC,
            double normalizedD,
            double normalizedE,
            double normalizedAllCost
    ) {
    }
}
