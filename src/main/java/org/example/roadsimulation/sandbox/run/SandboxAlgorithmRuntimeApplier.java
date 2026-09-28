package org.example.roadsimulation.sandbox.run;

import jakarta.annotation.PostConstruct;
import org.example.roadsimulation.service.OriginalVrpDispatchPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Applies the published immutable profile to algorithm services with mutable legacy configuration. */
@Component
@Profile("sandbox-runtime")
public final class SandboxAlgorithmRuntimeApplier {
    private final SandboxRunRuntimeContext runtimeContext;
    private final OriginalVrpDispatchPolicy originalPolicy;

    public SandboxAlgorithmRuntimeApplier(
            SandboxRunRuntimeContext runtimeContext,
            OriginalVrpDispatchPolicy originalPolicy
    ) {
        this.runtimeContext = runtimeContext;
        this.originalPolicy = originalPolicy;
    }

    @PostConstruct
    public void apply() {
        if ("ORIGINAL".equals(runtimeContext.specification().dispatch().strategy())) {
            SandboxAlgorithmRuntimeConfiguration.applyOriginal(
                    runtimeContext.algorithmProfile(), originalPolicy);
        } else if (!"HEURISTIC".equals(runtimeContext.specification().dispatch().strategy())) {
            throw new SandboxRunException(
                    "UNSUPPORTED_DISPATCH_STRATEGY",
                    "Sandbox runtime supports only ORIGINAL or HEURISTIC dispatch");
        }
    }
}
