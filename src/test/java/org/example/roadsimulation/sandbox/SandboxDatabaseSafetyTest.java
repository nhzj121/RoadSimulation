package org.example.roadsimulation.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxDatabaseSafetyTest {

    @Test
    void rejectsMainBusinessDatabase() {
        assertThrows(IllegalStateException.class, () ->
                SandboxDatabaseSafety.requireEphemeralSandbox(
                        "jdbc:mysql://localhost:3306/vehicle_scheduler?useSSL=false"
                ));
    }

    @Test
    void rejectsMissingOrUnsupportedTargets() {
        assertThrows(IllegalStateException.class, () ->
                SandboxDatabaseSafety.requireEphemeralSandbox(null));
        assertThrows(IllegalStateException.class, () ->
                SandboxDatabaseSafety.requireEphemeralSandbox("jdbc:h2:mem:test"));
        assertThrows(IllegalStateException.class, () ->
                SandboxDatabaseSafety.requireEphemeralSandbox(
                        "jdbc:mariadb://localhost:3307/unrelated_test"
                ));
    }

    @Test
    void acceptsOnlyDedicatedSandboxDatabasePrefix() {
        assertDoesNotThrow(() ->
                SandboxDatabaseSafety.requireEphemeralSandbox(
                        "jdbc:mariadb://localhost:49152/road_simulation_sandbox_test"
                ));
        assertDoesNotThrow(() ->
                SandboxDatabaseSafety.requireEphemeralSandbox(
                        "jdbc:mysql://docker-host:49152/road_simulation_sandbox_test_run_2?useSSL=false"
                ));
    }
}
