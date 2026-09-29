package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverResourceServiceTest {
    final DriverRepository drivers=mock(DriverRepository.class);
    final AssignmentRepository assignments=mock(AssignmentRepository.class);
    final AssignmentDriverHistoryRepository history=mock(AssignmentDriverHistoryRepository.class);
    final DriverResourceService service=new DriverResourceService(drivers,assignments,history,new DriverPreferenceScorer());
    final LocalDateTime at=LocalDateTime.of(2026,1,1,0,0);
    Vehicle vehicle(long id) { var v=new Vehicle();v.setId(id);return v; }
    Driver driver(long id,Vehicle vehicle) {
        var d=new Driver();d.setId(id);d.setCurrentStatus(Driver.DriverStatus.IDLE);d.addVehicle(vehicle);
        when(drivers.findByIdForUpdate(id)).thenReturn(Optional.of(d));
        when(drivers.findDriversByVehicleId(vehicle.getId())).thenReturn(List.of(d));return d;
    }
    Assignment task(long id,Vehicle vehicle) {
        var a=new Assignment();a.setId(id);a.setAssignedVehicle(vehicle);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);return a;
    }

    @Test void aSharedDriverCannotBeReservedTwiceAndCancellationIsOwnerChecked() {
        var v=vehicle(2);var d=driver(20,v);var a=task(10,v);
        assertEquals(d,service.reserveReplacement(v,a,100L,at).orElseThrow());
        assertEquals(Driver.DriverStatus.ASSIGNED,d.getCurrentStatus());
        assertTrue(service.reserveReplacement(v,a,101L,at).isEmpty());
        service.cancelReservation(20L,101L,at);assertEquals(100L,d.getReservedReplacementEventId());
        service.cancelReservation(20L,100L,at);assertNull(d.getReservedReplacementEventId());
        assertEquals(Driver.DriverStatus.IDLE,d.getCurrentStatus());
    }

    @Test void handoffUsesReplacementVehiclesDriverAndLeavesBaselineLinksUntouched() {
        var original=vehicle(1);var next=vehicle(2);var old=driver(10,original);var replacement=driver(20,next);
        var a=task(30,original);old.addAssignment(a);old.setCurrentStatus(Driver.DriverStatus.ASSIGNED);
        when(assignments.findDriverCurrentAssignments(eq(10L),anyList())).thenReturn(List.of(a));
        service.reserveReplacement(next,a,100L,at);
        assertEquals(Driver.DriverStatus.ASSIGNED,old.getCurrentStatus());
        service.handoff(a,original,next,10L,20L,100L,at.plusMinutes(30));
        assertSame(replacement,a.getAssignedDriver());assertEquals(Driver.DriverStatus.IDLE,old.getCurrentStatus());
        assertEquals(Driver.DriverStatus.ASSIGNED,replacement.getCurrentStatus());
        assertNull(replacement.getReservedReplacementEventId());
        assertEquals(Set.of(original),old.getVehicles());assertEquals(Set.of(next),replacement.getVehicles());
        var rows=org.mockito.ArgumentCaptor.forClass(AssignmentDriverHistory.class);
        verify(history,times(2)).save(rows.capture());
        assertEquals(at.plusMinutes(30),rows.getAllValues().get(0).getSimTime());
    }

    @Test void missingOrChangedOriginalDriverRejectsHandoffWithoutPublishingOwnership() {
        var original=vehicle(1);var next=vehicle(2);var old=driver(10,original);var replacement=driver(20,next);
        var a=task(30,original);old.addAssignment(a);old.setCurrentStatus(Driver.DriverStatus.OFF);
        service.reserveReplacement(next,a,100L,at);
        assertThrows(IllegalStateException.class,()->service.handoff(a,original,next,10L,20L,100L,at));
        assertSame(old,a.getAssignedDriver());assertEquals(100L,replacement.getReservedReplacementEventId());
        verifyNoInteractions(history);
    }

    @Test void normalClaimCannotStealReservedDriverOrDriverWithActiveTask() {
        var v=vehicle(2);var d=driver(20,v);var a=task(10,v);
        d.setReservedReplacementEventId(100L);
        assertThrows(IllegalStateException.class,()->service.claimForAssignment(a,v,at,"test"));
        d.setReservedReplacementEventId(null);
        when(assignments.findDriverCurrentAssignments(eq(20L),anyList())).thenReturn(List.of(task(11,v)));
        assertThrows(IllegalStateException.class,()->service.claimForAssignment(a,v,at,"test"));
        assertNull(a.getAssignedDriver());
    }

    @Test void normalClaimIsStableAndExecutionWithoutDriverIsRejected() {
        var v=vehicle(2);var d=driver(20,v);var a=task(10,v);
        assertThrows(IllegalStateException.class,()->service.requireExecutionDriver(a));
        assertSame(d,service.claimForAssignment(a,v,at,"test"));service.requireExecutionDriver(a);
        when(assignments.findDriverCurrentAssignments(eq(20L),anyList())).thenReturn(List.of(a));
        service.claimForAssignment(a,v,at,"test");verify(history,times(1)).save(any());
    }
}
