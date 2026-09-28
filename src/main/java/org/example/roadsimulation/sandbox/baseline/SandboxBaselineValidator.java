package org.example.roadsimulation.sandbox.baseline;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.InitialInventory;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingChain;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingEdge;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingInput;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingStage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Structural and semantic checks shared by formal-baseline and effective-data preparation. */
public final class SandboxBaselineValidator {

    private static final Set<String> DRIVER_CARGO_PREFERENCES = Set.of(
            "水泥", "家具", "轮胎", "橡胶", "汽车", "钢铁", "木材", "矿石", "石料");

    public void validateBaseline(SandboxBaselinePackageV1 baseline) {
        require(baseline != null, "Baseline must not be null");
        requireText(baseline.baselineId(), "baselineId");
        require(baseline.source() != null, "source is required");
        require(baseline.source().capturedAtUtc() != null, "source.capturedAtUtc is required");
        require(baseline.fingerprints() != null, "fingerprints are required");
        require(baseline.eligibilityPolicy() != null, "eligibilityPolicy is required");
        require(baseline.restorePolicy() != null, "restorePolicy is required");
        require(baseline.data() != null, "data is required");
        validateData(baseline.data(), false);
        validateSourceCounts(baseline);
        validateExclusionsExist(baseline);
    }

    public void validateEffectiveData(Data data) {
        validateData(data, true);
    }

    private void validateData(Data data, boolean executableEligibilityRequired) {
        require(data != null, "data must not be null");
        Map<Long, SandboxBaselinePackageV1.Poi> pois = unique(
                data.pois(), SandboxBaselinePackageV1.Poi::id, "poi");
        Map<Long, SandboxBaselinePackageV1.Goods> goods = unique(
                data.goods(), SandboxBaselinePackageV1.Goods::id, "goods");
        Map<Long, SandboxBaselinePackageV1.Vehicle> vehicles = unique(
                data.vehicles(), SandboxBaselinePackageV1.Vehicle::id, "vehicle");
        Map<Long, SandboxBaselinePackageV1.Driver> drivers = unique(
                data.drivers(), SandboxBaselinePackageV1.Driver::id, "driver");

        for (SandboxBaselinePackageV1.Poi poi : data.pois()) {
            requireText(poi.name(), "poi[" + poi.id() + "].name");
            requireText(poi.poiType(), "poi[" + poi.id() + "].poiType");
            requireDecimalRange(poi.longitude(), -180, 180, "poi[" + poi.id() + "].longitude");
            requireDecimalRange(poi.latitude(), -90, 90, "poi[" + poi.id() + "].latitude");
        }

        Set<String> skus = new HashSet<>();
        for (SandboxBaselinePackageV1.Goods item : data.goods()) {
            requireText(item.name(), "goods[" + item.id() + "].name");
            requireText(item.sku(), "goods[" + item.id() + "].sku");
            require(skus.add(item.sku()), "Duplicate goods SKU: " + item.sku());
            if (executableEligibilityRequired) {
                requirePositive(item.weightPerUnitTonnes(), "goods[" + item.id() + "].weightPerUnitTonnes");
                requirePositive(item.volumePerUnitCubicMeters(), "goods[" + item.id() + "].volumePerUnitCubicMeters");
            } else {
                requireNonNegativeIfPresent(item.weightPerUnitTonnes(),
                        "goods[" + item.id() + "].weightPerUnitTonnes");
                requireNonNegativeIfPresent(item.volumePerUnitCubicMeters(),
                        "goods[" + item.id() + "].volumePerUnitCubicMeters");
            }
        }

        Set<String> licensePlates = new HashSet<>();
        for (SandboxBaselinePackageV1.Vehicle vehicle : data.vehicles()) {
            requireText(vehicle.licensePlate(), "vehicle[" + vehicle.id() + "].licensePlate");
            require(licensePlates.add(vehicle.licensePlate()),
                    "Duplicate vehicle license plate: " + vehicle.licensePlate());
            requirePositive(vehicle.maxLoadCapacityTonnes(),
                    "vehicle[" + vehicle.id() + "].maxLoadCapacityTonnes");
            if (executableEligibilityRequired) {
                requirePositive(vehicle.cargoVolumeCubicMeters(),
                        "vehicle[" + vehicle.id() + "].cargoVolumeCubicMeters");
            } else {
                requireNonNegativeIfPresent(vehicle.cargoVolumeCubicMeters(),
                        "vehicle[" + vehicle.id() + "].cargoVolumeCubicMeters");
            }
            requireNonNegativeIfPresent(vehicle.lengthMeters(), "vehicle[" + vehicle.id() + "].lengthMeters");
            requireNonNegativeIfPresent(vehicle.widthMeters(), "vehicle[" + vehicle.id() + "].widthMeters");
            requireNonNegativeIfPresent(vehicle.heightMeters(), "vehicle[" + vehicle.id() + "].heightMeters");
        }

        Set<String> driverNames = new HashSet<>();
        Set<String> driverPhones = new HashSet<>();
        for (SandboxBaselinePackageV1.Driver driver : data.drivers()) {
            requireText(driver.driverName(), "driver[" + driver.id() + "].driverName");
            require(driverNames.add(driver.driverName()),
                    "Duplicate driver name: " + driver.driverName());
            requireText(driver.driverPhone(), "driver[" + driver.id() + "].driverPhone");
            require(driverPhones.add(driver.driverPhone()),
                    "Duplicate driver phone: " + driver.driverPhone());
            require(DRIVER_CARGO_PREFERENCES.contains(driver.preferredCargoType()),
                    "Invalid driver cargo preference: " + driver.id());
            requirePositive(driver.preferredMaxDistanceKm(),
                    "driver[" + driver.id() + "].preferredMaxDistanceKm");
            requirePositive(driver.preferredMaxWeightTons(),
                    "driver[" + driver.id() + "].preferredMaxWeightTons");
        }

        Set<String> bindingPairs = new HashSet<>();
        Set<Long> vehiclesWithDriver = new HashSet<>();
        Set<Long> boundDrivers = new HashSet<>();
        for (SandboxBaselinePackageV1.DriverVehicleBinding binding : data.driverVehicleBindings()) {
            require(drivers.containsKey(binding.driverId()),
                    "Driver binding references missing driver: " + binding.driverId());
            require(vehicles.containsKey(binding.vehicleId()),
                    "Driver binding references missing vehicle: " + binding.vehicleId());
            require(bindingPairs.add(binding.driverId() + ":" + binding.vehicleId()),
                    "Duplicate driver vehicle binding: " + binding.driverId() + ":" + binding.vehicleId());
            require(boundDrivers.add(binding.driverId()),
                    "Driver is bound to more than one vehicle: " + binding.driverId());
            vehiclesWithDriver.add(binding.vehicleId());
        }
        for (Long vehicleId : vehicles.keySet()) {
            require(vehiclesWithDriver.contains(vehicleId),
                    "Vehicle has no baseline driver: " + vehicleId);
        }
        if (executableEligibilityRequired) {
            require(boundDrivers.size() == drivers.size(),
                    "Effective data must not contain unbound drivers");
        }

        validateProcessing(data, pois, goods);
        validateInventories(data, pois.keySet(), goods.keySet());
    }

    private void validateProcessing(
            Data data,
            Map<Long, SandboxBaselinePackageV1.Poi> pois,
            Map<Long, SandboxBaselinePackageV1.Goods> goods
    ) {
        Map<Long, ProcessingChain> chains = unique(
                data.processingChains(), ProcessingChain::id, "processingChain");
        Set<String> chainCodes = new HashSet<>();
        Map<Long, Long> globalStageOwners = new HashMap<>();
        Map<Long, Long> globalInputOwners = new HashMap<>();
        Set<Long> globalEdgeIds = new HashSet<>();

        for (ProcessingChain chain : chains.values()) {
            requireText(chain.chainCode(), "processingChain[" + chain.id() + "].chainCode");
            require(chainCodes.add(chain.chainCode()), "Duplicate chain code: " + chain.chainCode());
            requireText(chain.chainName(), "processingChain[" + chain.id() + "].chainName");
            requireText(chain.status(), "processingChain[" + chain.id() + "].status");
            require(!chain.stages().isEmpty(), "Processing chain has no stages: " + chain.id());

            Map<Long, ProcessingStage> stages = new HashMap<>();
            Map<Long, ProcessingInput> inputs = new HashMap<>();
            Set<String> stageKeys = new HashSet<>();
            for (ProcessingStage stage : chain.stages()) {
                require(stage.id() > 0 && stages.putIfAbsent(stage.id(), stage) == null,
                        "Duplicate or invalid stage id: " + stage.id());
                require(globalStageOwners.putIfAbsent(stage.id(), chain.id()) == null,
                        "Stage id belongs to more than one chain: " + stage.id());
                require(stage.stageOrder() > 0, "Stage order must be positive: " + stage.id());
                requireText(stage.stageKey(), "stage[" + stage.id() + "].stageKey");
                require(stageKeys.add(stage.stageKey()),
                        "Duplicate stage key in chain " + chain.id() + ": " + stage.stageKey());
                requireText(stage.stageName(), "stage[" + stage.id() + "].stageName");
                SandboxBaselinePackageV1.Poi poi = requireReference(
                        pois, stage.processingPoiId(), "stage[" + stage.id() + "].processingPoiId");
                require(stage.requiredPoiType().equals(poi.poiType()),
                        "Stage " + stage.id() + " requiredPoiType does not match template POI type");
                requireOptionalReference(goods, stage.legacyInputGoodsId(),
                        "stage[" + stage.id() + "].legacyInputGoodsId");
                requireOptionalReference(goods, stage.outputGoodsId(),
                        "stage[" + stage.id() + "].outputGoodsId");
                requirePositive(stage.legacyInputWeightRatio(),
                        "stage[" + stage.id() + "].legacyInputWeightRatio");
                requirePositive(stage.outputWeightRatio(),
                        "stage[" + stage.id() + "].outputWeightRatio");
                require(stage.processingTimeMinutes() >= 0,
                        "Stage processing time must not be negative: " + stage.id());
                if (stage.minBatchSize() != null && stage.maxCapacityPerCycle() != null) {
                    require(stage.minBatchSize().compareTo(stage.maxCapacityPerCycle()) <= 0,
                            "Stage min batch exceeds capacity: " + stage.id());
                }

                Set<String> inputKeys = new HashSet<>();
                for (ProcessingInput input : stage.inputs()) {
                    require(input.id() > 0 && inputs.putIfAbsent(input.id(), input) == null,
                            "Duplicate or invalid input id: " + input.id());
                    require(globalInputOwners.putIfAbsent(input.id(), stage.id()) == null,
                            "Input id belongs to more than one stage: " + input.id());
                    requireText(input.inputKey(), "input[" + input.id() + "].inputKey");
                    require(inputKeys.add(input.inputKey()),
                            "Duplicate input key in stage " + stage.id() + ": " + input.inputKey());
                    SandboxBaselinePackageV1.Goods inputGoods = requireReference(
                            goods, input.goodsId(), "input[" + input.id() + "].goodsId");
                    require(inputGoods.sku().equals(input.sku()),
                            "Input SKU does not match goods id: " + input.id());
                    requirePositive(input.inputShare(), "input[" + input.id() + "].inputShare");
                }
            }

            Set<Long> stagesWithOutgoingEdge = new HashSet<>();
            for (ProcessingEdge edge : chain.edges()) {
                require(edge.id() > 0 && globalEdgeIds.add(edge.id()),
                        "Duplicate or invalid edge id: " + edge.id());
                ProcessingStage from = requireReference(stages, edge.fromStageId(), "edge.fromStageId");
                ProcessingStage to = requireReference(stages, edge.toStageId(), "edge.toStageId");
                ProcessingInput targetInput = requireReference(inputs, edge.toStageInputId(),
                        "edge.toStageInputId");
                require(globalInputOwners.get(edge.toStageInputId()).equals(to.id()),
                        "Edge input does not belong to target stage: " + edge.id());
                require(from.outputGoodsSku() != null && from.outputGoodsSku().equals(targetInput.sku()),
                        "Edge SKU mismatch: " + edge.id());
                stagesWithOutgoingEdge.add(from.id());
            }

            Set<Long> sinks = new HashSet<>(stages.keySet());
            sinks.removeAll(stagesWithOutgoingEdge);
            require(sinks.size() == 1, "Processing chain must have exactly one sink: " + chain.id());
            long sinkId = sinks.iterator().next();
            for (ProcessingStage stage : stages.values()) {
                if (stage.id() == sinkId) {
                    require(stage.outputGoodsId() == null,
                            "Sink stage must not reference output goods: " + stage.id());
                } else {
                    require(stage.outputGoodsId() != null,
                            "Non-sink stage must reference output goods: " + stage.id());
                }
            }
        }
    }

    private void validateInventories(Data data, Set<Long> poiIds, Set<Long> goodsIds) {
        Set<String> pairs = new HashSet<>();
        for (InitialInventory inventory : data.initialInventories()) {
            require(poiIds.contains(inventory.poiId()), "Inventory references missing POI: " + inventory.poiId());
            require(goodsIds.contains(inventory.goodsId()),
                    "Inventory references missing goods: " + inventory.goodsId());
            require(inventory.quantity() >= 0, "Inventory quantity must not be negative");
            require(pairs.add(inventory.poiId() + ":" + inventory.goodsId()),
                    "Duplicate inventory pair: " + inventory.poiId() + ":" + inventory.goodsId());
        }
    }

    private void validateSourceCounts(SandboxBaselinePackageV1 baseline) {
        Map<String, Integer> counts = baseline.source().tableRowCounts();
        requireCount(counts, "poi", baseline.data().pois().size());
        requireCount(counts, "goods", baseline.data().goods().size());
        requireCount(counts, "vehicle", baseline.data().vehicles().size());
        requireCount(counts, "driver", baseline.data().drivers().size());
        requireCount(counts, "driver_vehicle", baseline.data().driverVehicleBindings().size());
        requireCount(counts, "enrollment", baseline.data().initialInventories().size());
        requireCount(counts, "processing_chain", baseline.data().processingChains().size());
        requireCount(counts, "processing_stage", baseline.data().processingChains().stream()
                .mapToInt(chain -> chain.stages().size()).sum());
        requireCount(counts, "processing_stage_input", baseline.data().processingChains().stream()
                .flatMap(chain -> chain.stages().stream()).mapToInt(stage -> stage.inputs().size()).sum());
        requireCount(counts, "processing_stage_edge", baseline.data().processingChains().stream()
                .mapToInt(chain -> chain.edges().size()).sum());
    }

    private void validateExclusionsExist(SandboxBaselinePackageV1 baseline) {
        Set<Long> poiIds = ids(baseline.data().pois(), SandboxBaselinePackageV1.Poi::id);
        Set<Long> goodsIds = ids(baseline.data().goods(), SandboxBaselinePackageV1.Goods::id);
        Set<Long> vehicleIds = ids(baseline.data().vehicles(), SandboxBaselinePackageV1.Vehicle::id);
        baseline.eligibilityPolicy().defaultExcludedPois().forEach(value ->
                require(poiIds.contains(value.id()), "Excluded POI is missing: " + value.id()));
        baseline.eligibilityPolicy().defaultExcludedGoods().forEach(value ->
                require(goodsIds.contains(value.id()), "Excluded goods is missing: " + value.id()));
        baseline.eligibilityPolicy().defaultExcludedVehicles().forEach(value ->
                require(vehicleIds.contains(value.id()), "Excluded vehicle is missing: " + value.id()));
    }

    private <T> Map<Long, T> unique(Iterable<T> values, Function<T, Long> id, String type) {
        Map<Long, T> result = new HashMap<>();
        for (T value : values) {
            long key = id.apply(value);
            require(key > 0 && result.putIfAbsent(key, value) == null,
                    "Duplicate or invalid " + type + " id: " + key);
        }
        return result;
    }

    private <T> Set<Long> ids(Iterable<T> values, Function<T, Long> id) {
        Set<Long> result = new HashSet<>();
        for (T value : values) {
            result.add(id.apply(value));
        }
        return result;
    }

    private <T> T requireReference(Map<Long, T> values, Long id, String field) {
        require(id != null && values.containsKey(id), field + " references missing id: " + id);
        return values.get(id);
    }

    private <T> void requireOptionalReference(Map<Long, T> values, Long id, String field) {
        if (id != null) {
            requireReference(values, id, field);
        }
    }

    private void requireCount(Map<String, Integer> counts, String name, int actual) {
        require(counts.get(name) != null && counts.get(name) == actual,
                "Source count mismatch for " + name + ": expected=" + counts.get(name) + ", actual=" + actual);
    }

    private void requirePositive(BigDecimal value, String field) {
        require(value != null && value.signum() > 0, field + " must be positive");
    }

    private void requireNonNegativeIfPresent(BigDecimal value, String field) {
        require(value == null || value.signum() >= 0, field + " must not be negative");
    }

    private void requireDecimalRange(BigDecimal value, int min, int max, String field) {
        require(value != null
                        && value.compareTo(BigDecimal.valueOf(min)) >= 0
                        && value.compareTo(BigDecimal.valueOf(max)) <= 0,
                field + " is out of range");
    }

    private void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " must not be blank");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new SandboxBaselineException(message);
        }
    }
}
