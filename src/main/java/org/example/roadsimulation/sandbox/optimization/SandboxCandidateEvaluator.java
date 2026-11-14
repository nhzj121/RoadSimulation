package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.*;
import org.example.roadsimulation.evaluation.EvaluationMetricCatalog;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
import org.example.roadsimulation.sandbox.cli.SandboxEvaluationWorker;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.management.*;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfile;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One problem and one immutable baseline per session. Only verified raw results are cached. */
public final class SandboxCandidateEvaluator {
    public static final String VERSION = "sandbox-candidate-evaluation/v1";
    public static final String THROUGHPUT_VERSION = "sandbox-candidate-evaluation/v2";
    public static ObjectiveRegistry objectives(String version) {
        if (THROUGHPUT_VERSION.equals(version)) return ObjectiveRegistry.throughput();
        ContractChecks.version(version, VERSION);
        return ObjectiveRegistry.standard();
    }
    public enum CacheMode { USE, BYPASS }
    public enum Status {
        SCORED_INPUT, NO_DEMAND, INVALID_PLAN, OBJECTIVES_UNAVAILABLE, EXECUTION_FAILED, INVALID_LEDGER,
        WORKSPACE_BUSY, TIMEOUT, INTERRUPTED, WORKER_FAILED, SKIPPED
    }
    public record Request(String artifactVersion, String runSpecKey, int runSpecRevision, String baselineResource,
                          PreferenceProfile preference, List<GlobalPlan> candidates, CacheMode cacheMode,
                          Long timeoutSeconds) {
        public Request {
            ContractChecks.text(runSpecKey, "runSpecKey");
            if (runSpecRevision <= 0) throw new IllegalArgumentException("Invalid run revision");
            ContractChecks.text(baselineResource, "baselineResource");
            Objects.requireNonNull(preference).validate(objectives(artifactVersion));
            candidates = ContractChecks.list(candidates, "candidates");
            Objects.requireNonNull(cacheMode);
            checkTimeout(timeoutSeconds);
        }
    }
    public record Timing(long preparationNanos, long jvmStartupNanos, long applicationStartupNanos, long tickNanos, long ledgerWriteNanos,
                         long verificationNanos, long totalNanos, long artifactBytes) {}
    public record Evidence(String executionId, String manifestSha256, String lastTickSha256,
                           CandidateAssessment assessment, PlanExecutionSummary summary, int mapRequests) {}
    public record Attempt(int index, String planFingerprint, String cacheKey, Status status, boolean cacheHit,
                          String executionId, Evidence evidence, Timing timing, List<String> diagnostics) {
        public Attempt { diagnostics = List.copyOf(diagnostics); }
    }
    public record Scoring(String preferenceFingerprint, SelectionResult selection,
                          Map<String, ScoreBreakdown> scores, long scoringNanos) {
        public Scoring { scores = Collections.unmodifiableMap(new TreeMap<>(scores)); }
    }
    public record Manifest(String artifactVersion, String runSpecKey, int runSpecRevision,
                           EvaluationBuildIdentity build, CompiledOptimizationProblem problem, String baselineSourceFingerprint,
                           GreedyBaseline.Result baseline, Attempt baselineAttempt, List<Attempt> candidates,
                           boolean stopped, Scoring scoring) {
        public Manifest { candidates = List.copyOf(candidates); }
    }

    private final String runKey;
    private final int revision;
    private final Path output, baselineFile;
    private final String baselineSourceFingerprint;
    private final Long timeoutSeconds;
    private final SandboxManagementSettings settings;
    private final PlanExecutionAuthentication.FrozenProblem frozen;
    private final SandboxRunSpecificationRevisionV2 published;
    private final EvaluationBuildIdentity build;
    private final ObjectMapper json;
    private final GreedyBaseline.Result baseline;
    private final String simulationRootSeed;
    private final String version;
    private final ObjectiveRegistry registry;
    private final Map<String, FulfillmentComparison.Facts> fulfillment = new HashMap<>();
    private final Map<String, Attempt> cache = new HashMap<>();
    private final List<Attempt> candidates = new ArrayList<>();
    private Attempt baselineAttempt;
    private boolean stopped;
    private int launchedWorkers;
    private final SandboxOptimizationJobStore job;

    public static SandboxCandidateEvaluator open(SandboxManagementSettings settings, String runKey, int revision,
            String baselineResource, Path output, Long timeoutSeconds) throws IOException {
        return open(settings, runKey, revision, baselineResource, output, timeoutSeconds, VERSION);
    }

    public static SandboxCandidateEvaluator open(SandboxManagementSettings settings, String runKey, int revision,
            String baselineResource, Path output, Long timeoutSeconds, String version) throws IOException {
        return new SandboxCandidateEvaluator(settings, runKey, revision, baselineResource, output, timeoutSeconds, version, null);
    }

    static SandboxCandidateEvaluator openManaged(SandboxManagementSettings settings, String runKey, int revision,
            String baselineResource, Path output, SandboxOptimizationJobStore job) throws IOException {
        return new SandboxCandidateEvaluator(settings, runKey, revision, baselineResource, output, null,
                THROUGHPUT_VERSION, Objects.requireNonNull(job));
    }

    private SandboxCandidateEvaluator(SandboxManagementSettings settings, String runKey, int revision,
            String baselineResource, Path output, Long timeoutSeconds, String version,
            SandboxOptimizationJobStore job) throws IOException {
        this.settings = Objects.requireNonNull(settings);
        this.runKey = ContractChecks.text(runKey, "runSpecKey");
        if (revision <= 0) throw new IllegalArgumentException("Invalid run revision");
        this.revision = revision;
        this.job = job;
        this.registry = objectives(version);
        this.version = version;
        this.output = Files.createDirectory(output.toAbsolutePath().normalize());
        json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        if (!baselineResource.startsWith("classpath:") && !baselineResource.startsWith("file:"))
            throw new IllegalArgumentException("Baseline resource must be a local file: or classpath: resource");
        baselineFile = this.output.resolve("baseline-source.json");
        try (var source = new DefaultResourceLoader().getResource(baselineResource).getInputStream()) {
            Files.copy(source, baselineFile);
        }
        var baselineSource = new FileSystemResource(baselineFile);
        baselineSourceFingerprint = EvaluationBuildIdentity.fileSha256(baselineFile);
        published = new SandboxRunSpecificationStore(settings.url(), settings.user(), settings.password(), json)
                .loadRevisionV2(runKey, revision);
        this.timeoutSeconds = job == null ? checkTimeout(timeoutSeconds)
                : SandboxOptimizationProfile.workerTimeoutSeconds(published.specification().simulationClock().totalLoops());
        simulationRootSeed = published.specification().random().rootSeed();
        var ref = published.specification().scenario();
        var scene = new SandboxScenarioStore(settings.url(), settings.user(), settings.password(), json)
                .loadRevision(ref.scenarioKey(), ref.revision());
        var paths = new SphericalPathModel.Configuration("SPHERICAL_DIRECT/v1", BigDecimal.valueOf(6371000), BigDecimal.valueOf(40));
        frozen = PlanExecutionAuthentication.compile(baselineSource, scene, published, paths, EvaluationMetricCatalog.CONTRACT_VERSION);
        baseline = new GreedyBaseline().build(frozen.problem(), frozen.data());
        build = EvaluationBuildIdentity.capture(SandboxEvaluationWorker.class);
    }

    Long workerTimeoutSeconds() { return timeoutSeconds; }

    SandboxAlgorithmProfile publishedProfile() { return published.algorithmProfile(); }

    public CompiledOptimizationProblem problem() { return frozen.problem(); }
    public GreedyBaseline.Result baseline() { return baseline; }
    SandboxBaselinePackageV1.Data fleetData() { return frozen.data(); }
    String simulationRootSeed() { return simulationRootSeed; }
    synchronized int launchedWorkers() { return launchedWorkers; }

    HeuristicSeedResolver.Acquisition acquireHeuristicSeed(Path cache) throws IOException {
        return new HeuristicSeedResolver(settings, json, published, frozen, job)
                .acquire(cache, baselineFile, output, timeoutSeconds == null ? 300 : timeoutSeconds);
    }

    public synchronized FulfillmentComparison.Report fulfillment(String baselineId, String selectedId) {
        var left = fulfillment.get(baselineId);
        var right = fulfillment.get(selectedId);
        if (left == null || right == null) throw new IllegalArgumentException("Missing verified fulfillment evidence");
        return FulfillmentComparison.compare(problem().catalog(), left, right);
    }

    synchronized FulfillmentComparison.Side delivery(String executionId) {
        var facts = fulfillment.get(executionId);
        return facts == null ? null : FulfillmentComparison.totals(problem().catalog(), facts);
    }

    synchronized Attempt replayPlan(GlobalPlan plan) throws IOException {
        if (launchedWorkers != 0 || baselineAttempt != null || !candidates.isEmpty())
            throw new IllegalStateException("Replay requires a fresh evaluation session");
        return evaluate(0, plan, CacheMode.BYPASS, "BASELINE");
    }

    public synchronized Attempt evaluateBaseline(CacheMode mode) throws IOException {
        Objects.requireNonNull(mode);
        if (baselineAttempt == null) baselineAttempt = evaluate(0, baseline.plan(), mode, "BASELINE");
        return baselineAttempt;
    }

    public synchronized Attempt evaluateCandidate(GlobalPlan plan, CacheMode mode) throws IOException {
        return evaluateCandidate(plan, mode, "CANDIDATE");
    }

    synchronized Attempt evaluateCandidate(GlobalPlan plan, CacheMode mode, String kind) throws IOException {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(mode);
        if (baselineAttempt == null) throw new IllegalStateException("Evaluate the baseline first");
        int index = candidates.size() + 1;
        var result = stopped || baselineAttempt.status != Status.SCORED_INPUT
                ? failure(index, plan.fingerprint(), null, Status.SKIPPED, null, "COMPARISON_NOT_AVAILABLE", 0)
                : evaluate(index, plan, mode, kind);
        candidates.add(result);
        return result;
    }

    public synchronized Manifest report(PreferenceProfile preference) {
        return report(preference, null);
    }

    synchronized Manifest report(PreferenceProfile preference, GlobalPlanSearch.Result search) {
        if (baselineAttempt == null) throw new IllegalStateException("Evaluate the baseline first");
        long started = System.nanoTime();
        SelectionResult selection;
        if (search == null) {
            selection = new CandidateSelector().select(problem().problem(), registry, preference,
                    assessment(baselineAttempt), candidates.stream().filter(attempt -> attempt.status == Status.SCORED_INPUT)
                            .map(SandboxCandidateEvaluator::assessment).toList());
        } else {
            var rejected = new TreeMap<String, List<String>>();
            for (var step : search.steps()) {
                if (step.child() == null || step.acceptedBest()) continue;
                var deliveryIssues = step.reasons().stream().filter(reason -> reason.equals("DELIVERY_EVIDENCE_MISSING")
                        || reason.equals("CUMULATIVE_DELIVERY_RETREAT_EXCEEDED")
                        || reason.equals("TERMINAL_DELIVERY_RETREAT_EXCEEDED")).toList();
                if (!deliveryIssues.isEmpty()) rejected.put(step.child(), deliveryIssues);
            }
            var selected = search.selected();
            String fingerprint = selected == null ? null : selected.planFingerprint();
            if (search.outcome() == GlobalPlanSearch.Outcome.NO_DEMAND) fingerprint = baselineAttempt.planFingerprint();
            selection = new SelectionResult(SelectionResult.Outcome.valueOf(search.outcome().name()), fingerprint,
                    selected == null ? null : selected.score(), List.of(search.stop().name()), rejected);
        }
        var scores = scores(preference, selection);
        var scoring = new Scoring(preference.fingerprint(), selection, scores, System.nanoTime() - started);
        return new Manifest(version, runKey, revision, build, problem(), baselineSourceFingerprint, baseline,
                baselineAttempt, candidates, stopped, scoring);
    }

    private Map<String, ScoreBreakdown> scores(PreferenceProfile preference, SelectionResult selection) {
        var scores = new TreeMap<String, ScoreBreakdown>();
        if (selection.score() == null) return scores;
        var baselineAssessment = assessment(baselineAttempt);
        var baselineValues = new TreeMap<String, BigDecimal>();
        registry.definitions().forEach(def -> baselineValues.put(def.metricId(), baselineAssessment.metrics().get(def.metricId()).value()));
        var context = new ScoringContext(problem().problem(), registry, preference, baselineAssessment.planFingerprint(), baselineValues);
        var scorer = new PreferenceScorer();
        scores.put(baselineAttempt.planFingerprint, scorer.score(context, baselineAssessment).score());
        candidates.stream().filter(attempt -> attempt.status == Status.SCORED_INPUT).forEach(attempt ->
                scores.computeIfAbsent(attempt.planFingerprint, key -> scorer.score(context, assessment(attempt)).score()));
        return scores;
    }

    private Attempt evaluate(int index, GlobalPlan plan, CacheMode mode, String kind) throws IOException {
        long started = System.nanoTime();
        String fingerprint = plan.fingerprint();
        PlanExecutionTask task;
        try {
            task = new PlanExecutionTask(PlanExecutionTask.VERSION, runKey, revision, problem(), plan,
                    PlanExecutionTask.DECODER, PlanExecutionTask.REPAIR);
            new GlobalPlanValidator().validate(task, frozen.data());
        } catch (IllegalArgumentException failure) {
            return failure(index, fingerprint, null, Status.INVALID_PLAN, null, failure.getMessage(), System.nanoTime()-started);
        }
        String key = cacheKey(task, build, baselineSourceFingerprint);
        var cached = cache.get(key);
        if (mode == CacheMode.USE && cached != null) {
            return new Attempt(index, fingerprint, key, cached.status, true, cached.executionId,
                    cached.evidence, emptyTiming(System.nanoTime()-started), List.of());
        }
        Path directory = Files.createDirectory(output.resolve("attempt-" + index));
        write(directory.resolve("work.json"), new SandboxEvaluationWorker.Work(task, build, baselineSourceFingerprint,
                job == null ? null : new SandboxOptimizationJobStore.Child(job.jobId(), kind)));
        Attempt result = execute(index, task, key, directory, started, kind);
        if (result.status == Status.SCORED_INPUT && cached != null
                && !cached.evidence.assessment.equals(result.evidence.assessment)) {
            stopped = true;
            result = new Attempt(index, fingerprint, key, Status.INVALID_LEDGER, false, result.executionId,
                    result.evidence, result.timing, List.of("CONFLICTING_REPLAY_EVIDENCE"));
        }
        if (result.status == Status.SCORED_INPUT
                || (THROUGHPUT_VERSION.equals(version) && result.evidence != null && !stopped))
            cache.putIfAbsent(key, result);
        try {
            write(directory.resolve("result.json"), result);
        } finally {
            if (result.status == Status.INTERRUPTED) Thread.currentThread().interrupt();
        }
        return result;
    }

    private Attempt execute(int index, PlanExecutionTask task, String key, Path directory, long started, String kind) throws IOException {
        String fingerprint = task.plan().fingerprint();
        var main = SandboxEvaluationWorker.class;
        var command = new ArrayList<>(SandboxWorkerLauncher.javaCommand(Path.of(System.getProperty("java.home")),
                EvaluationBuildIdentity.packagedSource(main), EvaluationBuildIdentity.classpath(), main.getName()));
        command.add(directory.toString());
        command.add(baselineFile.toString());
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve("worker.log").toFile());
        SandboxWorkerLauncher.environment(builder.environment(), settings);
        Process child;
        try {
            child = builder.start();
            launchedWorkers++;
        } catch (IOException failure) {
            return stop(index, fingerprint, key, directory, Status.WORKER_FAILED, "WORKER_START_FAILED", started);
        }
        try {
            child.getOutputStream().close();
            if (timeoutSeconds == null) child.waitFor();
            else if (!child.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                child.destroyForcibly().waitFor();
                return stop(index, fingerprint, key, directory, Status.TIMEOUT, "WORKER_TIMEOUT", started);
            }
        } catch (InterruptedException failure) {
            child.destroyForcibly();
            child.onExit().join();
            return stop(index, fingerprint, key, directory, Status.INTERRUPTED, "WORKER_INTERRUPTED", started);
        } catch (IOException failure) {
            child.destroyForcibly();
            child.onExit().join();
            return stop(index, fingerprint, key, directory, Status.WORKER_FAILED, "WORKER_PIPE_FAILED", started);
        }
        SandboxEvaluationWorker.Receipt receipt;
        try {
            receipt = OptimizationJson.read(Files.readString(directory.resolve("receipt.json")), SandboxEvaluationWorker.Receipt.class);
        } catch (IOException | IllegalArgumentException failure) {
            return stop(index, fingerprint, key, directory, Status.WORKER_FAILED, "MISSING_OR_INVALID_WORKER_RECEIPT", started);
        }
        if (job != null) {
            try {
                if (job.cancelled()) return stop(index, fingerprint, key, directory, Status.INTERRUPTED, "USER_CANCELLED", started);
            } catch (java.sql.SQLException failure) { throw new IOException("Cannot check cancellation", failure); }
        }
        if (child.exitValue() != 0 || receipt.run() == null || receipt.errorCode() != null) {
            String code = Objects.toString(receipt.errorCode(), "WORKER_NONZERO_EXIT");
            Status status = switch (code) {
                case "WORKSPACE_BUSY" -> Status.WORKSPACE_BUSY;
                case "EXECUTION_RECORD_CORRUPT" -> Status.INVALID_LEDGER;
                default -> Status.EXECUTION_FAILED;
            };
            return stop(index, fingerprint, key, directory, status, code, started);
        }
        var run = receipt.run();
        long verifying = System.nanoTime();
        Evidence evidence;
        JsonNode export = null;
        JsonNode metadata;
        JsonNode finalFacts;
        try {
            String executionId = readExecutionId(directory);
            if (executionId == null) throw new IllegalArgumentException("Missing execution ID");
            var store = new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json);
            if (job != null) {
                var journal = store.verifyJournal(executionId);
                evidence = evidenceFromVerifiedJournal(task, journal, run.summary(), run.mapRequests(), json);
                metadata = journal.metadata();
                finalFacts = journal.lastTick().facts();
            } else {
                export = store.export(executionId);
                evidence = evidence(task, export, run.summary(), run.mapRequests(), json);
                metadata = export;
                finalFacts = export.path("ticks").get(task.plan().problem().totalLoops() - 1).path("facts");
            }
        } catch (SandboxWorkspaceException | IllegalArgumentException failure) {
            return stop(index, fingerprint, key, directory, Status.INVALID_LEDGER, "EXECUTION_EVIDENCE_INVALID", started);
        }
        FulfillmentComparison.Facts delivery = null;
        if (THROUGHPUT_VERSION.equals(version)) {
            try {
                delivery = FulfillmentComparison.factsFromFinalTick(task, evidence.executionId(), metadata.path("manifest"),
                        finalFacts, evidence.assessment(), json);
            } catch (IllegalArgumentException failure) {
                return stop(index, fingerprint, key, directory, Status.INVALID_LEDGER, "FULFILLMENT_EVIDENCE_INVALID", started);
            }
        }
        if (job != null) {
            try { job.verified(evidence.executionId(), evidence.manifestSha256(), evidence.lastTickSha256()); }
            catch (java.sql.SQLException failure) { throw new IOException("Cannot record verified evaluation", failure); }
        }
        long verification = run.verificationNanos() + System.nanoTime()-verifying;
        long artifactBytes = 0;
        if (export != null) {
            Path artifact = directory.resolve("execution.json");
            try (var output = Files.newOutputStream(artifact, StandardOpenOption.CREATE_NEW)) {
                OptimizationJson.write(output, export);
            }
            artifactBytes = Files.size(artifact);
        }
        if (delivery != null) fulfillment.put(evidence.executionId(), delivery);
        var issues = new PreferenceScorer().eligibilityIssues(problem().problem(), registry, evidence.assessment);
        var status = issues.isEmpty() ? Status.SCORED_INPUT : Status.OBJECTIVES_UNAVAILABLE;
        if (index == 0 && confirmedNoDemand(evidence.assessment)) status = Status.NO_DEMAND;
        var timing = new Timing(receipt.preparationNanos(), receipt.jvmStartupNanos(), run.startupNanos(), run.tickNanos(), run.ledgerWriteNanos(),
                verification, System.nanoTime()-started, artifactBytes);
        return new Attempt(index, fingerprint, key, status, false, evidence.executionId, evidence, timing, issues);
    }

    /** Called only with the output of the authenticated, snapshot-consistent ledger export. */
    static Evidence evidence(PlanExecutionTask task, JsonNode export, PlanExecutionSummary summary,
                             int mapRequests, ObjectMapper json) {
        var ticks = export.path("ticks");
        var assessment = assessmentFromVerifiedExport(task, export, json);
        return evidence(task, export, ticks.get(ticks.size() - 1).path("facts"), assessment, summary, mapRequests, json);
    }

    static Evidence evidenceFromVerifiedJournal(PlanExecutionTask task, SandboxExecutionStore.VerifiedJournal journal,
                                                PlanExecutionSummary summary, int mapRequests, ObjectMapper json) {
        var last = journal.lastTick();
        if (last == null) throw new IllegalArgumentException("Missing completed evaluation");
        var assessment = assessment(task, journal.metadata(), journal.recordedLoops(),
                journal.failedAssignments(), last.evaluation(), json);
        return evidence(task, journal.metadata(), last.facts(), assessment, summary, mapRequests, json);
    }

    private static Evidence evidence(PlanExecutionTask task, JsonNode metadata, JsonNode lastFacts,
                                     CandidateAssessment assessment, PlanExecutionSummary summary,
                                     int mapRequests, ObjectMapper json) {
        var problem = task.plan().problem();
        String id = metadata.path("executionId").asText();
        if (!summary.executionId().equals(id) || !summary.state().taskFingerprint().equals(task.fingerprint())
                || summary.requestedLoops() != problem.totalLoops() || summary.completedLoops() != problem.totalLoops()
                || mapRequests != 0)
            throw new IllegalArgumentException("Execution identity or horizon mismatch");
        var ledgerState = json.convertValue(lastFacts.path("runtime").path("planExecution").path("state"),
                PlanExecutionState.Snapshot.class);
        if (!summary.state().equals(ledgerState)) throw new IllegalArgumentException("Summary state differs from ledger");
        return new Evidence(id, ContractChecks.hash(metadata.path("manifestSha256").asText(), "manifest hash"),
                ContractChecks.hash(metadata.path("lastTickSha256").asText(), "last tick hash"), assessment, summary, mapRequests);
    }

    static CandidateAssessment assessmentFromVerifiedExport(PlanExecutionTask task, JsonNode export, ObjectMapper json) {
        var ticks = export.path("ticks");
        if (!ticks.isArray() || ticks.isEmpty())
            throw new IllegalArgumentException("Execution identity or horizon mismatch");
        int failures = 0;
        for (var tick : ticks) {
            var count = tick.path("evaluation").path("failedAssignmentCount");
            if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0)
                throw new IllegalArgumentException("Missing or invalid transport failure count");
            failures = Math.addExact(failures, count.intValue());
        }
        return assessment(task, export, ticks.size(), failures, ticks.get(ticks.size() - 1).path("evaluation"), json);
    }

    private static CandidateAssessment assessment(PlanExecutionTask task, JsonNode metadata, int recorded,
                                                  int failures, JsonNode evaluation, ObjectMapper json) {
        var problem = task.plan().problem();
        var manifest = metadata.path("manifest");
        if (!"VERIFIED".equals(metadata.path("integrity").asText()) || !"COMPLETED".equals(metadata.path("status").asText())
                || !"GLOBAL_PLAN".equals(manifest.path("executionMode").asText())
                || !task.fingerprint().equals(manifest.path("planExecutionTaskSha256").asText())
                || metadata.path("requestedLoops").asInt() != problem.totalLoops()
                || metadata.path("completedLoops").asInt() != problem.totalLoops()
                || recorded != problem.totalLoops())
            throw new IllegalArgumentException("Execution identity or horizon mismatch");
        var snapshot = json.convertValue(evaluation, EvaluationSnapshot.class);
        var assessment = EvaluationMetricAdapter.fromSnapshot(problem, task.plan().fingerprint(), true,
                CandidateAssessment.ExecutionStatus.COMPLETED, recorded, failures, snapshot);
        var audit = new TreeMap<>(assessment.auditData());
        audit.put("catalogTaskCount", BigDecimal.valueOf(task.compiledProblem().catalog().tasks().size()));
        assessment = new CandidateAssessment(assessment.problem(), assessment.planFingerprint(), true, assessment.completion(),
                assessment.metrics(), audit, assessment.diagnostics());
        if (!assessment.executionIssues().isEmpty()) throw new IllegalArgumentException("Invalid execution assessment");
        return assessment;
    }

    static String cacheKey(PlanExecutionTask task, EvaluationBuildIdentity build, String baselineFingerprint) {
        return OptimizationJson.fingerprint(List.of(VERSION, task.fingerprint(), build, baselineFingerprint));
    }

    private static CandidateAssessment assessment(Attempt attempt) {
        return attempt.evidence == null ? null : attempt.evidence.assessment;
    }

    private static boolean confirmedNoDemand(CandidateAssessment assessment) {
        var audit = assessment.auditData();
        return BigDecimal.ZERO.equals(audit.get("catalogTaskCount")) && BigDecimal.ZERO.equals(audit.get("cargoRequiredTonnes"))
                && BigDecimal.ZERO.equals(audit.get("cargoDeliveredTonnes"));
    }

    private Attempt stop(int index, String fingerprint, String key, Path directory, Status status, String code, long started)
            throws IOException {
        stopped = true;
        return failure(index, fingerprint, key, status, readExecutionId(directory), code, System.nanoTime()-started);
    }

    private static Attempt failure(int index, String fingerprint, String key, Status status, String id, String reason, long elapsed) {
        return new Attempt(index, fingerprint, key, status, false, id, null, emptyTiming(elapsed), List.of(reason));
    }

    private static Timing emptyTiming(long elapsed) { return new Timing(0, 0, 0, 0, 0, 0, elapsed, 0); }

    private static String readExecutionId(Path directory) throws IOException {
        Path path = directory.resolve("execution-id.txt");
        if (!Files.exists(path)) return null;
        String id = Files.readString(path);
        try { return UUID.fromString(id).toString().equals(id) ? id : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static Long checkTimeout(Long timeoutSeconds) {
        if (timeoutSeconds != null && timeoutSeconds <= 0) throw new IllegalArgumentException("Timeout must be positive");
        return timeoutSeconds;
    }

    private static void write(Path path, Object value) throws IOException {
        Files.writeString(path, OptimizationJson.write(value), StandardOpenOption.CREATE_NEW);
    }
}
