package org.example.roadsimulation.service;

import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.core.*;
import org.example.roadsimulation.dto.WeatherCurrentDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WeatherChangingEnvironmentTest {
    @Test void congestionEndsBetweenTicksWhileRainContinuesThenFogStarts() {
        var f = new Fixture();
        f.event(TransportRandomEvent.EventType.TRAFFIC_CONGESTION, 0, 45, .4);
        var p = f.driving.settle(f.vehicle, f.time(120));
        // 0–30 sunny+jam:12; 30–45 rain+jam:4.8; 45–90 rain:36; 90–120 fog:18 minutes.
        assertEquals(6552, p.getRemainingWorkSeconds(), 1e-7);
        assertEquals(.6, f.driving.effectiveFactor(12L, f.time(120)), 1e-7);
        assertEquals(6552, f.driving.settle(f.vehicle, f.time(120)).getRemainingWorkSeconds(), 1e-7);
        assertEquals(6552, f.driving.settle(f.vehicle, f.time(90)).getRemainingWorkSeconds(), 1e-7);
    }

    @Test void repairDuringChangedWeatherResumesSameWorkAndResolvesOnce() {
        var f = new Fixture();
        var p = f.driving.settle(f.vehicle, f.time(15));
        var e = f.event(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN, 15, 75, 0);
        e.setPreviousVehicleStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING);
        e.setRemainingStatusSeconds(9900L);
        f.vehicle.transitionToStatus(Vehicle.VehicleStatus.BREAKDOWN, f.time(15), Duration.ofMinutes(60));
        f.driving.settle(f.vehicle, f.time(60));
        assertEquals(9900, p.getRemainingWorkSeconds(), 1e-7);
        f.driving.settle(f.vehicle, f.time(90));
        // Repair at minute 75: next 15 minutes use rain, despite resolving at tick 90.
        assertEquals(9180, p.getRemainingWorkSeconds(), 1e-7);
        var service = new TransportRandomEventService(f.events, f.vehicles, mock(AssignmentRepository.class), new RandomEventDecisionPolicy(), new RandomEventProperties());
        ReflectionTestUtils.setField(service, "drivingProgressService", f.driving);
        service.tick(f.time(90), 30, 3);
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING, f.vehicle.getCurrentStatus());
        assertEquals(f.time(0), f.vehicle.getStatusStartTime());
        assertEquals(f.time(75), e.getResolvedTime());
        assertEquals(.6, f.driving.effectiveFactor(12L, f.time(90)), 1e-7);
        assertEquals(p.getPhaseKey(), f.driving.settle(f.vehicle, f.time(120)).getPhaseKey());
        assertEquals(8100, p.getRemainingWorkSeconds(), 1e-7);
        service.tick(f.time(120), 30, 4);
        verify(f.events, times(1)).save(e);
        assertEquals(1, f.memory.size());
    }

    private static class Fixture {
        final LocalDateTime start = LocalDateTime.of(2026,1,1,0,0);
        final VehicleRepository vehicles = mock(VehicleRepository.class);
        final TransportRandomEventRepository events = mock(TransportRandomEventRepository.class);
        final List<TransportRandomEvent> eventList = new ArrayList<>();
        final Map<String, DrivingProgress> memory = new HashMap<>();
        final Vehicle vehicle = new Vehicle();
        final DrivingProgressService driving;
        Fixture() {
            var repo = mock(DrivingProgressRepository.class);
            when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(memory.get(i.getArgument(0))));
            when(repo.save(any())).thenAnswer(i -> { DrivingProgress p=i.getArgument(0);memory.put(p.getPhaseKey(),p);return p; });
            when(repo.findFirstByVehicleIdOrderByPhaseStartDesc(12L)).thenAnswer(i -> memory.values().stream().max(Comparator.comparing(DrivingProgress::getPhaseStart)));
            var weather = mock(WeatherEnvironmentService.class);
            when(weather.runId()).thenReturn("changing-weather");
            when(weather.at(any())).thenAnswer(i -> {
                LocalDateTime t=i.getArgument(0); double factor=t.isBefore(time(30))?1:t.isBefore(time(90))?.8:.6;
                return new WeatherCurrentDTO(1L,"changing-weather",factor==1?"SUNNY":factor==.8?"RAIN":"FOG",factor,null,false,false,true,t);
            });
            when(weather.boundaries(any(),any())).thenAnswer(i -> List.of(time(30),time(90)).stream().filter(t -> t.isAfter(i.getArgument(0)) && t.isBefore(i.getArgument(1))).toList());
            when(events.findByVehicleId(12L)).thenReturn(eventList);
            when(events.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenAnswer(i -> eventList.stream().filter(e -> e.getStatus()==TransportRandomEvent.EventStatus.ACTIVE).toList());
            vehicle.setId(12L); vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,time(0),Duration.ofMinutes(180));
            var a=new Assignment(); a.setId(88L);a.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);vehicle.addAssignment(a);
            when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
            driving=new DrivingProgressService(repo,vehicles,events,weather,new SimulationContext(),mock(SimulationModeGuard.class));
        }
        LocalDateTime time(int minute) { return start.plusMinutes(minute); }
        TransportRandomEvent event(TransportRandomEvent.EventType type,int from,int to,double factor) {
            var e=new TransportRandomEvent();e.setRunId("changing-weather");e.setVehicleId(12L);e.setAssignmentId(88L);
            e.setEventType(type);e.setStatus(TransportRandomEvent.EventStatus.ACTIVE);e.setStartTime(time(from));e.setPlannedEndTime(time(to));e.setSpeedFactor(factor);eventList.add(e);return e;
        }
    }
}
