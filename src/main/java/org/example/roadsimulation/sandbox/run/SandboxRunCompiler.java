package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.scenario.definition.CompiledSandboxScenario;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates and compiles a run specification against one published scenario revision. */
public final class SandboxRunCompiler {
    private static final LocalDateTime SIMULATION_START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final long TICK_DURATION_SECONDS = 1800L;
    private static final int PRODUCTION_INTERVAL_LOOPS = 6;
    private static final int DISPATCH_INTERVAL_LOOPS = 3;
    private static final Set<String> INITIAL_POI_TYPES = Set.of("WAREHOUSE", "DISTRIBUTION_CENTER");

    private final ObjectMapper objectMapper;
    private final SandboxAlgorithmProfiles profiles = new SandboxAlgorithmProfiles();
    private final SandboxRunCodec codec;
    private final SandboxVehicleInitialStateGenerator initialStateGenerator;

    public SandboxRunCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.codec = new SandboxRunCodec(objectMapper);
        this.initialStateGenerator = new SandboxVehicleInitialStateGenerator(objectMapper);
    }

    public SandboxRunSpecificationV1 read(Resource resource) {
        try (var input = resource.getInputStream()) {
            return objectMapper.readValue(input, SandboxRunSpecificationV1.class);
        } catch (IOException exception) {
            throw new SandboxRunException("RUN_SPEC_READ_FAILED", "Cannot read run specification", exception);
        }
    }

    public CompiledSandboxRunSpecification compile(
            SandboxRunSpecificationV1 source,
            SandboxScenarioRevisionV1 scenarioRevision,
            CompiledSandboxScenario scenario
    ) {
        SandboxRunSpecificationV1 normalized = normalizeAndValidate(source);
        validateScenarioReference(normalized.scenario(), scenarioRevision, scenario);
        require(scenario.effectiveData().data().processingChains().stream()
                        .anyMatch(chain -> "ACTIVE".equals(chain.status())),
                "PRODUCTION_ACTIVE_CHAIN_REQUIRED", "PRODUCTION requires at least one selected ACTIVE processing chain");
        SandboxAlgorithmProfile profile = profiles.resolve(
                normalized.dispatch().strategy(), normalized.dispatch().algorithmProfileId());
        List<SandboxVehicleInitialState> states = initialStateGenerator.generate(
                scenario.effectiveData(), normalized.random().rootSeed());
        int eligiblePoiCount = initialStateGenerator.eligibleCandidateCount(scenario.effectiveData());
        String specHash = codec.runSpecificationHash(normalized, profile);
        String stateHash = codec.vehicleInitialStateHash(states);
        String preparedHash = codec.preparedRunFactsHash(
                scenario.effectiveData().effectiveScenarioDataSha256(), specHash, stateHash);
        return new CompiledSandboxRunSpecification(
                normalized, profile, states, eligiblePoiCount, specHash, stateHash, preparedHash,
                "sandbox-" + specHash.substring(0, 16));
    }

    private SandboxRunSpecificationV1 normalizeAndValidate(SandboxRunSpecificationV1 source) {
        require(source != null, "MISSING_RUN_SPEC", "Run specification is required");
        require(SandboxRunSpecificationV1.ARTIFACT_VERSION.equals(source.artifactVersion()),
                "UNSUPPORTED_RUN_SPEC_VERSION", "Unsupported run specification artifactVersion");
        String key = text(source.runSpecKey(), "runSpecKey");
        require(key.matches("[a-z0-9][a-z0-9-]{0,127}"),
                "INVALID_RUN_SPEC_KEY", "runSpecKey must use lower-case letters, digits and hyphens");
        require(source.scenario() != null, "MISSING_SCENARIO_REFERENCE", "scenario is required");
        require(source.simulationClock() != null, "MISSING_SIMULATION_CLOCK", "simulationClock is required");
        require(source.demand() != null, "MISSING_DEMAND", "demand is required");
        require(source.dispatch() != null, "MISSING_DISPATCH", "dispatch is required");
        require(source.environment() != null, "MISSING_ENVIRONMENT", "environment is required");
        require(source.vehicleInitialization() != null,
                "MISSING_VEHICLE_INITIALIZATION", "vehicleInitialization is required");
        require(source.driverBehavior() != null,
                "MISSING_DRIVER_BEHAVIOR", "driverBehavior is required");
        require(source.random() != null, "MISSING_RANDOM_PROTOCOL", "random is required");

        SandboxRunSpecificationV1.SimulationClock clock = source.simulationClock();
        require(SIMULATION_START.equals(clock.startLocalDateTime()),
                "UNSUPPORTED_SIMULATION_START", "Run specification v1 requires 2026-01-01T00:00:00");
        require(clock.tickDurationSeconds() == TICK_DURATION_SECONDS,
                "UNSUPPORTED_TICK_DURATION", "Run specification v1 requires 1800-second ticks");
        require(clock.totalLoops() > 0,
                "INVALID_TOTAL_LOOPS", "totalLoops must be positive");

        String demandMode = upper(source.demand().mode(), "demand.mode");
        require("PRODUCTION".equals(demandMode),
                "UNSUPPORTED_DEMAND_MODE", "Sandbox run specification v1 supports only PRODUCTION");
        require(source.demand().generationIntervalLoops() == PRODUCTION_INTERVAL_LOOPS,
                "UNSUPPORTED_DEMAND_INTERVAL", "PRODUCTION generation interval must be 6 loops");
        require(!source.demand().startupPreGenerationEnabled(),
                "STARTUP_PREGENERATION_FORBIDDEN", "Startup pre-generation must be disabled");

        String strategy = upper(source.dispatch().strategy(), "dispatch.strategy");
        require(Set.of("ORIGINAL", "HEURISTIC").contains(strategy),
                "UNSUPPORTED_DISPATCH_STRATEGY", "Dispatch strategy must be ORIGINAL or HEURISTIC");
        require(source.dispatch().dispatchIntervalLoops() == DISPATCH_INTERVAL_LOOPS,
                "UNSUPPORTED_DISPATCH_INTERVAL", "Dispatch interval must be 3 loops");

        SandboxRunSpecificationV1.Environment environment = source.environment();
        require("1".equals(text(environment.scenarioVersion(), "environment.scenarioVersion")),
                "UNSUPPORTED_ENVIRONMENT_VERSION", "Environment scenarioVersion must be 1");
        require(environment.modeledRoadCount() > 0,
                "INVALID_ENVIRONMENT", "modeledRoadCount must be positive");
        require(Double.isFinite(environment.normalNetworkSpeedKph())
                        && environment.normalNetworkSpeedKph() > 0.0,
                "INVALID_ENVIRONMENT", "normalNetworkSpeedKph must be positive and finite");
        require("PROGRESS_AFFECTING".equals(upper(environment.applicationMode(), "environment.applicationMode")),
                "UNSUPPORTED_ENVIRONMENT_MODE", "Environment must affect progress");
        require("DERIVED_FROM_ROOT".equals(upper(environment.phaseSeedPolicy(), "environment.phaseSeedPolicy")),
                "UNSUPPORTED_ENVIRONMENT_SEED_POLICY", "Environment phase seed must derive from root seed");

        SandboxRunSpecificationV1.VehicleInitialization initialization = source.vehicleInitialization();
        require("RANDOM_ELIGIBLE_POI".equals(upper(initialization.defaultPolicy(),
                        "vehicleInitialization.defaultPolicy")),
                "UNSUPPORTED_VEHICLE_INITIALIZATION", "Default vehicle policy must be RANDOM_ELIGIBLE_POI");
        List<String> poiTypes = initialization.eligiblePoiTypes().stream()
                .map(value -> upper(value, "vehicleInitialization.eligiblePoiTypes"))
                .distinct().sorted().toList();
        require(Set.copyOf(poiTypes).equals(INITIAL_POI_TYPES) && poiTypes.size() == INITIAL_POI_TYPES.size(),
                "UNSUPPORTED_INITIAL_POI_TYPES", "Initial POI types must be WAREHOUSE and DISTRIBUTION_CENTER");
        require("CURRENT_POI".equals(upper(initialization.coordinateAuthority(),
                        "vehicleInitialization.coordinateAuthority")),
                "UNSUPPORTED_COORDINATE_AUTHORITY", "Initial coordinate authority must be CURRENT_POI");

        SandboxRunSpecificationV1.DriverBehavior driverBehavior = source.driverBehavior();
        require("MARKOV_V1".equals(upper(driverBehavior.transitionPolicy(),
                        "driverBehavior.transitionPolicy")),
                "UNSUPPORTED_DRIVER_BEHAVIOR_POLICY", "Driver behavior transitionPolicy must be MARKOV_V1");
        require("DERIVED_FROM_ROOT".equals(upper(driverBehavior.seedPolicy(),
                        "driverBehavior.seedPolicy")),
                "UNSUPPORTED_DRIVER_BEHAVIOR_SEED_POLICY", "Driver behavior seed must derive from root seed");
        require(probability(driverBehavior.idleToRejecting())
                        && probability(driverBehavior.idleToMaintenance())
                        && probability(driverBehavior.rejectingToIdle())
                        && probability(driverBehavior.maintenanceToIdle()),
                "INVALID_DRIVER_BEHAVIOR_PROBABILITY", "Driver behavior probabilities must be in [0,1]");
        require(driverBehavior.idleToRejecting() + driverBehavior.idleToMaintenance() <= 1.0,
                "INVALID_DRIVER_BEHAVIOR_PROBABILITY",
                "idleToRejecting + idleToMaintenance must not exceed 1");

        require(SandboxRandomProtocol.PROTOCOL_ID.equals(source.random().protocolId()),
                "UNSUPPORTED_RANDOM_PROTOCOL", "Unsupported random protocol");
        try {
            SandboxRandomProtocol.validateRootSeed(source.random().rootSeed());
        } catch (IllegalArgumentException exception) {
            throw new SandboxRunException("INVALID_ROOT_SEED", exception.getMessage(), exception);
        }

        return new SandboxRunSpecificationV1(
                SandboxRunSpecificationV1.ARTIFACT_VERSION,
                key,
                nullableText(source.displayName()),
                nullableText(source.description()),
                normalizeScenarioReference(source.scenario()),
                clock,
                new SandboxRunSpecificationV1.Demand(demandMode, PRODUCTION_INTERVAL_LOOPS, false),
                new SandboxRunSpecificationV1.Dispatch(
                        strategy, DISPATCH_INTERVAL_LOOPS,
                        text(source.dispatch().algorithmProfileId(), "dispatch.algorithmProfileId")),
                new SandboxRunSpecificationV1.Environment(
                        text(environment.scenarioId(), "environment.scenarioId"),
                        "1", environment.modeledRoadCount(), environment.normalNetworkSpeedKph(),
                        "PROGRESS_AFFECTING", "DERIVED_FROM_ROOT"),
                new SandboxRunSpecificationV1.VehicleInitialization(
                        "RANDOM_ELIGIBLE_POI", poiTypes, "CURRENT_POI"),
                new SandboxRunSpecificationV1.DriverBehavior(
                        driverBehavior.enabled(), "MARKOV_V1",
                        driverBehavior.idleToRejecting(), driverBehavior.idleToMaintenance(),
                        driverBehavior.rejectingToIdle(), driverBehavior.maintenanceToIdle(),
                        "DERIVED_FROM_ROOT"),
                new SandboxRunSpecificationV1.RandomProtocol(
                        SandboxRandomProtocol.PROTOCOL_ID, source.random().rootSeed()));
    }

    private boolean probability(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private SandboxRunSpecificationV1.ScenarioReference normalizeScenarioReference(
            SandboxRunSpecificationV1.ScenarioReference source
    ) {
        require(source.revision() > 0, "INVALID_SCENARIO_REFERENCE", "scenario.revision must be positive");
        return new SandboxRunSpecificationV1.ScenarioReference(
                text(source.scenarioKey(), "scenario.scenarioKey"),
                source.revision(),
                sha256(source.scenarioDefinitionSha256(), "scenario.scenarioDefinitionSha256"),
                sha256(source.effectiveScenarioDataSha256(), "scenario.effectiveScenarioDataSha256"));
    }

    private void validateScenarioReference(
            SandboxRunSpecificationV1.ScenarioReference reference,
            SandboxScenarioRevisionV1 revision,
            CompiledSandboxScenario compiled
    ) {
        require(revision != null && compiled != null,
                "MISSING_PUBLISHED_SCENARIO", "Published scenario revision is required");
        require(reference.scenarioKey().equals(revision.scenarioKey())
                        && reference.revision() == revision.revision()
                        && reference.scenarioDefinitionSha256().equals(
                        revision.fingerprints().scenarioDefinitionSha256())
                        && reference.effectiveScenarioDataSha256().equals(
                        revision.fingerprints().effectiveScenarioDataSha256())
                        && reference.scenarioDefinitionSha256().equals(compiled.scenarioDefinitionSha256())
                        && reference.effectiveScenarioDataSha256().equals(
                        compiled.effectiveData().effectiveScenarioDataSha256()),
                "SCENARIO_REFERENCE_MISMATCH", "Run specification does not match the published scenario revision");
    }

    private String sha256(String value, String field) {
        String normalized = text(value, field).toLowerCase(Locale.ROOT);
        require(normalized.matches("[0-9a-f]{64}"), "INVALID_SHA256", field + " must be 64 hexadecimal characters");
        return normalized;
    }

    private String upper(String value, String field) {
        return text(value, field).toUpperCase(Locale.ROOT);
    }

    private String text(String value, String field) {
        require(value != null && !value.isBlank(), "MISSING_TEXT", field + " must not be blank");
        return value.trim();
    }

    private String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new SandboxRunException(code, message);
        }
    }
}
