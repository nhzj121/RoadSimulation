package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfile;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** Published specifications expose only the identity of these immutable solver settings. */
public final class SandboxOptimizationProfile {
    public static final String STRATEGY = "GLOBAL_OPTIMIZATION";
    public static final String ID = "global-multi-objective/v1";
    public static final String CARGO_REINSERT_ID = "global-multi-objective-cargo-reinsert/v1";
    public static final long SEARCH_SEED = 20261109L;
    // Keep the v1 fingerprint compatible with published revisions. The worker deadline is an operational policy.
    private static final long V1_PROFILE_TIMEOUT_SECONDS = 300;
    public static final GlobalPlanSearch.Budget BUDGET = new GlobalPlanSearch.Budget(8, 8, 2, 16);
    public static final DeliveryRetreatLimits LIMITS =
            new DeliveryRetreatLimits(new BigDecimal("0.04"), BigDecimal.ZERO);
    private static final BigDecimal SCALE = new BigDecimal("0.01");

    private SandboxOptimizationProfile() {}

    public static SphericalPathModel.Configuration paths() {
        return new SphericalPathModel.Configuration("SPHERICAL_DIRECT/v1", BigDecimal.valueOf(6371000), BigDecimal.valueOf(40));
    }

    public static SandboxAlgorithmProfile identity() {
        return identity(ID);
    }

    public static boolean supports(String profileId) {
        return ID.equals(profileId) || CARGO_REINSERT_ID.equals(profileId);
    }

    public static SandboxAlgorithmProfile identity(String profileId) {
        if (!supports(profileId)) throw new IllegalArgumentException("Unknown optimization profile: " + profileId);
        var configuration = new TreeMap<String, Object>(Map.of(
                "search", GlobalPlanSearch.THROUGHPUT_VERSION, "budget", BUDGET, "searchSeed", SEARCH_SEED,
                "timeout", V1_PROFILE_TIMEOUT_SECONDS, "limits", LIMITS, "path", paths(), "decoder", PlanExecutionTask.DECODER,
                "repair", PlanExecutionTask.REPAIR, "seedStrategy", HeuristicSeedResolver.VERSION,
                "preference", preference(BigDecimal.ONE)));
        if (CARGO_REINSERT_ID.equals(profileId)) {
            configuration.put("moveOperator", "timed-cargo-reinsert/v1");
            configuration.put("cargoPlacementLimit", TimedMoveRanking.CARGO_LIMIT);
        }
        return new SandboxAlgorithmProfile(profileId, STRATEGY,
                Map.of("configurationSha256", OptimizationJson.fingerprint(configuration)));
    }

    public static long workerTimeoutSeconds(int totalLoops) {
        if (totalLoops <= 0) throw new IllegalArgumentException("Simulation horizon must be positive");
        return Math.max(1200L, 500L + 10L * totalLoops);
    }

    public static PreferenceProfile preferenceFor(ProductionTaskCatalog catalog) {
        var tonnes = catalog.tasks().stream().map(ProductionTaskCatalog.Task::weightTonnes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return preference(tonnes.movePointLeft(2));
    }

    private static PreferenceProfile preference(BigDecimal deliveryScale) {
        var objectives = new TreeMap<String, PreferenceProfile.ObjectivePreference>();
        for (var definition : ObjectiveRegistry.throughput().definitions()) {
            String id = definition.metricId();
            boolean delivery = "cargoDeliveredTonnes".equals(id);
            var share = BigDecimal.valueOf(delivery ? 2 : ObjectiveRegistry.PRIMARY.equals(id) ? 0 : 1);
            objectives.put(id, new PreferenceProfile.ObjectivePreference(definition.unit(),
                    delivery ? deliveryScale : SCALE, share));
        }
        return new PreferenceProfile(PreferenceProfile.VERSION, objectives, BigDecimal.valueOf(4),
                BigDecimal.valueOf(4), SCALE, BigDecimal.valueOf(2));
    }
}
