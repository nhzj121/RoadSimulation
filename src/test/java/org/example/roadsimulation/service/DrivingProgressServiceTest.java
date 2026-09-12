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
}
