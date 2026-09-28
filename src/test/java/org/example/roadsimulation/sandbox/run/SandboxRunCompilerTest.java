package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.scenario.definition.CompiledSandboxScenario;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCodec;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxRunCompilerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SandboxScenarioCompiler scenarioCompiler = new SandboxScenarioCompiler(objectMapper);
    private final SandboxRunCompiler runCompiler = new SandboxRunCompiler(objectMapper);
    private LoadedSandboxBaseline baseline;
    private SandboxScenarioDefinitionV1 scenarioDefinition;
    private CompiledSandboxScenario scenario;
    private SandboxScenarioRevisionV1 scenarioRevision;

    @BeforeEach
    void setUp() {
        baseline = new SandboxBaselineLoader(objectMapper).load(
                new ClassPathResource("sandbox/baseline/baseline-v1.json"));
        scenarioDefinition = scenarioCompiler.readDefinition(
                new ClassPathResource("sandbox/scenarios/default-all-eligible-v1.json"));
        scenario = scenarioCompiler.compile(baseline, scenarioDefinition);
        scenarioRevision = published(scenario, 1);
    }

    @Test
    void compilesProductionRunAndResolvesAllVehicleInitialStates() {
        CompiledSandboxRunSpecification first = runCompiler.compile(
                specification("ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "20260927"),
                scenarioRevision, scenario);
        CompiledSandboxRunSpecification repeated = runCompiler.compile(
                specification("ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "20260927"),
                scenarioRevision, scenario);

        assertEquals(85, first.vehicleInitialStates().size());
        assertEquals(1016, first.eligibleVehicleInitialPoiCount());
        assertFalse(first.vehicleInitialStates().stream().anyMatch(state -> state.poiId() == 3466L));
        assertTrue(first.vehicleInitialStates().stream().allMatch(
                state -> SandboxVehicleInitialState.RANDOM_DERIVED_POI.equals(state.initializationPolicy())));
        assertEquals(first.vehicleInitialStates(), repeated.vehicleInitialStates());
        assertEquals(first.runSpecificationSha256(), repeated.runSpecificationSha256());
        assertEquals("sandbox-" + first.runSpecificationSha256().substring(0, 16),
                first.deterministicSimulationRunId());
    }

    @Test
    void descriptiveMetadataDoesNotChangeHashButSeedAndProfileDo() {
        SandboxRunSpecificationV1 original = specification(
                "ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "10");
        CompiledSandboxRunSpecification first = runCompiler.compile(original, scenarioRevision, scenario);
        SandboxRunSpecificationV1 renamed = new SandboxRunSpecificationV1(
                original.artifactVersion(), original.runSpecKey(), "renamed", "changed",
                original.scenario(), original.simulationClock(), original.demand(), original.dispatch(),
                original.environment(), original.vehicleInitialization(), original.random());
        assertEquals(first.runSpecificationSha256(),
                runCompiler.compile(renamed, scenarioRevision, scenario).runSpecificationSha256());

        assertNotEquals(first.runSpecificationSha256(), runCompiler.compile(
                specification("ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "11"),
                scenarioRevision, scenario).runSpecificationSha256());
        assertNotEquals(first.resolvedVehicleInitialStateSha256(), runCompiler.compile(
                specification("ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "11"),
                scenarioRevision, scenario).resolvedVehicleInitialStateSha256());
        assertNotEquals(first.runSpecificationSha256(), runCompiler.compile(
                specification("HEURISTIC", SandboxAlgorithmProfiles.HEURISTIC_V1, "10"),
                scenarioRevision, scenario).runSpecificationSha256());
    }

    @Test
    void driverBehaviorIsHashedAndInvalidProbabilitiesAreRejected() {
        SandboxRunSpecificationV1 original = specification(
                "ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "10");
        String enabledHash = runCompiler.compile(
                original, scenarioRevision, scenario).runSpecificationSha256();

        SandboxRunSpecificationV1 disabled = withDriverBehavior(original,
                new SandboxRunSpecificationV1.DriverBehavior(
                        false, "MARKOV_V1", 0.02, 0.01, 0.30, 0.25,
                        "DERIVED_FROM_ROOT"));
        assertNotEquals(enabledHash, runCompiler.compile(
                disabled, scenarioRevision, scenario).runSpecificationSha256());

        SandboxRunSpecificationV1 invalid = withDriverBehavior(original,
                new SandboxRunSpecificationV1.DriverBehavior(
                        true, "MARKOV_V1", 0.75, 0.50, 0.30, 0.25,
                        "DERIVED_FROM_ROOT"));
        SandboxRunException failure = assertThrows(SandboxRunException.class,
                () -> runCompiler.compile(invalid, scenarioRevision, scenario));
        assertEquals("INVALID_DRIVER_BEHAVIOR_PROBABILITY", failure.errorCode());
    }

    @Test
    void hashesDoNotDependOnCallerDateSerializationPreference() {
        SandboxRunSpecificationV1 specification = specification(
                "ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "20260927");
        CompiledSandboxRunSpecification timestampMapperResult = new SandboxRunCompiler(
                new ObjectMapper().findAndRegisterModules()
                        .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .compile(specification, scenarioRevision, scenario);
        CompiledSandboxRunSpecification isoStringMapperResult = new SandboxRunCompiler(
                new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .compile(specification, scenarioRevision, scenario);

        assertEquals(timestampMapperResult.runSpecificationSha256(),
                isoStringMapperResult.runSpecificationSha256());
        assertEquals(timestampMapperResult.resolvedVehicleInitialStateSha256(),
                isoStringMapperResult.resolvedVehicleInitialStateSha256());
        assertEquals(timestampMapperResult.preparedRunFactsSha256(),
                isoStringMapperResult.preparedRunFactsSha256());
    }

    @Test
    void rejectsLegacyMixedStartupPreGenerationAndWrongProfile() {
        SandboxRunSpecificationV1 original = specification(
                "ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "10");
        for (String mode : List.of("LEGACY", "MIXED")) {
            SandboxRunSpecificationV1 invalid = withDemand(original,
                    new SandboxRunSpecificationV1.Demand(mode, 6, false));
            assertEquals("UNSUPPORTED_DEMAND_MODE", assertThrows(SandboxRunException.class,
                    () -> runCompiler.compile(invalid, scenarioRevision, scenario)).errorCode());
        }

        SandboxRunSpecificationV1 preGeneration = withDemand(original,
                new SandboxRunSpecificationV1.Demand("PRODUCTION", 6, true));
        assertEquals("STARTUP_PREGENERATION_FORBIDDEN", assertThrows(SandboxRunException.class,
                () -> runCompiler.compile(preGeneration, scenarioRevision, scenario)).errorCode());

        SandboxRunSpecificationV1 wrongProfile = specification(
                "ORIGINAL", SandboxAlgorithmProfiles.HEURISTIC_V1, "10");
        assertEquals("ALGORITHM_PROFILE_STRATEGY_MISMATCH", assertThrows(SandboxRunException.class,
                () -> runCompiler.compile(wrongProfile, scenarioRevision, scenario)).errorCode());
    }

    @Test
    void fixedVehicleWinsWithoutConsumingRandomDomain() {
        long vehicleId = scenario.effectiveData().data().vehicles().get(0).id();
        long poiId = scenario.effectiveData().data().pois().stream()
                .filter(poi -> "WAREHOUSE".equals(poi.poiType())).findFirst().orElseThrow().id();
        SandboxScenarioDefinitionV1.Overrides overrides = new SandboxScenarioDefinitionV1.Overrides(
                List.of(), List.of(), List.of(),
                List.of(new SandboxScenarioDefinitionV1.VehicleInitialPoi(
                        vehicleId, poiId, SandboxScenarioDefinitionV1.VehicleInitialPoi.FIXED_POI)));
        SandboxScenarioDefinitionV1 fixedDefinition = new SandboxScenarioDefinitionV1(
                scenarioDefinition.artifactVersion(), scenarioDefinition.scenarioKey(),
                scenarioDefinition.displayName(), scenarioDefinition.description(), scenarioDefinition.baseline(),
                scenarioDefinition.selection(), overrides);
        CompiledSandboxScenario fixedScenario = scenarioCompiler.compile(baseline, fixedDefinition);
        SandboxScenarioRevisionV1 fixedRevision = published(fixedScenario, 2);

        SandboxVehicleInitialState fixed = runCompiler.compile(
                        specificationFor(fixedRevision, "ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "1"),
                        fixedRevision, fixedScenario)
                .vehicleInitialStates().stream().filter(state -> state.vehicleId() == vehicleId)
                .findFirst().orElseThrow();
        SandboxVehicleInitialState fixedWithAnotherSeed = runCompiler.compile(
                        specificationFor(fixedRevision, "ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1, "2"),
                        fixedRevision, fixedScenario)
                .vehicleInitialStates().stream().filter(state -> state.vehicleId() == vehicleId)
                .findFirst().orElseThrow();
        assertEquals(SandboxVehicleInitialState.FIXED_POI, fixed.initializationPolicy());
        assertEquals(poiId, fixed.poiId());
        assertEquals(fixed, fixedWithAnotherSeed);
        assertNull(fixed.decisionDomain());
        assertNull(fixed.decisionKey());
        assertNull(fixed.derivedSeedHex());
    }

    @Test
    void candidateOrderingDoesNotDependOnIncomingPoiOrder() {
        var original = scenario.effectiveData();
        var pois = new ArrayList<>(original.data().pois());
        var vehicles = new ArrayList<>(original.data().vehicles());
        Collections.reverse(pois);
        Collections.reverse(vehicles);
        var reversedData = new org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data(
                pois, original.data().goods(), vehicles,
                original.data().processingChains(), original.data().initialInventories());
        var reversed = new org.example.roadsimulation.sandbox.scenario.definition.EffectiveScenarioData(
                reversedData, original.vehicleInitializations(),
                original.baseDataProjectionSha256(), original.effectiveScenarioDataSha256());
        SandboxVehicleInitialStateGenerator generator = new SandboxVehicleInitialStateGenerator(objectMapper);
        var first = generator.generate(original, "999");
        var repeated = generator.generate(reversed, "999");

        assertEquals(first, repeated);
        assertFalse(first.isEmpty());
        assertTrue(first.stream()
                .map(SandboxVehicleInitialState::derivedSeedHex)
                .allMatch(value -> value != null && value.matches("[0-9a-f]{16}")));
    }

    @Test
    void rejectsFixedPoiWithoutCompleteCoordinates() {
        var original = scenario.effectiveData();
        var vehicle = original.data().vehicles().get(0);
        var fixedPoi = original.data().pois().stream()
                .filter(poi -> "WAREHOUSE".equals(poi.poiType()))
                .findFirst().orElseThrow();
        var pois = original.data().pois().stream()
                .map(poi -> poi.id() == fixedPoi.id()
                        ? new org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Poi(
                                poi.id(), poi.name(), null, poi.latitude(), poi.poiType())
                        : poi)
                .toList();
        var data = new org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data(
                pois, original.data().goods(), original.data().vehicles(),
                original.data().processingChains(), original.data().initialInventories());
        var invalid = new org.example.roadsimulation.sandbox.scenario.definition.EffectiveScenarioData(
                data,
                List.of(new org.example.roadsimulation.sandbox.scenario.definition.EffectiveScenarioData.VehicleInitialization(
                        vehicle.id(), SandboxVehicleInitialState.FIXED_POI, fixedPoi.id())),
                original.baseDataProjectionSha256(), original.effectiveScenarioDataSha256());

        SandboxRunException failure = assertThrows(SandboxRunException.class,
                () -> new SandboxVehicleInitialStateGenerator(objectMapper).generate(invalid, "1"));
        assertEquals("INVALID_FIXED_VEHICLE_POI", failure.errorCode());
    }

    @Test
    void rejectsRandomInitializationWhenScenarioHasNoEligiblePoi() {
        var original = scenario.effectiveData();
        var factory = original.data().pois().stream()
                .filter(poi -> !List.of("WAREHOUSE", "DISTRIBUTION_CENTER").contains(poi.poiType()))
                .findFirst().orElseThrow();
        var data = new org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data(
                List.of(factory), List.of(), List.of(original.data().vehicles().get(0)),
                List.of(), List.of());
        var noCandidate = new org.example.roadsimulation.sandbox.scenario.definition.EffectiveScenarioData(
                data, List.of(), "base", "scenario");

        SandboxRunException failure = assertThrows(SandboxRunException.class,
                () -> new SandboxVehicleInitialStateGenerator(objectMapper).generate(noCandidate, "1"));
        assertEquals("NO_VEHICLE_INITIALIZATION_POI", failure.errorCode());
    }

    private SandboxRunSpecificationV1 specification(String strategy, String profile, String seed) {
        return specificationFor(scenarioRevision, strategy, profile, seed);
    }

    private SandboxRunSpecificationV1 specificationFor(
            SandboxScenarioRevisionV1 revision,
            String strategy,
            String profile,
            String seed
    ) {
        return new SandboxRunSpecificationV1(
                SandboxRunSpecificationV1.ARTIFACT_VERSION,
                "production-test",
                "Production deterministic run",
                null,
                new SandboxRunSpecificationV1.ScenarioReference(
                        revision.scenarioKey(), revision.revision(),
                        revision.fingerprints().scenarioDefinitionSha256(),
                        revision.fingerprints().effectiveScenarioDataSha256()),
                new SandboxRunSpecificationV1.SimulationClock(
                        LocalDateTime.of(2026, 1, 1, 0, 0), 1800, 48),
                new SandboxRunSpecificationV1.Demand("PRODUCTION", 6, false),
                new SandboxRunSpecificationV1.Dispatch(strategy, 3, profile),
                new SandboxRunSpecificationV1.Environment(
                        "deterministic-network-cycle", "1", 100, 60.0,
                        "PROGRESS_AFFECTING", "DERIVED_FROM_ROOT"),
                new SandboxRunSpecificationV1.VehicleInitialization(
                        "RANDOM_ELIGIBLE_POI",
                        List.of("WAREHOUSE", "DISTRIBUTION_CENTER"),
                        "CURRENT_POI"),
                new SandboxRunSpecificationV1.RandomProtocol(
                        SandboxRandomProtocol.PROTOCOL_ID, seed));
    }

    private SandboxRunSpecificationV1 withDemand(
            SandboxRunSpecificationV1 source,
            SandboxRunSpecificationV1.Demand demand
    ) {
        return new SandboxRunSpecificationV1(
                source.artifactVersion(), source.runSpecKey(), source.displayName(), source.description(),
                source.scenario(), source.simulationClock(), demand, source.dispatch(), source.environment(),
                source.vehicleInitialization(), source.random());
    }

    private SandboxRunSpecificationV1 withDriverBehavior(
            SandboxRunSpecificationV1 source,
            SandboxRunSpecificationV1.DriverBehavior driverBehavior
    ) {
        return new SandboxRunSpecificationV1(
                source.artifactVersion(), source.runSpecKey(), source.displayName(), source.description(),
                source.scenario(), source.simulationClock(), source.demand(), source.dispatch(),
                source.environment(), source.vehicleInitialization(), driverBehavior, source.random());
    }

    private SandboxScenarioRevisionV1 published(CompiledSandboxScenario compiled, int revision) {
        return new SandboxScenarioRevisionV1(
                SandboxScenarioRevisionV1.ARTIFACT_VERSION,
                compiled.normalizedDefinition().scenarioKey(),
                revision,
                compiled.normalizedDefinition(),
                compiled.resolvedSelection(),
                new SandboxScenarioRevisionV1.Fingerprints(
                        SandboxScenarioCodec.CANONICALIZATION,
                        compiled.scenarioDefinitionSha256(),
                        compiled.effectiveData().baseDataProjectionSha256(),
                        compiled.effectiveData().effectiveScenarioDataSha256()),
                Instant.parse("2026-09-27T00:00:00Z"));
    }
}
