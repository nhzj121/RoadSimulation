package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig;
import org.example.roadsimulation.optimizer.multi.insertion.FeasibleInsertionService;
import org.example.roadsimulation.optimizer.multi.init.InitialPopulationConfig;
import org.example.roadsimulation.optimizer.multi.init.MultiOrderInitialPopulationBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MultiOrderRandomContractTest {

    @Test
    void operatorsRejectMissingRandomSourceInsteadOfFallingBack() {
        FeasibleInsertionService insertionService = mock(FeasibleInsertionService.class);
        MultiOrderCrossoverOperator crossover = new MultiOrderCrossoverOperator(
                mock(RouteGeneSelector.class), insertionService);
        MultiOrderMutationOperator mutation = new MultiOrderMutationOperator(insertionService);

        assertThrows(NullPointerException.class, () -> crossover.crossover(
                new MultiOrderSolution(), new MultiOrderSolution(), List.of(), List.of(),
                new CostNormalizationConfig(), new MutationConfig(), null));
        assertThrows(NullPointerException.class, () -> mutation.mutate(
                new MultiOrderSolution(), List.of(), List.of(), new MutationConfig(), null));
    }

    @Test
    void initialPopulationBuilderRejectsMissingRandomSource() {
        MultiOrderInitialPopulationBuilder builder = new MultiOrderInitialPopulationBuilder(
                mock(FeasibleInsertionService.class));
        ShipmentItem item = new ShipmentItem();
        item.setId(1L);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);

        assertThrows(NullPointerException.class, () -> builder.buildInitialPopulation(
                List.of(item), List.of(vehicle), new InitialPopulationConfig(),
                new MutationConfig(), null));
    }
}
