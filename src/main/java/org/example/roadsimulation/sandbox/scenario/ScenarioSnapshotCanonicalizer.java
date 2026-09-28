package org.example.roadsimulation.sandbox.scenario;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.example.roadsimulation.sandbox.scenario.ScenarioSnapshot.*;

/** Produces the one authoritative ordering and decimal representation used for hashing. */
public final class ScenarioSnapshotCanonicalizer {

    public ScenarioSnapshot canonicalize(ScenarioSnapshot source) {
        List<PoiFact> pois = source.pois().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparingLong(PoiFact::id))
                .toList();
        List<GoodsFact> goods = source.goods().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparingLong(GoodsFact::id))
                .toList();
        List<VehicleFact> vehicles = source.vehicles().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparingLong(VehicleFact::id))
                .toList();
        List<InventoryFact> inventories = source.inventories().stream()
                .sorted(Comparator.comparingLong(InventoryFact::poiId)
                        .thenComparingLong(InventoryFact::goodsId))
                .toList();
        List<InitialDemandFact> demands = source.initialDemands().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparing(InitialDemandFact::demandKey))
                .toList();
        List<ProcessingChainFact> chains = source.processingChains().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparingLong(ProcessingChainFact::id))
                .toList();
        List<PathFact> paths = source.paths().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparing(PathFact::pathKey))
                .toList();

        return new ScenarioSnapshot(
                source.schemaVersion(), pois, goods, vehicles, inventories, demands, chains, paths
        );
    }

    private PoiFact canonicalize(PoiFact value) {
        return new PoiFact(value.id(), decimal(value.longitude()), decimal(value.latitude()), value.poiType());
    }

    private GoodsFact canonicalize(GoodsFact value) {
        return new GoodsFact(
                value.id(), value.sku(), value.category(), decimal(value.weightPerUnitTonnes()),
                decimal(value.volumePerUnitCubicMeters()), value.requiresTemperatureControl(),
                value.hazmatLevel(), value.shelfLifeDays(), value.vehicleFit()
        );
    }

    private VehicleFact canonicalize(VehicleFact value) {
        return new VehicleFact(
                value.id(), value.licensePlate(), decimal(value.maxLoadCapacityTonnes()),
                decimal(value.cargoVolumeCubicMeters()), value.brand(), value.modelType(),
                value.vehicleType(), value.hasTemperatureControl(), value.hazmatQualification(),
                value.specialVehicleType(), decimal(value.lengthMeters()), decimal(value.widthMeters()),
                decimal(value.heightMeters()), value.suitableGoods(), value.currentStatus(),
                value.currentPoiId(), decimal(value.currentLongitude()), decimal(value.currentLatitude()),
                decimal(value.currentLoadTonnes()), decimal(value.currentVolumeCubicMeters()),
                value.lastAssignmentRound()
        );
    }

    private InitialDemandFact canonicalize(InitialDemandFact value) {
        List<DemandItemFact> items = value.items().stream()
                .map(item -> new DemandItemFact(
                        item.itemKey(), item.goodsId(), item.quantity(),
                        decimal(item.totalWeightTonnes()), decimal(item.totalVolumeCubicMeters())
                ))
                .sorted(Comparator.comparing(DemandItemFact::itemKey))
                .toList();
        return new InitialDemandFact(
                value.demandKey(), value.originPoiId(), value.destinationPoiId(),
                value.pickupAppointmentOffsetSeconds(), value.deliveryAppointmentOffsetSeconds(), items
        );
    }

    private ProcessingChainFact canonicalize(ProcessingChainFact value) {
        List<ProcessingStageFact> stages = value.stages().stream()
                .map(this::canonicalize)
                .sorted(Comparator.comparingLong(ProcessingStageFact::id))
                .toList();
        List<ProcessingEdgeFact> edges = value.edges().stream()
                .sorted(Comparator.comparingLong(ProcessingEdgeFact::id))
                .toList();
        return new ProcessingChainFact(value.id(), value.chainCode(), value.status(), stages, edges);
    }

    private ProcessingStageFact canonicalize(ProcessingStageFact value) {
        List<ProcessingInputFact> inputs = value.inputs().stream()
                .map(input -> new ProcessingInputFact(
                        input.id(), input.inputKey(), input.goodsId(), input.sku(), decimal(input.inputShare())
                ))
                .sorted(Comparator.comparingLong(ProcessingInputFact::id))
                .toList();
        return new ProcessingStageFact(
                value.id(), value.stageOrder(), value.stageKey(), value.processingPoiId(),
                value.legacyInputGoodsId(), value.legacyInputGoodsSku(),
                decimal(value.legacyInputWeightRatio()), value.outputGoodsId(), value.outputGoodsSku(),
                decimal(value.outputWeightRatio()), value.processingTimeMinutes(),
                decimal(value.minBatchSize()), decimal(value.maxCapacityPerCycle()), inputs
        );
    }

    private PathFact canonicalize(PathFact value) {
        List<PathSegmentFact> segments = value.segments().stream()
                .map(segment -> new PathSegmentFact(
                        segment.sequence(), segment.road(), segment.instruction(), segment.action(),
                        segment.orientation(), decimal(segment.distanceMeters()),
                        decimal(segment.durationSeconds()), segment.polyline()
                ))
                .sorted(Comparator.comparingInt(PathSegmentFact::sequence))
                .toList();
        return new PathFact(
                value.pathKey(), value.originPoiId(), value.destinationPoiId(), value.strategy(),
                decimal(value.distanceMeters()), decimal(value.durationSeconds()), decimal(value.tolls()),
                decimal(value.tollDistanceMeters()), value.polyline(), segments
        );
    }

    private BigDecimal decimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.stripTrailingZeros();
    }
}
