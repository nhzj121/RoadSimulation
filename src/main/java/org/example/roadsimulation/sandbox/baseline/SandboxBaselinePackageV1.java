package org.example.roadsimulation.sandbox.baseline;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of {@code sandbox-baseline-package/v1}.
 *
 * <p>This model deliberately contains only static base data. It is not a runnable
 * {@code ScenarioSnapshot}: vehicle positions, demand, paths and run-time state are supplied by
 * later sandbox phases.</p>
 */
public record SandboxBaselinePackageV1(
        String artifactVersion,
        String baselineId,
        String baselineStatus,
        boolean baselineReady,
        boolean sandboxRunReady,
        String contractVersion,
        Source source,
        Fingerprints fingerprints,
        EligibilityPolicy eligibilityPolicy,
        RestorePolicy restorePolicy,
        List<String> excludedSourceDomains,
        Data data
) {

    public static final String ARTIFACT_VERSION = "sandbox-baseline-package/v1";
    public static final String CONTRACT_VERSION = "baseline-data-contract/v1";
    public static final String ELIGIBILITY_POLICY_VERSION = "baseline-eligibility/v1";

    public SandboxBaselinePackageV1 {
        excludedSourceDomains = immutable(excludedSourceDomains);
    }

    public record Source(
            String databaseName,
            String databaseProduct,
            String databaseVersion,
            Instant capturedAtUtc,
            String characterSet,
            String collation,
            String globalSqlMode,
            String sessionSqlMode,
            String systemTimeZone,
            String sessionTimeZone,
            String transactionIsolation,
            String captureMethod,
            Map<String, Integer> tableRowCounts,
            String codeCommitObserved,
            boolean codeWorktreeFrozen
    ) {
        public Source {
            tableRowCounts = Map.copyOf(tableRowCounts);
        }
    }

    public record Fingerprints(
            String canonicalization,
            String restorationPayloadSha256,
            String simulationFactsSha256
    ) {}

    public record EligibilityPolicy(
            String policyVersion,
            List<Exclusion> defaultExcludedGoods,
            List<Exclusion> defaultExcludedVehicles,
            List<Exclusion> defaultExcludedPois,
            String temperatureControlPolicy,
            String rule
    ) {
        public EligibilityPolicy {
            defaultExcludedGoods = immutable(defaultExcludedGoods);
            defaultExcludedVehicles = immutable(defaultExcludedVehicles);
            defaultExcludedPois = immutable(defaultExcludedPois);
        }
    }

    public record Exclusion(long id, String reason) {}

    public record RestorePolicy(
            boolean preserveDatabasePrimaryKeys,
            boolean auditFieldsRestored,
            boolean autoIncrementStateRestored,
            String initialInventoryPolicy,
            String vehicleInitialStatePolicy,
            String terminalStageOutputPolicy,
            String routePolicy
    ) {}

    public record Data(
            List<Poi> pois,
            List<Goods> goods,
            List<Vehicle> vehicles,
            List<ProcessingChain> processingChains,
            List<InitialInventory> initialInventories
    ) {
        public Data {
            pois = immutable(pois);
            goods = immutable(goods);
            vehicles = immutable(vehicles);
            processingChains = immutable(processingChains);
            initialInventories = immutable(initialInventories);
        }
    }

    public record Poi(
            long id,
            String name,
            BigDecimal longitude,
            BigDecimal latitude,
            String poiType
    ) {}

    public record Goods(
            long id,
            String sku,
            String name,
            String category,
            String description,
            BigDecimal weightPerUnitTonnes,
            BigDecimal volumePerUnitCubicMeters,
            Boolean requiresTemperatureControl,
            String hazmatLevel,
            Integer shelfLifeDays,
            String vehicleFit
    ) {}

    public record Vehicle(
            long id,
            String licensePlate,
            BigDecimal maxLoadCapacityTonnes,
            BigDecimal cargoVolumeCubicMeters,
            String brand,
            String modelType,
            String vehicleType,
            Boolean hasTemperatureControl,
            String hazmatQualification,
            String specialVehicleType,
            BigDecimal lengthMeters,
            BigDecimal widthMeters,
            BigDecimal heightMeters,
            String suitableGoods
    ) {}

    public record ProcessingChain(
            long id,
            String chainCode,
            String chainName,
            String status,
            String description,
            List<ProcessingStage> stages,
            List<ProcessingEdge> edges
    ) {
        public ProcessingChain {
            stages = immutable(stages);
            edges = immutable(edges);
        }
    }

    public record ProcessingStage(
            long id,
            int stageOrder,
            String stageKey,
            String stageName,
            String description,
            long processingPoiId,
            String requiredPoiType,
            Long legacyInputGoodsId,
            String legacyInputGoodsSku,
            BigDecimal legacyInputWeightRatio,
            Long outputGoodsId,
            String outputGoodsSku,
            BigDecimal outputWeightRatio,
            int processingTimeMinutes,
            BigDecimal minBatchSize,
            BigDecimal maxCapacityPerCycle,
            List<ProcessingInput> inputs
    ) {
        public ProcessingStage {
            inputs = immutable(inputs);
        }
    }

    public record ProcessingInput(
            long id,
            String inputKey,
            Long goodsId,
            String sku,
            BigDecimal inputShare
    ) {}

    public record ProcessingEdge(
            long id,
            long fromStageId,
            long toStageId,
            long toStageInputId
    ) {}

    public record InitialInventory(long poiId, long goodsId, int quantity) {}

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
