package org.example.roadsimulation.sandbox.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxRuntimePhaseOneBlockerTest {

    @Test
    void refusesToTreatBaseDataReadyAsRunnableSimulation() {
        SandboxWorkspaceException exception = assertThrows(
                SandboxWorkspaceException.class,
                SandboxRuntimePhaseOneBlocker::new
        );

        assertEquals("SANDBOX_NOT_RUN_READY", exception.errorCode());
    }
}
