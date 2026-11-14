package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;

import static org.example.roadsimulation.sandbox.optimization.GaOperatorExperimentCli.write;

final class GaOperatorExperimentReport {
    record Row(Path directory, int horizon, long seed, GaOperatorExperimentCli.Variant variant, int evaluations,
               BigDecimal score, BigDecimal emptyRatio, BigDecimal emptyKm, BigDecimal waste,
               BigDecimal delivered, BigDecimal terminal, BigDecimal carbon, Integer firstAboveSeed,
               long generationNanos, long evaluationNanos, String executionId) {}
    private record Actual(GlobalPlan.TaskKey task, Integer releaseLoop, Integer dispatchLoop, Integer unloadLoop,
                          String blocked, PlanTimingProjection.Timing predicted, Integer releaseError,
                          Integer dispatchError, Integer unloadError) {}
    private record Trace(List<Actual> tasks, BigDecimal emptyKm, Set<String> unfinishedLegs) {}
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static Row collect(SandboxManagementSettings settings, Path directory, int horizon, long seed,
                       GaOperatorExperimentCli.Variant variant, SandboxCandidateEvaluator.Manifest evaluation,
                       GlobalPlanSearch.Result search, FulfillmentComparison.Report delivery,
                       TimedMoveRanking ranker, long generationNanos) throws IOException {
        var selected = search.selected();
        var attempts = attempts(evaluation);
        Trace chosen = null;
        for (var attempt : attempts) {
            if (attempt.status() != SandboxCandidateEvaluator.Status.SCORED_INPUT) continue;
            var attemptPath = directory.resolve("attempt-" + attempt.index());
            var plan = GaOperatorExperimentCli.read(attemptPath.resolve("work.json"),
                    org.example.roadsimulation.sandbox.cli.SandboxEvaluationWorker.Work.class).task().plan();
            var trace = trace(settings, attempt.evidence(), plan, ranker.project(plan));
            write(attemptPath.resolve("task-times.json"), trace);
            if (selected.executionId().equals(attempt.executionId())) chosen = trace;
        }
        if (chosen == null) throw new IOException("Selected execution evidence missing");
        var seedScore = search.steps().stream().filter(s -> s.operator().equals("HEURISTIC_SEED"))
                .map(GlobalPlanSearch.Step::totalScore).filter(Objects::nonNull).findFirst().orElseThrow();
        Integer first = search.steps().stream().filter(step -> step.generation() > 0 && step.acceptedBest())
                .filter(step -> step.totalScore().compareTo(seedScore) < 0).map(GlobalPlanSearch.Step::launchedEvaluations)
                .findFirst().orElse(null);
        long evaluationNanos = attempts.stream().map(SandboxCandidateEvaluator.Attempt::timing)
                .mapToLong(SandboxCandidateEvaluator.Timing::totalNanos).sum();
        return new Row(directory, horizon, seed, variant, search.launchedEvaluations(), selected.score().total(),
                metric(selected, "emptyMileageRatio"), chosen.emptyKm(), metric(selected, "capacityWasteRatio"),
                delivery.selected().deliveredTonnes(), delivery.selected().terminalDeliveredTonnes(),
                metric(selected, "carbonIntensity"), first, generationNanos, evaluationNanos, selected.executionId());
    }

    static void diagnostic(SandboxManagementSettings settings, Path path, SandboxCandidateEvaluator session,
                           SandboxCandidateEvaluator.Manifest report, Map<String, GlobalPlan> samples,
                           GlobalPlan parent, GlobalPlan historicalMove, TimedMoveRanking ranker,
                           GlobalPlan.TaskKey focus) throws IOException {
        var evidence = new ArrayList<Map<String, Object>>();
        for (var attempt : attempts(report)) {
            var plan = samples.getOrDefault(attempt.planFingerprint(), report.baseline().plan());
            var trace = trace(settings, attempt.evidence(), plan, ranker.project(plan));
            write(path.resolve("attempt-" + attempt.index()).resolve("task-times.json"), trace);
            var delivery = session.delivery(attempt.executionId());
            var row = new LinkedHashMap<String, Object>();
            row.put("label", plan.equals(parent) ? "PARENT" : plan.equals(historicalMove) ? "HISTORICAL_MOVE"
                    : attempt.index() == 0 ? "GREEDY" : "PLACEMENT");
            row.put("plan", attempt.planFingerprint());
            row.put("execution", attempt.executionId());
            row.put("score", report.scoring().scores().get(attempt.planFingerprint()));
            row.put("delivery", delivery);
            row.put("deliveryLimits", SandboxOptimizationProfile.LIMITS.issues(session.delivery(report.baselineAttempt().executionId()), delivery));
            row.put("emptyKm", trace.emptyKm());
            row.put("focus", trace.tasks().stream().filter(task -> task.task().equals(focus)).findFirst().orElseThrow());
            row.put("unfinishedLegs", trace.unfinishedLegs());
            evidence.add(row);
        }
        write(path.resolve("comparison.json"), evidence);
    }

    static void requireSource(SandboxManagementSettings settings, SandboxCandidateEvaluator.Evidence evidence,
                              PlanExecutionTask task) throws IOException {
        var journal = store(settings).verifyJournal(evidence.executionId());
        requireJournal(journal, evidence);
        if (!task.fingerprint().equals(journal.metadata().path("manifest").path("planExecutionTaskSha256").asText()))
            throw new IOException("Historical manifest and plan execution task differ");
    }

    static void verifyReplay(SandboxManagementSettings settings, GlobalPlanSearch.Best original,
                             SandboxCandidateEvaluator.Attempt replay, Path directory) throws IOException {
        // Both executions are checked by the normal evaluator before this comparison.
        var store = store(settings);
        var a = store.verifyJournal(original.executionId());
        var b = store.verifyJournal(replay.executionId());
        requireJournal(b, replay.evidence());
        var left = actual(settings, original.executionId(), original.plan(), null);
        var right = actual(settings, replay.executionId(), original.plan(), null);
        boolean same = original.assessment().equals(replay.evidence().assessment())
                && left.tasks().equals(right.tasks()) && a.recordedLoops() == b.recordedLoops();
        write(directory.resolve("replay-comparison.json"), Map.of("matches", same, "original", original.executionId(),
                "replayed", replay.executionId(), "originalTrace", left, "replayedTrace", right));
        if (!same) throw new IOException("Independent replay metrics or task timeline differ");
    }

    private static Trace trace(SandboxManagementSettings settings, SandboxCandidateEvaluator.Evidence evidence,
                               GlobalPlan plan, PlanTimingProjection.Projection prediction) throws IOException {
        if (evidence.mapRequests() != 0) throw new IOException("Unexpected map access");
        return actual(settings, evidence.executionId(), plan, prediction);
    }

    private static Trace actual(SandboxManagementSettings settings, String id, GlobalPlan plan,
                                PlanTimingProjection.Projection prediction) throws IOException {
        var releases = new HashMap<GlobalPlan.TaskKey, Integer>();
        var dispatches = new HashMap<GlobalPlan.TaskKey, Integer>();
        var unloads = new HashMap<GlobalPlan.TaskKey, Integer>();
        var tripTasks = new HashMap<TripKey, List<GlobalPlan.TaskKey>>();
        for (var vehicle : plan.vehicles()) for (int i = 0; i < vehicle.trips().size(); i++) {
            var tasks = vehicle.trips().get(i).nodes().stream().filter(node -> node.action() == GlobalPlan.Action.LOAD)
                    .map(GlobalPlan.Node::task).toList();
            tripTasks.put(new TripKey(vehicle.vehicleId(), i), tasks);
        }
        var journal = store(settings).verifyJournal(id, verified -> {
            int loop = verified.tick().loopIndex();
            var facts = verified.facts();
            var planFacts = facts.path("runtime").path("planExecution");
            var mapped = new HashMap<Long, GlobalPlan.TaskKey>();
            for (var binding : planFacts.path("mapping").path("tasks")) {
                var key = JSON.convertValue(binding.path("key"), GlobalPlan.TaskKey.class);
                mapped.put(binding.path("shipmentItemId").asLong(), key);
                releases.putIfAbsent(key, loop);
            }
            for (var binding : planFacts.path("state").path("bindings")) {
                var trip = JSON.convertValue(binding.path("trip"), TripKey.class);
                for (var task : tripTasks.getOrDefault(trip, List.of())) dispatches.putIfAbsent(task, loop);
            }
            for (var item : facts.path("database").path("shipment_item")) {
                var key = mapped.get(item.path("id").asLong());
                if (key != null && item.path("status").asText().equals("DELIVERED")) unloads.putIfAbsent(key, loop);
            }
        });
        if (journal.recordedLoops() != plan.problem().totalLoops()) throw new IOException("Incomplete task timeline");
        var last = journal.lastTick();
        var evaluation = JSON.convertValue(last.evaluation(), EvaluationSnapshot.class);
        var emptyMetric = evaluation.metrics().get("vehicleEmptyExecutedDistanceKm");
        var emptyKm = emptyMetric == null || emptyMetric.value() == null ? null : BigDecimal.valueOf(emptyMetric.value());
        var projected = new HashMap<GlobalPlan.TaskKey, PlanTimingProjection.Timing>();
        if (prediction != null) prediction.tasks().forEach(task -> projected.put(task.task(), task));
        var bindings = new HashMap<Long, TripKey>();
        var blocked = new HashMap<GlobalPlan.TaskKey, String>();
        var state = last.facts().path("runtime").path("planExecution").path("state");
        for (var entry : state.path("bindings")) bindings.put(entry.path("assignmentId").asLong(),
                JSON.convertValue(entry.path("trip"), TripKey.class));
        for (var queue : state.path("queues")) {
            var head = new TripKey(queue.path("logicalVehicleId").asLong(), queue.path("nextTripIndex").asInt());
            for (var task : tripTasks.getOrDefault(head, List.of())) blocked.put(task, queue.path("reason").asText());
        }
        var unfinished = new TreeSet<String>();
        for (var leg : last.facts().path("database").path("assignment_leg")) {
            var trip = bindings.get(leg.path("assignment_id").asLong());
            if (trip != null && !leg.path("progress_status").asText().equals("COMPLETED"))
                unfinished.add(trip + "/" + leg.path("sequence_index").asInt() + "/" + leg.path("load_state").asText()
                        + "/" + leg.path("executed_distance_meters").asText() + "/" + leg.path("distance_meters").asText());
        }
        var deferred = new HashSet<>(plan.deferredTasks());
        var timeline = new ArrayList<Actual>();
        for (var task : plan.catalogTaskKeys()) {
            var release = releases.get(task);
            var dispatch = dispatches.get(task);
            var unload = unloads.get(task);
            var forecast = projected.get(task);
            String reason = unload != null ? null : deferred.contains(task) ? "DEFERRED" : dispatch != null ? "UNFINISHED_TRIP"
                    : blocked.getOrDefault(task, release == null ? "NOT_RELEASED" : "QUEUE_NOT_REACHED");
            timeline.add(new Actual(task, release, dispatch, unload, reason, forecast,
                    forecast == null ? null : difference(release, forecast.releaseLoop()),
                    forecast == null ? null : difference(dispatch, forecast.dispatchLoop()),
                    forecast == null ? null : difference(unload, forecast.unloadLoop())));
        }
        return new Trace(List.copyOf(timeline), emptyKm, unfinished);
    }

    private static Integer difference(Integer actual, Integer predicted) {
        return actual == null || predicted == null ? null : actual - predicted;
    }

    private static SandboxExecutionStore store(SandboxManagementSettings settings) {
        return new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), JSON);
    }

    private static void requireJournal(SandboxExecutionStore.VerifiedJournal journal,
                                       SandboxCandidateEvaluator.Evidence evidence) throws IOException {
        var metadata = journal.metadata();
        if (!metadata.path("manifestSha256").asText().equals(evidence.manifestSha256())
                || !metadata.path("lastTickSha256").asText().equals(evidence.lastTickSha256())
                || !"COMPLETED".equals(metadata.path("status").asText()) || evidence.mapRequests() != 0
                || journal.recordedLoops() != evidence.assessment().problem().totalLoops())
            throw new IOException("Execution identity or horizon differs from verified evidence");
    }

    private static BigDecimal metric(GlobalPlanSearch.Best selected, String id) {
        return selected.assessment().metrics().get(id).value();
    }

    private static List<SandboxCandidateEvaluator.Attempt> attempts(SandboxCandidateEvaluator.Manifest report) {
        var list = new ArrayList<SandboxCandidateEvaluator.Attempt>();
        list.add(report.baselineAttempt());
        list.addAll(report.candidates());
        return list;
    }

    static void summary(Path directory, List<Row> rows, String verdict) throws IOException {
        var csv = new StringBuilder("horizon,seed,variant,evaluations,score,emptyRatio,emptyKm,capacityWasteRatio,deliveredTonnes,terminalTonnes,carbonIntensity,firstAboveSeed,generationSeconds,evaluationSeconds,executionId\n");
        var markdown = new StringBuilder("# 全局 GA 算子对照\n\n").append(verdict)
                .append("\n\n| 轮数 | 种子 | 组 | 评价次数 | 综合分 | 空载率 | 空载公里 | 累计吨 | 末端吨 |\n")
                .append("|---:|---:|---|---:|---:|---:|---:|---:|---:|\n");
        for (var row : rows) {
            csv.append(String.join(",", Arrays.asList(Integer.toString(row.horizon()), Long.toString(row.seed()), row.variant().name(),
                    Integer.toString(row.evaluations()), row.score().toPlainString(), row.emptyRatio().toPlainString(),
                    Objects.toString(row.emptyKm(), ""), row.waste().toPlainString(), row.delivered().toPlainString(),
                    row.terminal().toPlainString(), row.carbon().toPlainString(), Objects.toString(row.firstAboveSeed(), ""),
                    BigDecimal.valueOf(row.generationNanos(), 9).toPlainString(),
                    BigDecimal.valueOf(row.evaluationNanos(), 9).toPlainString(), row.executionId()))).append('\n');
            markdown.append("| ").append(row.horizon()).append(" | ").append(row.seed()).append(" | ").append(row.variant())
                    .append(" | ").append(row.evaluations()).append(" | ").append(row.score()).append(" | ").append(row.emptyRatio())
                    .append(" | ").append(row.emptyKm()).append(" | ").append(row.delivered()).append(" | ").append(row.terminal()).append(" |\n");
        }
        Files.writeString(directory.resolve("comparison.csv"), csv);
        Files.writeString(directory.resolve("report.md"), markdown);
    }
}
