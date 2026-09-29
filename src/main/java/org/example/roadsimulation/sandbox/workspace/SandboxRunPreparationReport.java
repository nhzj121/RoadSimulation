package org.example.roadsimulation.sandbox.workspace;

import java.util.Map;

public record SandboxRunPreparationReport(
        SandboxWorkspaceState state,
        String schemaVersion,
        String controlSchemaVersion,
        String baselineId,
        String scenarioKey,
        int scenarioRevision,
        String runSpecKey,
        int runSpecRevision,
        String demandMode,
        String dispatchStrategy,
        String algorithmProfileId,
        String randomProtocolId,
        String rootSeedFingerprint,
        String runSpecificationSha256,
        String resolvedVehicleInitialStateSha256,
        String preparedRunFactsSha256,
        String deterministicSimulationRunId,
        Map<String, Long> rowCounts,
        long fixedVehicleCount,
        long randomVehicleCount,
        int eligibleVehicleInitialPoiCount,
        String errorCode,
        String errorMessage,
        String artifactVersion,
        String weatherTimelineSha256,
        String eventConfigurationSha256
) {
    public SandboxRunPreparationReport {
        rowCounts = Map.copyOf(rowCounts);
    }
}
