package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

import static org.example.roadsimulation.sandbox.optimization.PreferenceScorer.PRECISION;

final class TimedMoveRanking {
    static final int CARGO_LIMIT = 128;
    private static final String DELIVERY = "cargoDeliveredTonnes";
    record Ranked(PlanSearchOperators.MoveOption option, String fingerprint, Integer distanceRank,
                  Integer timeRank, BigDecimal score, List<String> rejections,
                  PlanTimingProjection.Projection projection) {}
    record Ranking(List<Ranked> placements, int truncated, String selected, long elapsedNanos) {}
    record MoveTrace(String parent, TripKey source, Ranking ranking) {}
    private record Entry(GlobalPlan plan, Ranked evidence) {}
    record Selection(Ranking ranking, List<GlobalPlan> orderedPlans) {}

    private final PlanSearchOperators operators;
    private final PlanTimingProjection timing;
    private final PlanTimingProjection.Projection reference;
    private final GlobalPlanValidator validator = new GlobalPlanValidator();
    private final CompiledOptimizationProblem problem;
    private final org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data fleet;
    private final PreferenceProfile preference;
    private final BigDecimal deliveryWeight;

    TimedMoveRanking(SandboxCandidateEvaluator session, PreferenceProfile preference, PlanSearchOperators operators) {
        this(session.problem(), session.fleetData(), session.baseline().plan(), preference, operators);
    }

    TimedMoveRanking(CompiledOptimizationProblem problem,
                     org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data fleet,
                     GlobalPlan baseline, PreferenceProfile preference, PlanSearchOperators operators) {
        this.problem = problem;
        this.fleet = fleet;
        this.operators = operators;
        this.preference = preference;
        timing = new PlanTimingProjection(problem);
        reference = project(baseline);
        BigDecimal shares = preference.objectives().entrySet().stream().filter(e -> !e.getKey().equals(ObjectiveRegistry.PRIMARY))
                .map(e -> e.getValue().secondaryShare()).reduce(BigDecimal.ZERO, BigDecimal::add);
        deliveryWeight = preference.secondaryGroupWeight().multiply(preference.objectives().get(DELIVERY).secondaryShare(), PRECISION)
                .divide(shares, PRECISION);
    }

    PlanTimingProjection.Projection project(GlobalPlan plan) {
        return timing.project(plan, validator.validatePlan(problem, plan, fleet));
    }

    GlobalPlanSearch.Proposer proposer(boolean includeCargo, Consumer<MoveTrace> observe) {
        return (kind, parent, summary, donor, donorSummary, random) -> {
            if (kind != PlanSearchOperators.Kind.MOVE)
                return operators.propose(kind, parent, summary, donor, donorSummary, random);
            var source = operators.moveSource(parent, summary, random);
            if (source == null) return new PlanSearchOperators.Proposal(null, List.of("NO_APPLICABLE_CHANGE"));
            operators.advanceMoveRandom(parent, source, random);
            var selection = rank(parent, source, includeCargo);
            observe.accept(new MoveTrace(parent.fingerprint(), source, selection.ranking()));
            if (selection.orderedPlans().isEmpty())
                return new PlanSearchOperators.Proposal(null, List.of("NO_LEGAL_PLACEMENT"));
            return new PlanSearchOperators.Proposal(selection.orderedPlans().get(0), List.of());
        };
    }

    Selection rank(GlobalPlan parent, TripKey source, boolean includeCargo) {
        long start = System.nanoTime();
        var options = new ArrayList<>(operators.wholeMoves(parent, source));
        int truncated = 0;
        if (includeCargo) {
            var trip = parent.vehicles().stream().filter(v -> v.vehicleId() == source.logicalVehicleId())
                    .findFirst().orElseThrow().trips().get(source.tripIndex());
            // A single stable cargo choice per proposal. Prefer the heaviest cargo, then its stable key.
            var weights = new HashMap<GlobalPlan.TaskKey, BigDecimal>();
            problem.catalog().tasks().forEach(task -> weights.put(task.key(), task.weightTonnes()));
            var cargo = trip.nodes().stream().filter(node -> node.action() == GlobalPlan.Action.LOAD).map(GlobalPlan.Node::task)
                    .min(Comparator.<GlobalPlan.TaskKey, BigDecimal>comparing(weights::get).reversed()
                            .thenComparing(key -> OptimizationJson.write(key))).orElseThrow();
            var additional = operators.cargoMoves(parent, source, cargo);
            options.addAll(additional.subList(0, Math.min(CARGO_LIMIT, additional.size())));
            truncated = Math.max(0, additional.size() - CARGO_LIMIT);
        }
        var ranked = new ArrayList<Ranked>();
        var valid = new ArrayList<Entry>();
        var seen = new HashSet<String>();
        int distanceRank = 0;
        for (var option : options) {
            var proposal = operators.applyMove(parent, source, option);
            Integer distancePosition = option.type().equals("WHOLE_TRIP") && proposal.plan() != null ? ++distanceRank : null;
            if (proposal.plan() == null) {
                ranked.add(new Ranked(option, null, null, null, null, proposal.rejections(), null));
                continue;
            }
            var plan = proposal.plan();
            var fingerprint = plan.fingerprint();
            if (!seen.add(fingerprint)) {
                ranked.add(new Ranked(option, fingerprint, distancePosition, null, null, List.of("DUPLICATE_PLACEMENT"), null));
                continue;
            }
            var prediction = project(plan);
            valid.add(new Entry(plan, new Ranked(option, fingerprint, distancePosition, null, score(prediction), List.of(), prediction)));
        }
        valid.sort(Comparator.comparing((Entry entry) -> entry.evidence().score(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.evidence().projection().terminalTonnes(), Comparator.reverseOrder())
                .thenComparing(entry -> entry.evidence().projection().emptyKm())
                .thenComparing(entry -> entry.evidence().fingerprint()));
        for (int index = 0; index < valid.size(); index++) {
            var entry = valid.get(index).evidence();
            ranked.add(new Ranked(entry.option(), entry.fingerprint(), entry.distanceRank(), index + 1,
                    entry.score(), entry.rejections(), entry.projection()));
        }
        var plans = valid.stream().map(Entry::plan).toList();
        String selected = valid.isEmpty() ? null : valid.get(0).evidence().fingerprint();
        return new Selection(new Ranking(List.copyOf(ranked), truncated, selected, System.nanoTime() - start), plans);
    }

    private BigDecimal score(PlanTimingProjection.Projection prediction) {
        if (prediction.emptyRatio() == null) return null;
        BigDecimal primaryScale = preference.objectives().get(ObjectiveRegistry.PRIMARY).scale();
        BigDecimal raw = prediction.emptyRatio().subtract(
                reference.emptyRatio() == null ? BigDecimal.ZERO : reference.emptyRatio(), PRECISION);
        BigDecimal excess = raw.subtract(preference.primaryAllowanceRaw(), PRECISION)
                .divide(primaryScale, PRECISION).max(BigDecimal.ZERO);
        return raw.divide(primaryScale, PRECISION).multiply(preference.primaryWeight(), PRECISION)
                .add(reference.deliveredTonnes().subtract(prediction.deliveredTonnes(), PRECISION)
                        .divide(preference.objectives().get(DELIVERY).scale(), PRECISION)
                        .multiply(deliveryWeight, PRECISION), PRECISION)
                .add(preference.primaryPenaltyWeight().multiply(excess.pow(2, PRECISION), PRECISION), PRECISION);
    }
}
