package org.example.roadsimulation.sandbox.optimization;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

/** Isolated CLI for the current, timed whole-trip, and cargo insertion variants. */
public final class GaOperatorExperimentCli {
    enum Variant { A, B, C }
    private static final GlobalPlan.TaskKey DIAGNOSTIC_TASK = new GlobalPlan.TaskKey(0, 2, 5, 6, "input", 0);
    private static final String BASELINE = "classpath:sandbox/baseline/baseline-v1.json";
    private static final int DIAGNOSTIC_BUDGET = 8;

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !(args[0].equals("run") || args[0].equals("diagnose")))
            throw new IllegalArgumentException("Expected run|diagnose OUTPUT_DIRECTORY HISTORICAL_SEEDED_DIRECTORY");
        if (args[0].equals("run")) GlobalPlanSearch.requireLegacyCliEnabled();
        var settings = new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"), System.getenv("SANDBOX_DB_USER"),
                System.getenv("SANDBOX_DB_PASSWORD"));
        var experiment = new GaOperatorExperimentCli(settings, Path.of(args[1]).toAbsolutePath(), Path.of(args[2]));
        try {
            experiment.diagnose();
            if (args[0].equals("run")) experiment.compare();
        } catch (Exception failure) {
            String code = failure instanceof SandboxWorkspaceException sandbox ? sandbox.errorCode() : failure.getClass().getSimpleName();
            write(experiment.output.resolve("failure.json"), Map.of("code", code, "reason", Objects.toString(failure.getMessage(), "")));
            throw failure;
        }
    }

    private final SandboxManagementSettings settings;
    private final Path output;
    private final Path historical;
    private final Map<Integer, String> frozenSeeds = new HashMap<>();
    private final List<GaOperatorExperimentReport.Row> rows = new ArrayList<>();

    private GaOperatorExperimentCli(SandboxManagementSettings settings, Path output, Path historical) throws IOException {
        this.settings = settings;
        this.output = output;
        this.historical = historical;
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
    }

    private SandboxCandidateEvaluator open(Path path, int horizon) throws IOException {
        return SandboxCandidateEvaluator.open(settings, horizon == 48 ? "demo-v1-a-reference" : "demo-v1-a-reference-ga",
                horizon == 48 ? 1 : 2, BASELINE, path, SandboxOptimizationProfile.workerTimeoutSeconds(horizon),
                SandboxCandidateEvaluator.THROUGHPUT_VERSION);
    }

    private void compare() throws IOException {
        paired(48, 20261109L, List.of(Variant.values()));
        if (wins(48, Variant.B, Variant.A) + wins(48, Variant.C, Variant.A) == 0) {
            replay(row(48, 20261109L, Variant.A));
            GaOperatorExperimentReport.summary(output, rows, "首轮两个改动组均未严格改善。按实验条件结束。");
            return;
        }
        paired(48, 20261110L, List.of(Variant.values()));
        paired(48, 20261111L, List.of(Variant.values()));
        long bWins = wins(48, Variant.B, Variant.A);
        long cWins = wins(48, Variant.C, Variant.A);
        Variant preferred = cWins > bWins || (cWins == bWins && wins(48, Variant.C, Variant.B) >= 2) ? Variant.C : Variant.B;
        if (bWins >= 2 && cWins >= 2) preferred = wins(48, Variant.C, Variant.B) >= 2 ? Variant.C : Variant.B;
        paired(50, 20261109L, List.of(Variant.A, preferred));
        var winner = row(50, 20261109L, preferred);
        replay(winner);
        boolean eligible = wins(48, preferred, Variant.A) >= 2
                && winner.score().compareTo(row(50, 20261109L, Variant.A).score()) <= 0;
        String verdict = eligible ? "达到后续 G6-B 版本验收条件，推荐 " + preferred
                : "未达到后续 G6-B 版本验收条件，继续使用当前配置。";
        GaOperatorExperimentReport.summary(output, rows, verdict);
    }

    private void replay(GaOperatorExperimentReport.Row row) throws IOException {
        var selected = read(row.directory().resolve("search.json"), GlobalPlanSearch.Result.class).selected();
        var replayPath = output.resolve("independent-replay");
        var replay = open(replayPath, row.horizon()).replayPlan(selected.plan());
        if (replay.status() != SandboxCandidateEvaluator.Status.SCORED_INPUT || replay.evidence().mapRequests() != 0)
            throw new IOException("Independent replay failed: " + replay.diagnostics());
        GaOperatorExperimentReport.verifyReplay(settings, selected, replay, replayPath);
    }

    private long wins(int horizon, Variant challenger, Variant reference) {
        return rows.stream().filter(r -> r.horizon() == horizon && r.variant() == challenger)
                .filter(r -> r.score().compareTo(row(horizon, r.seed(), reference).score()) < 0).count();
    }

    private GaOperatorExperimentReport.Row row(int horizon, long seed, Variant variant) {
        return rows.stream().filter(r -> r.horizon() == horizon && r.seed() == seed && r.variant() == variant).findFirst().orElseThrow();
    }

    private void paired(int horizon, long searchSeed, List<Variant> variants) throws IOException {
        for (var variant : variants) {
            Path path = output.resolve(horizon + "-" + searchSeed + "-" + variant);
            var session = open(path, horizon);
            var preference = SandboxOptimizationProfile.preferenceFor(session.problem().catalog());
            var acquisition = session.acquireHeuristicSeed(output.resolve("seed-cache"));
            var seed = acquisition.seed().plan();
            var baseline = session.baseline().plan();
            var startFingerprint = OptimizationJson.fingerprint(List.of(baseline.fingerprint(), seed.fingerprint()));
            if (!Objects.equals(frozenSeeds.computeIfAbsent(horizon, key -> startFingerprint), startFingerprint))
                throw new IOException("Seed plans differ within the same frozen scenario");
            if (horizon == 48) requireHistoricalSeeds(baseline, seed);
            var request = new GlobalPlanSearch.Request(GlobalPlanSearch.THROUGHPUT_VERSION, horizon == 48
                    ? "demo-v1-a-reference" : "demo-v1-a-reference-ga", horizon == 48 ? 1 : 2, BASELINE, preference,
                    searchSeed, SandboxOptimizationProfile.BUDGET, session.workerTimeoutSeconds());
            inputs(path, session, Map.of("request", request, "limits", SandboxOptimizationProfile.LIMITS,
                    "variant", variant, "heuristic", acquisition));
            var operators = new PlanSearchOperators(session.problem(), session.fleetData());
            var ranking = new TimedMoveRanking(session, preference, operators);
            var evaluation = new GlobalPlanSearch.Evaluation() {
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
                public FulfillmentComparison.Side delivery(String id) { return session.delivery(id); }
            };
            long[] generationNanos = {0};
            int[] proposalNumber = {0};
            GlobalPlanSearch.Result result;
            try (var placements = Files.newBufferedWriter(path.resolve("placements.jsonl"), StandardOpenOption.CREATE_NEW)) {
                GlobalPlanSearch.Proposer proposer = variant == Variant.A ? operators::propose
                        : ranking.proposer(variant == Variant.C, trace -> {
                            try {
                                line(placements, Map.of("proposal", proposalNumber[0], "parent", trace.parent(),
                                        "source", trace.source(), "ranking", trace.ranking()));
                            } catch (IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        });
                result = GlobalPlanSearch.search(request, session.simulationRootSeed(), baseline, evaluation,
                        (kind, parent, summary, donor, donorSummary, random) -> {
                            long started = System.nanoTime();
                            ++proposalNumber[0];
                            try {
                                return proposer.propose(kind, parent, summary, donor, donorSummary, random);
                            } finally {
                                generationNanos[0] += System.nanoTime() - started;
                            }
                        }, best -> write(path.resolve("best-" + best.planFingerprint() + ".json"), best), System.nanoTime(),
                        SandboxOptimizationProfile.LIMITS, seed);
            }
            write(path.resolve("search.json"), result);
            var report = session.report(preference, result);
            write(path.resolve("evaluation.json"), report);
            if (result.selected() == null || result.stop() != GlobalPlanSearch.Stop.EVALUATION_BUDGET
                    && result.stop() != GlobalPlanSearch.Stop.GENERATION_BUDGET)
                throw new IOException("Incomplete comparison group " + path.getFileName() + ": " + result.stop());
            var fulfillment = session.fulfillment(result.baseline().executionId(), result.selected().executionId());
            write(path.resolve("fulfillment.json"), fulfillment);
            rows.add(GaOperatorExperimentReport.collect(settings, path, horizon, searchSeed, variant, report, result, fulfillment,
                    ranking, generationNanos[0]));
            GaOperatorExperimentReport.summary(output, rows, "对照实验进行中");
            System.out.printf("%s score=%s evaluations=%d%n", path.getFileName(), result.selected().score().total(), result.launchedEvaluations());
        }
    }

    private void diagnose() throws IOException {
        Path path = output.resolve("diagnostic");
        var session = open(path, 48);
        var preference = SandboxOptimizationProfile.preferenceFor(session.problem().catalog());
        var parent = historicalPlan(3, session);
        var oldMove = historicalPlan(7, session);
        var operators = new PlanSearchOperators(session.problem(), session.fleetData());
        var ranker = new TimedMoveRanking(session, preference, operators);
        var vehicle = parent.vehicles().stream().filter(v -> v.vehicleId() == 22).findFirst().orElseThrow();
        int sourceIndex = 0;
        while (vehicle.trips().get(sourceIndex).nodes().stream().noneMatch(n -> n.task().equals(DIAGNOSTIC_TASK))) sourceIndex++;
        var source = new TripKey(vehicle.vehicleId(), sourceIndex);
        var selection = ranker.rank(parent, source, false);
        var byDistance = selection.ranking().placements().stream().filter(r -> r.timeRank() != null)
                .sorted(Comparator.comparing(TimedMoveRanking.Ranked::distanceRank)).toList();
        var samples = new LinkedHashMap<String, GlobalPlan>();
        add(samples, parent);
        add(samples, oldMove);
        byDistance.stream().limit(2).forEach(r -> add(samples, selection.orderedPlans().get(r.timeRank() - 1)));
        selection.orderedPlans().stream().limit(2).forEach(plan -> add(samples, plan));
        byDistance.stream().filter(r -> r.timeRank() > selection.orderedPlans().size() / 2).findFirst()
                .ifPresent(r -> add(samples, selection.orderedPlans().get(r.timeRank() - 1)));
        if (samples.size() + 1 > DIAGNOSTIC_BUDGET) throw new IllegalStateException("Diagnostic budget exceeded");
        inputs(path, session, Map.of("parent", parent.fingerprint(), "oldMove", oldMove.fingerprint(),
                "source", source, "task", DIAGNOSTIC_TASK, "maxEvaluations", DIAGNOSTIC_BUDGET, "preference", preference,
                "limits", SandboxOptimizationProfile.LIMITS));
        write(path.resolve("diagnostic-selection.json"), samples.keySet());
        try (var placements = Files.newBufferedWriter(path.resolve("placements.jsonl"), StandardOpenOption.CREATE_NEW)) {
            line(placements, selection.ranking());
        }
        var baseline = session.evaluateBaseline(SandboxCandidateEvaluator.CacheMode.USE);
        requireCompleted(baseline);
        for (var plan : samples.values()) {
            var attempt = session.evaluateCandidate(plan, SandboxCandidateEvaluator.CacheMode.USE);
            requireCompleted(attempt);
            System.out.printf("diagnostic evaluation=%d execution=%s%n", session.launchedWorkers(), attempt.executionId());
        }
        var report = session.report(preference);
        write(path.resolve("evaluation.json"), report);
        GaOperatorExperimentReport.diagnostic(settings, path, session, report, samples, parent, oldMove, ranker, DIAGNOSTIC_TASK);
    }

    private GlobalPlan historicalPlan(int attempt, SandboxCandidateEvaluator session) throws IOException {
        Path path = historical.resolve("attempt-" + attempt);
        var task = read(path.resolve("work.json"), org.example.roadsimulation.sandbox.cli.SandboxEvaluationWorker.Work.class).task();
        var recorded = read(path.resolve("result.json"), SandboxCandidateEvaluator.Attempt.class).evidence();
        GaOperatorExperimentReport.requireSource(settings, recorded, task);
        if (!task.compiledProblem().problem().equals(session.problem().problem())
                || !recorded.assessment().planFingerprint().equals(task.plan().fingerprint()))
            throw new IOException("Historical diagnostic plan no longer matches the frozen problem");
        return task.plan();
    }

    private void requireHistoricalSeeds(GlobalPlan baseline, GlobalPlan seed) throws IOException {
        var historicalSearch = read(historical.resolve("search.json"), GlobalPlanSearch.Result.class);
        var historicalSeed = historicalSearch.steps().stream().filter(step -> step.operator().equals("HEURISTIC_SEED"))
                .findFirst().orElseThrow();
        if (!baseline.fingerprint().equals(historicalSearch.baseline().planFingerprint())
                || !seed.fingerprint().equals(historicalSeed.child()))
            throw new IOException("48-round seed or baseline differs from historical frozen inputs");
    }

    private static void requireCompleted(SandboxCandidateEvaluator.Attempt attempt) throws IOException {
        if (attempt.status() != SandboxCandidateEvaluator.Status.SCORED_INPUT || attempt.evidence().mapRequests() != 0)
            throw new IOException("Diagnostic run failed: " + attempt.status() + " " + attempt.diagnostics());
    }

    private static void add(Map<String, GlobalPlan> plans, GlobalPlan plan) { plans.putIfAbsent(plan.fingerprint(), plan); }

    private static void inputs(Path path, SandboxCandidateEvaluator session, Map<String, ?> config) throws IOException {
        write(path.resolve("inputs.json"), Map.of("artifactVersion", "ga-operator-experiment/v1", "config", config,
                "build", EvaluationBuildIdentity.capture(org.example.roadsimulation.sandbox.cli.SandboxEvaluationWorker.class),
                "problem", session.problem(), "baselinePlan", session.baseline().plan(),
                "baselineSourceSha256", EvaluationBuildIdentity.fileSha256(path.resolve("baseline-source.json")),
                "timeoutSeconds", session.workerTimeoutSeconds(), "simulationRootSeed", session.simulationRootSeed()));
    }

    static <T> T read(Path path, Class<T> type) throws IOException { return OptimizationJson.read(Files.readString(path), type); }
    static void write(Path path, Object value) throws IOException {
        Files.writeString(path, OptimizationJson.write(value), StandardOpenOption.CREATE_NEW);
    }
    static void line(BufferedWriter writer, Object value) throws IOException {
        writer.write(OptimizationJson.write(value));
        writer.newLine();
        writer.flush();
    }
}
