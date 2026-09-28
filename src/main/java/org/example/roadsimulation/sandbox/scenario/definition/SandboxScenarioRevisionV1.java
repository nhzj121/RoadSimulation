package org.example.roadsimulation.sandbox.scenario.definition;

import java.time.Instant;
import java.util.List;

/** Immutable published scenario revision. */
public record SandboxScenarioRevisionV1(
        String artifactVersion,
        String scenarioKey,
        int revision,
        SandboxScenarioDefinitionV1 definition,
        ResolvedSelection resolvedSelection,
        Fingerprints fingerprints,
        Instant publishedAtUtc
) {
    public static final String ARTIFACT_VERSION = "sandbox-scenario-revision/v1";

    public record ResolvedSelection(
            List<Long> vehicleIds,
            List<Long> poiIds,
            List<Long> goodsIds,
            List<Long> processingChainIds
    ) {
        public ResolvedSelection {
            vehicleIds = List.copyOf(vehicleIds);
            poiIds = List.copyOf(poiIds);
            goodsIds = List.copyOf(goodsIds);
            processingChainIds = List.copyOf(processingChainIds);
        }
    }

    public record Fingerprints(
            String canonicalization,
            String scenarioDefinitionSha256,
            String baseDataProjectionSha256,
            String effectiveScenarioDataSha256
    ) {}
}
