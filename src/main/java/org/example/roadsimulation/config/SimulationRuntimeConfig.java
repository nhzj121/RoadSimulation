package org.example.roadsimulation.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class SimulationRuntimeConfig {

    private volatile DispatchStrategy dispatchStrategy = DispatchStrategy.ORIGINAL;

    @Value("${simulation.demand.mode:PRODUCTION}")
    private volatile DemandGenerationMode demandGenerationMode = DemandGenerationMode.PRODUCTION;

    @Value("${simulation.demand.random-seed:#{null}}")
    private volatile Long demandRandomSeed;

    public DispatchStrategy getDispatchStrategy() {
        return dispatchStrategy;
    }

    public void setDispatchStrategy(DispatchStrategy dispatchStrategy) {
        this.dispatchStrategy = dispatchStrategy == null ? DispatchStrategy.ORIGINAL : dispatchStrategy;
    }

    public boolean useHeuristic() {
        return DispatchStrategy.HEURISTIC.equals(dispatchStrategy);
    }

    public DemandGenerationMode getDemandGenerationMode() {
        return demandGenerationMode;
    }

    public void setDemandGenerationMode(DemandGenerationMode demandGenerationMode) {
        this.demandGenerationMode = demandGenerationMode == null
                ? DemandGenerationMode.PRODUCTION
                : demandGenerationMode;
    }

    public Long getDemandRandomSeed() {
        return demandRandomSeed;
    }

    public void setDemandRandomSeed(Long demandRandomSeed) {
        this.demandRandomSeed = demandRandomSeed;
    }
}
