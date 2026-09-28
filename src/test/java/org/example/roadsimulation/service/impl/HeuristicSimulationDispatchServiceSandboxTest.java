package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig;
import org.example.roadsimulation.optimizer.multi.ga.MultiOrderGA;
import org.example.roadsimulation.optimizer.multi.ga.MultiOrderGAConfig;
import org.example.roadsimulation.optimizer.multi.ga.MutationConfig;
import org.example.roadsimulation.optimizer.multi.init.InitialPopulationConfig;
import org.example.roadsimulation.optimizer.multi.persist.MultiOrderAssignmentMaterializer;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfiles;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeuristicSimulationDispatchServiceSandboxTest {

    @Test
    void sandboxUsesStableInputsFrozenProfileAndSeparateDomainStreams() {
        ShipmentItemRepository itemRepository = mock(ShipmentItemRepository.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        MultiOrderGA ga = mock(MultiOrderGA.class);
        MultiOrderAssignmentMaterializer materializer = mock(MultiOrderAssignmentMaterializer.class);
        DataInitializer initializer = mock(DataInitializer.class);
        SandboxRunRuntimeContext runtime = mock(SandboxRunRuntimeContext.class);
        SimulationContext simulationContext = mock(SimulationContext.class);

        ShipmentItem item2 = item(2L);
        ShipmentItem item1 = item(1L);
        Vehicle vehicle9 = vehicle(9L);
        Vehicle vehicle3 = vehicle(3L);
        when(itemRepository.findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED))
                .thenReturn(new ArrayList<>(List.of(item2, item1)));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE))
                .thenReturn(new ArrayList<>(List.of(vehicle9, vehicle3)));
        when(runtime.algorithmProfile()).thenReturn(new SandboxAlgorithmProfiles()
                .resolve("HEURISTIC", SandboxAlgorithmProfiles.HEURISTIC_V1));
        when(simulationContext.getLoopCount()).thenReturn(6);
        when(simulationContext.getCurrentSimTime())
                .thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 3, 0));
        Random initialRandom = new Random(11L);
        Random evolutionRandom = new Random(22L);
        Map<String, Object> key = Map.of("loopIndex", 6, "dispatchOrdinal", 0);
        when(runtime.javaRandom(SandboxRandomDomain.HEURISTIC_INITIAL_POPULATION, key))
                .thenReturn(initialRandom);
        when(runtime.javaRandom(SandboxRandomDomain.HEURISTIC_EVOLUTION, key))
                .thenReturn(evolutionRandom);
        MultiOrderSolution solution = new MultiOrderSolution();
        when(ga.optimize(
                any(), any(), any(MultiOrderGAConfig.class), any(InitialPopulationConfig.class),
                any(CostNormalizationConfig.class), any(MutationConfig.class),
                eq(initialRandom), eq(evolutionRandom))).thenReturn(solution);
        when(materializer.materialize(eq(solution), any(), any())).thenReturn(List.of());

        HeuristicSimulationDispatchService service = new HeuristicSimulationDispatchService(
                itemRepository, vehicleRepository, ga, materializer, initializer);
        service.setSandboxRunRuntimeContext(runtime);
        service.setSimulationContext(simulationContext);
        service.dispatch();

        assertEquals(List.of(1L, 2L), List.of(item1.getId(), item2.getId()));
        verify(ga).optimize(
                eq(List.of(item1, item2)), eq(List.of(vehicle3, vehicle9)),
                any(MultiOrderGAConfig.class), any(InitialPopulationConfig.class),
                argThat(config -> java.time.LocalDateTime.of(2026, 1, 1, 3, 0)
                        .equals(config.getEvaluationTime())),
                argThat(config -> java.time.LocalDateTime.of(2026, 1, 1, 3, 0)
                        .equals(config.getEvaluationTime())),
                eq(initialRandom), eq(evolutionRandom));
        verify(runtime).javaRandom(SandboxRandomDomain.HEURISTIC_INITIAL_POPULATION, key);
        verify(runtime).javaRandom(SandboxRandomDomain.HEURISTIC_EVOLUTION, key);
    }

    private ShipmentItem item(long id) {
        ShipmentItem item = new ShipmentItem();
        item.setId(id);
        return item;
    }

    private Vehicle vehicle(long id) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        return vehicle;
    }
}
