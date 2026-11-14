package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.management.SandboxOptimizationJobStore;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

import static org.example.roadsimulation.sandbox.optimization.PlanSearchOperators.Kind;
import static org.example.roadsimulation.sandbox.optimization.SandboxCandidateEvaluator.Status;

/** Bounded, serial search. Only G3 evidence with a complete G0 score enters the population. */
public final class GlobalPlanSearch {
    public static final String VERSION = "sandbox-global-plan-search/v1";
    public static final String THROUGHPUT_VERSION = "sandbox-global-plan-search/v2";
    private static final int POPULATION_SIZE = 4;
    private static final List<Kind> FIRST = List.of(Kind.MOVE, Kind.SWAP, Kind.DEPARTURE);
    private static final List<Kind> LATER = List.of(Kind.MERGE, Kind.SPLIT, Kind.REINSERT, Kind.CROSSOVER,
            Kind.MOVE, Kind.SWAP, Kind.DEPARTURE);

    public record Budget(int maxEvaluations, int maxGenerations, int evaluationsPerGeneration, int proposalsPerGeneration) {
        public Budget {
            if (maxEvaluations <= 0 || maxGenerations <= 0 || evaluationsPerGeneration <= 0 || proposalsPerGeneration <= 0)
                throw new IllegalArgumentException("Search budgets must be positive");
        }
    }

    public record Request(String artifactVersion, String runSpecKey, int runSpecRevision, String baselineResource,
                          PreferenceProfile preference, long searchSeed, Budget budget, Long timeoutSeconds) {
        public Request {
            if (!THROUGHPUT_VERSION.equals(artifactVersion)) ContractChecks.version(artifactVersion, VERSION);
            ContractChecks.text(runSpecKey, "runSpecKey");
            ContractChecks.text(baselineResource, "baselineResource");
            if (runSpecRevision <= 0 || (timeoutSeconds != null && timeoutSeconds <= 0))
                throw new IllegalArgumentException("Invalid run revision or timeout");
            Objects.requireNonNull(preference).validate(SandboxCandidateEvaluator.objectives(evaluationVersion(artifactVersion)));
            Objects.requireNonNull(budget);
            if (THROUGHPUT_VERSION.equals(artifactVersion) && budget.maxEvaluations() > 16)
                throw new IllegalArgumentException("Throughput calibration allows at most 16 evaluations");
        }
    }

    public enum Outcome { IMPROVED, BASELINE_RETAINED, NO_VALID_BASELINE, NO_DEMAND }
    public enum Stop {
        EVALUATION_BUDGET, GENERATION_BUDGET, NO_VALID_BASELINE, NO_DEMAND, WORKSPACE_BUSY,
        EXECUTION_FAILED, TIMEOUT, INTERRUPTED, INVALID_LEDGER, WORKER_FAILED, EVALUATION_IO_FAILED,
        INVALID_HEURISTIC_SEED
    }
    public record Best(GlobalPlan plan, String planFingerprint, String executionId, CandidateAssessment assessment,
                       ScoreBreakdown score) {}
    public record Step(int generation, int proposal, String operator, String parent, String donor, String child,
                       String status, List<String> reasons, int launchedEvaluations, String executionId,
                       BigDecimal totalScore, boolean acceptedBest) {
        public Step { reasons = List.copyOf(reasons); }
    }
    public record Result(String artifactVersion, String problemFingerprint, String simulationRootSeed, long searchSeed,
                         String preferenceFingerprint, Budget budget, Outcome outcome, Stop stop, boolean interrupted,
                         int generations, int proposals, int launchedEvaluations, Best baseline, Best selected,
                         List<Step> steps, long elapsedNanos) {
        public Result { steps = List.copyOf(steps); }
    }

    interface Evaluation {
        SandboxCandidateEvaluator.Attempt baseline() throws IOException;
        SandboxCandidateEvaluator.Attempt candidate(GlobalPlan plan) throws IOException;
        default SandboxCandidateEvaluator.Attempt seed(GlobalPlan plan) throws IOException { return candidate(plan); }
        int launched();
        default FulfillmentComparison.Side delivery(String executionId) { return null; }
    }

    @FunctionalInterface
    interface Checkpoint {
        void save(Best best) throws IOException;
        default void prepared(Best best) throws IOException {}
        default void requireActive() throws IOException {}
    }

    @FunctionalInterface
    interface Proposer {
        PlanSearchOperators.Proposal propose(Kind kind, GlobalPlan parent, PlanExecutionSummary summary,
                GlobalPlan donor, PlanExecutionSummary donorSummary, SplittableRandom random);
    }

    private record Individual(Best best, PlanExecutionSummary summary) {}

    private GlobalPlanSearch() {}

    public static Result execute(SandboxManagementSettings settings, Request request, Path output) throws IOException {
        return execute(settings, request, output, null);
    }

    static Result execute(SandboxManagementSettings settings, Request request, Path output,
                          DeliveryRetreatLimits limits) throws IOException {
        return execute(settings, request, output, limits, null);
    }

    public static Result executeWithHeuristicSeed(SandboxManagementSettings settings, Request request, Path output,
                                                  DeliveryRetreatLimits limits, Path cache) throws IOException {
        Objects.requireNonNull(limits, "HEURISTIC seeding requires explicit delivery retreat limits");
        Objects.requireNonNull(cache, "HEURISTIC seed cache directory is required");
        if (request.budget().maxEvaluations() < 2)
            throw new IllegalArgumentException("Budget must cover both the greedy baseline and the HEURISTIC seed");
        return execute(settings, request, output, limits, cache);
    }

    private static Result execute(SandboxManagementSettings settings, Request request, Path output,
                                  DeliveryRetreatLimits limits, Path seedCache) throws IOException {
        if (limits != null && !THROUGHPUT_VERSION.equals(request.artifactVersion()))
            throw new IllegalArgumentException("Delivery retreat limits require throughput scoring");
        long start = System.nanoTime();
        var session = SandboxCandidateEvaluator.open(settings, request.runSpecKey(), request.runSpecRevision(),
                request.baselineResource(), output, request.timeoutSeconds(), evaluationVersion(request.artifactVersion()));
        return execute(request, output, limits, seedCache, start, session, null, false);
    }

    public static Stop executeManaged(SandboxManagementSettings settings, String key, int revision, Path output,
                                      Path seedCache, SandboxOptimizationJobStore job) throws IOException, java.sql.SQLException {
        long start = System.nanoTime();
        String baseline = "classpath:sandbox/baseline/baseline-v1.json";
        var session = SandboxCandidateEvaluator.openManaged(settings, key, revision, baseline, output, job);
        var profile = session.publishedProfile();
        if (!SandboxOptimizationProfile.supports(profile.profileId())
                || !SandboxOptimizationProfile.identity(profile.profileId()).equals(profile))
            throw new SandboxWorkspaceException("OPTIMIZATION_PROFILE_CHANGED", "Published optimization profile differs");
        job.freezeProblem(session.problem().problem().problemFingerprint(),
                profile.parameters().get("configurationSha256").toString());
        write(output.resolve("algorithm-profile.json"), profile);
        if (session.problem().catalog().tasks().isEmpty()) return Stop.NO_DEMAND;
        var request = new Request(THROUGHPUT_VERSION, key, revision, baseline,
                SandboxOptimizationProfile.preferenceFor(session.problem().catalog()),
                SandboxOptimizationProfile.SEARCH_SEED, SandboxOptimizationProfile.BUDGET,
                session.workerTimeoutSeconds());
        boolean cargoReinsert = SandboxOptimizationProfile.CARGO_REINSERT_ID.equals(profile.profileId());
        return execute(request, output, SandboxOptimizationProfile.LIMITS, seedCache, start, session, job, cargoReinsert).stop();
    }

    private static Result execute(Request request, Path output, DeliveryRetreatLimits limits, Path seedCache, long start,
                                  SandboxCandidateEvaluator session, SandboxOptimizationJobStore job, boolean cargoReinsert) throws IOException {
        var checkpoint = new Checkpoint() {
            private int index;
            private boolean prepared;

            public void save(Best best) throws IOException {
                if (prepared) archive(best, false);
                write(output.resolve("best-" + index++ + ".json"), best);
            }

            public void prepared(Best best) throws IOException {
                archive(best, true);
                prepared = true;
            }

            public void requireActive() throws IOException {
                if (job == null) return;
                try {
                    if (job.cancelled()) throw cancelled();
                } catch (java.sql.SQLException failure) { throw new IOException("Cannot check optimization cancellation", failure); }
            }

            private void archive(Best best, boolean initial) throws IOException {
                if (job == null) return;
                try {
                    if (!job.checkpoint(best.executionId(), initial)) throw cancelled();
                } catch (java.sql.SQLException failure) { throw new IOException("Cannot archive optimization result", failure); }
            }
        };
        checkpoint.requireActive();
        var baselinePlan = session.baseline().plan();
        GlobalPlan heuristicSeed = null;
        if (seedCache != null && !session.problem().catalog().tasks().isEmpty()) {
            var acquired = session.acquireHeuristicSeed(seedCache);
            boolean duplicate = baselinePlan.equals(acquired.seed().plan());
            write(output.resolve("heuristic-seed.json"), Map.of("acquisition", acquired, "duplicateOfGreedy", duplicate));
            if (!duplicate) heuristicSeed = acquired.seed().plan();
            checkpoint.requireActive();
        }
        var evaluation = new Evaluation() {
            public SandboxCandidateEvaluator.Attempt baseline() throws IOException {
                return session.evaluateBaseline(SandboxCandidateEvaluator.CacheMode.USE);
            }
            public SandboxCandidateEvaluator.Attempt candidate(GlobalPlan plan) throws IOException {
                return session.evaluateCandidate(plan, SandboxCandidateEvaluator.CacheMode.USE);
            }
            public SandboxCandidateEvaluator.Attempt seed(GlobalPlan plan) throws IOException {
                return session.evaluateCandidate(plan, SandboxCandidateEvaluator.CacheMode.USE, "HEURISTIC_SEED");
            }
            public int launched() { return session.launchedWorkers(); }
            public FulfillmentComparison.Side delivery(String executionId) {
                return session.delivery(executionId);
            }
        };
        var operators = new PlanSearchOperators(session.problem(), session.fleetData());
        var moves = new ArrayList<Map<String, Object>>();
        Proposer proposer = operators::propose;
        if (cargoReinsert) {
            proposer = new TimedMoveRanking(session, request.preference(), operators).proposer(true, trace -> {
                var ranking = trace.ranking();
                ranking.placements().stream().filter(placement -> Integer.valueOf(1).equals(placement.timeRank()))
                        .findFirst().ifPresent(selected -> moves.add(Map.of("parent", trace.parent(), "source", trace.source(),
                                "child", selected.fingerprint(), "move", selected.option().type(),
                                "considered", ranking.placements().size(), "truncated", ranking.truncated())));
            });
        }
        var result = search(request, session.simulationRootSeed(), baselinePlan, evaluation, proposer,
                checkpoint, start, limits, heuristicSeed);
        if (cargoReinsert) write(output.resolve("timed-moves.json"), moves);
        write(output.resolve("search.json"), result);
        if (result.steps().stream().noneMatch(step -> Stop.EVALUATION_IO_FAILED.name().equals(step.status())))
            write(output.resolve("evaluation.json"), limits == null ? session.report(request.preference())
                    : session.report(request.preference(), result));
        if (THROUGHPUT_VERSION.equals(request.artifactVersion()) && result.selected() != null)
            write(output.resolve("fulfillment.json"), session.fulfillment(result.baseline().executionId(),
                    result.selected().executionId()));
        return result;
    }

    private static SandboxWorkspaceException cancelled() {
        return new SandboxWorkspaceException("USER_CANCELLED", "Optimization cancelled");
    }

    private static String evaluationVersion(String searchVersion) {
        return THROUGHPUT_VERSION.equals(searchVersion) ? SandboxCandidateEvaluator.THROUGHPUT_VERSION
                : SandboxCandidateEvaluator.VERSION;
    }

    static Result search(Request request, String simulationSeed, GlobalPlan baselinePlan, Evaluation evaluation,
                         Proposer proposer, Checkpoint checkpoint, long start) throws IOException {
        return search(request, simulationSeed, baselinePlan, evaluation, proposer, checkpoint, start, null);
    }

    static Result search(Request request, String simulationSeed, GlobalPlan baselinePlan, Evaluation evaluation,
                         Proposer proposer, Checkpoint checkpoint, long start, DeliveryRetreatLimits limits) throws IOException {
        return search(request, simulationSeed, baselinePlan, evaluation, proposer, checkpoint, start, limits, null);
    }

    static Result search(Request request, String simulationSeed, GlobalPlan baselinePlan, Evaluation evaluation,
                         Proposer proposer, Checkpoint checkpoint, long start, DeliveryRetreatLimits limits,
                         GlobalPlan heuristicSeed) throws IOException {
        if (heuristicSeed != null) {
            if (request.budget().maxEvaluations() < 2)
                throw new IllegalArgumentException("Budget must cover both the greedy baseline and the HEURISTIC seed");
            if (!heuristicSeed.problem().equals(baselinePlan.problem()) || heuristicSeed.equals(baselinePlan))
                throw new IllegalArgumentException("HEURISTIC seed must be a distinct plan for the same frozen problem");
        }
        return new SearchRun(request, simulationSeed, baselinePlan, evaluation, proposer, checkpoint, start, limits)
                .run(heuristicSeed);
    }

    private static final class SearchRun {
        private final Request request;
        private final String simulationSeed;
        private final GlobalPlan baselinePlan;
        private final Evaluation evaluation;
        private final Proposer proposer;
        private final Checkpoint checkpoint;
        private final long start;
        private final SplittableRandom random;
        private final ObjectiveRegistry registry;
        private final DeliveryRetreatLimits limits;
        private final PreferenceScorer scorer = new PreferenceScorer();
        private final Set<String> seen = new HashSet<>();
        private final List<Step> steps = new ArrayList<>();
        private final List<Individual> population = new ArrayList<>();
        private Best baseline, best;
        private ScoringContext context;
        private int generations, proposals;

        SearchRun(Request request, String simulationSeed, GlobalPlan baselinePlan, Evaluation evaluation,
                  Proposer proposer, Checkpoint checkpoint, long start, DeliveryRetreatLimits limits) {
            this.request = request;
            this.simulationSeed = simulationSeed;
            this.baselinePlan = baselinePlan;
            this.evaluation = evaluation;
            this.proposer = proposer;
            this.checkpoint = checkpoint;
            this.start = start;
            this.limits = limits;
            registry = SandboxCandidateEvaluator.objectives(evaluationVersion(request.artifactVersion()));
            random = new SplittableRandom(request.searchSeed());
        }

        Result run(GlobalPlan heuristicSeed) throws IOException {
            String fingerprint = baselinePlan.fingerprint();
            SandboxCandidateEvaluator.Attempt evidence;
            try {
                evidence = evaluation.baseline();
            } catch (IOException failure) {
                steps.add(step(0, 0, "BASELINE", null, null, fingerprint, Stop.EVALUATION_IO_FAILED.name(),
                        List.of("EVALUATION_IO_FAILED"), null, null, false));
                return result(Outcome.NO_VALID_BASELINE, Stop.EVALUATION_IO_FAILED);
            }
            if (evidence.status() != Status.SCORED_INPUT) {
                steps.add(step(0, 0, "BASELINE", null, null, fingerprint, evidence.status().name(),
                        evidence.diagnostics(), evidence.executionId(), null, false));
                if (evidence.status() == Status.NO_DEMAND) return result(Outcome.NO_DEMAND, Stop.NO_DEMAND);
                var stop = fatal(evidence.status());
                return result(Outcome.NO_VALID_BASELINE, stop == null ? Stop.NO_VALID_BASELINE : stop);
            }
            if (limits != null && evaluation.delivery(evidence.executionId()) == null) {
                steps.add(step(0, 0, "BASELINE", null, null, fingerprint, Status.OBJECTIVES_UNAVAILABLE.name(),
                        List.of("DELIVERY_EVIDENCE_MISSING"), evidence.executionId(), null, false));
                return result(Outcome.NO_VALID_BASELINE, Stop.NO_VALID_BASELINE);
            }
            var baselineAssessment = assessment(baselinePlan, evidence);
            var values = new TreeMap<String, BigDecimal>();
            registry.definitions().forEach(d -> values.put(d.metricId(), baselineAssessment.metrics().get(d.metricId()).value()));
            context = new ScoringContext(baselinePlan.problem(), registry, request.preference(), fingerprint, values);
            var individual = individual(baselinePlan, evidence);
            baseline = individual.best;
            best = baseline;
            checkpoint.save(best);
            population.add(individual);
            seen.add(fingerprint);
            steps.add(step(0, 0, "BASELINE", null, null, fingerprint, evidence.status().name(), evidence.diagnostics(),
                    evidence.executionId(), best.score.total(), true));

            if (heuristicSeed != null) {
                var stop = evaluatePlan(0, 1, "HEURISTIC_SEED", null, null,
                        new PlanSearchOperators.Proposal(heuristicSeed, List.of()), population);
                if (stop != null) return done(stop);
                if (!Status.SCORED_INPUT.name().equals(steps.get(steps.size() - 1).status()))
                    return done(Stop.INVALID_HEURISTIC_SEED);
            }
            checkpoint.prepared(best);
            var budget = request.budget();
            for (int generation = 1; generation <= budget.maxGenerations() && evaluation.launched() < budget.maxEvaluations(); generation++) {
                checkpoint.requireActive();
                if (Thread.currentThread().isInterrupted()) return done(Stop.INTERRUPTED);
                generations = generation;
                var stop = generation(generation);
                if (stop != null) return done(stop);
            }
            return done(evaluation.launched() >= budget.maxEvaluations() ? Stop.EVALUATION_BUDGET : Stop.GENERATION_BUDGET);
        }

        private Stop generation(int generation) throws IOException {
            var parents = List.copyOf(population);
            var children = new ArrayList<Individual>();
            var operators = generation == 1 ? FIRST : LATER;
            int offset = generation == 1 ? 0 : (generation - 2) * 2 % operators.size();
            int before = evaluation.launched();
            var budget = request.budget();
            for (int number = 1; number <= budget.proposalsPerGeneration()
                    && evaluation.launched() < budget.maxEvaluations()
                    && evaluation.launched() - before < budget.evaluationsPerGeneration(); number++) {
                if (Thread.currentThread().isInterrupted()) return Stop.INTERRUPTED;
                proposals++;
                var kind = operators.get((offset + number - 1) % operators.size());
                var parent = tournament(parents, random);
                Individual donor = kind == Kind.CROSSOVER && parents.size() > 1
                        ? tournament(parents.stream().filter(p -> p != parent).toList(), random) : null;
                var proposal = proposer.propose(kind, parent.best.plan, parent.summary,
                        donor == null ? null : donor.best.plan, donor == null ? null : donor.summary, random);
                var stop = evaluate(generation, number, kind, parent, donor, proposal, children);
                if (stop != null) return stop;
            }
            population.addAll(children);
            population.sort(GlobalPlanSearch::compare);
            if (population.size() > POPULATION_SIZE) population.subList(POPULATION_SIZE, population.size()).clear();
            return null;
        }

        private Stop evaluate(int generation, int number, Kind kind, Individual parent, Individual donor,
                              PlanSearchOperators.Proposal proposal, List<Individual> children) throws IOException {
            return evaluatePlan(generation, number, kind.name(), parent.best.planFingerprint,
                    donor == null ? null : donor.best.planFingerprint, proposal, children);
        }

        private Stop evaluatePlan(int generation, int number, String operator, String parentId, String donorId,
                                  PlanSearchOperators.Proposal proposal, List<Individual> children) throws IOException {
            var plan = proposal.plan();
            String child = plan == null ? null : plan.fingerprint();
            if (child == null || !seen.add(child)) {
                var reasons = new ArrayList<>(proposal.rejections());
                if (child != null) reasons.add("PLAN_FINGERPRINT_SEEN");
                steps.add(step(generation, number, operator, parentId, donorId, child,
                        child == null ? "REJECTED" : "DUPLICATE", reasons, null, null, false));
                return null;
            }
            checkpoint.requireActive();
            SandboxCandidateEvaluator.Attempt evidence;
            try {
                evidence = "HEURISTIC_SEED".equals(operator) ? evaluation.seed(plan) : evaluation.candidate(plan);
            } catch (IOException failure) {
                steps.add(step(generation, number, operator, parentId, donorId, child, Stop.EVALUATION_IO_FAILED.name(),
                        List.of("EVALUATION_IO_FAILED"), null, null, false));
                return Stop.EVALUATION_IO_FAILED;
            }
            var reasons = new ArrayList<>(proposal.rejections());
            reasons.addAll(evidence.diagnostics());
            BigDecimal total = null;
            boolean improved = false;
            if (evidence.status() == Status.SCORED_INPUT) {
                var individual = individual(plan, evidence);
                children.add(individual);
                total = individual.best.score.total();
                var retreatIssues = limits == null ? List.<String>of()
                        : limits.issues(evaluation.delivery(baseline.executionId()), evaluation.delivery(evidence.executionId()));
                reasons.addAll(retreatIssues);
                improved = retreatIssues.isEmpty() && total.compareTo(best.score.total()) < 0;
                if (improved) {
                    best = individual.best;
                    checkpoint.save(best);
                }
            }
            steps.add(step(generation, number, operator, parentId, donorId, child, evidence.status().name(),
                    reasons, evidence.executionId(), total, improved));
            return fatal(evidence.status());
        }

        private Individual individual(GlobalPlan plan, SandboxCandidateEvaluator.Attempt attempt) {
            var assessment = assessment(plan, attempt);
            var scoring = scorer.score(context, assessment);
            if (!scoring.eligible()) throw new IllegalStateException("G3 eligible evidence violates G0 scoring contract");
            return new Individual(new Best(plan, attempt.planFingerprint(), attempt.executionId(), assessment, scoring.score()),
                    attempt.evidence().summary());
        }

        private Step step(int generation, int number, String operator, String parent, String donor, String child,
                          String status, List<String> reasons, String executionId, BigDecimal total, boolean improved) {
            return new Step(generation, number, operator, parent, donor, child, status, reasons,
                    evaluation.launched(), executionId, total, improved);
        }

        private Result done(Stop stop) {
            return result(best == baseline ? Outcome.BASELINE_RETAINED : Outcome.IMPROVED, stop);
        }

        private Result result(Outcome outcome, Stop stop) {
            return new Result(request.artifactVersion(), baselinePlan.problem().problemFingerprint(), simulationSeed, request.searchSeed(),
                    request.preference().fingerprint(), request.budget(), outcome, stop,
                    stop != Stop.EVALUATION_BUDGET && stop != Stop.GENERATION_BUDGET
                            && stop != Stop.NO_VALID_BASELINE && stop != Stop.NO_DEMAND,
                    generations, proposals, evaluation.launched(), baseline, best, steps, System.nanoTime() - start);
        }
    }

    private static CandidateAssessment assessment(GlobalPlan plan, SandboxCandidateEvaluator.Attempt attempt) {
        var assessment = attempt.evidence().assessment();
        if (!plan.problem().equals(assessment.problem()) || !plan.fingerprint().equals(attempt.planFingerprint())
                || !attempt.planFingerprint().equals(assessment.planFingerprint()))
            throw new IllegalStateException("G3 evidence does not match candidate plan");
        return assessment;
    }

    private static Stop fatal(Status status) {
        return switch (status) {
            case WORKSPACE_BUSY -> Stop.WORKSPACE_BUSY;
            case EXECUTION_FAILED -> Stop.EXECUTION_FAILED;
            case TIMEOUT -> Stop.TIMEOUT;
            case INTERRUPTED -> Stop.INTERRUPTED;
            case INVALID_LEDGER -> Stop.INVALID_LEDGER;
            case WORKER_FAILED, SKIPPED -> Stop.WORKER_FAILED;
            case SCORED_INPUT, NO_DEMAND, INVALID_PLAN, OBJECTIVES_UNAVAILABLE -> null;
        };
    }

    private static Individual tournament(List<Individual> parents, SplittableRandom random) {
        int first = random.nextInt(parents.size());
        if (parents.size() == 1) return parents.get(first);
        int second = random.nextInt(parents.size() - 1);
        if (second >= first) second++;
        var a = parents.get(first);
        var b = parents.get(second);
        return compare(a, b) <= 0 ? a : b;
    }

    private static int compare(Individual left, Individual right) {
        return ScoreBreakdown.compare(left.best.score, right.best.score);
    }

    private static void write(Path path, Object value) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            Files.writeString(path, OptimizationJson.write(value), StandardOpenOption.CREATE_NEW);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
