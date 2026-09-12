package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WeatherMonitorConsistencyTest {
    @Test void breakdownShowsZeroUntilStateRestoresEvenAfterPlannedRepairTime() {
        var service=new TransportMonitorService();
        var shipments=mock(ShipmentRepository.class);var assignments=mock(AssignmentRepository.class);
        var driving=mock(DrivingProgressService.class);
        ReflectionTestUtils.setField(service,"shipmentRepository",shipments);
        ReflectionTestUtils.setField(service,"shipmentItemRepository",mock(ShipmentItemRepository.class));
        ReflectionTestUtils.setField(service,"assignmentRepository",assignments);
        ReflectionTestUtils.setField(service,"transportRandomEventService",mock(TransportRandomEventService.class));
        ReflectionTestUtils.setField(service,"drivingProgressService",driving);
        when(shipments.findByStatusIn(any())).thenReturn(new ArrayList<>());
        when(driving.enabled()).thenReturn(true);
        when(driving.now()).thenReturn(LocalDateTime.of(2026,1,1,6,0));
        when(driving.effectiveFactor(eq(12L),any())).thenReturn(.6);
        var vehicle=new Vehicle();vehicle.setId(12L);
        vehicle.transitionToStatus(Vehicle.VehicleStatus.BREAKDOWN,LocalDateTime.of(2026,1,1,5,30),Duration.ofMinutes(30));
        var a=new Assignment();a.setId(8L);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);vehicle.addAssignment(a);
        when(assignments.findRuntimeActiveAssignments()).thenReturn(List.of(a));
        var dto=service.getActiveMonitor().getVehicles().get(0);
        assertEquals("BREAKDOWN",dto.getStatus());assertEquals(0.0,dto.getEffectiveSpeedFactor());
        vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,driving.now(),Duration.ofMinutes(30));
        assertEquals(.6,service.getActiveMonitor().getVehicles().get(0).getEffectiveSpeedFactor());
    }
}
