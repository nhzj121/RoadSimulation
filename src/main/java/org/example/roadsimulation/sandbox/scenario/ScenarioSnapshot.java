package org.example.roadsimulation.sandbox.scenario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Versioned, database-independent input facts for one reproducible simulation scenario.
 *
 * <p>The contract deliberately excludes audit timestamps, database-generated runtime IDs,
 * display descriptions and accumulated result metrics. Those values do not determine the
 * initial business state. IDs in the stable master-data records are the existing database
 * primary keys. Runtime demand uses scenario-local keys instead.</p>
 */
public record ScenarioSnapshot(
        String schemaVersion,
        List<PoiFact> pois,
        List<GoodsFact> goods,
        List<VehicleFact> vehicles,
        List<InventoryFact> inventories,
        List<InitialDemandFact> initialDemands,
        List<ProcessingChainFact> processingChains,
        List<PathFact> paths
) {

    public static final String SCHEMA_VERSION = "scenario-snapshot/v0";

    public ScenarioSnapshot {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        pois = immutable(pois, "pois");
        goods = immutable(goods, "goods");
        vehicles = immutable(vehicles, "vehicles");
        inventories = immutable(inventories, "inventories");
        initialDemands = immutable(initialDemands, "initialDemands");
        processingChains = immutable(processingChains, "processingChains");
        paths = immutable(paths, "paths");
    }

    private static <T> List<T> immutable(List<T> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name));
    }

    public record PoiFact(
            long id,
            BigDecimal longitude,
            BigDecimal latitude,
            String poiType
    ) {}

    public record GoodsFact(
            long id,
            String sku,
            String category,
            BigDecimal weightPerUnitTonnes,
            BigDecimal volumePerUnitCubicMeters,
            Boolean requiresTemperatureControl,
            String hazmatLevel,
            Integer shelfLifeDays,
            String vehicleFit
    ) {}

    public record VehicleFact(
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
            String suitableGoods,
            String currentStatus,
            Long currentPoiId,
            BigDecimal currentLongitude,
            BigDecimal currentLatitude,
            BigDecimal currentLoadTonnes,
            BigDecimal currentVolumeCubicMeters,
            int lastAssignmentRound
    ) {}

    /** Inventory is identified by its business pair, not by Enrollment's generated ID. */
    public record InventoryFact(
            long poiId,
            long goodsId,
            int quantity
    ) {}

    /**
     * Demand present before round zero. Keys are stable only inside this scenario and do not
     * reuse database shipment numbers. Appointment values are offsets from simulation start.
     */
    public record InitialDemandFact(
            String demandKey,
            long originPoiId,
            long destinationPoiId,
            Long pickupAppointmentOffsetSeconds,
            Long deliveryAppointmentOffsetSeconds,
            List<DemandItemFact> items
    ) {
        public InitialDemandFact {
            items = immutable(items, "items");
        }
    }

    public record DemandItemFact(
            String itemKey,
            long goodsId,
            int quantity,
            BigDecimal totalWeightTonnes,
            BigDecimal totalVolumeCubicMeters
    ) {}

    public record ProcessingChainFact(
            long id,
            String chainCode,
            String status,
            List<ProcessingStageFact> stages,
            List<ProcessingEdgeFact> edges
    ) {
        public ProcessingChainFact {
            stages = immutable(stages, "stages");
            edges = immutable(edges, "edges");
        }
    }

    /**
     * Both the current legacy input columns and the multi-input rows are retained in v0.
     * This makes the transition to the completed processing-chain data explicit and auditable.
     */
    public record ProcessingStageFact(
            long id,
            int stageOrder,
            String stageKey,
            long processingPoiId,
            Long legacyInputGoodsId,
            String legacyInputGoodsSku,
            BigDecimal legacyInputWeightRatio,
            Long outputGoodsId,
            String outputGoodsSku,
            BigDecimal outputWeightRatio,
            int processingTimeMinutes,
            BigDecimal minBatchSize,
            BigDecimal maxCapacityPerCycle,
            List<ProcessingInputFact> inputs
    ) {
        public ProcessingStageFact {
            inputs = immutable(inputs, "inputs");
        }
    }

    public record ProcessingInputFact(
            long id,
            String inputKey,
            Long goodsId,
            String sku,
            BigDecimal inputShare
    ) {}

    public record ProcessingEdgeFact(
            long id,
            long fromStageId,
            long toStageId,
            long toStageInputId
    ) {}

    /** A selected external route-planning result, independent of the descriptive Route table. */
    public record PathFact(
            String pathKey,
            long originPoiId,
            long destinationPoiId,
            String strategy,
            BigDecimal distanceMeters,
            BigDecimal durationSeconds,
            BigDecimal tolls,
            BigDecimal tollDistanceMeters,
            String polyline,
            List<PathSegmentFact> segments
    ) {
        public PathFact {
            segments = immutable(segments, "segments");
        }
    }

    public record PathSegmentFact(
            int sequence,
            String road,
            String instruction,
            String action,
            String orientation,
            BigDecimal distanceMeters,
            BigDecimal durationSeconds,
            String polyline
    ) {}
}
