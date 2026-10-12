package org.example.roadsimulation.sandbox.management;

import java.util.Map;
import java.util.concurrent.*;

/** Only monitors the child; main application never executes sandbox business code. */
public final class SandboxJobCoordinator implements AutoCloseable {
    private final SandboxManagementSettings settings;
    private final SandboxManagementJobStore jobs;
    private final SandboxWorkerLauncher launcher;
    private final ExecutorService monitor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "sandbox-job-monitor"); thread.setDaemon(true); return thread;
    });
    public SandboxJobCoordinator(SandboxManagementSettings settings, SandboxWorkerLauncher launcher) {
        this.settings = settings; this.launcher = launcher; jobs = new SandboxManagementJobStore(settings);
    }
    public synchronized Map<String, Object> submit(String key, int revision) {
        var accepted = jobs.reserve(key, revision);
        dispatch(accepted.get("job_id").toString());
        return accepted;
    }
    public synchronized SandboxManagementJobStore.StartReceipt submitRequest(String key, int revision, String requestId) {
        var reservation = jobs.reserveRequest(key, revision, requestId);
        if (reservation.created()) dispatch(reservation.job().get("job_id").toString());
        return SandboxManagementJobStore.receipt(reservation, requestId);
    }
    private void dispatch(String id) {
        try { monitor.execute(() -> supervise(id)); }
        catch (RejectedExecutionException failure) { jobs.failBeforeExecution(id, "MANAGER_STOPPED"); throw failure; }
    }
    private void supervise(String id) {
        Process child;
        try { child = launcher.launch(id, settings); }
        catch (Exception failure) {
            try { jobs.failBeforeExecution(id, "WORKER_LAUNCH_FAILED"); }
            catch (Exception unsafeToRelease) { try { jobs.interrupted(id); } catch (Exception ignored) {} }
            return;
        }
        try { child.waitFor(); jobs.interrupted(id); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); /* Child owns durable state. Never destroy it. */ }
        catch (Exception ignored) { /* Database unavailable: preserve reservation, inspect later. */ }
    }
    @Override public void close() { monitor.shutdown(); /* No cancel/kill on main application shutdown. */ }
}
