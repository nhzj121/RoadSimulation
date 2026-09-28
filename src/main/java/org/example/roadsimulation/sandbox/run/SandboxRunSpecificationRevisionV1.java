package org.example.roadsimulation.sandbox.run;

import java.time.Instant;
import java.util.List;

/** Immutable published deterministic run specification. */
public record SandboxRunSpecificationRevisionV1(
        String artifactVersion,
        String runSpecKey,
        int revision,
        SandboxRunSpecificationV1 specification,
        SandboxAlgorithmProfile algorithmProfile,
        List<SandboxVehicleInitialState> vehicleInitialStates,
        Fingerprints fingerprints,
        Instant publishedAtUtc
) {
    public static final String ARTIFACT_VERSION = "sandbox-run-specification-revision/v1";

    public SandboxRunSpecificationRevisionV1 {
        vehicleInitialStates = List.copyOf(vehicleInitialStates);
    }

    public record Fingerprints(
            String canonicalization,
            String runSpecificationSha256,
            String resolvedVehicleInitialStateSha256,
            String preparedRunFactsSha256
    ) {}
}
