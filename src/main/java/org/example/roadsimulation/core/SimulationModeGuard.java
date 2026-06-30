package org.example.roadsimulation.core;

import org.springframework.stereotype.Component;

@Component
public class SimulationModeGuard {

    private volatile boolean dispatchComparisonExperimentActive = false;

    public boolean isDispatchComparisonExperimentActive() {
        return dispatchComparisonExperimentActive;
    }

    public void markDispatchComparisonExperimentActive() {
        dispatchComparisonExperimentActive = true;
    }

    public void clearDispatchComparisonExperimentActive() {
        dispatchComparisonExperimentActive = false;
    }
}
