package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

/** No business defaults: callers must supply a complete, explicitly versioned preference. */
public record PreferenceProfile(String artifactVersion, Map<String, ObjectivePreference> objectives,
                                BigDecimal primaryWeight, BigDecimal secondaryGroupWeight,
                                BigDecimal primaryAllowanceRaw, BigDecimal primaryPenaltyWeight) {
    public static final String VERSION = "sandbox-optimization-preferences/v1";

    public record ObjectivePreference(String unit, BigDecimal scale, BigDecimal secondaryShare) {
        public ObjectivePreference {
            unit = ContractChecks.text(unit, "unit");
            scale = ContractChecks.positive(scale, "scale");
            secondaryShare = ContractChecks.nonNegative(secondaryShare, "secondaryShare");
        }
    }

    public PreferenceProfile {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        if (objectives == null || objectives.values().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("objectives are required");
        var copy = new TreeMap<String, ObjectivePreference>();
        objectives.forEach((key, value) -> copy.put(ContractChecks.text(key, "objective key"), value));
        objectives = Collections.unmodifiableMap(copy);
        primaryWeight = ContractChecks.positive(primaryWeight, "primaryWeight");
        secondaryGroupWeight = ContractChecks.positive(secondaryGroupWeight, "secondaryGroupWeight");
        primaryAllowanceRaw = ContractChecks.nonNegative(primaryAllowanceRaw, "primaryAllowanceRaw");
        primaryPenaltyWeight = ContractChecks.nonNegative(primaryPenaltyWeight, "primaryPenaltyWeight");
    }

    public void validate(ObjectiveRegistry registry) {
        if (!objectives.keySet().equals(registry.metricIds()))
            throw new IllegalArgumentException("Preference must configure exactly the registered objectives");
        for (var definition : registry.definitions()) {
            var pref = objectives.get(definition.metricId());
            if (!definition.unit().equals(pref.unit())) throw new IllegalArgumentException("Objective unit mismatch");
            if (definition.metricId().equals(ObjectiveRegistry.PRIMARY)) {
                if (pref.secondaryShare().signum() != 0)
                    throw new IllegalArgumentException("Primary cannot consume a secondary share");
            } else if (pref.secondaryShare().signum() <= 0) {
                throw new IllegalArgumentException("Secondary shares must be positive");
            }
        }
    }

    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
