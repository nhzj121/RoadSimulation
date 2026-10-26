package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.core.SimulationModeGuard;
import org.example.roadsimulation.sandbox.management.SandboxManagementService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** UI navigation policy only; not a run mutex or a simulation control operation. */
@RestController
@Profile("!sandbox-runtime & !sandbox-controlled-runtime")
public class SimulationNavigationController {
    private final SimulationMainLoop loop;
    private final SimulationModeGuard modes;
    private final ObjectProvider<SandboxManagementService> sandbox;

    public SimulationNavigationController(SimulationMainLoop loop, SimulationModeGuard modes,
                                          ObjectProvider<SandboxManagementService> sandbox) {
        this.loop = loop; this.modes = modes; this.sandbox = sandbox;
    }

    @GetMapping("/api/simulation/navigation-state")
    public ResponseEntity<NavigationState> state() {
        boolean running = loop.isRunning(), busy = loop.isNavigationLifecycleBusy();
        boolean comparison = modes.isDispatchComparisonExperimentActive();
        var management = sandbox.getIfAvailable();
        boolean sandboxBusy = false;
        String workspace = null;
        try {
            if (management != null) {
                var observation = management.navigationObservation();
                sandboxBusy = observation.busy(); workspace = observation.workspaceState();
            }
        } catch (RuntimeException failure) {
            // Never forward SQL, JDBC settings, credentials or exception text.
            return ResponseEntity.status(503).body(new NavigationState("mode-navigation-state/v1", false,
                    false, "NAVIGATION_STATE_UNCONFIRMED", running, busy, comparison, true, null, null));
        }
        String reason = comparison ? "COMPARISON_ACTIVE" : busy ? "ORDINARY_TRANSITION_BUSY"
                : running ? "ORDINARY_RUNNING" : sandboxBusy ? "SANDBOX_BUSY" : null;
        return ResponseEntity.ok(new NavigationState("mode-navigation-state/v1", true, reason == null,
                reason, running, busy, comparison, management != null, sandboxBusy, workspace));
    }

    public record NavigationState(String artifactVersion, boolean confirmed, boolean canSwitch,
            String reason, boolean ordinaryRunning, boolean ordinaryBusy, boolean comparisonActive,
            boolean sandboxManagementEnabled, Boolean sandboxBusy, String sandboxWorkspaceState) {}
}
