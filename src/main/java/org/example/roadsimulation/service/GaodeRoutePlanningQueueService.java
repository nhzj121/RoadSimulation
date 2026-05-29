package org.example.roadsimulation.service;

import jakarta.annotation.PreDestroy;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GaodeRoutePlanningQueueService {

    private static final Logger log = LoggerFactory.getLogger(GaodeRoutePlanningQueueService.class);

    private static final int MAX_QUEUE_SIZE = 1000;
    private static final long REQUEST_TIMEOUT_SECONDS = 120;
    private static final long REQUEST_INTERVAL_MS = 400;

    private final GaodeMapService gaodeMapService;
    private final LinkedBlockingQueue<RouteTask> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean workerRunning = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong(0);
    private final AtomicReference<RouteTask> activeTask = new AtomicReference<>();
    private final AtomicReference<Future<?>> activeCall = new AtomicReference<>();
    private final ExecutorService routeCallExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "gaode-route-planning-call");
        thread.setDaemon(true);
        return thread;
    });
    private final Object pauseMonitor = new Object();
    private final Thread workerThread;

    public GaodeRoutePlanningQueueService(GaodeMapService gaodeMapService) {
        this.gaodeMapService = gaodeMapService;
        this.workerThread = new Thread(this::runWorker, "gaode-route-planning-queue");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public GaodeRouteResponse submitAndWait(GaodeRouteRequest request) {
        if (!accepting.get()) {
            return GaodeRouteResponse.error("Gaode route planning queue is not accepting requests");
        }

        RouteTask task = new RouteTask(copyRequest(request), generation.get());
        if (!queue.offer(task)) {
            return GaodeRouteResponse.error("Gaode route planning queue is full");
        }

        try {
            return task.future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GaodeRouteResponse.error("Gaode route planning interrupted");
        } catch (TimeoutException e) {
            task.future.cancel(false);
            return GaodeRouteResponse.error("Gaode route planning timed out in queue");
        } catch (ExecutionException e) {
            return GaodeRouteResponse.error("Gaode route planning failed: " + e.getMessage());
        }
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
        synchronized (pauseMonitor) {
            pauseMonitor.notifyAll();
        }
    }

    public void pauseAndCancelPending() {
        long nextGeneration = generation.incrementAndGet();
        pause();
        discardQueuedTasks("Gaode route planning task discarded by simulation pause");
        completeActiveTask("Gaode route planning interrupted by simulation pause");
        cancelActiveCall();
        log.info("Gaode route planning queue paused and cleared. generation={}", nextGeneration);
    }

    public void reset() {
        long nextGeneration = generation.incrementAndGet();
        pause();

        discardQueuedTasks("Gaode route planning task discarded by simulation reset");
        completeActiveTask("Gaode route planning result discarded by simulation reset");
        cancelActiveCall();

        log.info("Gaode route planning queue reset. generation={}", nextGeneration);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public long getGeneration() {
        return generation.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        shutdown();
    }

    @PreDestroy
    public void shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }

        accepting.set(false);
        workerRunning.set(false);
        generation.incrementAndGet();
        discardQueuedTasks("Gaode route planning task discarded by application shutdown");
        completeActiveTask("Gaode route planning interrupted by application shutdown");
        cancelActiveCall();

        resume();
        workerThread.interrupt();
        routeCallExecutor.shutdownNow();
    }

    private void runWorker() {
        while (workerRunning.get()) {
            try {
                waitIfPaused();

                RouteTask task = queue.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }

                activeTask.set(task);
                try {
                    if (task.future.isCancelled()) {
                        continue;
                    }

                    waitIfPaused();

                    if (task.generation != generation.get() || !workerRunning.get()) {
                        task.future.complete(GaodeRouteResponse.error("Gaode route planning task is stale"));
                        continue;
                    }

                    Future<GaodeRouteResponse> call = routeCallExecutor.submit(
                            () -> gaodeMapService.planDrivingRoute(task.request)
                    );
                    activeCall.set(call);

                    GaodeRouteResponse response = call.get();

                    if (task.generation != generation.get()) {
                        task.future.complete(GaodeRouteResponse.error("Gaode route planning result discarded by simulation reset"));
                    } else if (!task.future.isDone()) {
                        task.future.complete(response);
                    }
                } catch (CancellationException e) {
                    if (!task.future.isDone()) {
                        task.future.complete(GaodeRouteResponse.error("Gaode route planning cancelled"));
                    }
                } catch (ExecutionException e) {
                    if (!task.future.isDone()) {
                        task.future.complete(GaodeRouteResponse.error("Gaode route planning failed: " + e.getMessage()));
                    }
                } finally {
                    activeTask.compareAndSet(task, null);
                    activeCall.set(null);
                }

                if (workerRunning.get()) {
                    Thread.sleep(REQUEST_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                RouteTask runningTask = activeTask.getAndSet(null);
                if (runningTask != null && !runningTask.future.isDone()) {
                    runningTask.future.complete(GaodeRouteResponse.error("Gaode route planning interrupted"));
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Gaode route planning queue worker failed", e);
            }
        }
    }

    private void discardQueuedTasks(String message) {
        List<RouteTask> pendingTasks = new ArrayList<>();
        queue.drainTo(pendingTasks);
        for (RouteTask task : pendingTasks) {
            task.future.complete(GaodeRouteResponse.error(message));
        }
    }

    private void completeActiveTask(String message) {
        RouteTask runningTask = activeTask.get();
        if (runningTask != null && !runningTask.future.isDone()) {
            runningTask.future.complete(GaodeRouteResponse.error(message));
        }
    }

    private void cancelActiveCall() {
        Future<?> runningCall = activeCall.getAndSet(null);
        if (runningCall != null) {
            runningCall.cancel(true);
        }
    }

    private void waitIfPaused() throws InterruptedException {
        if (!paused.get()) {
            return;
        }

        synchronized (pauseMonitor) {
            while (paused.get() && workerRunning.get()) {
                pauseMonitor.wait();
            }
        }
    }

    private GaodeRouteRequest copyRequest(GaodeRouteRequest source) {
        GaodeRouteRequest copy = new GaodeRouteRequest();
        if (source == null) {
            return copy;
        }

        copy.setOrigin(source.getOrigin());
        copy.setDestination(source.getDestination());
        copy.setStrategy(source.getStrategy());
        copy.setWaypoints(source.getWaypoints());
        copy.setAvoidpolygons(source.getAvoidpolygons());
        copy.setPlate(source.getPlate());
        copy.setCartype(source.getCartype());
        copy.setFerry(source.getFerry());
        copy.setMaxDirectionChangeCount(source.getMaxDirectionChangeCount());
        copy.setMaxRampCount(source.getMaxRampCount());
        return copy;
    }

    private static class RouteTask {
        private final GaodeRouteRequest request;
        private final long generation;
        private final CompletableFuture<GaodeRouteResponse> future = new CompletableFuture<>();

        private RouteTask(GaodeRouteRequest request, long generation) {
            this.request = request;
            this.generation = generation;
        }
    }
}
