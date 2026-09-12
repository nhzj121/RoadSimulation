package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.core.*;
import org.example.roadsimulation.dto.WeatherCurrentDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real state machine, driving integration, cargo lifecycle and inventory settlement; mocked storage. */
class WeatherCargoDeliveryTest {
    @Test void rainyMultiStopDeliveryKeepsLegsCargoAndInventorySeparate() throws Exception {
        var f = new Fixture();
        ShipmentItem first = f.item(1L, 10), second = f.item(2L, 20);
        f.node(first, AssignmentNode.NodeActionType.LOAD, 10, true);
        f.node(second, AssignmentNode.NodeActionType.LOAD, 20, true);
        f.node(first, AssignmentNode.NodeActionType.UNLOAD, -10, false);
        f.node(second, AssignmentNode.NodeActionType.UNLOAD, -20, false);
        f.assignment.setCurrentActionIndex(2);
        f.vehicle.setCurrentLoad(30.0);
        f.vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING, f.start, Duration.ofMinutes(60));

        f.tick(60);
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING, f.vehicle.getCurrentStatus());
        f.tick(90);
        assertEquals(Vehicle.VehicleStatus.UNLOADING, f.vehicle.getCurrentStatus());
        assertEquals(ShipmentItem.ShipmentItemStatus.IN_TRANSIT, first.getStatus());
        f.tick(120);
        assertEquals(ShipmentItem.ShipmentItemStatus.DELIVERED, first.getStatus());
        assertEquals(ShipmentItem.ShipmentItemStatus.IN_TRANSIT, second.getStatus());
        assertEquals(20.0, f.vehicle.getCurrentLoad());
        assertEquals(3, f.assignment.getCurrentActionIndex());
        assertEquals(10, f.inventory.getQuantity()); // inventory settles once at final delivery
        f.tick(120); // duplicate tick must not skip the next driving leg
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING, f.vehicle.getCurrentStatus());
        assertFalse(f.assignment.getNodes().get(3).isCompleted());

        for (int minute = 150; minute <= 300 && f.assignment.getStatus() != Assignment.AssignmentStatus.COMPLETED; minute += 30) f.tick(minute);
        assertEquals(Assignment.AssignmentStatus.COMPLETED, f.assignment.getStatus());
        assertEquals(ShipmentItem.ShipmentItemStatus.DELIVERED, second.getStatus());
        assertEquals(0.0, f.vehicle.getCurrentLoad());
        assertEquals(8, f.inventory.getQuantity());
        assertEquals(Set.of(2, 3), f.progress.values().stream().map(DrivingProgress::getLegIndex).collect(java.util.stream.Collectors.toSet()));
        f.settlement.settleWeatherDelivery(f.assignment, f.vehicle, f.destination);
        assertEquals(8, f.inventory.getQuantity());
        verify(f.enrollments, times(2)).save(f.inventory);
    }

    @Test void cancelledCargoDoesNotConsumeInventoryAtFinalSettlement() throws Exception {
        var f = new Fixture();
        f.item(1L, 10);
        f.item(2L, 20).setStatus(ShipmentItem.ShipmentItemStatus.CANCELLED);
        f.settlement.settleWeatherDelivery(f.assignment, f.vehicle, f.destination);
        assertEquals(9, f.inventory.getQuantity());
    }

    private static class Fixture {
        final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        final VehicleRepository vehicles = mock(VehicleRepository.class);
        final AssignmentRepository assignments = mock(AssignmentRepository.class);
        final ShipmentItemRepository items = mock(ShipmentItemRepository.class);
        final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        final SimulationContext clock = new SimulationContext();
        final Map<String, DrivingProgress> progress = new HashMap<>();
        final Vehicle vehicle = new Vehicle();
        final Assignment assignment = new Assignment();
        final POI origin = new POI(), destination = new POI();
        final Goods goods = new Goods();
        final Enrollment inventory = new Enrollment();
        final StateTransitionServiceImpl state = new StateTransitionServiceImpl();
        final DataInitializer settlement;

        Fixture() throws Exception {
            vehicle.setId(1L); assignment.setId(2L);
            assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
            vehicle.addAssignment(assignment);
            origin.setId(3L); destination.setId(4L); assignment.setDestPOI(destination);
            inventory.setQuantity(10);
            when(enrollments.findByPoiAndGoods(origin, goods)).thenReturn(Optional.of(inventory));
            when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle));
            var lifecycle = new TransportLifecycleService(mock(ShipmentRepository.class), items, assignments, vehicles, mock(TransportRandomEventService.class));
            // Supply mocks for unrelated constructor dependencies; tested collaborators below are real.
            var constructor = DataInitializer.class.getConstructors()[0];
            Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(t -> mock(t)).toArray();
            settlement = (DataInitializer) constructor.newInstance(dependencies);
            ReflectionTestUtils.setField(settlement, "enrollmentRepository", enrollments);
            ReflectionTestUtils.setField(settlement, "transportLifecycleService", lifecycle);
            ReflectionTestUtils.setField(settlement, "simulationContext", clock);
            var repo = mock(DrivingProgressRepository.class);
            when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(progress.get(i.getArgument(0))));
            when(repo.save(any())).thenAnswer(i -> { DrivingProgress p = i.getArgument(0); progress.put(p.getPhaseKey(), p); return p; });
            var weather = mock(WeatherEnvironmentService.class);
            when(weather.runId()).thenReturn("cargo-run");
            when(weather.at(any())).thenAnswer(i -> new WeatherCurrentDTO(1L, "cargo-run", "RAIN", .8, null, false, false, true, i.getArgument(0)));
            var driving = new DrivingProgressService(repo, vehicles, mock(TransportRandomEventRepository.class), weather, clock, mock(SimulationModeGuard.class));
            ReflectionTestUtils.setField(state, "vehicleRepository", vehicles);
            ReflectionTestUtils.setField(state, "assignmentRepository", assignments);
            ReflectionTestUtils.setField(state, "transportRandomEventService", mock(TransportRandomEventService.class));
            ReflectionTestUtils.setField(state, "transportLifecycleService", lifecycle);
            ReflectionTestUtils.setField(state, "deliverySettlement", settlement);
            ReflectionTestUtils.setField(state, "drivingProgressService", driving);
        }
        ShipmentItem item(long id, double weight) {
            var shipment = new Shipment(); shipment.setId(id); shipment.setOriginPOI(origin);
            var item = new ShipmentItem(); item.setId(id); item.setShipment(shipment); item.setGoods(goods); item.setWeight(weight);
            assignment.addShipmentItem(item); item.setStatus(ShipmentItem.ShipmentItemStatus.IN_TRANSIT);
            when(items.findByShipmentId(id)).thenReturn(List.of(item));
            return item;
        }
        void node(ShipmentItem item, AssignmentNode.NodeActionType action, double weight, boolean completed) {
            var node = new AssignmentNode(assignment, assignment.getNodes().size(), destination, action, item, weight, 0.0);
            node.setCompleted(completed); assignment.addNode(node);
        }
        void tick(int minute) {
            state.updateVehicleStateWithContext(vehicle, start.plusMinutes(minute), 30);
        }
    }
}
