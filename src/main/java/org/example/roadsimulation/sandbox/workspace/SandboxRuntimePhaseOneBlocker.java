package org.example.roadsimulation.sandbox.workspace;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Phase three deliberately stops at RUN_SPEC_READY. Path facts and the full
 * simulation replay contract are later-phase requirements.
 */
@Configuration(proxyBeanMethods = false)
@Profile("sandbox-runtime")
public class SandboxRuntimePhaseOneBlocker {

    public SandboxRuntimePhaseOneBlocker() {
        throw new SandboxWorkspaceException(
                "SANDBOX_NOT_RUN_READY",
                "Sandbox workspace is RUN_SPEC_READY at most; path facts and full simulation "
                        + "execution are intentionally not enabled in phase three"
        );
    }
}
