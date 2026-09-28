package org.example.roadsimulation.sandbox.scenario.definition;

import java.math.BigDecimal;
import java.util.List;

/** Mutable authoring contract for a deterministic sandbox data scenario. */
public record SandboxScenarioDefinitionV1(
        String artifactVersion,
        String scenarioKey,
        String displayName,
        String description,
        BaselineReference baseline,
        Selection selection,
        Overrides overrides
) {
    public static final String ARTIFACT_VERSION = "sandbox-scenario-definition/v1";

    public enum SelectionMode { ALL_ELIGIBLE, EXPLICIT_IDS }

    public record BaselineReference(
            String baselineId,
            String restorationPayloadSha256,
            String simulationFactsSha256,
            String effectiveBaseDataSha256
    ) {}

    public record Selection(
            VehicleSelection vehicles,
            PoiSelection pois,
            GoodsSelection goods,
            IdSelection processingChains
    ) {}

    public record IdSelection(
            SelectionMode mode,
            List<Long> includeIds,
            List<Long> excludeIds
    ) {}

    public record VehicleSelection(
            SelectionMode mode,
            List<Long> includeIds,
            List<Long> excludeIds,
            BigDecimal minimumLoadCapacityTonnes,
            BigDecimal maximumLoadCapacityTonnes,
            BigDecimal minimumCargoVolumeCubicMeters,
            BigDecimal maximumCargoVolumeCubicMeters,
            List<String> vehicleTypes,
            List<String> modelTypes
    ) {}

    public record PoiSelection(
            SelectionMode mode,
            List<Long> includeIds,
            List<Long> excludeIds,
            List<String> poiTypes
    ) {}

    public record GoodsSelection(
            SelectionMode mode,
            List<Long> includeIds,
            List<Long> excludeIds,
            List<String> categories
    ) {}

    public record Overrides(
            List<StageOutputRatio> stageOutputRatios,
            List<ProcessingInputShare> processingInputShares,
            List<InitialInventory> initialInventories,
            List<VehicleInitialPoi> vehicleInitialPois
    ) {}

    public record StageOutputRatio(long stageId, BigDecimal outputWeightRatio) {}

    public record ProcessingInputShare(long inputId, BigDecimal inputShare) {}

    public record InitialInventory(long poiId, long goodsId, int quantity) {}

    public record VehicleInitialPoi(long vehicleId, long poiId, String initializationPolicy) {
        public static final String FIXED_POI = "FIXED_POI";
    }
}
