package org.example.roadsimulation.sandbox.run;

import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.sandbox.random.SplitMix64Random;
import org.example.roadsimulation.sandbox.random.SplitMix64V1;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxRuntimeConfigurationTest {

    @Test
    void splitMixRandomAdapterUsesThePersistedGenerator() {
        long seed = 0x123456789abcdef0L;
        SplitMix64V1 expected = new SplitMix64V1(seed);
        SplitMix64Random actual = new SplitMix64Random(seed);

        assertEquals(expected.nextLong(), actual.nextLong());
        assertEquals(expected.nextLong(), actual.nextLong());
    }

    @Test
    void publishedConfigurationCannotBeChangedAtRuntime() {
        SimulationRuntimeConfig config = new SimulationRuntimeConfig();
        SandboxAlgorithmProfile profile = new SandboxAlgorithmProfiles()
                .resolve("ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1);

        config.freezeForSandbox(
                DispatchStrategy.ORIGINAL,
                DemandGenerationMode.PRODUCTION,
                "20260927",
                profile);

        assertTrue(config.isSandboxFrozen());
        assertEquals(profile, config.getSandboxAlgorithmProfile());
        config.setDispatchStrategy(DispatchStrategy.ORIGINAL);
        assertThrows(IllegalStateException.class,
                () -> config.setDispatchStrategy(DispatchStrategy.HEURISTIC));
        assertThrows(IllegalStateException.class,
                () -> config.setDemandGenerationMode(DemandGenerationMode.LEGACY));
        assertThrows(IllegalStateException.class,
                () -> config.setDemandRandomSeed(1L));
    }

    @Test
    void deterministicRunIdSurvivesReset() {
        SimulationContext context = new SimulationContext();
        String runId = "sandbox-0123456789abcdef";
        context.configureDeterministicRun(
                runId, LocalDateTime.of(2026, 1, 1, 0, 0), 1800);

        assertEquals(runId, context.beginRunIfAbsent());
        context.reset();
        assertEquals(runId, context.beginRunIfAbsent());
    }
}
