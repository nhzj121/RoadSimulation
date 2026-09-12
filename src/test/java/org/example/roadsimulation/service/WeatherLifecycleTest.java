package org.example.roadsimulation.service;

import org.example.roadsimulation.core.*;
import org.example.roadsimulation.dto.WeatherCurrentDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WeatherLifecycleTest {
    @Test void rainFinishesWorkBeforeWeatherEndsAndUnloadsOnlyAfterActionWindow() {
        LocalDateTime start = LocalDateTime.of(2026,1,1,0,0);
        var vehicles = mock(VehicleRepository.class);
        var repo = mock(DrivingProgressRepository.class);
        var events = mock(TransportRandomEventRepository.class);
        var weather = mock(WeatherEnvironmentService.class);
        when(weather.runId()).thenReturn("rain-run");
        when(weather.at(any())).thenAnswer(i -> new WeatherCurrentDTO(1L,"rain-run","RAIN",.8,start.plusDays(1),false,false,true,i.getArgument(0)));
        Map<String,DrivingProgress> memory = new HashMap<>();
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(memory.get(i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> { DrivingProgress p=i.getArgument(0); memory.put(p.getPhaseKey(),p); return p; });
        var progress = new DrivingProgressService(repo,vehicles,events,weather,new SimulationContext(),mock(SimulationModeGuard.class));
        var lifecycle = mock(TransportLifecycleService.class);
        var settlement = mock(org.example.roadsimulation.DataInitializer.class);
        var state = new StateTransitionServiceImpl();
        ReflectionTestUtils.setField(state,"vehicleRepository",vehicles);
        ReflectionTestUtils.setField(state,"assignmentRepository",mock(AssignmentRepository.class));
        ReflectionTestUtils.setField(state,"transportRandomEventService",mock(TransportRandomEventService.class));
        ReflectionTestUtils.setField(state,"transportLifecycleService",lifecycle);
        ReflectionTestUtils.setField(state,"drivingProgressService",progress);
        ReflectionTestUtils.setField(state,"deliverySettlement",settlement);
        var vehicle = new Vehicle(); vehicle.setId(1L);
        var assignment = new Assignment(); assignment.setId(2L); assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        vehicle.addAssignment(assignment);
        vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,start,Duration.ofMinutes(60));
        when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle));
        state.updateVehicleStateWithContext(vehicle,start.plusMinutes(60),30);
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING,vehicle.getCurrentStatus());
        state.updateVehicleStateWithContext(vehicle,start.plusMinutes(90),30);
        assertEquals(Vehicle.VehicleStatus.UNLOADING,vehicle.getCurrentStatus());
        var p = memory.values().iterator().next();
        assertEquals(start.plusMinutes(75),p.getModelCompletedTime());
        assertEquals(start.plusMinutes(90),p.getObservedCompletedTime());
        state.updateVehicleStateWithContext(vehicle,start.plusMinutes(100),30);
        verifyNoInteractions(settlement);
        state.updateVehicleStateWithContext(vehicle,start.plusMinutes(120),30);
        verify(settlement).settleWeatherDelivery(eq(assignment),eq(vehicle),isNull());
    }
}
