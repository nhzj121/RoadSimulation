package org.example.roadsimulation.service;

import org.example.roadsimulation.core.*;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherLifecycleTest {
    @Test void rainUsesMasterLegCompletionAndUnloadingStillOccupiesItsOwnActionWindow() {
        var start=LocalDateTime.of(2026,1,1,0,0);
        var vehicles=mock(VehicleRepository.class);var assignments=mock(AssignmentRepository.class);
        var legs=mock(AssignmentLegRepository.class);var weather=mock(WeatherEnvironmentService.class);
        var lifecycle=new TransportLifecycleService(mock(ShipmentRepository.class),mock(ShipmentItemRepository.class),assignments,vehicles);
        var settlement=mock(TransportDeliverySettlementService.class);
        var state=new StateTransitionServiceImpl();
        ReflectionTestUtils.setField(state,"vehicleRepository",vehicles);ReflectionTestUtils.setField(state,"assignmentRepository",assignments);
        ReflectionTestUtils.setField(state,"assignmentLegRepository",legs);ReflectionTestUtils.setField(state,"transportLifecycleService",lifecycle);
        ReflectionTestUtils.setField(state,"transportDeliverySettlementService",settlement);
        var progress=new TransportProgressService(assignments,legs,lifecycle,mock(PlatformTransactionManager.class));
        progress.setWeatherEnvironmentService(weather);
        var v=new Vehicle();v.setId(1L);v.setMaxLoadCapacity(10.0);
        var a=new Assignment();a.setId(2L);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);v.addAssignment(a);
        var poi=new POI();poi.setId(3L);a.setDestPOI(poi);
        var leg=new AssignmentLeg();leg.setId(4L);leg.setAssignment(a);leg.setVehicle(v);leg.setToPOI(poi);
        leg.setSequenceIndex(0);leg.setLoadState(AssignmentLeg.LoadState.LOADED);
        leg.setPlannedDistanceMeters(3600.0);leg.setPlannedDrivingSeconds(3600L);
        v.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,start,Duration.ZERO);
        when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(v));when(vehicles.findById(1L)).thenReturn(Optional.of(v));
        when(assignments.findById(2L)).thenReturn(Optional.of(a));when(legs.findByAssignmentIdOrderBySequenceIndexAsc(2L)).thenReturn(List.of(leg));
        for(int loop=0;loop<3;loop++) {
            var from=start.plusMinutes(30L*loop);var tick=new SimulationTick(loop,from,from.plusMinutes(30),1800);
            when(weather.averageSpeedFactor(from,tick.tickEnd())).thenReturn(.8);
            when(weather.windows(from,tick.tickEnd())).thenReturn(List.of(new WeatherDrivingIntegrator.Window(from,tick.tickEnd(),.8)));
            state.updateVehicleStateWithContext(v,from,30);progress.advanceAssignment(2L,tick);
        }
        assertEquals(start.plusMinutes(75),leg.getCompletedSimTime());
        assertEquals(4500,leg.getExecutedDrivingSeconds());assertEquals(Vehicle.VehicleStatus.UNLOADING,v.getCurrentStatus());
        assertEquals(start.plusMinutes(90),v.getStatusStartTime());
        state.updateVehicleStateWithContext(v,start.plusMinutes(100),30);verifyNoInteractions(settlement);
        state.updateVehicleStateWithContext(v,start.plusMinutes(120),30);
        verify(settlement).settleAfterBackendUnloading(eq(a),eq(v),eq(poi),eq(start.plusMinutes(120)),anyString());
    }
}
