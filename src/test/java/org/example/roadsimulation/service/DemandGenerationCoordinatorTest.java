package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.core.SimulationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DemandGenerationCoordinatorTest {

    private DataInitializer legacyGenerator;
    private ProductionDemandGenerator productionGenerator;
    private SimulationRuntimeConfig runtimeConfig;
    private DemandGenerationCoordinator coordinator;
    private SimulationContext simulationContext;

    @BeforeEach
    void setUp() {
        legacyGenerator = mock(DataInitializer.class);
        productionGenerator = mock(ProductionDemandGenerator.class);
        runtimeConfig = new SimulationRuntimeConfig();
        simulationContext = new SimulationContext();
        simulationContext.beginRunIfAbsent();
        coordinator = new DemandGenerationCoordinator(
                legacyGenerator, productionGenerator, runtimeConfig, simulationContext);
    }

    @Test
    void defaultProductionModeUsesSixLoopCadenceWithoutLegacyDemand() {
        coordinator.generateForTick(tick(5));
        coordinator.generateForTick(tick(6));

        verify(productionGenerator, never()).generate(
                simulationContext.getSimulationRunId().orElseThrow(), tick(5));
        verify(productionGenerator).generate(
                simulationContext.getSimulationRunId().orElseThrow(), tick(6));
        verify(legacyGenerator, never()).generateGoods(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void explicitLegacyModePreservesTwoLoopCadence() {
        runtimeConfig.setDemandGenerationMode(DemandGenerationMode.LEGACY);
        coordinator.generateForTick(tick(2));
        coordinator.generateForTick(tick(3));

        verify(legacyGenerator).generateGoods(2);
        verify(legacyGenerator, never()).generateGoods(3);
        verify(productionGenerator, never()).generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mixedModeRoutesBothSourcesAtTheirOwnCadence() {
        runtimeConfig.setDemandGenerationMode(DemandGenerationMode.MIXED);
        SimulationTick tick = tick(6);

        coordinator.generateForTick(tick);

        verify(legacyGenerator).generateGoods(6);
        verify(productionGenerator).generate(
                simulationContext.getSimulationRunId().orElseThrow(), tick);
    }

    private SimulationTick tick(int loop) {
        return SimulationTick.of(
                loop,
                LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(30L * loop),
                Duration.ofMinutes(30)
        );
    }
}
