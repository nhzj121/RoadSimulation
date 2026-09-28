package org.example.roadsimulation.sandbox.workspace;

import java.util.Map;

public record SandboxPreparationReport(
        SandboxWorkspaceState state,
        String schemaVersion,
        String baselineId,
        String restorationPayloadSha256,
        String simulationFactsSha256,
        String effectiveBaseDataSha256,
        String eligibilityPolicyVersion,
        Map<String, Long> rowCounts,
        String errorCode,
        String errorMessage
) {
    public SandboxPreparationReport {
        rowCounts = rowCounts == null ? Map.of() : Map.copyOf(rowCounts);
    }
}
