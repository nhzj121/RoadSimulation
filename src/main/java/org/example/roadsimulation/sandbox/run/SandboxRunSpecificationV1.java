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
        RandomProtocol random
) {
    public static final String ARTIFACT_VERSION = "sandbox-run-specification/v1";

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

    public record RandomProtocol(String protocolId, String rootSeed) {}
}
