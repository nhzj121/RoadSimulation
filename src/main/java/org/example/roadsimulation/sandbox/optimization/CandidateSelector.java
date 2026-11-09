package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;
import static org.example.roadsimulation.sandbox.optimization.SelectionResult.Outcome.*;

public final class CandidateSelector {
    private final PreferenceScorer scorer = new PreferenceScorer();

    public SelectionResult select(OptimizationProblemRef problem, ObjectiveRegistry registry,
            PreferenceProfile preference, CandidateAssessment baseline, List<CandidateAssessment> candidates) {
        preference.validate(registry);
        candidates = ContractChecks.list(candidates, "candidates");
        if (baseline == null) return noBaseline(List.of("BASELINE_MISSING"));
        if (!problem.equals(baseline.problem())) return noBaseline(List.of("BASELINE_PROBLEM_MISMATCH"));
        // Cross-problem candidates are API misuse, not mediocre candidates in this population.
        for (var candidate : candidates) {
            if (!problem.equals(candidate.problem())) throw new IllegalArgumentException("Candidate problem mismatch");
        }
        if (!baseline.executionIssues().isEmpty()) return noBaseline(baseline.executionIssues());
        if (BigDecimal.ZERO.equals(baseline.auditData().get("cargoRequiredTonnes"))) {
            // Demand is endogenous: zero generated demand alone does not prove the frozen problem has no tasks.
            if (BigDecimal.ZERO.equals(baseline.auditData().get("cargoDeliveredTonnes"))
                    && BigDecimal.ZERO.equals(baseline.auditData().get("catalogTaskCount")))
                return new SelectionResult(NO_DEMAND, baseline.planFingerprint(), null,
                        List.of("EMPTY_TASK_CATALOG_AND_NO_DEMAND: ratios are not scored"), Map.of());
            return noBaseline(List.of("NO_DEMAND_NOT_CONFIRMED_BY_EMPTY_CATALOG"));
        }
        var baselineIssues = scorer.eligibilityIssues(problem, registry, baseline);
        if (!baselineIssues.isEmpty()) return noBaseline(baselineIssues);
        var reference = new TreeMap<String, BigDecimal>();
        registry.definitions().forEach(d -> reference.put(d.metricId(), baseline.metrics().get(d.metricId()).value()));
        var context = new ScoringContext(problem, registry, preference, baseline.planFingerprint(), reference);
        var best = scorer.score(context, baseline).score();
        var rejected = new TreeMap<String, List<String>>();
        var assessed = new HashMap<String, CandidateAssessment>();
        assessed.put(baseline.planFingerprint(), baseline);
        for (var candidate : candidates) {
            var previous = assessed.putIfAbsent(candidate.planFingerprint(), candidate);
            if (previous != null) {
                if (!previous.equals(candidate))
                    throw new IllegalArgumentException("Conflicting evidence for the same plan fingerprint");
                continue;
            }
            var result = scorer.score(context, candidate);
            if (!result.eligible()) rejected.put(candidate.planFingerprint(), result.issues());
            else if (ScoreBreakdown.compare(result.score(), best) < 0) best = result.score();
        }
        boolean improved = best.total().signum() < 0;
        return new SelectionResult(improved ? IMPROVED : BASELINE_RETAINED,
                best.planFingerprint(), best, List.of(improved ? "STRICTLY_BETTER_SOFT_SCORE" : "NO_STRICT_IMPROVEMENT"), rejected);
    }

    private SelectionResult noBaseline(List<String> reasons) {
        return new SelectionResult(NO_VALID_BASELINE, null, null, reasons, Map.of());
    }
}
