package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.service.impl.AssignmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherAssignmentRestoreTest {
    @Test void missingCacheRestoresOrderedVrpButNotTasksWaitingForRoutePlanning() {
        var service=new AssignmentServiceImpl();var repo=mock(AssignmentRepository.class);
        var cache=mock(DataInitializer.class);var progress=mock(DrivingProgressService.class);
        ReflectionTestUtils.setField(service,"assignmentRepository",repo);
        ReflectionTestUtils.setField(service,"dataInitializer",cache);
        ReflectionTestUtils.setField(service,"drivingProgressService",progress);
        var a=new Assignment();a.setId(1L);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        var poi=new POI();poi.setId(9L);
        a.addNode(new AssignmentNode(a,1,poi,AssignmentNode.NodeActionType.UNLOAD,-10.0,-1.0));
        a.addNode(new AssignmentNode(a,0,poi,AssignmentNode.NodeActionType.LOAD,10.0,1.0));
        var waiting=new Assignment();waiting.setId(2L);waiting.setStatus(Assignment.AssignmentStatus.ASSIGNED);
        when(repo.findActiveAssignments()).thenReturn(List.of(a,waiting));when(progress.enabled()).thenReturn(true);
        var restored=service.getActiveAssignments();assertEquals(1,restored.size());
        assertTrue(restored.get(0).isVrp());assertEquals(List.of(0,1),restored.get(0).getNodes().stream().map(n->n.getSequenceIndex()).toList());
        when(cache.getActiveAssignments()).thenReturn(restored);
        assertEquals(1,service.getActiveAssignments().size());
        when(cache.getActiveAssignments()).thenReturn(List.of());when(progress.enabled()).thenReturn(false);
        assertTrue(service.getActiveAssignments().isEmpty());
    }

    @Test void cachedActiveAssignmentIsDecoratedFromDurableReplacementHistoryAfterRefresh() {
        var service=new AssignmentServiceImpl();var repo=mock(AssignmentRepository.class);
        var cache=mock(DataInitializer.class);var progress=mock(DrivingProgressService.class);
        var events=mock(TransportRandomEventService.class);
        ReflectionTestUtils.setField(service,"assignmentRepository",repo);
        ReflectionTestUtils.setField(service,"dataInitializer",cache);
        ReflectionTestUtils.setField(service,"drivingProgressService",progress);
        ReflectionTestUtils.setField(service,"transportRandomEventService",events);
        var replacement=new Vehicle();replacement.setId(21L);replacement.setLicensePlate("T");
        replacement.transitionToStatus(Vehicle.VehicleStatus.UNLOADING,java.time.LocalDateTime.of(2026,1,1,9,0),java.time.Duration.ofMinutes(30));
        var assignment=new Assignment();assignment.setId(88L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);replacement.addAssignment(assignment);
        var cached=new org.example.roadsimulation.dto.AssignmentBriefDTO();cached.setAssignmentId(88L);cached.setStatus("IN_PROGRESS");cached.setVehicleId(21L);
        when(cache.getActiveAssignments()).thenReturn(List.of(cached));
        when(repo.findById(88L)).thenReturn(Optional.of(assignment));
        when(events.replacementRecoveryState(assignment)).thenReturn(
                new TransportRandomEventService.ReplacementRecoveryState(true,101L,12L,21L,true));

        var restored=service.getActiveAssignments().get(0);

        assertTrue(restored.getReplacementRecovery());
        assertEquals(101L,restored.getReplacementEventId());
        assertEquals(12L,restored.getReplacementOriginalVehicleId());
        assertEquals(21L,restored.getCurrentOwnerVehicleId());
        assertTrue(restored.getReplacementArrivalReady());
    }
}
