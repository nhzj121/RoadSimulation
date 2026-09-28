package org.example.roadsimulation.config;

import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class SimulationRuntimeConfig {

    private volatile DispatchStrategy dispatchStrategy = DispatchStrategy.ORIGINAL;

    @Value("${simulation.demand.mode:PRODUCTION}")
    private volatile DemandGenerationMode demandGenerationMode = DemandGenerationMode.PRODUCTION;

    @Value("${simulation.demand.random-seed:#{null}}")
    private volatile Long demandRandomSeed;

    private volatile boolean sandboxFrozen;
    private volatile String sandboxRootSeed;
    private volatile SandboxAlgorithmProfile sandboxAlgorithmProfile;

    public DispatchStrategy getDispatchStrategy() {
        return dispatchStrategy;
    }

    public void setDispatchStrategy(DispatchStrategy dispatchStrategy) {
        DispatchStrategy normalized = dispatchStrategy == null ? DispatchStrategy.ORIGINAL : dispatchStrategy;
        requireMutableOrEqual("dispatch strategy", this.dispatchStrategy, normalized);
        this.dispatchStrategy = normalized;
    }

    public boolean useHeuristic() {
        return DispatchStrategy.HEURISTIC.equals(dispatchStrategy);
    }

    public DemandGenerationMode getDemandGenerationMode() {
        return demandGenerationMode;
    }

    public void setDemandGenerationMode(DemandGenerationMode demandGenerationMode) {
        DemandGenerationMode normalized = demandGenerationMode == null
                ? DemandGenerationMode.PRODUCTION
                : demandGenerationMode;
        requireMutableOrEqual("demand generation mode", this.demandGenerationMode, normalized);
        this.demandGenerationMode = normalized;
    }

    public Long getDemandRandomSeed() {
        return demandRandomSeed;
    }

    public void setDemandRandomSeed(Long demandRandomSeed) {
        requireMutableOrEqual("demand random seed", this.demandRandomSeed, demandRandomSeed);
        this.demandRandomSeed = demandRandomSeed;
    }

    public synchronized void freezeForSandbox(
            DispatchStrategy dispatchStrategy,
            DemandGenerationMode demandGenerationMode,
            String rootSeed,
            SandboxAlgorithmProfile algorithmProfile
    ) {
        if (sandboxFrozen) {
            throw new IllegalStateException("sandbox runtime configuration is already frozen");
        }
        if (dispatchStrategy == null || demandGenerationMode != DemandGenerationMode.PRODUCTION
                || algorithmProfile == null) {
            throw new IllegalArgumentException("complete PRODUCTION sandbox configuration is required");
        }
        long parsedRootSeed = SandboxRandomProtocol.validateRootSeed(rootSeed);
        this.dispatchStrategy = dispatchStrategy;
        this.demandGenerationMode = demandGenerationMode;
        this.demandRandomSeed = parsedRootSeed;
        this.sandboxRootSeed = rootSeed;
        this.sandboxAlgorithmProfile = algorithmProfile;
        this.sandboxFrozen = true;
    }

    public boolean isSandboxFrozen() {
        return sandboxFrozen;
    }

    public String getSandboxRootSeed() {
        return sandboxRootSeed;
    }

    public SandboxAlgorithmProfile getSandboxAlgorithmProfile() {
        return sandboxAlgorithmProfile;
    }

    private void requireMutableOrEqual(String field, Object current, Object requested) {
        if (sandboxFrozen && !java.util.Objects.equals(current, requested)) {
            throw new IllegalStateException(field + " is frozen by the published sandbox run specification");
        }
    }
}
