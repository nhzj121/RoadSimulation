package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

import static org.example.roadsimulation.sandbox.optimization.PlanSearchOperators.Kind;
import static org.example.roadsimulation.sandbox.optimization.SandboxCandidateEvaluator.Status;

/** Bounded, serial search. Only G3 evidence with a complete G0 score enters the population. */
public final class GlobalPlanSearch {
    public static final String VERSION = "sandbox-global-plan-search/v1";
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
            ContractChecks.version(artifactVersion, VERSION);
            ContractChecks.text(runSpecKey, "runSpecKey");
            ContractChecks.text(baselineResource, "baselineResource");
            if (runSpecRevision <= 0 || (timeoutSeconds != null && timeoutSeconds <= 0))
                throw new IllegalArgumentException("Invalid run revision or timeout");
            Objects.requireNonNull(preference).validate(ObjectiveRegistry.standard());
            Objects.requireNonNull(budget);
        }
    }

    public enum Outcome { IMPROVED, BASELINE_RETAINED, NO_VALID_BASELINE, NO_DEMAND }
    public enum Stop {
        EVALUATION_BUDGET, GENERATION_BUDGET, NO_VALID_BASELINE, NO_DEMAND, WORKSPACE_BUSY,
        EXECUTION_FAILED, TIMEOUT, INTERRUPTED, INVALID_LEDGER, WORKER_FAILED, EVALUATION_IO_FAILED
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
        int launched();
    }

    @FunctionalInterface
    interface Checkpoint { void save(Best best) throws IOException; }

    @FunctionalInterface
    interface Proposer {
        PlanSearchOperators.Proposal propose(Kind kind, GlobalPlan parent, PlanExecutionSummary summary,
                GlobalPlan donor, PlanExecutionSummary donorSummary, SplittableRandom random);
    }

    private record Individual(Best best, PlanExecutionSummary summary) {}

    private GlobalPlanSearch() {}

    public static Result execute(SandboxManagementSettings settings, Request request, Path output) throws IOException {
        long start = System.nanoTime();
        var session = SandboxCandidateEvaluator.open(settings, request.runSpecKey(), request.runSpecRevision(),
                request.baselineResource(), output, request.timeoutSeconds());
        var evaluation = new Evaluation() {
            public SandboxCandidateEvaluator.Attempt baseline() throws IOException {
                return session.evaluateBaseline(SandboxCandidateEvaluator.CacheMode.USE);
            }
            public SandboxCandidateEvaluator.Attempt candidate(GlobalPlan plan) throws IOException {
                return session.evaluateCandidate(plan, SandboxCandidateEvaluator.CacheMode.USE);
            }
            public int launched() { return session.launchedWorkers(); }
        };
        var operators = new PlanSearchOperators(session.problem(), session.fleetData());
        int[] index = {0};
        var result = search(request, session.simulationRootSeed(), session.baseline().plan(), evaluation, operators::propose,
                best -> write(output.resolve("best-" + index[0]++ + ".json"), best), start);
        write(output.resolve("search.json"), result);
        // G3 records every evaluation and also supplies its own deterministic tie ordering for comparison reports.
        if (result.steps().stream().noneMatch(step -> Stop.EVALUATION_IO_FAILED.name().equals(step.status())))
            write(output.resolve("evaluation.json"), session.report(request.preference()));
        return result;
    }

    static Result search(Request request, String simulationSeed, GlobalPlan baselinePlan, Evaluation evaluation,
                         Proposer proposer, Checkpoint checkpoint, long start) throws IOException {
        var run = new SearchRun(request, simulationSeed, baselinePlan, evaluation, proposer, checkpoint, start);
        return run.run();
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
        private final ObjectiveRegistry registry = ObjectiveRegistry.standard();
        private final PreferenceScorer scorer = new PreferenceScorer();
        private final Set<String> seen = new HashSet<>();
        private final List<Step> steps = new ArrayList<>();
        private final List<Individual> population = new ArrayList<>();
        private Best baseline, best;
        private ScoringContext context;
        private int generations, proposals;

        SearchRun(Request request, String simulationSeed, GlobalPlan baselinePlan, Evaluation evaluation,
                  Proposer proposer, Checkpoint checkpoint, long start) {
            this.request = request;
            this.simulationSeed = simulationSeed;
            this.baselinePlan = baselinePlan;
            this.evaluation = evaluation;
            this.proposer = proposer;
            this.checkpoint = checkpoint;
            this.start = start;
            random = new SplittableRandom(request.searchSeed());
        }

        Result run() throws IOException {
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

            var budget = request.budget();
            for (int generation = 1; generation <= budget.maxGenerations() && evaluation.launched() < budget.maxEvaluations(); generation++) {
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
            String parentId = parent.best.planFingerprint;
            String donorId = donor == null ? null : donor.best.planFingerprint;
            var plan = proposal.plan();
            String child = plan == null ? null : plan.fingerprint();
            if (child == null || !seen.add(child)) {
                var reasons = new ArrayList<>(proposal.rejections());
                if (child != null) reasons.add("PLAN_FINGERPRINT_SEEN");
                steps.add(step(generation, number, kind.name(), parentId, donorId, child,
                        child == null ? "REJECTED" : "DUPLICATE", reasons, null, null, false));
                return null;
            }
            SandboxCandidateEvaluator.Attempt evidence;
            try {
                evidence = evaluation.candidate(plan);
            } catch (IOException failure) {
                steps.add(step(generation, number, kind.name(), parentId, donorId, child, Stop.EVALUATION_IO_FAILED.name(),
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
                improved = total.compareTo(best.score.total()) < 0;
                if (improved) {
                    best = individual.best;
                    checkpoint.save(best);
                }
            }
            steps.add(step(generation, number, kind.name(), parentId, donorId, child, evidence.status().name(),
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
            return new Result(VERSION, baselinePlan.problem().problemFingerprint(), simulationSeed, request.searchSeed(),
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
