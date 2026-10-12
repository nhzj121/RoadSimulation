package org.example.roadsimulation.sandbox.execution;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** One fresh runtime context executes exactly one published, fixed-horizon run. */
@Component
@Profile("sandbox-runtime")
public final class SandboxControlledRunExecutor {
    private final SimulationMainLoop mainLoop;
    private final SimulationContext clock;
    private final SandboxRunRuntimeContext runtime;
    private final AtomicBoolean claimed = new AtomicBoolean();

    public SandboxControlledRunExecutor(SimulationMainLoop mainLoop, SimulationContext clock,
                                        SandboxRunRuntimeContext runtime) {
        this.mainLoop = mainLoop;
        this.clock = clock;
        this.runtime = runtime;
    }

    public Result execute(SandboxExecutionLease lease, SandboxTickObserver observer) {
        Objects.requireNonNull(lease, "execution lease is required");
        Objects.requireNonNull(observer, "tick observer is required");
        if (!runtime.isV2() || !clock.isDeterministicSandboxRun()
                || clock.getLoopCount() != 0 || clock.isRunning() || clock.isResetting()) {
            throw new SandboxWorkspaceException("SANDBOX_EXECUTION_NOT_FRESH",
                    "Execution requires a fresh, prepared v2 runtime at loop zero");
        }
        lease.requireCompatible(runtime);
        int horizon = runtime.simulationClock().totalLoops();
        if (horizon <= 0 || !claimed.compareAndSet(false, true)) {
            throw new SandboxWorkspaceException("SANDBOX_EXECUTION_ALREADY_CLAIMED",
                    "A runtime context may execute once; prepare again in a fresh process");
        }
        try {
            org.example.roadsimulation.entity.CostEntity.reset();
            if (lease.stopAtBoundary(false)) {
                lease.beginFinalization();
                return new Result(runtime.deterministicSimulationRunId(), horizon, 0, true);
            }
            for (int index = 0; index < horizon; index++) {
                lease.requireHeld();
                if (Thread.currentThread().isInterrupted()) {
                    throw new SandboxWorkspaceException("SANDBOX_EXECUTION_INTERRUPTED",
                            "Execution interrupted before loop " + index);
                }
                var tick = clock.getCurrentTick();
                if (tick.loopIndex() != index) {
                    throw new SandboxWorkspaceException("SANDBOX_LOOP_SEQUENCE_MISMATCH",
                            "Unexpected clock position before loop " + index);
                }
                var evaluation = mainLoop.executeControlledTick(index);
                if (clock.getLoopCount() != index + 1 || evaluation.loopIndex() != index
                        || !tick.tickEnd().equals(evaluation.simTime())) {
                    throw new SandboxWorkspaceException("SANDBOX_LOOP_SEQUENCE_MISMATCH",
                            "Tick did not produce its expected completed state");
                }
                observer.afterCompletedTick(tick, evaluation);
                if (lease.stopAtBoundary(index + 1 == horizon)) {
                    lease.beginFinalization();
                    return new Result(runtime.deterministicSimulationRunId(), horizon, clock.getLoopCount(), true);
                }
            }
            lease.requireHeld();
            lease.beginFinalization();
            return new Result(runtime.deterministicSimulationRunId(), horizon, clock.getLoopCount());
        } finally {
            mainLoop.stop();
        }
    }

    public record Result(String deterministicRunId, int requestedLoops, int completedLoops, boolean cancelled) {
        public Result(String deterministicRunId, int requestedLoops, int completedLoops) {
            this(deterministicRunId, requestedLoops, completedLoops, false);
        }
    }
}
