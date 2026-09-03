package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TransportLifecycleRandomEventTest {

    @Test
    void loadingMutationIsRejectedAfterVehicleLockWhenEventIsActive() {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipmentItemRepository shipmentItemRepository = mock(ShipmentItemRepository.class);
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        TransportRandomEventService eventService = mock(TransportRandomEventService.class);
        TransportLifecycleService lifecycleService = new TransportLifecycleService(
                shipmentRepository,
                shipmentItemRepository,
                assignmentRepository,
                vehicleRepository,
                eventService
        );

        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        Assignment assignment = new Assignment();
        assignment.setId(88L);
        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignment.setAssignedVehicle(vehicle);
        LocalDateTime simNow = LocalDateTime.of(2026, 1, 1, 8, 30);
        when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        when(vehicleRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(eventService.isTransitionBlocked(12L, simNow)).thenReturn(true);

        assertThrows(
                TransportRandomEventService.TransitionBlockedException.class,
                () -> lifecycleService.markFrontendLoadingCompleted(88L, 12L, simNow, "test")
        );

        verify(vehicleRepository).findByIdForUpdate(12L);
        verify(eventService).isTransitionBlocked(12L, simNow);
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(shipmentItemRepository, shipmentRepository);
    }
}
