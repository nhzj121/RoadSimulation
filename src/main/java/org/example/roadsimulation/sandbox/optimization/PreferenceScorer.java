package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.evaluation.EvaluationMetricValueStatus;
import java.math.*;
import java.util.*;

/** Pure G0 scoring; no Spring component, repositories, clocks, random draws or runtime defaults. */
public final class PreferenceScorer {
    public static final MathContext PRECISION = MathContext.DECIMAL128;

    public record Result(ScoreBreakdown score, List<String> issues) {
        public Result {
            issues = List.copyOf(issues);
            if ((score == null) == issues.isEmpty()) throw new IllegalArgumentException("Score or issues required");
        }
        public boolean eligible() { return score != null; }
    }

    public List<String> eligibilityIssues(OptimizationProblemRef problem, ObjectiveRegistry registry,
                                           CandidateAssessment candidate) {
        if (!problem.equals(candidate.problem())) throw new IllegalArgumentException("Candidate problem mismatch");
        var issues = new ArrayList<>(candidate.executionIssues());
        for (var definition : registry.definitions()) {
            var reading = candidate.metrics().get(definition.metricId());
            if (reading == null) issues.add("MISSING_OBJECTIVE:" + definition.metricId());
            else if (!definition.unit().equals(reading.unit())) issues.add("UNIT_MISMATCH:" + definition.metricId());
            else if (reading.status() != EvaluationMetricValueStatus.AVAILABLE)
                issues.add("OBJECTIVE_UNAVAILABLE:" + definition.metricId() + ":" + reading.status());
            else {
                try { definition.validate(reading.value()); }
                catch (IllegalArgumentException invalid) { issues.add("OBJECTIVE_OUT_OF_RANGE:" + definition.metricId()); }
            }
        }
        return List.copyOf(issues);
    }

    public Result score(ScoringContext context, CandidateAssessment candidate) {
        var issues = eligibilityIssues(context.problem(), context.registry(), candidate);
        if (!issues.isEmpty()) return new Result(null, issues);
        boolean baseline = context.baselinePlanFingerprint().equals(candidate.planFingerprint());
        if (baseline) {
            for (var definition : context.registry().definitions()) {
                String id = definition.metricId();
                if (context.baselineValues().get(id).compareTo(candidate.metrics().get(id).value()) != 0)
                    throw new IllegalArgumentException("Baseline fingerprint has conflicting evaluation values");
            }
        }
        var pref = context.preference();
        BigDecimal shares = pref.objectives().entrySet().stream()
                .filter(e -> !e.getKey().equals(ObjectiveRegistry.PRIMARY))
                .map(e -> e.getValue().secondaryShare()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var contributions = new TreeMap<String, ScoreBreakdown.Contribution>();
        BigDecimal total = BigDecimal.ZERO;
        for (var definition : context.registry().definitions()) {
            String id = definition.metricId();
            var setting = pref.objectives().get(id);
            BigDecimal raw = candidate.metrics().get(id).value().subtract(context.baselineValues().get(id), PRECISION);
            BigDecimal oriented = definition.direction() == ObjectiveDefinition.Direction.MINIMIZE ? raw : raw.negate();
            BigDecimal normalized = oriented.divide(setting.scale(), PRECISION);
            BigDecimal weight = id.equals(ObjectiveRegistry.PRIMARY) ? pref.primaryWeight()
                    : pref.secondaryGroupWeight().multiply(setting.secondaryShare(), PRECISION).divide(shares, PRECISION);
            BigDecimal weighted = weight.multiply(normalized, PRECISION);
            contributions.put(id, new ScoreBreakdown.Contribution(raw, normalized, weight, weighted));
            total = total.add(weighted, PRECISION);
        }
        BigDecimal rawPrimary = contributions.get(ObjectiveRegistry.PRIMARY).rawDelta();
        BigDecimal excess = rawPrimary.subtract(pref.primaryAllowanceRaw(), PRECISION)
                .divide(pref.objectives().get(ObjectiveRegistry.PRIMARY).scale(), PRECISION).max(BigDecimal.ZERO);
        BigDecimal penalty = pref.primaryPenaltyWeight().multiply(excess.pow(2, PRECISION), PRECISION);
        return new Result(new ScoreBreakdown(context.fingerprint(), candidate.planFingerprint(), baseline,
                contributions, penalty, total.add(penalty, PRECISION)), List.of());
    }
}
