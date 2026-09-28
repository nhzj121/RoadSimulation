package org.example.roadsimulation.sandbox.scenario.definition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseData;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineException;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingChain;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingInput;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingStage;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineValidator;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1.*;

/** Resolves one scenario definition exclusively against an authenticated formal baseline. */
public final class SandboxScenarioCompiler {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal SHARE_TOLERANCE = new BigDecimal("0.000001");

    private final ObjectMapper objectMapper;
    private final SandboxBaselineLoader baselineLoader;
    private final SandboxBaselineValidator baselineValidator;
    private final SandboxScenarioCodec codec;

    public SandboxScenarioCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.baselineLoader = new SandboxBaselineLoader(this.objectMapper);
        this.baselineValidator = new SandboxBaselineValidator();
        this.codec = new SandboxScenarioCodec(this.objectMapper);
    }

    public SandboxScenarioDefinitionV1 readDefinition(Resource resource) {
        try (var input = resource.getInputStream()) {
            return objectMapper.readValue(input, SandboxScenarioDefinitionV1.class);
        } catch (IOException exception) {
            throw new SandboxScenarioException(
                    "INVALID_SCENARIO_JSON", "Cannot read sandbox scenario definition: " + resource, exception);
        }
    }

    public CompiledSandboxScenario compile(Resource baselineResource, Resource scenarioResource) {
        return compile(baselineLoader.load(baselineResource), readDefinition(scenarioResource));
    }

    public CompiledSandboxScenario compile(
            LoadedSandboxBaseline loaded,
            SandboxScenarioDefinitionV1 source
    ) {
        SandboxScenarioDefinitionV1 definition = normalizeAndValidateDefinition(source);
        EffectiveBaseData eligible = baselineLoader.selectAllEligible(loaded);
        validateBaselineReference(definition.baseline(), loaded, eligible);

        Data universe = eligible.data();
        List<SandboxBaselinePackageV1.Vehicle> vehicles = selectVehicles(universe.vehicles(), definition.selection().vehicles());
        List<SandboxBaselinePackageV1.Poi> pois = selectPois(universe.pois(), definition.selection().pois());
        List<SandboxBaselinePackageV1.Goods> goods = selectGoods(universe.goods(), definition.selection().goods());
        List<ProcessingChain> chains = selectByIds(
                universe.processingChains(), ProcessingChain::id,
                definition.selection().processingChains(), true, "processingChain");

        require(!vehicles.isEmpty(), "EMPTY_VEHICLE_SELECTION", "Scenario must select at least one vehicle");
        require(!pois.isEmpty(), "EMPTY_POI_SELECTION", "Scenario must select at least one POI");
        require(!goods.isEmpty(), "EMPTY_GOODS_SELECTION", "Scenario must select at least one goods record");

        Set<Long> selectedVehicleClosureIds = idSet(vehicles, SandboxBaselinePackageV1.Vehicle::id);
        List<SandboxBaselinePackageV1.DriverVehicleBinding> driverBindings =
                universe.driverVehicleBindings().stream()
                        .filter(binding -> selectedVehicleClosureIds.contains(binding.vehicleId()))
                        .toList();
        Set<Long> selectedDriverIds = driverBindings.stream()
                .map(SandboxBaselinePackageV1.DriverVehicleBinding::driverId)
                .collect(Collectors.toSet());
        List<SandboxBaselinePackageV1.Driver> drivers = universe.drivers().stream()
                .filter(driver -> selectedDriverIds.contains(driver.id()))
                .toList();

        Data selectedBaseProjection = codec.normalizeData(
                new Data(pois, goods, vehicles, chains, List.of(), drivers, driverBindings));
        String baseHash = codec.baseDataProjectionHash(
                loaded.baseline().eligibilityPolicy().policyVersion(), selectedBaseProjection);

        Map<Long, BigDecimal> outputRatios = uniqueMap(
                definition.overrides().stageOutputRatios(), StageOutputRatio::stageId,
                StageOutputRatio::outputWeightRatio, "stage output ratio");
        Map<Long, BigDecimal> inputShares = uniqueMap(
                definition.overrides().processingInputShares(), ProcessingInputShare::inputId,
                ProcessingInputShare::inputShare, "processing input share");
        outputRatios.forEach((id, value) -> validateRatio(value, true, "stage[" + id + "].outputWeightRatio"));
        inputShares.forEach((id, value) -> validateRatio(value, false, "input[" + id + "].inputShare"));

        List<ProcessingChain> overriddenChains = applyProcessingOverrides(chains, outputRatios, inputShares);
        validateAllOverridesApplied(overriddenChains, outputRatios.keySet(), inputShares.keySet());

        Set<Long> selectedPoiIds = idSet(pois, SandboxBaselinePackageV1.Poi::id);
        Set<Long> selectedGoodsIds = idSet(goods, SandboxBaselinePackageV1.Goods::id);
        Set<Long> selectedVehicleIds = idSet(vehicles, SandboxBaselinePackageV1.Vehicle::id);
        List<SandboxBaselinePackageV1.InitialInventory> inventories = normalizeInventories(
                definition.overrides().initialInventories(), selectedPoiIds, selectedGoodsIds);
        List<EffectiveScenarioData.VehicleInitialization> initializations = normalizeInitializations(
                definition.overrides().vehicleInitialPois(), selectedVehicleIds, pois);

        Data selected = codec.normalizeData(new Data(
                pois, goods, vehicles, overriddenChains, inventories, drivers, driverBindings));
        try {
            baselineValidator.validateEffectiveData(selected);
        } catch (SandboxBaselineException exception) {
            throw new SandboxScenarioException("INVALID_SCENARIO_DEPENDENCY", exception.getMessage(), exception);
        }
        validateInputShareSums(selected.processingChains());

        SandboxScenarioRevisionV1.ResolvedSelection resolved = new SandboxScenarioRevisionV1.ResolvedSelection(
                ids(vehicles, SandboxBaselinePackageV1.Vehicle::id),
                ids(pois, SandboxBaselinePackageV1.Poi::id),
                ids(goods, SandboxBaselinePackageV1.Goods::id),
                ids(overriddenChains, ProcessingChain::id));
        String definitionHash = codec.definitionHash(definition, resolved);
        String effectiveHash = codec.effectiveDataHash(selected, initializations);
        return new CompiledSandboxScenario(
                definition,
                resolved,
                new EffectiveScenarioData(selected, initializations, baseHash, effectiveHash),
                definitionHash);
    }

    private SandboxScenarioDefinitionV1 normalizeAndValidateDefinition(SandboxScenarioDefinitionV1 source) {
        require(source != null, "MISSING_SCENARIO", "Scenario definition is required");
        require(ARTIFACT_VERSION.equals(source.artifactVersion()),
                "UNSUPPORTED_SCENARIO_VERSION", "Unsupported artifactVersion: " + source.artifactVersion());
        String key = text(source.scenarioKey(), "scenarioKey");
        require(key.matches("[a-z0-9][a-z0-9-]{0,127}"),
                "INVALID_SCENARIO_KEY", "scenarioKey must be lower-case letters, digits and hyphens");
        require(source.baseline() != null, "MISSING_BASELINE_REFERENCE", "baseline is required");
        require(source.selection() != null, "MISSING_SELECTION", "selection is required");
        require(source.overrides() != null, "MISSING_OVERRIDES", "overrides is required");

        Selection selection = new Selection(
                normalizeVehicleSelection(source.selection().vehicles()),
                normalizePoiSelection(source.selection().pois()),
                normalizeGoodsSelection(source.selection().goods()),
                normalizeIdSelection(source.selection().processingChains(), "processingChains"));
        Overrides overrides = normalizeOverrides(source.overrides());
        return new SandboxScenarioDefinitionV1(
                ARTIFACT_VERSION, key,
                nullableText(source.displayName()), nullableText(source.description()),
                source.baseline(), selection, overrides);
    }

    private VehicleSelection normalizeVehicleSelection(VehicleSelection value) {
        require(value != null, "MISSING_VEHICLE_SELECTION", "selection.vehicles is required");
        validateRange(value.minimumLoadCapacityTonnes(), value.maximumLoadCapacityTonnes(), "load capacity");
        validateRange(value.minimumCargoVolumeCubicMeters(), value.maximumCargoVolumeCubicMeters(), "cargo volume");
        return new VehicleSelection(requireMode(value.mode(), "vehicles"), ids(value.includeIds(), "vehicle.includeIds"),
                ids(value.excludeIds(), "vehicle.excludeIds"), value.minimumLoadCapacityTonnes(),
                value.maximumLoadCapacityTonnes(), value.minimumCargoVolumeCubicMeters(),
                value.maximumCargoVolumeCubicMeters(), strings(value.vehicleTypes(), "vehicleTypes"),
                strings(value.modelTypes(), "modelTypes"));
    }

    private PoiSelection normalizePoiSelection(PoiSelection value) {
        require(value != null, "MISSING_POI_SELECTION", "selection.pois is required");
        return new PoiSelection(requireMode(value.mode(), "pois"), ids(value.includeIds(), "poi.includeIds"),
                ids(value.excludeIds(), "poi.excludeIds"), strings(value.poiTypes(), "poiTypes"));
    }

    private GoodsSelection normalizeGoodsSelection(GoodsSelection value) {
        require(value != null, "MISSING_GOODS_SELECTION", "selection.goods is required");
        return new GoodsSelection(requireMode(value.mode(), "goods"), ids(value.includeIds(), "goods.includeIds"),
                ids(value.excludeIds(), "goods.excludeIds"), strings(value.categories(), "goods.categories"));
    }

    private IdSelection normalizeIdSelection(IdSelection value, String field) {
        require(value != null, "MISSING_ID_SELECTION", "selection." + field + " is required");
        return new IdSelection(requireMode(value.mode(), field), ids(value.includeIds(), field + ".includeIds"),
                ids(value.excludeIds(), field + ".excludeIds"));
    }

    private Overrides normalizeOverrides(Overrides value) {
        return new Overrides(
                list(value.stageOutputRatios()).stream().sorted(Comparator.comparingLong(StageOutputRatio::stageId)).toList(),
                list(value.processingInputShares()).stream().sorted(Comparator.comparingLong(ProcessingInputShare::inputId)).toList(),
                list(value.initialInventories()).stream().sorted(Comparator.comparingLong(InitialInventory::poiId)
                        .thenComparingLong(InitialInventory::goodsId)).toList(),
                list(value.vehicleInitialPois()).stream().map(item -> new VehicleInitialPoi(
                                item.vehicleId(), item.poiId(), item.initializationPolicy() == null
                                ? VehicleInitialPoi.FIXED_POI : item.initializationPolicy()))
                        .sorted(Comparator.comparingLong(VehicleInitialPoi::vehicleId)).toList());
    }

    private void validateBaselineReference(
            BaselineReference reference,
            LoadedSandboxBaseline loaded,
            EffectiveBaseData eligible
    ) {
        require(loaded.baseline().baselineId().equals(reference.baselineId())
                        && loaded.baseline().fingerprints().restorationPayloadSha256().equals(reference.restorationPayloadSha256())
                        && loaded.baseline().fingerprints().simulationFactsSha256().equals(reference.simulationFactsSha256())
                        && eligible.effectiveBaseDataSha256().equals(reference.effectiveBaseDataSha256()),
                "BASELINE_REFERENCE_MISMATCH", "Scenario baseline reference does not match the formal baseline");
    }

    private List<SandboxBaselinePackageV1.Vehicle> selectVehicles(
            List<SandboxBaselinePackageV1.Vehicle> universe,
            VehicleSelection selection
    ) {
        Predicate<SandboxBaselinePackageV1.Vehicle> filter = value ->
                atLeast(value.maxLoadCapacityTonnes(), selection.minimumLoadCapacityTonnes())
                        && atMost(value.maxLoadCapacityTonnes(), selection.maximumLoadCapacityTonnes())
                        && atLeast(value.cargoVolumeCubicMeters(), selection.minimumCargoVolumeCubicMeters())
                        && atMost(value.cargoVolumeCubicMeters(), selection.maximumCargoVolumeCubicMeters())
                        && matches(selection.vehicleTypes(), value.vehicleType())
                        && matches(selection.modelTypes(), value.modelType());
        return select(universe, SandboxBaselinePackageV1.Vehicle::id, selection.mode(), selection.includeIds(),
                selection.excludeIds(), filter, false, "vehicle");
    }

    private List<SandboxBaselinePackageV1.Poi> selectPois(
            List<SandboxBaselinePackageV1.Poi> universe,
            PoiSelection selection
    ) {
        return select(universe, SandboxBaselinePackageV1.Poi::id, selection.mode(), selection.includeIds(),
                selection.excludeIds(), value -> matches(selection.poiTypes(), value.poiType()), false, "poi");
    }

    private List<SandboxBaselinePackageV1.Goods> selectGoods(
            List<SandboxBaselinePackageV1.Goods> universe,
            GoodsSelection selection
    ) {
        return select(universe, SandboxBaselinePackageV1.Goods::id, selection.mode(), selection.includeIds(),
                selection.excludeIds(), value -> matches(selection.categories(), value.category()), false, "goods");
    }

    private <T> List<T> selectByIds(
            List<T> universe,
            Function<T, Long> id,
            IdSelection selection,
            boolean emptyExplicitAllowed,
            String field
    ) {
        return select(universe, id, selection.mode(), selection.includeIds(), selection.excludeIds(),
                ignored -> true, emptyExplicitAllowed, field);
    }

    private <T> List<T> select(
            List<T> universe,
            Function<T, Long> id,
            SelectionMode mode,
            List<Long> includeIds,
            List<Long> excludeIds,
            Predicate<T> filter,
            boolean emptyExplicitAllowed,
            String field
    ) {
        Map<Long, T> byId = universe.stream().collect(Collectors.toMap(id, Function.identity()));
        validateKnownIds(includeIds, byId.keySet(), field + ".includeIds");
        validateKnownIds(excludeIds, byId.keySet(), field + ".excludeIds");
        if (mode == SelectionMode.EXPLICIT_IDS && includeIds.isEmpty() && !emptyExplicitAllowed) {
            throw new SandboxScenarioException("EMPTY_EXPLICIT_SELECTION", field + " includeIds must not be empty");
        }
        Set<Long> candidates = mode == SelectionMode.ALL_ELIGIBLE
                ? new HashSet<>(byId.keySet()) : new HashSet<>(includeIds);
        candidates.removeAll(excludeIds);
        return universe.stream().filter(value -> candidates.contains(id.apply(value))).filter(filter)
                .sorted(Comparator.comparingLong(id::apply)).toList();
    }

    private List<ProcessingChain> applyProcessingOverrides(
            List<ProcessingChain> chains,
            Map<Long, BigDecimal> ratios,
            Map<Long, BigDecimal> shares
    ) {
        return chains.stream().map(chain -> new ProcessingChain(
                chain.id(), chain.chainCode(), chain.chainName(), chain.status(), chain.description(),
                chain.stages().stream().map(stage -> new ProcessingStage(
                        stage.id(), stage.stageOrder(), stage.stageKey(), stage.stageName(), stage.description(),
                        stage.processingPoiId(), stage.requiredPoiType(), stage.legacyInputGoodsId(),
                        stage.legacyInputGoodsSku(), stage.legacyInputWeightRatio(), stage.outputGoodsId(),
                        stage.outputGoodsSku(), ratios.getOrDefault(stage.id(), stage.outputWeightRatio()),
                        stage.processingTimeMinutes(), stage.minBatchSize(), stage.maxCapacityPerCycle(),
                        stage.inputs().stream().map(input -> new ProcessingInput(
                                input.id(), input.inputKey(), input.goodsId(), input.sku(),
                                shares.getOrDefault(input.id(), input.inputShare()))).toList())).toList(),
                chain.edges())).toList();
    }

    private void validateAllOverridesApplied(
            List<ProcessingChain> chains,
            Set<Long> ratioIds,
            Set<Long> shareIds
    ) {
        Set<Long> stages = chains.stream().flatMap(chain -> chain.stages().stream())
                .map(ProcessingStage::id).collect(Collectors.toSet());
        Set<Long> inputs = chains.stream().flatMap(chain -> chain.stages().stream())
                .flatMap(stage -> stage.inputs().stream()).map(ProcessingInput::id).collect(Collectors.toSet());
        require(stages.containsAll(ratioIds), "OVERRIDE_TARGET_NOT_SELECTED", "Stage ratio override targets an unselected stage");
        require(inputs.containsAll(shareIds), "OVERRIDE_TARGET_NOT_SELECTED", "Input share override targets an unselected input");
    }

    private List<SandboxBaselinePackageV1.InitialInventory> normalizeInventories(
            List<InitialInventory> values,
            Set<Long> poiIds,
            Set<Long> goodsIds
    ) {
        Set<String> pairs = new HashSet<>();
        List<SandboxBaselinePackageV1.InitialInventory> result = new ArrayList<>();
        for (InitialInventory value : values) {
            require(value.quantity() > 0, "INVALID_INVENTORY", "Initial inventory quantity must be positive");
            require(poiIds.contains(value.poiId()) && goodsIds.contains(value.goodsId()),
                    "INVALID_INVENTORY_REFERENCE", "Initial inventory references unselected POI or goods");
            require(pairs.add(value.poiId() + ":" + value.goodsId()),
                    "DUPLICATE_INVENTORY", "Duplicate initial inventory pair");
            result.add(new SandboxBaselinePackageV1.InitialInventory(
                    value.poiId(), value.goodsId(), value.quantity()));
        }
        return result;
    }

    private List<EffectiveScenarioData.VehicleInitialization> normalizeInitializations(
            List<VehicleInitialPoi> values,
            Set<Long> vehicleIds,
            List<SandboxBaselinePackageV1.Poi> pois
    ) {
        Map<Long, SandboxBaselinePackageV1.Poi> poiById = pois.stream()
                .collect(Collectors.toMap(SandboxBaselinePackageV1.Poi::id, Function.identity()));
        Set<Long> vehicles = new HashSet<>();
        List<EffectiveScenarioData.VehicleInitialization> result = new ArrayList<>();
        for (VehicleInitialPoi value : values) {
            require(VehicleInitialPoi.FIXED_POI.equals(value.initializationPolicy()),
                    "INVALID_INITIALIZATION_POLICY", "Only FIXED_POI is supported in phase two");
            require(vehicleIds.contains(value.vehicleId()),
                    "INVALID_VEHICLE_INITIAL_POI", "Initial POI references an unselected vehicle");
            SandboxBaselinePackageV1.Poi poi = poiById.get(value.poiId());
            require(poi != null, "INVALID_VEHICLE_INITIAL_POI", "Initial POI is not selected");
            require(Set.of("WAREHOUSE", "DISTRIBUTION_CENTER").contains(poi.poiType()),
                    "INVALID_VEHICLE_INITIAL_POI_TYPE", "Vehicle initial POI must be a warehouse or distribution center");
            require(vehicles.add(value.vehicleId()),
                    "DUPLICATE_VEHICLE_INITIAL_POI", "Vehicle has more than one fixed initial POI");
            result.add(new EffectiveScenarioData.VehicleInitialization(
                    value.vehicleId(), VehicleInitialPoi.FIXED_POI, value.poiId()));
        }
        return result.stream().sorted(Comparator.comparingLong(
                EffectiveScenarioData.VehicleInitialization::vehicleId)).toList();
    }

    private void validateInputShareSums(List<ProcessingChain> chains) {
        for (ProcessingChain chain : chains) {
            for (ProcessingStage stage : chain.stages()) {
                BigDecimal sum = stage.inputs().stream().map(ProcessingInput::inputShare)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                require(sum.subtract(ONE).abs().compareTo(SHARE_TOLERANCE) <= 0,
                        "INVALID_INPUT_SHARE_SUM", "Input shares must sum to 1 for stage " + stage.id());
            }
        }
    }

    private void validateRatio(BigDecimal value, boolean atMostOne, String field) {
        require(value != null && value.signum() > 0, "INVALID_RATIO", field + " must be positive");
        require(!atMostOne || value.compareTo(ONE) <= 0, "INVALID_RATIO", field + " must not exceed 1");
        require(value.stripTrailingZeros().scale() <= 6, "INVALID_RATIO_PRECISION", field + " has more than 6 decimals");
    }

    private <T> Map<Long, BigDecimal> uniqueMap(
            List<T> values,
            Function<T, Long> id,
            Function<T, BigDecimal> amount,
            String field
    ) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (T value : values) {
            long key = id.apply(value);
            require(key > 0 && result.putIfAbsent(key, amount.apply(value)) == null,
                    "DUPLICATE_OVERRIDE", "Duplicate or invalid " + field + " target: " + key);
        }
        return result;
    }

    private void validateRange(BigDecimal minimum, BigDecimal maximum, String field) {
        require(minimum == null || minimum.signum() >= 0, "INVALID_FILTER", field + " minimum is negative");
        require(maximum == null || maximum.signum() >= 0, "INVALID_FILTER", field + " maximum is negative");
        require(minimum == null || maximum == null || minimum.compareTo(maximum) <= 0,
                "INVALID_FILTER", field + " minimum exceeds maximum");
    }

    private boolean atLeast(BigDecimal actual, BigDecimal minimum) {
        return minimum == null || actual != null && actual.compareTo(minimum) >= 0;
    }

    private boolean atMost(BigDecimal actual, BigDecimal maximum) {
        return maximum == null || actual != null && actual.compareTo(maximum) <= 0;
    }

    private boolean matches(List<String> accepted, String actual) {
        return accepted.isEmpty() || accepted.contains(actual);
    }

    private SelectionMode requireMode(SelectionMode mode, String field) {
        require(mode != null, "MISSING_SELECTION_MODE", field + ".mode is required");
        return mode;
    }

    private void validateKnownIds(List<Long> values, Set<Long> known, String field) {
        for (long value : values) {
            require(known.contains(value), "UNKNOWN_OR_INELIGIBLE_ID", field + " contains unknown or ineligible id: " + value);
        }
    }

    private List<Long> ids(List<Long> values, String field) {
        List<Long> result = list(values).stream().sorted().toList();
        Set<Long> unique = new HashSet<>();
        for (Long value : result) {
            require(value != null && value > 0 && unique.add(value),
                    "INVALID_ID_LIST", field + " contains duplicate or invalid id: " + value);
        }
        return result;
    }

    private <T> List<Long> ids(List<T> values, Function<T, Long> id) {
        return values.stream().map(id).sorted().toList();
    }

    private <T> Set<Long> idSet(List<T> values, Function<T, Long> id) {
        return values.stream().map(id).collect(Collectors.toSet());
    }

    private List<String> strings(List<String> values, String field) {
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : list(values)) {
            String normalized = text(value, field);
            require(unique.add(normalized), "INVALID_STRING_LIST", field + " contains duplicate: " + normalized);
            result.add(normalized);
        }
        return result.stream().sorted().toList();
    }

    private String text(String value, String field) {
        require(value != null && !value.isBlank(), "MISSING_TEXT", field + " must not be blank");
        return value.trim();
    }

    private String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new SandboxScenarioException(code, message);
        }
    }
}
