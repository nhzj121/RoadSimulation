package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.VehicleCostDTO;
import org.example.roadsimulation.dto.VehicleCostSummaryDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GetCostServiceVehicleCostTest {

    private final GetCostService getCostService = new GetCostService();

    @Test
    void calculatesVehicleCostsWithDynamicMinMaxAndNewCostItems() {
        Vehicle vehicleA = vehicle(1L, "TEST-A", 10.0, 50.0, 5.0, 120.0, 20.0, 12);
        Vehicle vehicleB = vehicle(2L, "TEST-B", 10.0, 50.0, 10.0, 100.0, 0.0, 8);

        Assignment assignmentA = assignment(vehicleA, 100.0, 10.0, 120.0, 20.0, 12, 30.0);
        Assignment assignmentB = assignment(vehicleB, 100.0, 10.0, 100.0, 0.0, 8, 50.0);

        VehicleCostSummaryDTO summary = getCostService.calculateVehicleCostSummary(
                List.of(vehicleA, vehicleB),
                List.of(assignmentA, assignmentB),
                10L,
                2L
        );

        assertEquals(2, summary.getVehicleCount());
        assertEquals(10L, summary.getTotalTaskCount());
        assertEquals(2L, summary.getUnassignedTaskCount());
        assertEquals(0.2, summary.getUnassignedTaskCost(), 1e-9);
        assertEquals(0.15, summary.getWeightA(), 1e-9);
        assertEquals(0.15, summary.getWeightB(), 1e-9);
        assertEquals(0.18, summary.getWeightC(), 1e-9);
        assertEquals(0.12, summary.getWeightD(), 1e-9);
        assertEquals(0.12, summary.getWeightE(), 1e-9);
        assertEquals(0.10, summary.getWeightG(), 1e-9);
        assertEquals(0.08, summary.getWeightH(), 1e-9);
        assertEquals(0.10, summary.getWeightI(), 1e-9);

        VehicleCostDTO costA = findByPlate(summary, "TEST-A");
        VehicleCostDTO costB = findByPlate(summary, "TEST-B");

        assertEquals(10.0, costA.getCostA(), 1e-9);
        assertEquals(0.0, costB.getCostA(), 1e-9);
        assertEquals(0.0, summary.getCostAMin(), 1e-9);
        assertEquals(10.0, summary.getCostAMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostA(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostA(), 1e-9);

        assertEquals(1.0 / 15.0, costA.getCostB(), 1e-9);
        assertEquals(0.0, costB.getCostB(), 1e-9);
        assertEquals(0.0, summary.getCostBMin(), 1e-9);
        assertEquals(1.0 / 15.0, summary.getCostBMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostB(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostB(), 1e-9);

        assertEquals(0.5, costA.getCostC(), 1e-9);
        assertEquals(0.0, costB.getCostC(), 1e-9);
        assertEquals(0.0, summary.getCostCMin(), 1e-9);
        assertEquals(0.5, summary.getCostCMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostC(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostC(), 1e-9);

        assertEquals(5.1, costA.getCostD(), 1e-9);
        assertEquals(3.2, costB.getCostD(), 1e-9);
        assertEquals(3.2, summary.getCostDMin(), 1e-9);
        assertEquals(5.1, summary.getCostDMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostD(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostD(), 1e-9);

        assertEquals(0.2, costA.getCostE(), 1e-9);
        assertEquals(0.2, costB.getCostE(), 1e-9);
        assertEquals(0.2, summary.getCostEMin(), 1e-9);
        assertEquals(0.2, summary.getCostEMax(), 1e-9);
        assertEquals(0.0, costA.getNormalizedCostE(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostE(), 1e-9);

        assertEquals(0.2, costA.getCostG(), 1e-9);
        assertEquals(0.0, costB.getCostG(), 1e-9);
        assertEquals(0.0, summary.getCostGMin(), 1e-9);
        assertEquals(0.2, summary.getCostGMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostG(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostG(), 1e-9);

        assertEquals(0.4, costA.getCostH(), 1e-9);
        assertEquals(0.0, costB.getCostH(), 1e-9);
        assertEquals(0.0, summary.getCostHMin(), 1e-9);
        assertEquals(0.4, summary.getCostHMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostH(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostH(), 1e-9);

        assertEquals(0.2, costA.getCostI(), 1e-9);
        assertEquals(0.0, costB.getCostI(), 1e-9);
        assertEquals(0.0, summary.getCostIMin(), 1e-9);
        assertEquals(0.2, summary.getCostIMax(), 1e-9);
        assertEquals(1.0, costA.getNormalizedCostI(), 1e-9);
        assertEquals(0.0, costB.getNormalizedCostI(), 1e-9);

        assertEquals(0.88, costA.getTotalCost(), 1e-9);
        assertEquals(0.0, costB.getTotalCost(), 1e-9);
        assertEquals(0.44, summary.getAverageTotalCost(), 1e-9);
        assertEquals(0.38, summary.getGlobalCost(), 1e-9);
        assertEquals(
                0.75 * summary.getAverageTotalCost() + 0.25 * summary.getUnassignedTaskCost(),
                summary.getGlobalCost(),
                1e-9
        );
    }

    @Test
    void minMaxNormalizeReturnsZeroWhenRangeHasNoSpread() {
        assertEquals(0.0, getCostService.minMaxNormalize(10.0, 10.0, 10.0), 1e-9);
    }

    private Vehicle vehicle(Long id,
                            String licensePlate,
                            Double maxLoad,
                            Double cargoVolume,
                            Double currentLoad,
                            Double totalDistance,
                            Double emptyDistance,
                            int drivingHours) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setLicensePlate(licensePlate);
        vehicle.setMaxLoadCapacity(maxLoad);
        vehicle.setCargoVolume(cargoVolume);
        vehicle.setCurrentLoad(currentLoad);
        vehicle.setTotalDrivingDistance(totalDistance);
        vehicle.setEmptyDrivingDistance(emptyDistance);
        vehicle.setTotalDrivingTime((long) drivingHours * 3600);
        vehicle.setLoadingWaitTime(0L);
        vehicle.setUnloadingWaitTime(0L);
        vehicle.setWaitingAssignmentTime(0L);
        return vehicle;
    }

    private Assignment assignment(Vehicle vehicle,
                                  Double routeDistance,
                                  Double estimatedHours,
                                  Double actualDistance,
                                  Double emptyDistance,
                                  int actualHours,
                                  Double itemVolume) {
        Route route = new Route();
        route.setDistance(routeDistance);
        route.setEstimatedTime(estimatedHours);

        Assignment assignment = new Assignment();
        assignment.setAssignedVehicle(vehicle);
        assignment.setRoute(route);
        assignment.setTotalDrivingDistance(actualDistance);
        assignment.setEmptyDrivingDistance(emptyDistance);
        assignment.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        assignment.setEndTime(assignment.getStartTime().plusHours(actualHours));

        ShipmentItem item = new ShipmentItem();
        item.setName("test-item");
        item.setQty(1);
        item.setVolume(itemVolume);
        assignment.addShipmentItem(item);

        return assignment;
    }

    private VehicleCostDTO findByPlate(VehicleCostSummaryDTO summary, String licensePlate) {
        VehicleCostDTO dto = summary.getVehicleCosts().stream()
                .filter(cost -> licensePlate.equals(cost.getLicensePlate()))
                .findFirst()
                .orElse(null);
        assertNotNull(dto);
        return dto;
    }
}
