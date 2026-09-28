package org.example.roadsimulation.sandbox.run;

import java.util.List;

public record CompiledSandboxRunSpecification(
        SandboxRunSpecificationV1 normalizedSpecification,
        SandboxAlgorithmProfile algorithmProfile,
        List<SandboxVehicleInitialState> vehicleInitialStates,
        int eligibleVehicleInitialPoiCount,
        String runSpecificationSha256,
        String resolvedVehicleInitialStateSha256,
        String preparedRunFactsSha256,
        String deterministicSimulationRunId
) {
    public CompiledSandboxRunSpecification {
        vehicleInitialStates = List.copyOf(vehicleInitialStates);
    }
}
