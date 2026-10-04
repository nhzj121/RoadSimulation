package org.example.roadsimulation.sandbox.execution;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;

/** Called synchronously after a successfully completed business tick. */
@FunctionalInterface
public interface SandboxTickObserver {
    void afterCompletedTick(SimulationTick tick, EvaluationSnapshot evaluation);
}
