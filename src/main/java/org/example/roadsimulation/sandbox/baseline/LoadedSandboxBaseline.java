package org.example.roadsimulation.sandbox.baseline;

import com.fasterxml.jackson.databind.JsonNode;

public record LoadedSandboxBaseline(
        SandboxBaselinePackageV1 baseline,
        JsonNode sourceJson
) {}
