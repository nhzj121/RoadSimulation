package org.example.roadsimulation.sandbox.workspace;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The ordinary scheduled/web application is never a sandbox execution entry.
 * Published runs use the separate controlled bootstrap; path freezing is deferred.
 */
@Configuration(proxyBeanMethods = false)
@Profile("sandbox-runtime")
public class SandboxRuntimePhaseOneBlocker {

    public SandboxRuntimePhaseOneBlocker() {
        throw new SandboxWorkspaceException(
                "SANDBOX_NOT_RUN_READY",
                "Ordinary application startup cannot execute a sandbox; use the independent "
                        + "controlled execution entry for an explicitly prepared published revision"
        );
    }
}
