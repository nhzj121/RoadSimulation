package org.example.roadsimulation.sandbox.optimization;

import java.time.LocalDateTime;

/** Semantic input identity; provenance hashes containing display fields are kept outside this payload. */
public record OptimizationInputManifest(String artifactVersion, String productionRulesVersion,
        String scenarioFactsFingerprint, String executionFactsFingerprint, String evaluationContractVersion,
        LocalDateTime simulationStart, int totalLoops, long tickSeconds) {
    public static final String VERSION = "sandbox-optimization-input/v1";
    public OptimizationInputManifest {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        productionRulesVersion = ContractChecks.version(productionRulesVersion,
                org.example.roadsimulation.service.production.ProductionRules.VERSION);
        scenarioFactsFingerprint = ContractChecks.hash(scenarioFactsFingerprint, "scenarioFactsFingerprint");
        executionFactsFingerprint = ContractChecks.hash(executionFactsFingerprint, "executionFactsFingerprint");
        new OptimizationProblemRef(scenarioFactsFingerprint, simulationStart, totalLoops, tickSeconds, evaluationContractVersion);
    }
    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
