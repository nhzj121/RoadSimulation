package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;

import java.math.BigDecimal;
import java.util.*;

/** Rescores existing execution IDs without preparing a workspace or launching a simulation. */
public final class FulfillmentReplay {
    public static final String VERSION = "sandbox-fulfillment-replay/v1";

    public record Profile(String name, String evaluationVersion, PreferenceProfile preference) {
        public Profile {
            ContractChecks.text(name, "profile name");
            Objects.requireNonNull(preference).validate(SandboxCandidateEvaluator.objectives(evaluationVersion));
        }
    }

    public record Request(String artifactVersion, String baselineExecutionId, List<String> candidateExecutionIds,
                          List<Profile> profiles) {
        public Request {
            ContractChecks.version(artifactVersion, VERSION);
            executionId(baselineExecutionId);
            candidateExecutionIds = ContractChecks.list(candidateExecutionIds, "candidate execution IDs");
            profiles = ContractChecks.list(profiles, "profiles");
            candidateExecutionIds.forEach(FulfillmentReplay::executionId);
            if (profiles.isEmpty() || profiles.stream().map(Profile::name).distinct().count() != profiles.size())
                throw new IllegalArgumentException("Provide uniquely named replay profiles");
            if (candidateExecutionIds.contains(baselineExecutionId)
                    || candidateExecutionIds.stream().distinct().count() != candidateExecutionIds.size())
                throw new IllegalArgumentException("Duplicate replay execution ID");
        }
    }

    public record Source(String executionId, String manifestSha256, String lastTickSha256,
                         CandidateAssessment assessment) {}
    public record Ranked(String executionId, ScoreBreakdown score, List<String> issues) {
        public Ranked { issues = List.copyOf(issues); }
    }
    public record Ranking(Profile profile, SelectionResult selection, List<Ranked> ranking,
                          FulfillmentComparison.Report fulfillment) {
        public Ranking { ranking = List.copyOf(ranking); }
    }
    public record Result(String artifactVersion, String problemFingerprint, String catalogFingerprint,
                         BigDecimal catalogTonnes, Source baseline, List<Source> candidates,
                         List<FulfillmentComparison.Report> candidateComparisons, List<Ranking> rankings) {
        public Result {
            candidates = List.copyOf(candidates);
            candidateComparisons = List.copyOf(candidateComparisons);
            rankings = List.copyOf(rankings);
        }
    }

    private record Read(Source source, PlanExecutionTask task, FulfillmentComparison.Facts facts) {}

    private FulfillmentReplay() {}

    public static Result execute(SandboxManagementSettings settings, Request request) {
        var json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var store = new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json);
        var baseline = read(store, request.baselineExecutionId(), json);
        var problem = baseline.task.compiledProblem();
        var candidates = new ArrayList<Read>();
        var plans = new HashSet<String>();
        plans.add(baseline.source.assessment.planFingerprint());
        for (String id : request.candidateExecutionIds()) {
            var candidate = read(store, id, json);
            if (!problem.equals(candidate.task.compiledProblem())
                    || !baseline.task.runSpecKey().equals(candidate.task.runSpecKey())
                    || baseline.task.runSpecRevision() != candidate.task.runSpecRevision()
                    || !baseline.task.decoderVersion().equals(candidate.task.decoderVersion())
                    || !baseline.task.repairVersion().equals(candidate.task.repairVersion()))
                throw new IllegalArgumentException("Replay executions must belong to the same frozen run");
            if (!plans.add(candidate.source.assessment.planFingerprint()))
                throw new IllegalArgumentException("Replay requires one execution per plan fingerprint");
            candidates.add(candidate);
        }
        var rankings = request.profiles().stream().map(profile -> rank(baseline, candidates, profile)).toList();
        var catalog = problem.catalog();
        var tonnes = catalog.tasks().stream().map(ProductionTaskCatalog.Task::weightTonnes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var comparisons = candidates.stream().map(candidate ->
                FulfillmentComparison.compare(catalog, baseline.facts, candidate.facts)).toList();
        return new Result(VERSION, problem.problem().problemFingerprint(), catalog.fingerprint(), tonnes, baseline.source,
                candidates.stream().map(Read::source).toList(), comparisons, rankings);
    }

    private static Read read(SandboxExecutionStore store, String id, ObjectMapper json) {
        var export = store.export(id);
        var task = json.convertValue(export.path("manifest").path("planExecutionTask"), PlanExecutionTask.class);
        var assessment = SandboxCandidateEvaluator.assessmentFromVerifiedExport(task, export, json);
        return new Read(new Source(id, export.path("manifestSha256").asText(), export.path("lastTickSha256").asText(),
                assessment), task, FulfillmentComparison.facts(task, export, assessment, json));
    }

    private static Ranking rank(Read baseline, List<Read> candidates, Profile profile) {
        var registry = SandboxCandidateEvaluator.objectives(profile.evaluationVersion);
        var problem = baseline.task.plan().problem();
        var selection = new CandidateSelector().select(problem, registry, profile.preference,
                baseline.source.assessment, candidates.stream().map(c -> c.source.assessment).toList());
        if (selection.score() == null) return new Ranking(profile, selection, List.of(), null);
        var values = new TreeMap<String, BigDecimal>();
        registry.definitions().forEach(d -> values.put(d.metricId(), baseline.source.assessment.metrics().get(d.metricId()).value()));
        var context = new ScoringContext(problem, registry, profile.preference, baseline.source.assessment.planFingerprint(), values);
        var scorer = new PreferenceScorer();
        var all = new ArrayList<Read>();
        all.add(baseline);
        all.addAll(candidates);
        var ranking = all.stream().map(read -> {
            var result = scorer.score(context, read.source.assessment);
            return new Ranked(read.source.executionId, result.score(), result.issues());
        }).sorted(Comparator.comparing(Ranked::score, Comparator.nullsLast(ScoreBreakdown::compare))
                .thenComparing(Ranked::executionId)).toList();
        var selected = all.stream().filter(c -> c.source.assessment.planFingerprint().equals(selection.selectedPlanFingerprint()))
                .findFirst().orElseThrow();
        return new Ranking(profile, selection, ranking,
                FulfillmentComparison.compare(baseline.task.compiledProblem().catalog(), baseline.facts, selected.facts));
    }

    private static void executionId(String id) {
        if (id == null || !UUID.fromString(id).toString().equals(id))
            throw new IllegalArgumentException("Invalid replay execution ID");
    }
}
