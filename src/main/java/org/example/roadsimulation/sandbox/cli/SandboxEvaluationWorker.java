package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.*;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionLease;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.optimization.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.FileSystemResource;

import java.io.UncheckedIOException;
import java.nio.file.*;

/** Internal entry point. Receives a parent-owned attempt directory and frozen baseline file. */
public final class SandboxEvaluationWorker {
    private SandboxEvaluationWorker() {}

    public record Work(PlanExecutionTask task, EvaluationBuildIdentity build, String baselineSourceFingerprint) {}
    public record Receipt(String errorCode, long jvmStartupNanos, long preparationNanos, SandboxPlanExecutionCli.Run run) {}

    public static void main(String[] args) {
        long jvmStartupNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        int exit = 2;
        try {
            if (args.length != 2) throw new IllegalArgumentException("Expected attempt directory and baseline file");
            Path directory = Path.of(args[0]);
            try {
                var work = OptimizationJson.read(Files.readString(directory.resolve("work.json")), Work.class);
                if (!work.build().equals(EvaluationBuildIdentity.capture(SandboxEvaluationWorker.class)))
                    throw new SandboxWorkspaceException("EVALUATION_BUILD_CHANGED", "Worker build identity differs");
                var settings = new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"),
                        System.getenv("SANDBOX_DB_USER"), System.getenv("SANDBOX_DB_PASSWORD"));
                var json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                Path baseline = Path.of(args[1]);
                if (!EvaluationBuildIdentity.fileSha256(baseline).equals(work.baselineSourceFingerprint()))
                    throw new SandboxWorkspaceException("EVALUATION_BASELINE_CHANGED", "Worker baseline source differs");
                long started = System.nanoTime();
                Receipt receipt;
                try (var lease = SandboxExecutionLease.prepareAndAcquirePlan(settings.url(), settings.user(),
                        settings.password(), new FileSystemResource(baseline), work.task(), json)) {
                    long prepared = System.nanoTime() - started;
                    var run = SandboxPlanExecutionCli.run(lease, work.task(), lease.preparedPlanData(work.task()),
                            settings.url(), settings.user(), settings.password(), baseline.toUri().toString(), json,
                            true, id -> recordExecution(directory, id));
                    receipt = new Receipt(null, jvmStartupNanos, prepared, run);
                }
                write(directory.resolve("receipt.json"), receipt);
                exit = 0;
            } catch (Exception failure) {
                String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode()
                        : "PLAN_EXECUTION_FAILED";
                write(directory.resolve("receipt.json"), new Receipt(code, jvmStartupNanos, 0, null));
                System.err.println("sandbox-evaluation-worker[" + code + "]");
            }
        } catch (Exception failure) {
            System.err.println("sandbox-evaluation-worker[WORKER_PROTOCOL_FAILED]");
        }
        System.exit(exit);
    }

    private static void recordExecution(Path directory, String id) {
        try {
            Files.writeString(directory.resolve("execution-id.txt"), id, StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        Files.writeString(path, OptimizationJson.write(value), StandardOpenOption.CREATE_NEW);
    }
}
