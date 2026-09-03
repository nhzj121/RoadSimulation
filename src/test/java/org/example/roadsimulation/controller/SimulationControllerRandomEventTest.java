package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.service.TransportRandomEventService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SimulationControllerRandomEventTest {

    @Test
    void vehicleArrivalReturns409WhileEventBlocksTransition() {
        SimulationController controller = new SimulationController();
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        TransportRandomEventService eventService = mock(TransportRandomEventService.class);
        SimulationMainLoop simulationMainLoop = mock(SimulationMainLoop.class);
        ReflectionTestUtils.setField(controller, "assignmentRepository", assignmentRepository);
        ReflectionTestUtils.setField(controller, "transportRandomEventService", eventService);
        ReflectionTestUtils.setField(controller, "simulationMainLoop", simulationMainLoop);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        Assignment assignment = new Assignment();
        assignment.setId(88L);
        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignment.setAssignedVehicle(vehicle);
        LocalDateTime simNow = LocalDateTime.of(2026, 1, 1, 8, 30);
        when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        when(simulationMainLoop.getCurrentSimTime()).thenReturn(simNow);
        when(eventService.isTransitionBlocked(12L, simNow)).thenReturn(true);

        SimulationController.VehicleArrivedRequest request = new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L);
        request.setVehicleId(12L);
        ResponseEntity<Void> response = controller.handleVehicleArrived(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(assignmentRepository).findById(88L);
        verify(simulationMainLoop).getCurrentSimTime();
        verify(eventService).isTransitionBlocked(12L, simNow);
        verifyNoMoreInteractions(assignmentRepository, eventService, simulationMainLoop);
    }
}
