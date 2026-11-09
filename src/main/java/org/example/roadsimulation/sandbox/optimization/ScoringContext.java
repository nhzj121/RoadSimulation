package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

/** Frozen for one comparison. No reference is updated as better candidates are discovered. */
public record ScoringContext(OptimizationProblemRef problem, ObjectiveRegistry registry,
                             PreferenceProfile preference, String baselinePlanFingerprint,
                             Map<String, BigDecimal> baselineValues) {
    public ScoringContext {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(preference, "preference");
        preference.validate(registry);
        baselinePlanFingerprint = ContractChecks.hash(baselinePlanFingerprint, "baselinePlanFingerprint");
        baselineValues = ContractChecks.decimals(baselineValues, "baselineValues");
        if (!baselineValues.keySet().equals(registry.metricIds()))
            throw new IllegalArgumentException("Baseline must contain exactly the registered objectives");
        for (var definition : registry.definitions()) definition.validate(baselineValues.get(definition.metricId()));
    }

    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
