package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.evaluation.*;
import org.example.roadsimulation.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransportWeatherAuthorityTest {
    @Test void missingCapacityKeepsTransportProgressAndRecordsAnInvalidEnergyFragment(){
        var assignments=mock(AssignmentRepository.class);var legs=mock(AssignmentLegRepository.class);
        var weather=mock(WeatherEnvironmentService.class);var segments=mock(TransportExecutionSegmentRepository.class);
        var resources=mock(DriverResourceService.class);
        var service=new TransportProgressService(assignments,legs,mock(TransportLifecycleService.class),mock(PlatformTransactionManager.class));
        service.setWeatherEnvironmentService(weather);service.setExecutionResources(resources,segments);
        var assignment=new Assignment();assignment.setId(10L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        var vehicle=new Vehicle();vehicle.setId(7L);vehicle.setMaxLoadCapacity(null);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);vehicle.addAssignment(assignment);
        var driver=new Driver();driver.setId(20L);assignment.setAssignedDriver(driver);
        var leg=new AssignmentLeg();leg.setId(5L);leg.setAssignment(assignment);leg.setVehicle(vehicle);
        leg.setSequenceIndex(0);leg.setLoadState(AssignmentLeg.LoadState.EMPTY);
        leg.setPlannedDistanceMeters(3600.0);leg.setPlannedDrivingSeconds(3600L);
        var start=LocalDateTime.of(2026,1,1,0,0);var tick=new SimulationTick(0,start,start.plusSeconds(1800),1800);
        when(assignments.findById(10L)).thenReturn(Optional.of(assignment));
        when(legs.findByAssignmentIdOrderBySequenceIndexAsc(10L)).thenReturn(List.of(leg));
        when(weather.runId()).thenReturn("run");when(weather.averageSpeedFactor(start,tick.tickEnd())).thenReturn(1.0);
        when(weather.windows(start,tick.tickEnd())).thenReturn(List.of(new WeatherDrivingIntegrator.Window(start,tick.tickEnd(),1)));
        assertEquals(TransportProgressResult.Outcome.ADVANCED,service.advanceAssignment(10L,tick).outcome());
        assertEquals(1800,leg.getExecutedDistanceMeters());assertEquals(AssignmentLeg.EnergyFactStatus.INVALID,leg.getEnergyFactStatus());
        var rows=org.mockito.ArgumentCaptor.forClass(TransportExecutionSegment.class);verify(segments).save(rows.capture());
        assertNull(rows.getValue().getCapacityTonnes());assertFalse(rows.getValue().isEnergyValid());
        assertEquals(1800,rows.getValue().getDrivingSeconds());
    }
    @Test void replacementContinuesTheSameRouteOnlyAfterHandoffAndChargesTheActualOwner(){
        var assignments=mock(AssignmentRepository.class);var legs=mock(AssignmentLegRepository.class);
        var weather=mock(WeatherEnvironmentService.class);var events=mock(TransportRandomEventRepository.class);
        var segments=mock(TransportExecutionSegmentRepository.class);var resources=mock(DriverResourceService.class);
        var service=new TransportProgressService(assignments,legs,mock(TransportLifecycleService.class),mock(PlatformTransactionManager.class));
        service.setWeatherEnvironmentService(weather);service.setTransportRandomEventRepository(events);service.setExecutionResources(resources,segments);
        var assignment=new Assignment();assignment.setId(10L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);assignment.setCurrentLegIndex(0);
        var original=new Vehicle();original.setId(7L);original.setMaxLoadCapacity(7.0);
        var replacement=new Vehicle();replacement.setId(8L);replacement.setMaxLoadCapacity(20.0);replacement.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);
        replacement.addAssignment(assignment);var driver=new Driver();driver.setId(20L);assignment.setAssignedDriver(driver);
        var start=LocalDateTime.of(2026,1,1,0,0);var tick=new SimulationTick(0,start,start.plusSeconds(1800),1800);
        var leg=new AssignmentLeg();leg.setId(5L);leg.setAssignment(assignment);leg.setVehicle(original);leg.setSequenceIndex(0);
        leg.setLoadState(AssignmentLeg.LoadState.EMPTY);leg.setPlannedDistanceMeters(3600.0);leg.setPlannedDrivingSeconds(3600L);
        leg.setExecutedDistanceMeters(400.0);leg.setExecutedDrivingSeconds(400L);leg.setStartedSimTime(start.minusSeconds(400));
        leg.setProgressStatus(AssignmentLeg.ProgressStatus.RUNNING);
        var model=VehicleEnergyEmissionModel.defaultModel();var old=model.calculateDelta(400,7,0,1);
        leg.setEnergyFactStatus(AssignmentLeg.EnergyFactStatus.VALID);leg.setEmissionModelId(old.modelId());
        leg.setVehicleEmissionClassCode(old.vehicleClassCode());leg.setExecutedEnergyLiters(old.energyLiters());leg.setExecutedEmissionKg(old.emissionKg());
        when(assignments.findById(10L)).thenReturn(Optional.of(assignment));when(legs.findByAssignmentIdOrderBySequenceIndexAsc(10L)).thenReturn(List.of(leg));
        when(weather.runId()).thenReturn("run");when(weather.averageSpeedFactor(start,tick.tickEnd())).thenReturn(1.0);
        when(weather.windows(start,tick.tickEnd())).thenReturn(List.of(new WeatherDrivingIntegrator.Window(start,tick.tickEnd(),1)));
        var event=new TransportRandomEvent();event.setRunId("run");event.setAssignmentId(10L);event.setVehicleId(7L);
        event.setReplacementVehicleId(8L);event.setReplacementOutcome("REPLACED");event.setStartTime(start);
        event.setResolvedTime(start.plusSeconds(600));event.setSpeedFactor(0.0);
        when(events.findByRunId("run")).thenReturn(List.of(event));
        service.advanceAssignment(10L,tick);
        assertEquals(1600,leg.getExecutedDistanceMeters(),1e-9);assertEquals(1600,leg.getExecutedDrivingSeconds());
        assertSame(original,leg.getVehicle());assertEquals("MULTIPLE",leg.getVehicleEmissionClassCode());
        var added=model.calculateDelta(1200,20,0,1);assertEquals(old.energyLiters()+added.energyLiters(),leg.getExecutedEnergyLiters(),1e-12);
        var rows=org.mockito.ArgumentCaptor.forClass(TransportExecutionSegment.class);verify(segments).save(rows.capture());
        var row=rows.getValue();assertEquals(8L,row.getVehicleId());assertEquals(20L,row.getDriverId());
        assertEquals(start.plusSeconds(600),row.getFromSimTime());assertEquals(tick.tickEnd(),row.getToSimTime());
        assertEquals(1200,row.getDrivingSeconds());assertEquals(1200,row.getDistanceMeters(),1e-9);
        service.advanceAssignment(10L,tick);verify(segments,times(1)).save(any());
    }
    @Test void authoritativeProgressUsesWeatherSlicesForDistanceAndEnergyAndIgnoresOldCycle(){
        var assignments=mock(AssignmentRepository.class);var legs=mock(AssignmentLegRepository.class);
        var lifecycle=mock(TransportLifecycleService.class);
        var oldCycle=mock(ReproducibleEnvironmentScenarioService.class);
        var weather=mock(WeatherEnvironmentService.class);
        var service=new TransportProgressService(assignments,legs,lifecycle,mock(PlatformTransactionManager.class),oldCycle);
        service.setWeatherEnvironmentService(weather);
        var assignment=new Assignment();assignment.setId(10L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignment.setCurrentLegIndex(0);
        var vehicle=new Vehicle();vehicle.setId(7L);vehicle.setMaxLoadCapacity(10.0);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);vehicle.addAssignment(assignment);
        var leg=new AssignmentLeg();leg.setId(5L);leg.setAssignment(assignment);leg.setVehicle(vehicle);
        leg.setSequenceIndex(0);leg.setLoadState(AssignmentLeg.LoadState.EMPTY);
        leg.setPlannedDistanceMeters(1800.0);leg.setPlannedDrivingSeconds(1800L);
        when(assignments.findById(10L)).thenReturn(Optional.of(assignment));
        when(legs.findByAssignmentIdOrderBySequenceIndexAsc(10L)).thenReturn(List.of(leg));
        var start=LocalDateTime.of(2026,1,1,0,0);var tick=new SimulationTick(0,start,start.plusSeconds(1800),1800);
        when(weather.averageSpeedFactor(tick.tickStart(),tick.tickEnd())).thenReturn(2.0/3);
        when(weather.windows(tick.tickStart(),tick.tickEnd())).thenReturn(List.of(
                new WeatherDrivingIntegrator.Window(start,start.plusSeconds(600),1),
                new WeatherDrivingIntegrator.Window(start.plusSeconds(600),tick.tickEnd(),.5)));
        assertEquals(TransportProgressResult.Outcome.ADVANCED,service.advanceAssignment(10L,tick).outcome());
        assertEquals(1200,leg.getExecutedDistanceMeters(),1e-9);
        assertEquals(1800,leg.getExecutedDrivingSeconds());
        var model=VehicleEnergyEmissionModel.defaultModel();
        double expected=model.calculateDelta(600,10,0,1).energyLiters()+model.calculateDelta(600,10,0,2).energyLiters();
        assertEquals(expected,leg.getExecutedEnergyLiters(),1e-12);
        assertEquals(AssignmentLeg.EnergyFactStatus.VALID,leg.getEnergyFactStatus());
        assertEquals(TransportProgressResult.Outcome.DUPLICATE_TICK_SKIPPED,service.advanceAssignment(10L,tick).outcome());
        assertEquals(expected,leg.getExecutedEnergyLiters(),1e-12);
        verifyNoInteractions(oldCycle,lifecycle);
    }
}
