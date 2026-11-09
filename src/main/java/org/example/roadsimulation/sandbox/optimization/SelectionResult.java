package org.example.roadsimulation.sandbox.optimization;

import java.util.*;

public record SelectionResult(Outcome outcome, String selectedPlanFingerprint, ScoreBreakdown score,
                              List<String> reasons, Map<String, List<String>> rejectedCandidates) {
    public enum Outcome { IMPROVED, BASELINE_RETAINED, NO_VALID_BASELINE, NO_DEMAND }
    public SelectionResult {
        Objects.requireNonNull(outcome, "outcome");
        reasons = ContractChecks.list(reasons, "reasons");
        var copy = new TreeMap<String, List<String>>();
        Objects.requireNonNull(rejectedCandidates).forEach((id, issues) -> copy.put(id, List.copyOf(issues)));
        rejectedCandidates = Collections.unmodifiableMap(copy);
        if (outcome == Outcome.NO_VALID_BASELINE) {
            if (selectedPlanFingerprint != null || score != null) throw new IllegalArgumentException("Invalid selection");
        } else {
            ContractChecks.hash(selectedPlanFingerprint, "selectedPlanFingerprint");
            if (outcome == Outcome.NO_DEMAND ? score != null : score == null)
                throw new IllegalArgumentException("Invalid selection score");
            if (score != null && !selectedPlanFingerprint.equals(score.planFingerprint()))
                throw new IllegalArgumentException("Selection fingerprint mismatch");
        }
    }
}
