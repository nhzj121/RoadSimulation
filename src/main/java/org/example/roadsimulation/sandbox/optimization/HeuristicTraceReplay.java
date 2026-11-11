package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Runs one strict G6-A replay against a verified HEURISTIC execution. */
public final class HeuristicTraceReplay {
    static final String VERSION = "sandbox-heuristic-trace-replay/v1";
    private static final long TIMEOUT_SECONDS = 300;

    record Inputs(String commonInputFingerprint, String baselineSha256, String sourceRunFingerprint,
                  String referenceRunFingerprint, String pathFingerprint, EvaluationBuildIdentity sourceBuild,
                  EvaluationBuildIdentity replayBuild) {}
    record ReplayReport(String artifactVersion, String outcome, String sourceExecutionId, String replayExecutionId,
                        String planFingerprint, Inputs inputs, SphericalComparisonEvidence.Side source,
                        SphericalComparisonEvidence.Side replay, Map<String, BigDecimal> replayMinusSource,
                        HeuristicTraceComparison.Difference firstDifference, int differingTicks, int mapRequests) {}
    record Failure(String artifactVersion, String outcome, String phase, String sourceExecutionId,
                   String replayExecutionId, String planFingerprint, String code, String message) {}

    private final SandboxManagementSettings settings;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SandboxExecutionStore store;

    public HeuristicTraceReplay(SandboxManagementSettings settings) {
        this.settings = settings;
        store = new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json);
    }

    public void execute(String executionId, Path output) throws IOException {
        output = Files.createDirectory(output.toAbsolutePath().normalize());
        String phase = "SOURCE_VERIFICATION";
        String planFingerprint = null;
        String replayId = null;
        try {
            var source = store.export(executionId);
            var manifest = source.path("manifest");
            if (!SphericalComparisonRun.MODE.equals(manifest.path("executionMode").asText()))
                throw new IllegalArgumentException("Source must be a verified HEURISTIC spherical comparison");
            var spherical = json.convertValue(manifest.path("sphericalComparison"), SphericalComparisonRun.class);
            Path baseline = output.resolve("baseline-source.json");
            write(baseline, manifest.path("baselinePackage"));
            var frozen = new FormalSandboxComparison(settings).preflight(baseline);
            if (!spherical.commonInputFingerprint().equals(frozen.commonInputFingerprint())
                    || !spherical.paths().equals(frozen.referenceProblem().paths()))
                throw new IllegalArgumentException("Source and reference frozen inputs differ");
            var sourceSide = SphericalComparisonEvidence.heuristic(source, frozen.heuristicProblem(), spherical, 0, json);
            var inputs = new Inputs(frozen.commonInputFingerprint(), frozen.baselineSha256(),
                    frozen.heuristicProblem().sourceRunSha256(), frozen.referenceProblem().sourceRunSha256(),
                    spherical.paths().fingerprint(), spherical.build(), frozen.build());
            write(output.resolve("source.json"), Map.of("artifactVersion", VERSION, "evidence", sourceSide, "inputs", inputs));
            var finalDatabase = source.path("ticks").get(frozen.referenceProblem().problem().totalLoops() - 1)
                    .path("facts").path("database");
            var mapping = SphericalComparisonEvidence.mapping(frozen.heuristicProblem().catalog(), finalDatabase,
                    manifest.path("preparation").path("deterministicSimulationRunId").asText());

            phase = "TRACE_CONVERSION";
            var conversion = HeuristicTracePlan.convert(frozen.referenceProblem(), source, mapping);
            planFingerprint = conversion.plan().fingerprint();
            write(output.resolve("global-plan.json"), conversion.plan());
            write(output.resolve("source-trips.json"), Map.of("sourceExecutionId", executionId,
                    "planFingerprint", planFingerprint, "trips", conversion.trips(),
                    "dispatchedTasks", conversion.dispatchedTasks(), "deliveredTasks", conversion.deliveredTasks(),
                    "releasedNotDispatched", conversion.releasedNotDispatched(), "unreleased", conversion.unreleased()));

            phase = "PLAN_VALIDATION";
            var session = SandboxCandidateEvaluator.open(settings, frozen.reference().runSpecKey(), frozen.reference().revision(),
                    baseline.toUri().toString(), output.resolve("replay"), TIMEOUT_SECONDS,
                    SandboxCandidateEvaluator.THROUGHPUT_VERSION);
            if (!session.problem().equals(frozen.referenceProblem()))
                throw new IllegalArgumentException("Replay session compiled a different frozen problem");
            var validation = new GlobalPlanValidator().validatePlan(session.problem(), conversion.plan(), session.fleetData());
            write(output.resolve("validation.json"), Map.of("valid", true, "planFingerprint", planFingerprint,
                    "validatedTrips", validation.peaks().size(), "upstreamDeferredTrips", validation.upstreamDeferred().stream().sorted().toList()));

            phase = "REPLAY_EXECUTION";
            var attempt = session.replayPlan(conversion.plan());
            replayId = attempt.executionId();
            if (attempt.status() != SandboxCandidateEvaluator.Status.SCORED_INPUT)
                throw new IllegalArgumentException("Strict replay failed: " + attempt.status() + " " + attempt.diagnostics());
            var replay = store.export(replayId);
            if (!attempt.evidence().manifestSha256().equals(replay.path("manifestSha256").asText())
                    || !attempt.evidence().lastTickSha256().equals(replay.path("lastTickSha256").asText())
                    || attempt.evidence().mapRequests() != 0 || executionId.equals(replayId))
                throw new IllegalArgumentException("Independent replay evidence mismatch");
            var task = json.convertValue(replay.path("manifest").path("planExecutionTask"), PlanExecutionTask.class);
            if (!task.plan().equals(conversion.plan()))
                throw new IllegalArgumentException("Replay executed a different plan");
            var replaySide = SphericalComparisonEvidence.plan("GLOBAL_PLAN", replay, task, attempt.timing().totalNanos(), json);

            phase = "TRACE_COMPARISON";
            var comparison = new HeuristicTraceComparison(json).compare(conversion, session.problem().catalog(), mapping, source, replay);
            write(output.resolve("trace-comparison.json"), comparison);
            var delta = new TreeMap<String, BigDecimal>();
            sourceSide.metrics().forEach((key, value) -> delta.put(key, replaySide.metrics().get(key).value().subtract(value.value())));
            var terminalDelta = replaySide.delivery().terminalDeliveredTonnes().subtract(sourceSide.delivery().terminalDeliveredTonnes());
            delta.put("terminalDeliveredTonnes", terminalDelta);
            write(output.resolve("report.json"), new ReplayReport(VERSION,
                    comparison.matches() && terminalDelta.signum() == 0 ? "MATCHED" : "DIVERGED",
                    executionId, replayId, planFingerprint, inputs, sourceSide, replaySide, delta,
                    comparison.firstDifference(), (int) comparison.ticks().stream().filter(tick -> !tick.differences().isEmpty()).count(),
                    attempt.evidence().mapRequests()));
        } catch (IOException | RuntimeException failure) {
            boolean conversion = Set.of("TRACE_CONVERSION", "PLAN_VALIDATION").contains(phase);
            String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode()
                    : failure.getClass().getSimpleName();
            try {
                if ("PLAN_VALIDATION".equals(phase) && !Files.exists(output.resolve("validation.json")))
                    write(output.resolve("validation.json"), Map.of("valid", false, "message",
                            Objects.toString(failure.getMessage(), code)));
                write(output.resolve("report.json"), new Failure(VERSION, conversion ? "CONVERSION_BLOCKED" : "FAILED",
                        phase, executionId, replayId, planFingerprint, code, failure.getMessage()));
            } catch (IOException writing) {
                failure.addSuppressed(writing);
            }
            throw failure;
        }
    }

    private void write(Path path, Object value) throws IOException {
        try (var stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            json.writeValue(stream, value);
        }
    }
}
