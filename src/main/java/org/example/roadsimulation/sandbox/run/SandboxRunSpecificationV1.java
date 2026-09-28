package org.example.roadsimulation.sandbox.run;

import java.time.LocalDateTime;
import java.util.List;

/** Mutable authoring contract for one deterministic sandbox run. */
public record SandboxRunSpecificationV1(
        String artifactVersion,
        String runSpecKey,
        String displayName,
        String description,
        ScenarioReference scenario,
        SimulationClock simulationClock,
        Demand demand,
        Dispatch dispatch,
        Environment environment,
        VehicleInitialization vehicleInitialization,
        DriverBehavior driverBehavior,
        RandomProtocol random
) {
    public static final String ARTIFACT_VERSION = "sandbox-run-specification/v1";

    /**
     * Source-compatible constructor for callers written before driver behavior became an
     * explicit v1 run fact. JSON input still uses the canonical constructor, so an omitted
     * driverBehavior field is rejected by the compiler instead of being silently defaulted.
     */
    public SandboxRunSpecificationV1(
            String artifactVersion,
            String runSpecKey,
            String displayName,
            String description,
            ScenarioReference scenario,
            SimulationClock simulationClock,
            Demand demand,
            Dispatch dispatch,
            Environment environment,
            VehicleInitialization vehicleInitialization,
            RandomProtocol random
    ) {
        this(artifactVersion, runSpecKey, displayName, description, scenario, simulationClock,
                demand, dispatch, environment, vehicleInitialization,
                new DriverBehavior(true, "MARKOV_V1", 0.02, 0.01, 0.30, 0.25,
                        "DERIVED_FROM_ROOT"),
                random);
    }

    public record ScenarioReference(
            String scenarioKey,
            int revision,
            String scenarioDefinitionSha256,
            String effectiveScenarioDataSha256
    ) {}

    public record SimulationClock(
            LocalDateTime startLocalDateTime,
            long tickDurationSeconds,
            int totalLoops
    ) {}

    public record Demand(
            String mode,
            int generationIntervalLoops,
            boolean startupPreGenerationEnabled
    ) {}

    public record Dispatch(
            String strategy,
            int dispatchIntervalLoops,
            String algorithmProfileId
    ) {}

    public record Environment(
            String scenarioId,
            String scenarioVersion,
            int modeledRoadCount,
            double normalNetworkSpeedKph,
            String applicationMode,
            String phaseSeedPolicy
    ) {}

    public record VehicleInitialization(
            String defaultPolicy,
            List<String> eligiblePoiTypes,
            String coordinateAuthority
    ) {
        public VehicleInitialization {
            eligiblePoiTypes = eligiblePoiTypes == null ? List.of() : List.copyOf(eligiblePoiTypes);
        }
    }

    public record DriverBehavior(
            boolean enabled,
            String transitionPolicy,
            double idleToRejecting,
            double idleToMaintenance,
            double rejectingToIdle,
            double maintenanceToIdle,
            String seedPolicy
    ) {}

    public record RandomProtocol(String protocolId, String rootSeed) {}
}
