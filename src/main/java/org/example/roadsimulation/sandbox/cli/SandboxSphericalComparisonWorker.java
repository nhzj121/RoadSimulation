package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.execution.SandboxControlledRunExecutor;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionLease;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.execution.SandboxRuntimeFactCollector;
import org.example.roadsimulation.sandbox.execution.bootstrap.SandboxControlledApplication;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.management.SandboxOptimizationJobStore;
import org.example.roadsimulation.sandbox.management.SandboxWorkerLauncher;
import org.example.roadsimulation.sandbox.optimization.EvaluationBuildIdentity;
import org.example.roadsimulation.sandbox.optimization.OptimizationJson;
import org.example.roadsimulation.sandbox.optimization.PlanExecutionRuntime;
import org.example.roadsimulation.sandbox.optimization.SphericalComparisonRun;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** Internal worker for one authenticated HEURISTIC run with frozen spherical legs. */
public final class SandboxSphericalComparisonWorker {
    private SandboxSphericalComparisonWorker() {}

    public record Work(String runSpecKey, int revision, SphericalComparisonRun comparison, String baselineSha256,
                       SandboxOptimizationJobStore.Child child) {
        public Work(String runSpecKey, int revision, SphericalComparisonRun comparison, String baselineSha256) {
            this(runSpecKey, revision, comparison, baselineSha256, null);
        }
    }
    public record Receipt(String errorCode, String executionId, Integer mapRequests, long elapsedNanos) {}

    public static Receipt launch(SandboxManagementSettings settings, Work work, Path baseline, Path directory,
                                 long timeoutSeconds) throws IOException, InterruptedException {
        Files.createDirectory(directory);
        Files.writeString(directory.resolve("work.json"), OptimizationJson.write(work), StandardOpenOption.CREATE_NEW);
        var main = SandboxSphericalComparisonWorker.class;
        var command = new ArrayList<>(SandboxWorkerLauncher.javaCommand(Path.of(System.getProperty("java.home")),
                EvaluationBuildIdentity.packagedSource(main), EvaluationBuildIdentity.classpath(), main.getName()));
        command.add(directory.toAbsolutePath().toString());
        command.add(baseline.toAbsolutePath().toString());
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve("worker.log").toFile());
        SandboxWorkerLauncher.environment(builder.environment(), settings);
        Process child = builder.start();
        try {
            child.getOutputStream().close();
            if (!child.waitFor(timeoutSeconds, TimeUnit.SECONDS))
                throw new SandboxWorkspaceException("TIMEOUT", "HEURISTIC comparison exceeded " + timeoutSeconds + " seconds");
        } finally {
            if (child.isAlive()) child.destroyForcibly().onExit().join();
        }
        var receipt = OptimizationJson.read(Files.readString(directory.resolve("receipt.json")), Receipt.class);
        if (child.exitValue() != 0 || receipt.errorCode() != null || !Integer.valueOf(0).equals(receipt.mapRequests()))
            throw new IOException("HEURISTIC comparison failed: " + receipt.errorCode());
        return receipt;
    }

    public static void main(String[] args) throws Exception {
        long started = System.nanoTime();
        if (args.length != 2) throw new IllegalArgumentException("Expected attempt directory and baseline file");
        Path directory = Path.of(args[0]);
        String executionId = null;
        String error = null;
        try {
            var work = OptimizationJson.read(Files.readString(directory.resolve("work.json")), Work.class);
            Path baseline = Path.of(args[1]);
            if (!work.comparison().build().equals(EvaluationBuildIdentity.capture(SandboxSphericalComparisonWorker.class))
                    || !work.baselineSha256().equals(EvaluationBuildIdentity.fileSha256(baseline)))
                throw new IllegalArgumentException("Comparison worker build or baseline differs");
            var settings = new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"),
                    System.getenv("SANDBOX_DB_USER"), System.getenv("SANDBOX_DB_PASSWORD"));
            var json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            try (var lease = SandboxExecutionLease.prepareAndAcquireComparison(settings.url(), settings.user(), settings.password(),
                    new FileSystemResource(baseline), work.runSpecKey(), work.revision(), work.comparison(), json, work.child())) {
                lease.create(json);
                executionId = lease.executionId();
                if (run(lease, work.comparison(), baseline, settings, json, directory, work.child() != null))
                    error = "USER_CANCELLED";
            }
        } catch (Exception failure) {
            error = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode() : "SPHERICAL_COMPARISON_FAILED";
            System.err.println("sandbox-spherical-comparison-worker[" + error + "]");
        }
        var receipt = new Receipt(error, executionId, error == null ? 0 : null, System.nanoTime() - started);
        Files.writeString(directory.resolve("receipt.json"), OptimizationJson.write(receipt), StandardOpenOption.CREATE_NEW);
        if (error != null) System.exit(2);
    }

    private static boolean run(SandboxExecutionLease lease, SphericalComparisonRun comparison, Path baseline,
                            SandboxManagementSettings settings, ObjectMapper json, Path directory, boolean managed) throws Exception {
        int[] completed = {0};
        boolean cancelled;
        try {
            Files.writeString(directory.resolve("execution-id.txt"), lease.executionId(), StandardOpenOption.CREATE_NEW);
            try (var context = SandboxControlledApplication.open(settings.url(), settings.user(), settings.password(),
                    Map.of("sandbox.baseline-resource", baseline.toUri().toString(), "sandbox.g3-offline", true))) {
                var runtime = context.getBean(PlanExecutionRuntime.class);
                runtime.installComparison(comparison);
                lease.markRunning();
                var facts = context.getBean(SandboxRuntimeFactCollector.class);
                cancelled = context.getBean(SandboxControlledRunExecutor.class).execute(lease, (tick, evaluation) -> {
                    runtime.requireComparisonPathsHealthy();
                    context.getBean(OfflinePlanMaps.class).requireUnused();
                    var runtimeFacts = facts.capture();
                    runtimeFacts.put("sphericalComparisonSha256", comparison.fingerprint());
                    runtimeFacts.put("comparisonMapRequests", 0);
                    lease.recordTick(tick, evaluation, runtimeFacts, json);
                    completed[0]++;
                }).cancelled();
            }
            // The managed seed resolver authenticates the journal while collecting its dispatch trace.
            if (!managed || cancelled)
                new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json).verify(lease.executionId());
            if (cancelled) lease.finishCancelled(completed[0]);
            else lease.finish(completed[0], null);
            return cancelled;
        } catch (Exception failure) {
            try { lease.finish(completed[0], failure); }
            catch (Exception closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }
}
