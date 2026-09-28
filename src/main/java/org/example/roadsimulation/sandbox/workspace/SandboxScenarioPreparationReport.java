package org.example.roadsimulation.sandbox.workspace;

import java.util.Map;

public record SandboxScenarioPreparationReport(
        SandboxWorkspaceState state,
        String businessSchemaVersion,
        String controlSchemaVersion,
        String baselineId,
        String scenarioKey,
        int scenarioRevision,
        String scenarioDefinitionSha256,
        String baseDataProjectionSha256,
        String effectiveScenarioDataSha256,
        Map<String, Long> rowCounts,
        long fixedVehicleCount,
        String errorCode,
        String errorMessage
) {
    public SandboxScenarioPreparationReport {
        rowCounts = rowCounts == null ? Map.of() : Map.copyOf(rowCounts);
    }
}
