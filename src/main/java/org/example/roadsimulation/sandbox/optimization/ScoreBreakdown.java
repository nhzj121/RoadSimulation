package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

public record ScoreBreakdown(String contextFingerprint, String planFingerprint, boolean baseline,
                             Map<String, Contribution> contributions, BigDecimal primaryPenalty,
                             BigDecimal total) {
    public record Contribution(BigDecimal rawDelta, BigDecimal normalizedDelta,
                               BigDecimal effectiveWeight, BigDecimal weightedScore) {
        public Contribution {
            rawDelta = ContractChecks.decimal(rawDelta, "rawDelta");
            normalizedDelta = ContractChecks.decimal(normalizedDelta, "normalizedDelta");
            effectiveWeight = ContractChecks.positive(effectiveWeight, "effectiveWeight");
            weightedScore = ContractChecks.decimal(weightedScore, "weightedScore");
        }
    }

    public ScoreBreakdown {
        contextFingerprint = ContractChecks.hash(contextFingerprint, "contextFingerprint");
        planFingerprint = ContractChecks.hash(planFingerprint, "planFingerprint");
        var copy = new TreeMap<String, Contribution>();
        Objects.requireNonNull(contributions).forEach((key, value) -> copy.put(
                ContractChecks.text(key, "contribution key"), Objects.requireNonNull(value, "contribution")));
        contributions = Collections.unmodifiableMap(copy);
        primaryPenalty = ContractChecks.nonNegative(primaryPenalty, "primaryPenalty");
        total = ContractChecks.decimal(total, "total");
    }

    /** A total order within a context, without pairwise epsilon or moving reference values. */
    public static int compare(ScoreBreakdown left, ScoreBreakdown right) {
        if (!left.contextFingerprint.equals(right.contextFingerprint))
            throw new IllegalArgumentException("Cannot compare different scoring contexts");
        int score = left.total.compareTo(right.total);
        if (score != 0) return score;
        if (left.baseline != right.baseline) return left.baseline ? -1 : 1;
        return left.planFingerprint.compareTo(right.planFingerprint);
    }
}
