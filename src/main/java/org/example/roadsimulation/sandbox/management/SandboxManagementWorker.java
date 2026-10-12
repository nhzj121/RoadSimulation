package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.execution.bootstrap.SandboxControlledApplication;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.util.Map;

/** Dedicated JVM entry. Receives only a job UUID; credentials never appear in process arguments. */
public final class SandboxManagementWorker {
    private SandboxManagementWorker() {}
    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        int exit = 2;
        try {
            if (args.length != 1) throw new IllegalArgumentException("Worker requires exactly one job ID");
            var settings = new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"),
                    System.getenv("SANDBOX_DB_USER"), System.getenv("SANDBOX_DB_PASSWORD"));
            exit = run(args[0], settings, new ObjectMapper().findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        } catch (Exception failure) { System.err.println("sandbox-worker-error: configuration or startup failed"); }
        finally {
            try { Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread").getMethod("checkedShutdown").invoke(null); }
            catch (ReflectiveOperationException ignored) {}
        }
        if (exit != 0) System.exit(exit);
    }

    /** Additional sources are trusted integration fixtures, not exposed as HTTP/CLI options. */
    public static int run(String jobId, SandboxManagementSettings settings, ObjectMapper json, Class<?>... sources) {
        var jobs = new SandboxManagementJobStore(settings);
        int[] completed = {0};
        boolean claimed = false;
        try {
            jobs.claim(jobId); claimed = true;
            var job = jobs.get(jobId);
            try (var lease = SandboxExecutionLease.prepareAndAcquire(settings.url(), settings.user(), settings.password(),
                    settings.baseline(), job.get("run_spec_key").toString(), ((Number) job.get("run_spec_revision")).intValue(), json, jobId)) {
                lease.create(json);
                try {
                    SandboxControlledRunExecutor.Result result;
                    try (var context = SandboxControlledApplication.open(settings.url(), settings.user(), settings.password(), Map.of(), sources)) {
                        lease.markRunning();
                        result = context.getBean(SandboxControlledRunExecutor.class).execute(lease, (tick, evaluation) -> {
                            lease.recordTick(tick, evaluation, context.getBean(SandboxRuntimeFactCollector.class).capture(), json);
                            completed[0]++;
                        });
                    }
                    new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json).verify(lease.executionId());
                    if (result.cancelled()) lease.finishCancelled(completed[0]);
                    else lease.finish(completed[0], null);
                    return 0;
                } catch (Exception failure) {
                    lease.beginFailureFinalization();
                    lease.finish(completed[0], failure);
                    System.err.println("sandbox-worker-error[" + errorCode(failure) + "]");
                    return 2;
                }
            }
        } catch (Exception failure) {
            if (claimed) {
                try { jobs.failBeforeExecution(jobId, errorCode(failure)); }
                catch (Exception unsafeToRelease) { try { jobs.interrupted(jobId); } catch (Exception ignored) {} }
            }
            System.err.println("sandbox-worker-error[" + errorCode(failure) + "]");
            return 2;
        }
    }
    private static String errorCode(Exception failure) {
        return failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode() : "WORKER_FAILED";
    }
}
