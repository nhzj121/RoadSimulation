package org.example.roadsimulation.sandbox.scenario.definition;

import java.util.Map;

public record SandboxScenarioCompilationReport(
        String scenarioKey,
        String baselineId,
        String scenarioDefinitionSha256,
        String baseDataProjectionSha256,
        String effectiveScenarioDataSha256,
        SandboxScenarioRevisionV1.ResolvedSelection resolvedSelection,
        Map<String, Long> rowCounts,
        long fixedVehicleCount
) {
    public SandboxScenarioCompilationReport {
        rowCounts = Map.copyOf(rowCounts);
    }
}
