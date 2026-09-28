package org.example.roadsimulation.sandbox.baseline;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;

/** The exact static data set materialized into a sandbox workspace. */
public record EffectiveBaseData(
        String selectionMode,
        String eligibilityPolicyVersion,
        Data data,
        String effectiveBaseDataSha256
) {
    public static final String ALL_ELIGIBLE_V1 = "ALL_ELIGIBLE_V1";
}
