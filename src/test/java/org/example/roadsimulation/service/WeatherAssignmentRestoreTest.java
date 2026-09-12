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
}
