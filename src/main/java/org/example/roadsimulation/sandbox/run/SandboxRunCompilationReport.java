package org.example.roadsimulation.sandbox.run;

public record SandboxRunCompilationReport(
        String scenarioKey,
        int scenarioRevision,
        String runSpecKey,
        String demandMode,
        String dispatchStrategy,
        String algorithmProfileId,
        String randomProtocolId,
        String rootSeedFingerprint,
        String runSpecificationSha256,
        String resolvedVehicleInitialStateSha256,
        String preparedRunFactsSha256,
        String deterministicSimulationRunId,
        long vehicleCount,
        long fixedVehicleCount,
        long randomVehicleCount,
        int eligibleVehicleInitialPoiCount
) {}
