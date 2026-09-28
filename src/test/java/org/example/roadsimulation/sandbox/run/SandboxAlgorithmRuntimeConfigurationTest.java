package org.example.roadsimulation.sandbox.run;

import org.example.roadsimulation.service.OriginalVrpDispatchPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxAlgorithmRuntimeConfigurationTest {

    private final SandboxAlgorithmProfiles profiles = new SandboxAlgorithmProfiles();

    @Test
    void appliesOriginalProfileWithoutDependingOnApplicationProperties() {
        OriginalVrpDispatchPolicy policy = new OriginalVrpDispatchPolicy(0.1, 1.0, 2.0, 3.0);

        SandboxAlgorithmRuntimeConfiguration.applyOriginal(
                profiles.resolve("ORIGINAL", SandboxAlgorithmProfiles.ORIGINAL_V1), policy);

        assertEquals(0.80, policy.getMinLoadFactor());
        assertEquals(400.0, policy.getMaxAnchorDistanceKm());
        assertEquals(4000.0, policy.getMaxMarginalCost());
        assertEquals(0.02, policy.getMinAddedTonsPerExtraKm());
        assertTrue(policy.isSandboxFrozen());
        policy.setMinLoadFactor(0.80);
        assertThrows(IllegalStateException.class, () -> policy.setMinLoadFactor(0.70));
    }

    @Test
    void mapsEveryHeuristicProfileSectionToRuntimeConfiguration() {
        SandboxAlgorithmRuntimeConfiguration.HeuristicConfiguration configuration =
                SandboxAlgorithmRuntimeConfiguration.heuristic(
                        profiles.resolve("HEURISTIC", SandboxAlgorithmProfiles.HEURISTIC_V1));

        assertEquals(20, configuration.ga().getPopulationSize());
        assertEquals(30, configuration.ga().getMaxGeneration());
        assertEquals(20, configuration.initialPopulation().getPopulationSize());
        assertEquals(10, configuration.initialPopulation().getGreedyEliteCount());
        assertEquals(0.45, configuration.mutation().getSingleReinsertProbability());
        assertEquals(5, configuration.mutation().getMaxDestroyCount());
        assertEquals(1_000_000.0, configuration.costNormalization().getHardConstraintPenalty());
        assertEquals(0.50, configuration.costNormalization().getIdealUtilization());
    }
}
