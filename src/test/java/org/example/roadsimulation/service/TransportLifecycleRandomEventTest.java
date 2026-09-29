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

    @Test void cancellingTaskDoesNotReviveItsScrappedVehicle() {
        var assignments=mock(AssignmentRepository.class);var vehicles=mock(VehicleRepository.class);
        var service=new TransportLifecycleService(mock(ShipmentRepository.class),mock(ShipmentItemRepository.class),assignments,vehicles);
        var v=new Vehicle();v.setId(12L);v.markScrapped(LocalDateTime.of(2026,1,1,0,0));
        var a=new Assignment();a.setId(88L);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);v.addAssignment(a);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(v));
        service.cancelAssignment(a,"cancel",LocalDateTime.of(2026,1,1,1,0),"test");
        org.junit.jupiter.api.Assertions.assertEquals(Vehicle.VehicleStatus.SCRAPPED,v.getCurrentStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Assignment.AssignmentStatus.CANCELLED,a.getStatus());
    }

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
