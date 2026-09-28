package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentDriverHistory;
import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentDriverHistoryRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransportLifecycleServiceTest {

    private final ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
    private final ShipmentItemRepository shipmentItemRepository = mock(ShipmentItemRepository.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final DriverPreferenceScorer scorer = mock(DriverPreferenceScorer.class);
    private final AssignmentDriverHistoryRepository historyRepository = mock(AssignmentDriverHistoryRepository.class);

    private TransportLifecycleService service() {
        return new TransportLifecycleService(
                shipmentRepository, shipmentItemRepository, assignmentRepository,
                vehicleRepository, null, driverRepository, scorer, historyRepository);
    }

    private Driver driver(long id, String name, Driver.DriverStatus status) {
        Driver d = new Driver();
        d.setId(id);
        d.setDriverName(name);
        d.setCurrentStatus(status);
        return d;
    }

    private Vehicle vehicle(long id, Driver... drivers) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setDrivers(new LinkedHashSet<>(Set.of(drivers)));
        return v;
    }

    private Assignment openAssignment(long id, Vehicle vehicle, Driver holder) {
        Assignment a = new Assignment();
        a.setId(id);
        a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        a.setAssignedVehicle(vehicle);
        a.setShipmentItems(new HashSet<>());
        holder.addAssignment(a);
        return a;
    }

    @Test
    void reassignPicksPreferredIdleDriverFromSameVehicleAndRecordsHistory() {
        Driver d1 = driver(1L, "司机1", Driver.DriverStatus.MAINTENANCE);
        Driver d2 = driver(2L, "司机2", Driver.DriverStatus.IDLE);
        Driver d3 = driver(3L, "司机3", Driver.DriverStatus.IDLE);
        Vehicle v = vehicle(10L, d1, d2, d3);
        Assignment a = openAssignment(100L, v, d1);

        when(driverRepository.findById(1L)).thenReturn(Optional.of(d1));
        when(scorer.scoreFor(d2, null)).thenReturn(0.4);
        when(scorer.scoreFor(d3, null)).thenReturn(0.9);

        boolean replaced = service().reassignDriverIfNeeded(1L, "手动PATCH换司机");

        assertTrue(replaced);
        assertSame(d3, a.getAssignedDriver());
        assertEquals(Driver.DriverStatus.ASSIGNED, d3.getCurrentStatus());
        assertEquals(Driver.DriverStatus.MAINTENANCE, d1.getCurrentStatus());
        assertFalse(d1.getAssignments().contains(a));

        ArgumentCaptor<AssignmentDriverHistory> captor = ArgumentCaptor.forClass(AssignmentDriverHistory.class);
        verify(historyRepository, times(2)).save(captor.capture());
        List<AssignmentDriverHistory> rows = captor.getAllValues();
        assertEquals(AssignmentDriverHistory.Action.RELEASE, rows.get(0).getAction());
        assertEquals(1L, rows.get(0).getDriverId());
        assertEquals("ASSIGNED", rows.get(0).getFromStatus());
        assertEquals("MAINTENANCE", rows.get(0).getToStatus());
        assertEquals("手动PATCH换司机", rows.get(0).getReason());
        assertEquals(AssignmentDriverHistory.Action.BIND, rows.get(1).getAction());
        assertEquals(3L, rows.get(1).getDriverId());
        assertEquals("IDLE", rows.get(1).getFromStatus());
        assertEquals("ASSIGNED", rows.get(1).getToStatus());
        assertEquals(100L, rows.get(1).getAssignmentId());
        verify(assignmentRepository, atLeastOnce()).save(a);
    }

    @Test
    void noIdleCandidateLeavesTaskDriverlessAndRecordsReleaseOnly() {
        Driver d1 = driver(1L, "司机1", Driver.DriverStatus.OFF);
        Vehicle v = vehicle(10L, d1);
        Assignment a = openAssignment(100L, v, d1);

        when(driverRepository.findById(1L)).thenReturn(Optional.of(d1));

        boolean replaced = service().reassignDriverIfNeeded(1L, "手动PATCH换司机");

        assertFalse(replaced);
        assertNull(a.getAssignedDriver());
        assertEquals(Driver.DriverStatus.OFF, d1.getCurrentStatus());

        ArgumentCaptor<AssignmentDriverHistory> captor = ArgumentCaptor.forClass(AssignmentDriverHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AssignmentDriverHistory.Action.RELEASE, captor.getValue().getAction());
        assertEquals(1L, captor.getValue().getDriverId());
        assertEquals("OFF", captor.getValue().getToStatus());
        verify(assignmentRepository).save(a);
    }

    @Test
    void assignedDriverDoesNotTriggerReassignment() {
        Driver d1 = driver(1L, "司机1", Driver.DriverStatus.ASSIGNED);
        Vehicle v = vehicle(10L, d1);
        Assignment a = openAssignment(100L, v, d1);

        when(driverRepository.findById(1L)).thenReturn(Optional.of(d1));

        assertFalse(service().reassignDriverIfNeeded(1L, "手动PATCH换司机"));
        assertSame(d1, a.getAssignedDriver());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void driverWithoutOpenAssignmentDoesNothing() {
        Driver d1 = driver(1L, "司机1", Driver.DriverStatus.MAINTENANCE);
        Vehicle v = vehicle(10L, d1);
        openAssignment(100L, v, d1).setStatus(Assignment.AssignmentStatus.COMPLETED);

        when(driverRepository.findById(1L)).thenReturn(Optional.of(d1));

        assertFalse(service().reassignDriverIfNeeded(1L, "手动PATCH换司机"));
        verify(historyRepository, never()).save(any());
    }
}
