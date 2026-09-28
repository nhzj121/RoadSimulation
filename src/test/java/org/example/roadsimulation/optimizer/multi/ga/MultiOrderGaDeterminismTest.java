package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.VehicleRouteGene;
import org.example.roadsimulation.optimizer.multi.cost.MultiOrderCostEvaluator;
import org.example.roadsimulation.optimizer.multi.insertion.FeasibleInsertionService;
import org.example.roadsimulation.optimizer.multi.insertion.RouteSequenceCostEstimator;
import org.example.roadsimulation.optimizer.multi.init.MultiOrderInitialPopulationBuilder;
import org.example.roadsimulation.optimizer.node.AssignmentNodeFactory;
import org.example.roadsimulation.optimizer.node.AssignmentNodeSequenceValidator;
import org.example.roadsimulation.sandbox.random.SplitMix64Random;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfiles;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmRuntimeConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiOrderGaDeterminismTest {

    @Test
    void sameDomainSeedsProduceSameSolutionWhenInputOrderChanges() {
        MultiOrderGA ga = algorithm();
        List<ShipmentItem> items = List.of(
                item(10L, 2.0, 1.0, poi(101L, "104.00", "30.00"), poi(201L, "104.20", "30.20")),
                item(11L, 3.0, 1.5, poi(102L, "104.05", "30.05"), poi(202L, "104.25", "30.25")),
                item(12L, 1.0, 0.5, poi(103L, "104.10", "30.10"), poi(203L, "104.30", "30.30")));
        List<Vehicle> vehicles = List.of(vehicle(1L, poi(301L, "104.00", "30.00")),
                vehicle(2L, poi(302L, "104.15", "30.15")));

        MultiOrderSolution first = optimize(ga, items, vehicles);
        List<ShipmentItem> reversedItems = new ArrayList<>(items);
        java.util.Collections.reverse(reversedItems);
        List<Vehicle> reversedVehicles = new ArrayList<>(vehicles);
        java.util.Collections.reverse(reversedVehicles);
        MultiOrderSolution repeated = optimize(ga, reversedItems, reversedVehicles);

        assertEquals(first.getCost(), repeated.getCost());
        assertEquals(fingerprint(first), fingerprint(repeated));
    }

    private MultiOrderSolution optimize(
            MultiOrderGA ga,
            List<ShipmentItem> items,
            List<Vehicle> vehicles
    ) {
        var configuration = SandboxAlgorithmRuntimeConfiguration.heuristic(
                new SandboxAlgorithmProfiles().resolve(
                        "HEURISTIC", SandboxAlgorithmProfiles.HEURISTIC_V1));
        LocalDateTime decisionTime = LocalDateTime.of(2026, 1, 1, 3, 0);
        configuration.costNormalization().setEvaluationTime(decisionTime);
        configuration.mutation().setEvaluationTime(decisionTime);
        return ga.optimize(
                items, vehicles,
                configuration.ga(), configuration.initialPopulation(),
                configuration.costNormalization(), configuration.mutation(),
                new SplitMix64Random(1001L), new SplitMix64Random(2002L));
    }

    private MultiOrderGA algorithm() {
        AssignmentNodeSequenceValidator validator = new AssignmentNodeSequenceValidator();
        RouteSequenceCostEstimator estimator = new RouteSequenceCostEstimator();
        FeasibleInsertionService insertion = new FeasibleInsertionService(
                new AssignmentNodeFactory(), validator, estimator);
        return new MultiOrderGA(
                new MultiOrderInitialPopulationBuilder(insertion),
                new MultiOrderCostEvaluator(validator, estimator),
                new MultiOrderCrossoverOperator(new RouteGeneSelector(estimator), insertion),
                new MultiOrderMutationOperator(insertion));
    }

    private ShipmentItem item(long id, double weight, double volume, POI origin, POI destination) {
        Shipment shipment = new Shipment("S-" + id, origin, destination, weight, volume);
        shipment.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        ShipmentItem item = new ShipmentItem(shipment, "item-" + id, 1, "SKU-" + id, weight, volume);
        item.setId(id);
        return item;
    }

    private Vehicle vehicle(long id, POI currentPoi) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setMaxLoadCapacity(10.0);
        vehicle.setCargoVolume(10.0);
        vehicle.setCurrentPOI(currentPoi);
        return vehicle;
    }

    private POI poi(long id, String longitude, String latitude) {
        POI poi = new POI();
        poi.setId(id);
        poi.setLongitude(new BigDecimal(longitude));
        poi.setLatitude(new BigDecimal(latitude));
        return poi;
    }

    private String fingerprint(MultiOrderSolution solution) {
        StringBuilder value = new StringBuilder();
        solution.getVehicleRoutes().stream()
                .sorted(Comparator.comparing(VehicleRouteGene::getVehicleId))
                .forEach(route -> {
                    value.append(route.getVehicleId()).append(':');
                    route.getNodes().forEach(node -> value.append(node.getShipmentItemId())
                            .append('/').append(node.getActionType()).append(','));
                    value.append(';');
                });
        value.append('|');
        solution.getUnassignedShipmentItemIds().stream().sorted()
                .forEach(id -> value.append(id).append(','));
        return value.toString();
    }
}
