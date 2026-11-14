package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.cli.SandboxSphericalComparisonWorker;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** G6-A experiment orchestration. All business evidence comes from independently verified executions. */
public final class FormalSandboxComparison {
    public static final String VERSION = "sandbox-formal-comparison/v1";
    private static final String A = "demo-v1-a-reference";
    private static final String B = "demo-v1-b-strategy";
    private static final String ROOT_SEED = "20260927";
    private static final int REVISION = 1;
    private static final long TIMEOUT_SECONDS = 300;
    private static final long CALIBRATION_SEED = 20261112;
    private static final List<Long> SEEDS = List.of(20261109L, 20261110L, 20261111L);
    private static final GlobalPlanSearch.Budget BUDGET = new GlobalPlanSearch.Budget(8, 8, 2, 16);
    private static final List<BigDecimal> GRID = List.of("0", "0.02", "0.04", "0.06", "0.08")
            .stream().map(BigDecimal::new).toList();

    public record HistoricalEvidence(String baselineExecutionId, List<String> candidateExecutionIds) {
        public HistoricalEvidence {
            ContractChecks.text(baselineExecutionId, "historical baseline execution ID");
            candidateExecutionIds = ContractChecks.list(candidateExecutionIds, "historical candidate execution IDs");
            if (candidateExecutionIds.isEmpty()) throw new IllegalArgumentException("Historical candidates are required");
        }
    }

    public record Request(String artifactVersion, String baselineResource, DeliveryRetreatLimits approvedLimits,
                          HistoricalEvidence historicalEvidence) {
        public Request {
            ContractChecks.version(artifactVersion, VERSION);
            ContractChecks.text(baselineResource, "baselineResource");
            if (!baselineResource.startsWith("classpath:") && !baselineResource.startsWith("file:"))
                throw new IllegalArgumentException("Expected a local baseline resource");
        }
    }

    record Frozen(String commonInputFingerprint, String baselineSha256,
                  SandboxRunSpecificationRevisionV2 reference, SandboxRunSpecificationRevisionV2 heuristic,
                  CompiledOptimizationProblem referenceProblem, CompiledOptimizationProblem heuristicProblem,
                  PreferenceProfile preference, SphericalComparisonRun comparison, EvaluationBuildIdentity build) {}

    record CalibrationCase(String executionId, String planFingerprint, ScoreBreakdown score,
                           FulfillmentComparison.Side delivery, BigDecimal emptyMileageImprovementPoints,
                           BigDecimal cumulativeDeltaTonnes, BigDecimal terminalDeltaTonnes,
                           BigDecimal cumulativeDeltaPercent, BigDecimal terminalDeltaPercent) {}
    record GridChoice(DeliveryRetreatLimits limits, String selectedExecutionId, boolean improved) {}
    record CalibrationGroup(FulfillmentReplay.Result evidence, List<CalibrationCase> candidates, List<GridChoice> grid) {}
    record CalibrationReport(String artifactVersion, String commonInputFingerprint, long searchSeed,
                             int launchedEvaluations, CalibrationGroup historical, CalibrationGroup current) {}
    record SeedResult(long seed, int launchedEvaluations, long elapsedNanos, GlobalPlanSearch.Stop stop,
                      GlobalPlanSearch.Best baseline, GlobalPlanSearch.Best selected,
                      FulfillmentComparison.Report fulfillment) {}
    record Difference(Map<String, BigDecimal> objectiveDeltas, BigDecimal releasedDeltaTonnes,
                      BigDecimal terminalDeltaTonnes, BigDecimal cumulativeDeltaPercent, BigDecimal terminalDeltaPercent) {}
    record ComparisonReport(String artifactVersion, String experimentFingerprint, Frozen frozen,
                            DeliveryRetreatLimits approvedLimits, List<SeedResult> searches,
                            SphericalComparisonEvidence.Side heuristic, SphericalComparisonEvidence.Side greedy,
                            SphericalComparisonEvidence.Side selected, GlobalPlanSearch.Best selectedPlan,
                            FulfillmentComparison.Report fulfillment, SphericalComparisonEvidence.Side replay,
                            Difference heuristicMinusGreedy, Difference selectedMinusGreedy) {}

    private final SandboxManagementSettings settings;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SandboxExecutionStore store;

    public FormalSandboxComparison(SandboxManagementSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        store = new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json);
    }

    public void calibrate(Request request, Path output, Path completedSearch) throws IOException {
        if (request.historicalEvidence() == null)
            throw new IllegalArgumentException("Calibration requires G5 historical execution IDs");
        if (completedSearch == null) GlobalPlanSearch.requireLegacyCliEnabled();
        output = Files.createDirectory(output.toAbsolutePath().normalize());
        Path baseline = freezeBaseline(request, output);
        var frozen = preflight(baseline);
        write(output.resolve("inputs.json"), frozen);
        var historical = request.historicalEvidence();
        var historicalResult = replay(frozen, historical.baselineExecutionId(), historical.candidateExecutionIds());
        var historicalGroup = calibrationGroup(historicalResult);
        write(output.resolve("historical.json"), historicalGroup);
        var searchRequest = searchRequest(frozen, baseline, CALIBRATION_SEED);
        if (completedSearch == null) {
            completedSearch = output.resolve("search");
            GlobalPlanSearch.execute(settings, searchRequest, completedSearch);
        }
        var result = read(completedSearch.resolve("search.json"), GlobalPlanSearch.Result.class);
        requireSearch(result, searchRequest, frozen);
        var evaluation = read(completedSearch.resolve("evaluation.json"), SandboxCandidateEvaluator.Manifest.class);
        var attempts = evaluation.candidates().stream()
                .filter(attempt -> attempt.status() == SandboxCandidateEvaluator.Status.SCORED_INPUT).toList();
        var candidateIds = attempts.stream().map(SandboxCandidateEvaluator.Attempt::executionId).toList();
        var expectedIds = result.steps().stream()
                .filter(step -> SandboxCandidateEvaluator.Status.SCORED_INPUT.name().equals(step.status()))
                .map(GlobalPlanSearch.Step::executionId).toList();
        if (!expectedIds.equals(concat(result.baseline().executionId(), candidateIds))
                || !evaluation.problem().equals(frozen.referenceProblem())
                || !evaluation.baselineSourceFingerprint().equals(frozen.baselineSha256()))
            throw new IllegalArgumentException("Calibration search evidence does not match the frozen request");
        var current = replay(frozen, result.baseline().executionId(), candidateIds);
        requireAttempt(evaluation.baselineAttempt(), current.baseline());
        for (int i = 0; i < attempts.size(); i++) requireAttempt(attempts.get(i), current.candidates().get(i));
        write(output.resolve("calibration.json"), new CalibrationReport(VERSION, frozen.commonInputFingerprint(),
                CALIBRATION_SEED, result.launchedEvaluations(), historicalGroup, calibrationGroup(current)));
    }

    public void compare(Request request, Path output) throws Exception {
        if (request.approvedLimits() == null)
            throw new IllegalArgumentException("Formal comparison requires both approved delivery retreat limits");
        GlobalPlanSearch.requireLegacyCliEnabled();
        output = Files.createDirectory(output.toAbsolutePath().normalize());
        try {
            comparePrepared(request, output);
        } catch (Exception failure) {
            try { write(output.resolve("comparison-failed.json"), Map.of("status", "FAILED",
                    "error", failure.getClass().getSimpleName())); }
            catch (IOException writing) { failure.addSuppressed(writing); }
            throw failure;
        }
    }

    private void comparePrepared(Request request, Path output) throws Exception {
        Path baseline = freezeBaseline(request, output);
        var frozen = preflight(baseline);
        String identity = OptimizationJson.fingerprint(List.of(VERSION, frozen, request.approvedLimits(),
                BUDGET, TIMEOUT_SECONDS, SEEDS));
        write(output.resolve("inputs.json"), Map.of("experimentFingerprint", identity, "inputs", frozen,
                "approvedLimits", request.approvedLimits(), "seeds", SEEDS, "budget", BUDGET,
                "timeoutSeconds", TIMEOUT_SECONDS));
        var heuristic = executeHeuristic(frozen, baseline, output.resolve("heuristic"));
        var results = new ArrayList<SeedResult>();
        for (long seed : SEEDS) {
            var search = searchRequest(frozen, baseline, seed);
            var directory = output.resolve("search-" + seed);
            var result = GlobalPlanSearch.execute(settings, search, directory, request.approvedLimits());
            requireSearch(result, search, frozen);
            var fulfillment = read(directory.resolve("fulfillment.json"), FulfillmentComparison.Report.class);
            if (!request.approvedLimits().issues(fulfillment.baseline(), fulfillment.selected()).isEmpty())
                throw new IllegalArgumentException("Selected plan violates the frozen delivery limits");
            results.add(new SeedResult(seed, result.launchedEvaluations(), result.elapsedNanos(), result.stop(),
                    result.baseline(), result.selected(), fulfillment));
        }

        var reference = results.get(0);
        var referenceExport = store.export(reference.baseline().executionId());
        var greedy = SphericalComparisonEvidence.plan("GREEDY", referenceExport, task(referenceExport),
                baselineElapsed(output, reference.seed()), json);
        for (var result : results.subList(1, results.size())) {
            var export = store.export(result.baseline().executionId());
            SphericalComparisonEvidence.requireReplay(referenceExport, export, json);
            if (result.baseline().score().total().compareTo(reference.baseline().score().total()) != 0)
                throw new IllegalArgumentException("Greedy baseline scores differ across seeds");
        }

        var winningRun = results.stream().filter(result -> result.selected().score().total().compareTo(reference.baseline().score().total()) < 0)
                .min(Comparator.comparing((SeedResult result) -> result.selected().score().total())
                        .thenComparing(result -> result.selected().planFingerprint())).orElse(reference);
        var selected = winningRun.selected();
        var selectedExport = selected.executionId().equals(reference.baseline().executionId())
                ? referenceExport : store.export(selected.executionId());
        var task = task(selectedExport);
        boolean improved = selected.score().total().compareTo(reference.baseline().score().total()) < 0;
        var selectedSide = SphericalComparisonEvidence.plan(improved ? "GLOBAL_GA" : "GREEDY_RETAINED",
                selectedExport, task, selectedElapsed(output, winningRun), json);

        var session = SandboxCandidateEvaluator.open(settings, A, REVISION, baseline.toUri().toString(),
                output.resolve("replay"), TIMEOUT_SECONDS, SandboxCandidateEvaluator.THROUGHPUT_VERSION);
        var attempt = session.replayPlan(selected.plan());
        if (attempt.status() != SandboxCandidateEvaluator.Status.SCORED_INPUT)
            throw new IllegalArgumentException("Independent replay failed: " + attempt.status() + " " + attempt.diagnostics());
        var replayExport = store.export(attempt.executionId());
        SphericalComparisonEvidence.requireReplay(selectedExport, replayExport, json);
        var replay = SphericalComparisonEvidence.plan("REPLAY", replayExport, task, attempt.timing().totalNanos(), json);
        if (selectedSide.delivery().deliveredTonnes().compareTo(replay.delivery().deliveredTonnes()) != 0
                || selectedSide.delivery().terminalDeliveredTonnes().compareTo(replay.delivery().terminalDeliveredTonnes()) != 0
                || !request.approvedLimits().issues(greedy.delivery(), replay.delivery()).isEmpty())
            throw new IllegalArgumentException("Replay delivery does not match the selected plan");
        write(output.resolve("comparison.json"), new ComparisonReport(VERSION, identity, frozen, request.approvedLimits(),
                results, heuristic, greedy, selectedSide, selected, winningRun.fulfillment(), replay,
                difference(greedy, heuristic), difference(greedy, selectedSide)));
    }

    Frozen preflight(Path baseline) throws IOException {
        var runs = new SandboxRunSpecificationStore(settings.url(), settings.user(), settings.password(), json);
        var a = runs.loadRevisionV2(A, REVISION);
        var b = runs.loadRevisionV2(B, REVISION);
        var reference = a.specification().scenario();
        var clock = a.specification().simulationClock();
        if (!"demo-v1-full".equals(reference.scenarioKey()) || reference.revision() != REVISION
                || !reference.equals(b.specification().scenario()) || clock.totalLoops() != 48
                || clock.tickDurationSeconds() != 1800 || !ROOT_SEED.equals(a.specification().random().rootSeed())
                || !"ORIGINAL".equals(a.specification().dispatch().strategy())
                || !"HEURISTIC".equals(b.specification().dispatch().strategy()))
            throw new IllegalArgumentException("Unexpected formal comparison references or run settings");
        var scene = new SandboxScenarioStore(settings.url(), settings.user(), settings.password(), json)
                .loadRevision(reference.scenarioKey(), reference.revision());
        var source = new FileSystemResource(baseline);
        var left = SphericalComparisonRun.compile(source, scene, a);
        var right = SphericalComparisonRun.compile(source, scene, b);
        String identity = SphericalComparisonRun.commonIdentity(a, left, json);
        if (!identity.equals(SphericalComparisonRun.commonIdentity(b, right, json)))
            throw new IllegalArgumentException("A/B common frozen inputs differ");
        var build = EvaluationBuildIdentity.capture(SandboxSphericalComparisonWorker.class);
        var path = new SphericalComparisonRun(SphericalComparisonRun.VERSION, identity, right.paths(), build);
        var scale = left.catalog().tasks().stream().map(ProductionTaskCatalog.Task::weightTonnes)
                .reduce(BigDecimal.ZERO, BigDecimal::add).movePointLeft(2);
        return new Frozen(identity, EvaluationBuildIdentity.fileSha256(baseline), a, b, left, right,
                preference(scale), path, build);
    }

    private static PreferenceProfile preference(BigDecimal scale) {
        var objectives = new TreeMap<String, PreferenceProfile.ObjectivePreference>();
        for (var objective : ObjectiveRegistry.throughput().definitions()) {
            String id = objective.metricId();
            boolean delivery = "cargoDeliveredTonnes".equals(id);
            var share = BigDecimal.valueOf(delivery ? 2 : ObjectiveRegistry.PRIMARY.equals(id) ? 0 : 1);
            objectives.put(id, new PreferenceProfile.ObjectivePreference(objective.unit(),
                    delivery ? scale : new BigDecimal("0.01"), share));
        }
        return new PreferenceProfile(PreferenceProfile.VERSION, objectives, BigDecimal.valueOf(4), BigDecimal.valueOf(4),
                new BigDecimal("0.01"), BigDecimal.valueOf(2));
    }

    private static GlobalPlanSearch.Request searchRequest(Frozen frozen, Path baseline, long seed) {
        return new GlobalPlanSearch.Request(GlobalPlanSearch.THROUGHPUT_VERSION, A, REVISION, baseline.toUri().toString(),
                frozen.preference(), seed, BUDGET, TIMEOUT_SECONDS);
    }

    private static void requireSearch(GlobalPlanSearch.Result result, GlobalPlanSearch.Request request, Frozen frozen) {
        if (result.interrupted() || result.baseline() == null || result.selected() == null)
            throw new IllegalArgumentException("Search did not complete with a verified baseline: " + result.stop());
        if (!result.artifactVersion().equals(request.artifactVersion()) || result.searchSeed() != request.searchSeed()
                || !result.budget().equals(request.budget()) || result.launchedEvaluations() > BUDGET.maxEvaluations()
                || !frozen.referenceProblem().problem().problemFingerprint().equals(result.problemFingerprint())
                || !frozen.preference().fingerprint().equals(result.preferenceFingerprint())
                || !ROOT_SEED.equals(result.simulationRootSeed()))
            throw new IllegalArgumentException("Search evidence does not match the experiment configuration");
    }

    private FulfillmentReplay.Result replay(Frozen frozen, String baseline, List<String> candidates) {
        var profile = new FulfillmentReplay.Profile("g6-experiment", SandboxCandidateEvaluator.THROUGHPUT_VERSION, frozen.preference());
        var request = new FulfillmentReplay.Request(FulfillmentReplay.VERSION, baseline, candidates, List.of(profile));
        var result = FulfillmentReplay.execute(settings, request);
        if (!frozen.referenceProblem().problem().problemFingerprint().equals(result.problemFingerprint()))
            throw new IllegalArgumentException("Replay evidence belongs to a different frozen reference");
        return result;
    }

    private static CalibrationGroup calibrationGroup(FulfillmentReplay.Result evidence) {
        var ranking = evidence.rankings().get(0);
        var scores = ranking.ranking().stream().collect(Collectors.toMap(FulfillmentReplay.Ranked::executionId,
                entry -> entry));
        var baselineScore = scores.get(evidence.baseline().executionId());
        if (baselineScore == null || baselineScore.score() == null)
            throw new IllegalArgumentException("Calibration baseline is not scoreable");
        var comparisons = evidence.candidateComparisons().stream().collect(Collectors.toMap(
                pair -> pair.selected().executionId(), pair -> pair));
        var baselineEmpty = evidence.baseline().assessment().metrics().get(ObjectiveRegistry.PRIMARY).value();
        var candidates = new ArrayList<CalibrationCase>();
        for (var source : evidence.candidates()) {
            var score = scores.get(source.executionId());
            if (score == null || score.score() == null) continue;
            var pair = comparisons.get(source.executionId());
            var before = pair.baseline();
            var after = pair.selected();
            var cumulativeDelta = after.deliveredTonnes().subtract(before.deliveredTonnes());
            var terminalDelta = after.terminalDeliveredTonnes().subtract(before.terminalDeliveredTonnes());
            candidates.add(new CalibrationCase(source.executionId(), source.assessment().planFingerprint(), score.score(),
                    after, baselineEmpty.subtract(source.assessment().metrics().get(ObjectiveRegistry.PRIMARY).value()).movePointRight(2),
                    cumulativeDelta, terminalDelta, percent(cumulativeDelta, before.deliveredTonnes()),
                    percent(terminalDelta, before.terminalDeliveredTonnes())));
        }
        var grid = new ArrayList<GridChoice>();
        for (var cumulative : GRID) for (var terminal : GRID) {
            var limits = new DeliveryRetreatLimits(cumulative, terminal);
            String selected = evidence.baseline().executionId();
            BigDecimal best = baselineScore.score().total();
            for (var candidate : candidates) {
                var pair = comparisons.get(candidate.executionId());
                if (candidate.score().total().compareTo(best) < 0 && limits.issues(pair.baseline(), pair.selected()).isEmpty()) {
                    selected = candidate.executionId();
                    best = candidate.score().total();
                }
            }
            grid.add(new GridChoice(limits, selected, !selected.equals(evidence.baseline().executionId())));
        }
        return new CalibrationGroup(evidence, candidates, grid);
    }

    private static BigDecimal percent(BigDecimal delta, BigDecimal baseline) {
        return baseline.signum() == 0 ? null : delta.movePointRight(2).divide(baseline, PreferenceScorer.PRECISION);
    }

    private static Difference difference(SphericalComparisonEvidence.Side greedy, SphericalComparisonEvidence.Side other) {
        var deltas = new TreeMap<String, BigDecimal>();
        greedy.metrics().forEach((key, reading) -> deltas.put(key, other.metrics().get(key).value().subtract(reading.value())));
        var baseline = greedy.delivery();
        var delivery = other.delivery();
        var cumulativeDelta = delivery.deliveredTonnes().subtract(baseline.deliveredTonnes());
        var terminalDelta = delivery.terminalDeliveredTonnes().subtract(baseline.terminalDeliveredTonnes());
        return new Difference(deltas, delivery.releasedTonnes().subtract(baseline.releasedTonnes()), terminalDelta,
                percent(cumulativeDelta, baseline.deliveredTonnes()), percent(terminalDelta, baseline.terminalDeliveredTonnes()));
    }

    private SphericalComparisonEvidence.Side executeHeuristic(Frozen frozen, Path baseline, Path directory) throws Exception {
        var work = new SandboxSphericalComparisonWorker.Work(B, REVISION, frozen.comparison(), frozen.baselineSha256());
        var receipt = SandboxSphericalComparisonWorker.launch(settings, work, baseline, directory, TIMEOUT_SECONDS);
        var export = store.export(receipt.executionId());
        write(directory.resolve("execution.json"), export);
        var side = SphericalComparisonEvidence.heuristic(export, frozen.heuristicProblem(), frozen.comparison(),
                receipt.elapsedNanos(), json);
        write(directory.resolve("result.json"), side);
        return side;
    }

    private Path freezeBaseline(Request request, Path output) throws IOException {
        Path baseline = output.resolve("baseline-source.json");
        try (var input = new DefaultResourceLoader().getResource(request.baselineResource()).getInputStream()) {
            Files.copy(input, baseline);
        }
        return baseline;
    }

    private static List<String> concat(String first, List<String> remainder) {
        var ids = new ArrayList<String>();
        ids.add(first);
        ids.addAll(remainder);
        return ids;
    }

    private static void requireAttempt(SandboxCandidateEvaluator.Attempt attempt, FulfillmentReplay.Source source) {
        var evidence = attempt.evidence();
        if (evidence == null || evidence.mapRequests() != 0 || !source.executionId().equals(evidence.executionId())
                || !source.lastTickSha256().equals(evidence.lastTickSha256())
                || !source.manifestSha256().equals(evidence.manifestSha256()))
            throw new IllegalArgumentException("Search attempt evidence differs from verified ledger");
    }

    private static long baselineElapsed(Path output, long seed) throws IOException {
        return read(output.resolve("search-" + seed).resolve("attempt-0/result.json"),
                SandboxCandidateEvaluator.Attempt.class).timing().totalNanos();
    }

    private static long selectedElapsed(Path output, SeedResult result) throws IOException {
        var manifest = read(output.resolve("search-" + result.seed()).resolve("evaluation.json"),
                SandboxCandidateEvaluator.Manifest.class);
        if (result.selected().executionId().equals(result.baseline().executionId()))
            return manifest.baselineAttempt().timing().totalNanos();
        return manifest.candidates().stream().filter(attempt -> result.selected().executionId().equals(attempt.executionId()))
                .findFirst().orElseThrow().timing().totalNanos();
    }

    private PlanExecutionTask task(JsonNode export) {
        return json.convertValue(export.path("manifest").path("planExecutionTask"), PlanExecutionTask.class);
    }

    private static <T> T read(Path path, Class<T> type) throws IOException {
        return OptimizationJson.read(Files.readString(path), type);
    }

    private static void write(Path path, Object value) throws IOException {
        Files.writeString(path, OptimizationJson.write(value), StandardOpenOption.CREATE_NEW);
    }
}
