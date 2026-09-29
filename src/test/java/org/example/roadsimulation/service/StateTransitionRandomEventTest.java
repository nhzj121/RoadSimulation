package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;

class StateTransitionRandomEventTest {

    @Test
    void stateMutationChecksEventAfterAcquiringVehicleLock() {
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        TransportRandomEventService eventService = mock(TransportRandomEventService.class);
        StateTransitionServiceImpl service = new StateTransitionServiceImpl();
        ReflectionTestUtils.setField(service, "vehicleRepository", vehicleRepository);
        ReflectionTestUtils.setField(service, "transportRandomEventService", eventService);

        LocalDateTime simNow = LocalDateTime.of(2026, 1, 1, 8, 30);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setLicensePlate("川A-0012");
        vehicle.transitionToStatus(
                Vehicle.VehicleStatus.TRANSPORT_DRIVING,
                simNow.minusMinutes(60),
                Duration.ofMinutes(30)
        );
        when(vehicleRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(eventService.isTransitionBlocked(12L, simNow)).thenReturn(true);

        service.updateVehicleStateWithContext(vehicle, simNow, 30);

        verify(vehicleRepository).findByIdForUpdate(12L);
        verify(eventService).isTransitionBlocked(12L, simNow);
        verify(vehicleRepository, never()).save(any());
    }
}
