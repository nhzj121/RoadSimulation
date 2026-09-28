package org.example.roadsimulation.sandbox.scenario.definition;

public record CompiledSandboxScenario(
        SandboxScenarioDefinitionV1 normalizedDefinition,
        SandboxScenarioRevisionV1.ResolvedSelection resolvedSelection,
        EffectiveScenarioData effectiveData,
        String scenarioDefinitionSha256
) {}
