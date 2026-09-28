package org.example.roadsimulation.sandbox.scenario;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.example.roadsimulation.sandbox.scenario.ScenarioSnapshot.*;

/** Validates snapshot completeness and every reference before a fingerprint can be issued. */
public final class ScenarioSnapshotValidator {

    public void validate(ScenarioSnapshot snapshot) {
        require(snapshot != null, "snapshot must not be null");
        require(SCHEMA_VERSION.equals(snapshot.schemaVersion()),
                "unsupported schemaVersion: " + snapshot.schemaVersion());

        Set<Long> poiIds = uniqueIds(snapshot.pois().stream().map(PoiFact::id).toList(), "poi");
        Set<Long> goodsIds = uniqueIds(snapshot.goods().stream().map(GoodsFact::id).toList(), "goods");
        Set<Long> vehicleIds = uniqueIds(snapshot.vehicles().stream().map(VehicleFact::id).toList(), "vehicle");
        require(vehicleIds.size() == snapshot.vehicles().size(), "duplicate vehicle id");

        validatePois(snapshot);
        validateGoods(snapshot);
        validateVehicles(snapshot, poiIds);
        validateInventories(snapshot, poiIds, goodsIds);
        validateDemands(snapshot, poiIds, goodsIds);
        validateProcessing(snapshot, poiIds, goodsIds);
        validatePaths(snapshot, poiIds);
    }

    private void validatePois(ScenarioSnapshot snapshot) {
        for (PoiFact poi : snapshot.pois()) {
            requireDecimal(poi.longitude(), "poi[" + poi.id() + "].longitude");
            requireDecimal(poi.latitude(), "poi[" + poi.id() + "].latitude");
            require(poi.longitude().compareTo(BigDecimal.valueOf(-180)) >= 0
                            && poi.longitude().compareTo(BigDecimal.valueOf(180)) <= 0,
                    "poi[" + poi.id() + "].longitude out of range");
            require(poi.latitude().compareTo(BigDecimal.valueOf(-90)) >= 0
                            && poi.latitude().compareTo(BigDecimal.valueOf(90)) <= 0,
                    "poi[" + poi.id() + "].latitude out of range");
            requireText(poi.poiType(), "poi[" + poi.id() + "].poiType");
        }
    }

    private void validateGoods(ScenarioSnapshot snapshot) {
        Set<String> skus = new HashSet<>();
        for (GoodsFact goods : snapshot.goods()) {
            requireText(goods.sku(), "goods[" + goods.id() + "].sku");
            require(skus.add(goods.sku()), "duplicate goods sku: " + goods.sku());
            requireNonNegative(goods.weightPerUnitTonnes(), "goods[" + goods.id() + "].weightPerUnitTonnes");
            requireNonNegative(goods.volumePerUnitCubicMeters(),
                    "goods[" + goods.id() + "].volumePerUnitCubicMeters");
            require(goods.shelfLifeDays() == null || goods.shelfLifeDays() >= 0,
                    "goods[" + goods.id() + "].shelfLifeDays must be non-negative");
        }
    }

    private void validateVehicles(ScenarioSnapshot snapshot, Set<Long> poiIds) {
        Set<String> licensePlates = new HashSet<>();
        for (VehicleFact vehicle : snapshot.vehicles()) {
            requireText(vehicle.licensePlate(), "vehicle[" + vehicle.id() + "].licensePlate");
            require(licensePlates.add(vehicle.licensePlate()),
                    "duplicate vehicle licensePlate: " + vehicle.licensePlate());
            requireText(vehicle.currentStatus(), "vehicle[" + vehicle.id() + "].currentStatus");
            requireNonNegative(vehicle.maxLoadCapacityTonnes(),
                    "vehicle[" + vehicle.id() + "].maxLoadCapacityTonnes");
            requireNonNegative(vehicle.cargoVolumeCubicMeters(),
                    "vehicle[" + vehicle.id() + "].cargoVolumeCubicMeters");
            requireNonNegative(vehicle.currentLoadTonnes(),
                    "vehicle[" + vehicle.id() + "].currentLoadTonnes");
            requireNonNegative(vehicle.currentVolumeCubicMeters(),
                    "vehicle[" + vehicle.id() + "].currentVolumeCubicMeters");
            requireNonNegative(vehicle.lengthMeters(), "vehicle[" + vehicle.id() + "].lengthMeters");
            requireNonNegative(vehicle.widthMeters(), "vehicle[" + vehicle.id() + "].widthMeters");
            requireNonNegative(vehicle.heightMeters(), "vehicle[" + vehicle.id() + "].heightMeters");
            require(vehicle.lastAssignmentRound() >= 0,
                    "vehicle[" + vehicle.id() + "].lastAssignmentRound must be non-negative");

            if (vehicle.currentPoiId() != null) {
                requireReference(poiIds, vehicle.currentPoiId(),
                        "vehicle[" + vehicle.id() + "].currentPoiId");
            } else {
                require(vehicle.currentLongitude() != null && vehicle.currentLatitude() != null,
                        "vehicle[" + vehicle.id() + "] needs currentPoiId or both coordinates");
            }
            requireCoordinatePair(vehicle.currentLongitude(), vehicle.currentLatitude(),
                    "vehicle[" + vehicle.id() + "]");
        }
    }

    private void validateInventories(
            ScenarioSnapshot snapshot,
            Set<Long> poiIds,
            Set<Long> goodsIds
    ) {
        Set<String> pairs = new HashSet<>();
        for (InventoryFact inventory : snapshot.inventories()) {
            requireReference(poiIds, inventory.poiId(), "inventory.poiId");
            requireReference(goodsIds, inventory.goodsId(), "inventory.goodsId");
            require(inventory.quantity() >= 0, "inventory quantity must be non-negative");
            require(pairs.add(inventory.poiId() + ":" + inventory.goodsId()),
                    "duplicate inventory pair: " + inventory.poiId() + ":" + inventory.goodsId());
        }
    }

    private void validateDemands(
            ScenarioSnapshot snapshot,
            Set<Long> poiIds,
            Set<Long> goodsIds
    ) {
        Set<String> demandKeys = new HashSet<>();
        for (InitialDemandFact demand : snapshot.initialDemands()) {
            requireText(demand.demandKey(), "initialDemand.demandKey");
            require(demandKeys.add(demand.demandKey()),
                    "duplicate initial demand key: " + demand.demandKey());
            requireReference(poiIds, demand.originPoiId(), demand.demandKey() + ".originPoiId");
            requireReference(poiIds, demand.destinationPoiId(), demand.demandKey() + ".destinationPoiId");
            require(demand.pickupAppointmentOffsetSeconds() == null
                            || demand.pickupAppointmentOffsetSeconds() >= 0,
                    demand.demandKey() + ".pickupAppointmentOffsetSeconds must be non-negative");
            require(demand.deliveryAppointmentOffsetSeconds() == null
                            || demand.deliveryAppointmentOffsetSeconds() >= 0,
                    demand.demandKey() + ".deliveryAppointmentOffsetSeconds must be non-negative");
            if (demand.pickupAppointmentOffsetSeconds() != null
                    && demand.deliveryAppointmentOffsetSeconds() != null) {
                require(demand.deliveryAppointmentOffsetSeconds()
                                >= demand.pickupAppointmentOffsetSeconds(),
                        demand.demandKey() + ".delivery appointment precedes pickup");
            }
            require(!demand.items().isEmpty(), demand.demandKey() + " must contain at least one item");
            Set<String> itemKeys = new HashSet<>();
            for (DemandItemFact item : demand.items()) {
                requireText(item.itemKey(), demand.demandKey() + ".itemKey");
                require(itemKeys.add(item.itemKey()),
                        "duplicate item key in " + demand.demandKey() + ": " + item.itemKey());
                requireReference(goodsIds, item.goodsId(), demand.demandKey() + ".goodsId");
                require(item.quantity() > 0, demand.demandKey() + ".quantity must be positive");
                requireNonNegative(item.totalWeightTonnes(),
                        demand.demandKey() + ".totalWeightTonnes");
                requireNonNegative(item.totalVolumeCubicMeters(),
                        demand.demandKey() + ".totalVolumeCubicMeters");
            }
        }
    }

    private void validateProcessing(
            ScenarioSnapshot snapshot,
            Set<Long> poiIds,
            Set<Long> goodsIds
    ) {
        Set<Long> chainIds = new HashSet<>();
        Set<String> chainCodes = new HashSet<>();
        Map<Long, Long> stageOwners = new HashMap<>();
        Map<Long, Long> inputOwners = new HashMap<>();
        Set<Long> edgeIds = new HashSet<>();

        for (ProcessingChainFact chain : snapshot.processingChains()) {
            require(chain.id() > 0 && chainIds.add(chain.id()),
                    "duplicate or invalid processing chain id: " + chain.id());
            requireText(chain.chainCode(), "processingChain[" + chain.id() + "].chainCode");
            require(chainCodes.add(chain.chainCode()), "duplicate processing chain code: " + chain.chainCode());
            requireText(chain.status(), "processingChain[" + chain.id() + "].status");
            Set<String> stageKeys = new HashSet<>();

            for (ProcessingStageFact stage : chain.stages()) {
                require(stage.id() > 0 && stageOwners.putIfAbsent(stage.id(), chain.id()) == null,
                        "duplicate or invalid processing stage id: " + stage.id());
                require(stage.stageOrder() >= 0,
                        "processingStage[" + stage.id() + "].stageOrder must be non-negative");
                requireText(stage.stageKey(), "processingStage[" + stage.id() + "].stageKey");
                require(stageKeys.add(stage.stageKey()),
                        "duplicate stageKey in chain " + chain.id() + ": " + stage.stageKey());
                requireReference(poiIds, stage.processingPoiId(),
                        "processingStage[" + stage.id() + "].processingPoiId");
                requireOptionalReference(goodsIds, stage.legacyInputGoodsId(),
                        "processingStage[" + stage.id() + "].legacyInputGoodsId");
                requireOptionalReference(goodsIds, stage.outputGoodsId(),
                        "processingStage[" + stage.id() + "].outputGoodsId");
                requirePositiveIfPresent(stage.legacyInputWeightRatio(),
                        "processingStage[" + stage.id() + "].legacyInputWeightRatio");
                requirePositiveIfPresent(stage.outputWeightRatio(),
                        "processingStage[" + stage.id() + "].outputWeightRatio");
                require(stage.processingTimeMinutes() >= 0,
                        "processingStage[" + stage.id() + "].processingTimeMinutes must be non-negative");
                requireNonNegative(stage.minBatchSize(),
                        "processingStage[" + stage.id() + "].minBatchSize");
                requireNonNegative(stage.maxCapacityPerCycle(),
                        "processingStage[" + stage.id() + "].maxCapacityPerCycle");
                if (stage.minBatchSize() != null && stage.maxCapacityPerCycle() != null) {
                    require(stage.minBatchSize().compareTo(stage.maxCapacityPerCycle()) <= 0,
                            "processingStage[" + stage.id() + "] minBatchSize exceeds maxCapacityPerCycle");
                }

                Set<String> inputKeys = new HashSet<>();
                for (ProcessingInputFact input : stage.inputs()) {
                    require(input.id() > 0 && inputOwners.putIfAbsent(input.id(), stage.id()) == null,
                            "duplicate or invalid processing input id: " + input.id());
                    requireText(input.inputKey(), "processingInput[" + input.id() + "].inputKey");
                    require(inputKeys.add(input.inputKey()),
                            "duplicate inputKey in stage " + stage.id() + ": " + input.inputKey());
                    requireOptionalReference(goodsIds, input.goodsId(),
                            "processingInput[" + input.id() + "].goodsId");
                    requireText(input.sku(), "processingInput[" + input.id() + "].sku");
                    requirePositiveIfPresent(input.inputShare(),
                            "processingInput[" + input.id() + "].inputShare");
                }
            }

            for (ProcessingEdgeFact edge : chain.edges()) {
                require(edge.id() > 0 && edgeIds.add(edge.id()),
                        "duplicate or invalid processing edge id: " + edge.id());
                require(chain.id() == owner(stageOwners, edge.fromStageId(), "edge.fromStageId"),
                        "edge " + edge.id() + " fromStage belongs to another chain");
                require(chain.id() == owner(stageOwners, edge.toStageId(), "edge.toStageId"),
                        "edge " + edge.id() + " toStage belongs to another chain");
                require(edge.toStageId() == owner(inputOwners, edge.toStageInputId(), "edge.toStageInputId"),
                        "edge " + edge.id() + " input does not belong to toStage");
            }
        }
    }

    private void validatePaths(ScenarioSnapshot snapshot, Set<Long> poiIds) {
        Set<String> pathKeys = new HashSet<>();
        Set<String> pathSelections = new HashSet<>();
        for (PathFact path : snapshot.paths()) {
            requireText(path.pathKey(), "path.pathKey");
            require(pathKeys.add(path.pathKey()), "duplicate pathKey: " + path.pathKey());
            requireReference(poiIds, path.originPoiId(), path.pathKey() + ".originPoiId");
            requireReference(poiIds, path.destinationPoiId(), path.pathKey() + ".destinationPoiId");
            requireText(path.strategy(), path.pathKey() + ".strategy");
            String selection = path.originPoiId() + ":" + path.destinationPoiId() + ":" + path.strategy();
            require(pathSelections.add(selection), "ambiguous path selection: " + selection);
            requireNonNegative(path.distanceMeters(), path.pathKey() + ".distanceMeters");
            requireNonNegative(path.durationSeconds(), path.pathKey() + ".durationSeconds");
            requireNonNegative(path.tolls(), path.pathKey() + ".tolls");
            requireNonNegative(path.tollDistanceMeters(), path.pathKey() + ".tollDistanceMeters");
            require(!path.segments().isEmpty(), path.pathKey() + " must contain path segments");
            Set<Integer> sequences = new HashSet<>();
            for (PathSegmentFact segment : path.segments()) {
                require(segment.sequence() >= 0 && sequences.add(segment.sequence()),
                        "duplicate or invalid segment sequence in " + path.pathKey() + ": " + segment.sequence());
                requireNonNegative(segment.distanceMeters(),
                        path.pathKey() + ".segment[" + segment.sequence() + "].distanceMeters");
                requireNonNegative(segment.durationSeconds(),
                        path.pathKey() + ".segment[" + segment.sequence() + "].durationSeconds");
            }
        }
    }

    private Set<Long> uniqueIds(java.util.List<Long> ids, String type) {
        Set<Long> unique = new HashSet<>();
        for (Long id : ids) {
            require(id != null && id > 0, type + " id must be positive: " + id);
            require(unique.add(id), "duplicate " + type + " id: " + id);
        }
        return unique;
    }

    private long owner(Map<Long, Long> owners, long id, String field) {
        Long owner = owners.get(id);
        require(owner != null, field + " references missing id: " + id);
        return owner;
    }

    private void requireCoordinatePair(BigDecimal longitude, BigDecimal latitude, String field) {
        require((longitude == null) == (latitude == null), field + " coordinate pair is incomplete");
        if (longitude != null) {
            require(longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                            && longitude.compareTo(BigDecimal.valueOf(180)) <= 0,
                    field + ".currentLongitude out of range");
            require(latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                            && latitude.compareTo(BigDecimal.valueOf(90)) <= 0,
                    field + ".currentLatitude out of range");
        }
    }

    private void requireOptionalReference(Set<Long> ids, Long id, String field) {
        if (id != null) {
            requireReference(ids, id, field);
        }
    }

    private void requireReference(Set<Long> ids, long id, String field) {
        require(ids.contains(id), field + " references missing id: " + id);
    }

    private void requirePositiveIfPresent(BigDecimal value, String field) {
        if (value != null) {
            require(value.signum() > 0, field + " must be positive");
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value != null) {
            require(value.signum() >= 0, field + " must be non-negative");
        }
    }

    private void requireDecimal(BigDecimal value, String field) {
        require(value != null, field + " must not be null");
    }

    private void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " must not be blank");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ScenarioSnapshotValidationException(message);
        }
    }
}
