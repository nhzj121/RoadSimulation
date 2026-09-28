package org.example.roadsimulation.controller;

import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationControllerSandboxStrategyTest {

    @Test
    void publishedStrategyIsUsedWhenStartRequestDoesNotOverrideIt() {
        SimulationController controller = controllerWithFrozenHeuristic();

        DispatchStrategy result = ReflectionTestUtils.invokeMethod(
                controller, "resolveDispatchStrategy", new SimulationController.StartSimulationRequest());

        assertEquals(DispatchStrategy.HEURISTIC, result);
    }

    @Test
    void explicitStrategyOverrideIsRejectedAfterSandboxFreeze() {
        SimulationController controller = controllerWithFrozenHeuristic();
        SimulationController.StartSimulationRequest request = new SimulationController.StartSimulationRequest();
        request.setUseHeuristic(false);

        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "resolveDispatchStrategy", request));
    }

    private SimulationController controllerWithFrozenHeuristic() {
        SimulationRuntimeConfig config = new SimulationRuntimeConfig();
        config.freezeForSandbox(
                DispatchStrategy.HEURISTIC,
                DemandGenerationMode.PRODUCTION,
                "20260927",
                new SandboxAlgorithmProfiles().resolve(
                        "HEURISTIC", SandboxAlgorithmProfiles.HEURISTIC_V1));
        SimulationController controller = new SimulationController();
        ReflectionTestUtils.setField(controller, "simulationRuntimeConfig", config);
        return controller;
    }
}
