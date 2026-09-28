package org.example.roadsimulation.sandbox.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads, authenticates and selects data from a formal sandbox baseline package. */
public final class SandboxBaselineLoader {

    private static final Set<String> EXPECTED_COUNT_KEYS = Set.of(
            "poi", "goods", "vehicle", "enrollment", "processing_chain",
            "processing_stage", "processing_stage_input", "processing_stage_edge",
            "driver", "driver_vehicle"
    );

    private final ObjectMapper objectMapper;
    private final LexicographicJsonSha256 jsonHash;
    private final EffectiveBaseDataCodec effectiveDataCodec;
    private final SandboxBaselineValidator validator;

    public SandboxBaselineLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonHash = new LexicographicJsonSha256(objectMapper);
        this.effectiveDataCodec = new EffectiveBaseDataCodec(objectMapper);
        this.validator = new SandboxBaselineValidator();
    }

    public LoadedSandboxBaseline load(Resource resource) {
        try (var input = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            SandboxBaselinePackageV1 baseline = objectMapper.treeToValue(
                    root, SandboxBaselinePackageV1.class);
            verifyEnvelope(baseline, root);
            validator.validateBaseline(baseline);
            return new LoadedSandboxBaseline(baseline, root);
        } catch (IOException exception) {
            throw new SandboxBaselineException("Cannot read sandbox baseline: " + resource, exception);
        }
    }

    public EffectiveBaseData selectAllEligible(LoadedSandboxBaseline loaded) {
        SandboxBaselinePackageV1 baseline = loaded.baseline();
        if (!SandboxBaselinePackageV1.ELIGIBILITY_POLICY_VERSION.equals(
                baseline.eligibilityPolicy().policyVersion())) {
            throw new SandboxBaselineException(
                    "Unsupported eligibility policy: " + baseline.eligibilityPolicy().policyVersion());
        }

        Set<Long> excludedPoiIds = exclusionIds(baseline.eligibilityPolicy().defaultExcludedPois());
        Set<Long> excludedGoodsIds = exclusionIds(baseline.eligibilityPolicy().defaultExcludedGoods());
        Set<Long> excludedVehicleIds = exclusionIds(baseline.eligibilityPolicy().defaultExcludedVehicles());
        Set<Long> selectedVehicleIds = baseline.data().vehicles().stream()
                .filter(value -> !excludedVehicleIds.contains(value.id()))
                .map(SandboxBaselinePackageV1.Vehicle::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<SandboxBaselinePackageV1.DriverVehicleBinding> selectedBindings =
                baseline.data().driverVehicleBindings().stream()
                        .filter(value -> selectedVehicleIds.contains(value.vehicleId()))
                        .toList();
        Set<Long> selectedDriverIds = selectedBindings.stream()
                .map(SandboxBaselinePackageV1.DriverVehicleBinding::driverId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Data selected = effectiveDataCodec.normalize(new Data(
                baseline.data().pois().stream().filter(value -> !excludedPoiIds.contains(value.id())).toList(),
                baseline.data().goods().stream().filter(value -> !excludedGoodsIds.contains(value.id())).toList(),
                baseline.data().vehicles().stream().filter(value -> !excludedVehicleIds.contains(value.id())).toList(),
                baseline.data().processingChains(),
                baseline.data().initialInventories().stream()
                        .filter(value -> !excludedPoiIds.contains(value.poiId()))
                        .filter(value -> !excludedGoodsIds.contains(value.goodsId()))
                        .toList(),
                baseline.data().drivers().stream()
                        .filter(value -> selectedDriverIds.contains(value.id()))
                        .toList(),
                selectedBindings
        ));
        validator.validateEffectiveData(selected);

        return new EffectiveBaseData(
                EffectiveBaseData.ALL_ELIGIBLE_V1,
                baseline.eligibilityPolicy().policyVersion(),
                selected,
                effectiveDataCodec.hash(
                        EffectiveBaseData.ALL_ELIGIBLE_V1,
                        baseline.eligibilityPolicy().policyVersion(),
                        selected)
        );
    }

    private void verifyEnvelope(SandboxBaselinePackageV1 baseline, JsonNode root) {
        require(SandboxBaselinePackageV1.ARTIFACT_VERSION.equals(baseline.artifactVersion()),
                "Unsupported artifactVersion: " + baseline.artifactVersion());
        require(SandboxBaselinePackageV1.CONTRACT_VERSION.equals(baseline.contractVersion()),
                "Unsupported contractVersion: " + baseline.contractVersion());
        require("FORMAL_BASELINE".equals(baseline.baselineStatus()),
                "Baseline status must be FORMAL_BASELINE");
        require(baseline.baselineReady(), "Baseline is not marked ready");
        require(!baseline.sandboxRunReady(), "Phase-one baseline must not be run-ready");
        require("lexicographic-json-v1".equals(baseline.fingerprints().canonicalization()),
                "Unsupported canonicalization: " + baseline.fingerprints().canonicalization());
        require(root.path("data").isObject(), "Baseline data object is missing");

        String restoration = jsonHash.hash(root.path("data"));
        requireHash(restoration, baseline.fingerprints().restorationPayloadSha256(),
                "restorationPayloadSha256");
        String simulationFacts = jsonHash.hash(simulationFactsProjection(root));
        requireHash(simulationFacts, baseline.fingerprints().simulationFactsSha256(),
                "simulationFactsSha256");

        require(baseline.source().tableRowCounts().keySet().containsAll(EXPECTED_COUNT_KEYS),
                "Source row counts are incomplete");
    }

    /** Exact projection used by simulation-facts/v1; changing it requires a new contract version. */
    JsonNode simulationFactsProjection(JsonNode root) {
        ObjectNode projection = objectMapper.createObjectNode();
        projection.set("eligibilityPolicy", root.path("eligibilityPolicy").deepCopy());
        ObjectNode data = projection.putObject("data");
        data.set("pois", projectArray(root.path("data").path("pois"),
                List.of("id", "longitude", "latitude", "poiType")));
        data.set("goods", projectArray(root.path("data").path("goods"), List.of(
                "id", "sku", "category", "weightPerUnitTonnes", "volumePerUnitCubicMeters",
                "requiresTemperatureControl", "hazmatLevel", "shelfLifeDays", "vehicleFit")));
        data.set("vehicles", projectArray(root.path("data").path("vehicles"), List.of(
                "id", "maxLoadCapacityTonnes", "cargoVolumeCubicMeters", "modelType",
                "vehicleType", "hasTemperatureControl", "hazmatQualification",
                "specialVehicleType", "lengthMeters", "widthMeters", "heightMeters",
                "suitableGoods")));
        data.set("drivers", projectArray(root.path("data").path("drivers"), List.of(
                "id", "preferredCargoType", "preferredMaxDistanceKm", "preferredMaxWeightTons")));
        data.set("driverVehicleBindings",
                root.path("data").path("driverVehicleBindings").deepCopy());

        ArrayNode chains = objectMapper.createArrayNode();
        for (JsonNode chain : root.path("data").path("processingChains")) {
            ObjectNode selectedChain = copyFields(chain, List.of("id", "chainCode", "status"));
            ArrayNode stages = objectMapper.createArrayNode();
            for (JsonNode stage : chain.path("stages")) {
                ObjectNode selectedStage = copyFields(stage, List.of(
                        "id", "stageOrder", "stageKey", "processingPoiId", "requiredPoiType",
                        "legacyInputGoodsId", "legacyInputGoodsSku", "legacyInputWeightRatio",
                        "outputGoodsId", "outputGoodsSku", "outputWeightRatio",
                        "processingTimeMinutes", "minBatchSize", "maxCapacityPerCycle"));
                selectedStage.set("inputs", stage.path("inputs").deepCopy());
                stages.add(selectedStage);
            }
            selectedChain.set("stages", stages);
            selectedChain.set("edges", chain.path("edges").deepCopy());
            chains.add(selectedChain);
        }
        data.set("processingChains", chains);
        data.set("initialInventories", root.path("data").path("initialInventories").deepCopy());
        return projection;
    }

    private ArrayNode projectArray(JsonNode source, List<String> fields) {
        require(source.isArray(), "Expected JSON array for simulation facts projection");
        ArrayNode result = objectMapper.createArrayNode();
        source.forEach(value -> result.add(copyFields(value, fields)));
        return result;
    }

    private ObjectNode copyFields(JsonNode source, List<String> fields) {
        ObjectNode result = objectMapper.createObjectNode();
        for (String field : fields) {
            require(source.has(field), "Missing simulation fact field: " + field);
            result.set(field, source.get(field).deepCopy());
        }
        return result;
    }

    private Set<Long> exclusionIds(List<SandboxBaselinePackageV1.Exclusion> exclusions) {
        Set<Long> result = new HashSet<>();
        for (SandboxBaselinePackageV1.Exclusion exclusion : exclusions) {
            require(exclusion.id() > 0, "Excluded id must be positive");
            require(exclusion.reason() != null && !exclusion.reason().isBlank(),
                    "Exclusion reason must not be blank");
            require(result.add(exclusion.id()), "Duplicate exclusion id: " + exclusion.id());
        }
        return Set.copyOf(result);
    }

    private void requireHash(String actual, String expected, String name) {
        require(expected != null && expected.matches("(?i)[0-9a-f]{64}"),
                name + " is not a SHA-256 value");
        require(actual.equals(expected.toLowerCase(Locale.ROOT)),
                name + " mismatch: expected=" + expected + ", actual=" + actual);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new SandboxBaselineException(message);
        }
    }
}
