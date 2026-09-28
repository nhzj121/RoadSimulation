package org.example.roadsimulation.sandbox.run;

public record SandboxVehicleInitialState(
        long vehicleId,
        String initializationPolicy,
        long poiId,
        String decisionDomain,
        String decisionKey,
        String derivedSeedHex
) {
    public static final String FIXED_POI = "FIXED_POI";
    public static final String RANDOM_DERIVED_POI = "RANDOM_DERIVED_POI";
}
