package org.example.roadsimulation.sandbox.scenario.definition;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;

import java.util.List;
import java.util.Map;

/** Exact scenario facts materialized into the mutable sandbox workspace. */
public record EffectiveScenarioData(
        Data data,
        List<VehicleInitialization> vehicleInitializations,
        String baseDataProjectionSha256,
        String effectiveScenarioDataSha256
) {
    public EffectiveScenarioData {
        vehicleInitializations = List.copyOf(vehicleInitializations);
    }

    public record VehicleInitialization(long vehicleId, String policy, long poiId) {}

    public Map<Long, Long> fixedVehiclePois() {
        return vehicleInitializations.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                VehicleInitialization::vehicleId, VehicleInitialization::poiId));
    }
}
