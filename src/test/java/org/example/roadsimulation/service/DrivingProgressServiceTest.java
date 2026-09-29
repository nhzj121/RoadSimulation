package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.dto.WeatherCurrentDTO;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.core.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DrivingProgressServiceTest {
    private final LocalDateTime start=LocalDateTime.of(2026,1,1,0,0);
    private DrivingProgress work(double seconds){var p=new DrivingProgress();p.setInitialWorkSeconds(seconds);p.setRemainingWorkSeconds(seconds);return p;}
    @Test void sixtyMinutesOfWorkTakesSeventyFiveMinutesInRain() {
        var p=work(3600);
        DrivingProgressService.integrate(p,start,start.plusMinutes(60),.8);
        assertEquals(720,p.getRemainingWorkSeconds(),1e-7);
        DrivingProgressService.integrate(p,start.plusMinutes(60),start.plusMinutes(90),.8);
        assertEquals(0,p.getRemainingWorkSeconds());
        assertEquals(start.plusMinutes(75),p.getModelCompletedTime());
        assertEquals(4500,p.getAffectedSeconds(),1e-7);assertEquals(900,p.getLostWorkSeconds(),1e-7);
    }
    @Test void breakdownFreezesAndCongestionMultipliesWeather() {
        var p=work(3600);
        DrivingProgressService.integrate(p,start,start.plusMinutes(60),0);
        assertEquals(3600,p.getRemainingWorkSeconds());
        DrivingProgressService.integrate(p,start.plusMinutes(60),start.plusMinutes(90),.8*.4);
        assertEquals(3024,p.getRemainingWorkSeconds(),1e-7);
        DrivingProgressService.integrate(p,start.plusMinutes(90),start.plusMinutes(120),1);
        assertEquals(1224,p.getRemainingWorkSeconds(),1e-7);
    }
    @Test void settlesAcrossWeatherAndEventBoundaryOnceAndIsolatesNewLeg() {
        var repo=mock(DrivingProgressRepository.class);var vehicles=mock(VehicleRepository.class);
        var events=mock(TransportRandomEventRepository.class);var weather=mock(WeatherEnvironmentService.class);
        var state=new HashMap<String,DrivingProgress>();
        when(weather.runId()).thenReturn("run1");
        when(repo.findById(anyString())).thenAnswer(i->Optional.ofNullable(state.get(i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i->{DrivingProgress p=i.getArgument(0);state.put(p.getPhaseKey(),p);return p;});
        when(weather.at(any())).thenAnswer(i->new WeatherCurrentDTO(null,null,"RAIN",.8,null,false,false,false,i.getArgument(0)));
        when(weather.boundaries(any(),any())).thenReturn(List.of());
        var event=new TransportRandomEvent();event.setRunId("run1");event.setAssignmentId(88L);event.setStartTime(start);event.setPlannedEndTime(start.plusMinutes(30));event.setSpeedFactor(.4);
        when(events.findByVehicleId(12L)).thenReturn(List.of(event));
        var service=new DrivingProgressService(repo,vehicles,events,weather,new SimulationContext(),mock(SimulationModeGuard.class));
        var v=new Vehicle();v.setId(12L);v.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,start,Duration.ofMinutes(60));
        var a=new Assignment();a.setId(88L);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);v.addAssignment(a);
        var n=new AssignmentNode();n.setSequenceIndex(0);a.setNodes(new ArrayList<>(List.of(n)));
        var p=service.settle(v,start.plusMinutes(60));
        assertEquals(1584,p.getRemainingWorkSeconds(),1e-7);
        assertEquals(1584,service.settle(v,start.plusMinutes(60)).getRemainingWorkSeconds(),1e-7);
        assertFalse(service.canAdvance(v,start.plusMinutes(60)));
        assertTrue(service.canAdvance(v,start.plusMinutes(120)));
        assertEquals(start.plusMinutes(93),p.getModelCompletedTime());
        assertEquals(start.plusMinutes(120),p.getObservedCompletedTime());
        n.setCompleted(true);var next=new AssignmentNode();next.setSequenceIndex(1);a.getNodes().add(next);
        v.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,start.plusMinutes(120),Duration.ofMinutes(30));
        var other=service.settle(v,start.plusMinutes(120));
        assertNotEquals(p.getPhaseKey(),other.getPhaseKey());assertEquals(1800,other.getRemainingWorkSeconds());
    }
    @Test void assistanceBreakdownCrossedAtMinute150OnlyCountsWorkOutsideRepairWindow() {
        var p=work(7200);
        DrivingProgressService.integrate(p,start,start.plusMinutes(30),.8);
        DrivingProgressService.integrate(p,start.plusMinutes(30),start.plusMinutes(120),0);
        DrivingProgressService.integrate(p,start.plusMinutes(120),start.plusMinutes(150),.8);
        assertEquals(4320,p.getRemainingWorkSeconds(),1e-7);
        assertEquals(6120,p.getLostWorkSeconds(),1e-7);
    }
    @Test void openEndedReplacementEventIsNullSafeAndFreezesScrappedVehicle() {
        var repo=mock(DrivingProgressRepository.class);var vehicles=mock(VehicleRepository.class);
        var events=mock(TransportRandomEventRepository.class);var weather=mock(WeatherEnvironmentService.class);
        when(weather.runId()).thenReturn("run1");
        when(weather.at(any())).thenAnswer(i->new WeatherCurrentDTO(null,null,"SUNNY",1,null,false,false,false,i.getArgument(0)));
        var event=new TransportRandomEvent();event.setRunId("run1");event.setAssignmentId(88L);event.setStartTime(start);
        event.setPlannedEndTime(null);event.setSpeedFactor(0.0);event.setBreakdownLevel(TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED);
        when(events.findByVehicleId(12L)).thenReturn(List.of(event));
        var service=new DrivingProgressService(repo,vehicles,events,weather,new SimulationContext(),mock(SimulationModeGuard.class));
        assertEquals(0.0,service.effectiveFactor(12L,start.plusDays(2)));
    }

    @Test void transferredProgressIsCurrentForReplacementAndSettlesOnlyAfterReady() {
        var repo=mock(DrivingProgressRepository.class);var vehicles=mock(VehicleRepository.class);
        var events=mock(TransportRandomEventRepository.class);var weather=mock(WeatherEnvironmentService.class);
        var state=new HashMap<String,DrivingProgress>();
        when(weather.runId()).thenReturn("run1");
        when(weather.at(any())).thenAnswer(i->new WeatherCurrentDTO(null,null,"SUNNY",1,null,false,false,false,i.getArgument(0)));
        when(weather.boundaries(any(),any())).thenReturn(List.of());when(events.findByVehicleId(21L)).thenReturn(List.of());
        when(repo.findById(anyString())).thenAnswer(i->Optional.ofNullable(state.get(i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i->{DrivingProgress p=i.getArgument(0);state.put(p.getPhaseKey(),p);return p;});
        when(repo.findFirstByVehicleIdOrderByPhaseStartDesc(anyLong())).thenAnswer(i->state.values().stream()
                .filter(p->Objects.equals(p.getVehicleId(),i.getArgument(0)))
                .max(Comparator.comparing(DrivingProgress::getPhaseStart)));
        DrivingProgress source=progress("source",12L,88L,"run1",start,3600,3600,120,30);
        DrivingProgress priorReplacement=progress("prior",21L,77L,"run1",start.plusMinutes(50),600,0,5,2);
        state.put(source.getPhaseKey(),source);state.put(priorReplacement.getPhaseKey(),priorReplacement);
        var service=new DrivingProgressService(repo,vehicles,events,weather,new SimulationContext(),mock(SimulationModeGuard.class));
        var replacement=new Vehicle();replacement.setId(21L);
        LocalDateTime ready=start.plusHours(1);replacement.activateReplacement(Vehicle.VehicleStatus.TRANSPORT_DRIVING,ready,Duration.ofHours(1));
        var assignment=new Assignment();assignment.setId(88L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);replacement.addAssignment(assignment);

        DrivingProgress transferred=service.transferAndSettle(12L,replacement,assignment,"source",ready,ready.plusMinutes(30));

        assertEquals(ready,transferred.getPhaseStart());assertEquals(ready.plusMinutes(30),transferred.getLastSettledTime());
        assertEquals(1800,transferred.getRemainingWorkSeconds(),1e-7);assertEquals(120,transferred.getAffectedSeconds(),1e-7);
        assertEquals(30,transferred.getLostWorkSeconds(),1e-7);assertSame(transferred,service.latest(21L));
    }

    @Test void transferRejectsSourceFromWrongRunAssignmentOrCapturedPhase() {
        var repo=mock(DrivingProgressRepository.class);var weather=mock(WeatherEnvironmentService.class);
        when(weather.runId()).thenReturn("run1");
        var source=progress("other-phase",12L,99L,"other-run",start,3600,3600,0,0);
        when(repo.findFirstByVehicleIdOrderByPhaseStartDesc(12L)).thenReturn(Optional.of(source));
        var service=new DrivingProgressService(repo,mock(VehicleRepository.class),mock(TransportRandomEventRepository.class),weather,
                new SimulationContext(),mock(SimulationModeGuard.class));
        var replacement=new Vehicle();replacement.setId(21L);replacement.activateReplacement(Vehicle.VehicleStatus.TRANSPORT_DRIVING,start,Duration.ofHours(1));
        var assignment=new Assignment();assignment.setId(88L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);replacement.addAssignment(assignment);
        assertNull(service.transferAndSettle(12L,replacement,assignment,"captured",start,start.plusMinutes(30)));
        verify(repo,never()).save(any());
    }

    private DrivingProgress progress(String key,long vehicleId,long assignmentId,String run,LocalDateTime phaseStart,
            double initial,double remaining,double affected,double lost){
        var p=new DrivingProgress();p.setPhaseKey(key);p.setVehicleId(vehicleId);p.setAssignmentId(assignmentId);p.setRunId(run);
        p.setDrivingStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING);p.setPhaseStart(phaseStart);p.setLastSettledTime(phaseStart);
        p.setInitialWorkSeconds(initial);p.setRemainingWorkSeconds(remaining);p.setAffectedSeconds(affected);p.setLostWorkSeconds(lost);return p;
    }
}
