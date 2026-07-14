package org.example.roadsimulation.config;

import org.springframework.stereotype.Component;

/**
 * Process-local runtime selection used by both the ordinary loop and comparison experiments.
 */
@Component
public class SimulationRuntimeConfig {

    private volatile DispatchStrategy dispatchStrategy = DispatchStrategy.ORIGINAL;

    public DispatchStrategy getDispatchStrategy() {
        return dispatchStrategy;
    }

    public void setDispatchStrategy(DispatchStrategy dispatchStrategy) {
        this.dispatchStrategy = dispatchStrategy == null ? DispatchStrategy.ORIGINAL : dispatchStrategy;
    }

    public boolean useHeuristic() {
        return DispatchStrategy.HEURISTIC.equals(dispatchStrategy);
    }
}
