package org.example.roadsimulation;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.TransportLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationDataCleanupReplacementTest {
    @Test void simulationResetReturnsProtectedVehicleToIdleAndClearsReservationOwner(){
        var service=new SimulationDataCleanupService();var pois=mock(POIRepository.class);var vehicles=mock(VehicleRepository.class);
        var lifecycle=mock(TransportLifecycleService.class);ReflectionTestUtils.setField(service,"poiRepository",pois);
        ReflectionTestUtils.setField(service,"vehicleRepository",vehicles);ReflectionTestUtils.setField(service,"transportLifecycleService",lifecycle);
        var poi=new POI();poi.setId(3L);when(pois.findById(3L)).thenReturn(Optional.of(poi));
        var vehicle=new Vehicle();vehicle.setId(21L);vehicle.reserveAsReplacement(LocalDateTime.of(2026,1,1,8,0));vehicle.setReplacementReservationEventId(101L);
        when(vehicles.findAll()).thenReturn(List.of(vehicle));when(vehicles.save(any())).thenAnswer(i->i.getArgument(0));
        service.resetAllVehiclesToPOI(3L);
        assertEquals(Vehicle.VehicleStatus.IDLE,vehicle.getCurrentStatus());assertNull(vehicle.getReplacementReservationEventId());
    }
}
