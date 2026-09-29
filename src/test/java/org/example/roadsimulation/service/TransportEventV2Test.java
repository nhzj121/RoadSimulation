package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.sandbox.random.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TransportEventV2Test {
    private FrozenTransportEventConfiguration config(boolean auto){
        return new FrozenTransportEventConfiguration(true,auto,"456",
                new WeatherScenarioDTO.EventParameters(1,30,90,.4),
                new WeatherScenarioDTO.EventParameters(0,60,120,0),null);
    }
    @Test void eventDecisionAndDurationAreStableAndIsolatedFromDriverAndWeather(){
        var policy=new RandomEventDecisionPolicy();var config=config(true);
        var first=policy.decide(config,4,7,30);
        var rng=new SandboxRandomProtocol(new ObjectMapper());
        for(int i=0;i<100;i++)rng.random("456",SandboxRandomDomain.WEATHER_TIMELINE,
                Map.of("intervalIndex",i)).nextLong();
        rng.random("456",SandboxRandomDomain.DRIVER_BEHAVIOR_TRANSITION,
                Map.of("loopIndex",4,"driverId",1)).nextDouble();
        assertEquals(first,policy.decide(config,4,7,30));
        assertEquals(TransportRandomEvent.EventType.TRAFFIC_CONGESTION,first.orElseThrow().eventType());
        assertTrue(first.get().durationMinutes()>=30&&first.get().durationMinutes()<=90);
        assertTrue(policy.decide(config(false),4,7,30).isEmpty());
        assertThrows(IllegalArgumentException.class,()->policy.decide(config,-1,7,30));
    }
    @Test void breakdownLevelAndDurationDoNotDependOnOtherRandomConsumers(){
        var detail=new BreakdownDecisionPolicy();
        var parameters=new WeatherScenarioDTO.BreakdownPolicy("breakdown-v3",.6,30,60,30,60,60,120,.1,60,90);
        var first=detail.decide("123",8,5,parameters);
        new RandomEventDecisionPolicy().decide(config(true),8,5,30);
        assertEquals(first,detail.decide("123",8,5,parameters));
    }
    @Test void weatherAndEventBoundariesPreservePausedAndDrivingTimeSeparately(){
        var start=LocalDateTime.of(2026,1,1,0,0);
        var weather=List.of(new WeatherDrivingIntegrator.Window(start,start.plusSeconds(1800),1));
        var fault=new TransportRandomEvent();fault.setId(1L);fault.setRunId("run-A");
        fault.setAssignmentId(10L);fault.setVehicleId(7L);fault.setStartTime(start.plusSeconds(600));
        fault.setPlannedEndTime(start.plusSeconds(1200));fault.setSpeedFactor(0.0);
        var windows=TransportImpactWindows.combine(weather,List.of(fault),"run-A",10L,7L);
        assertEquals(3,windows.size());
        var result=WeatherDrivingIntegrator.integrate(1800,1800,0,windows);
        assertEquals(1200,result.executedDistance(),1e-9);assertEquals(1800,result.consumedSeconds());
        assertEquals(1200,result.increments().stream().mapToLong(WeatherDrivingIntegrator.Increment::seconds).sum());
        assertFalse(result.completed());
        assertEquals(1,TransportImpactWindows.combine(weather,List.of(fault),"run-B",10L,7L).size());
        assertEquals(1,TransportImpactWindows.combine(weather,List.of(fault),"run-A",11L,7L).size());
    }
    @Test void congestionAndWeatherAreMultipliedOnceAndCorruptPersistedFactorsFailClosed(){
        var start=LocalDateTime.of(2026,1,1,0,0);
        var weather=List.of(new WeatherDrivingIntegrator.Window(start,start.plusSeconds(1800),.8));
        var event=new TransportRandomEvent();event.setRunId("A");event.setAssignmentId(1L);event.setVehicleId(2L);
        event.setStartTime(start);event.setPlannedEndTime(start.plusSeconds(1800));event.setSpeedFactor(.5);
        var windows=TransportImpactWindows.combine(weather,List.of(event),"A",1L,2L);
        assertEquals(.4,windows.get(0).speedFactor(),1e-12);
        event.setSpeedFactor(Double.NaN);
        assertThrows(IllegalArgumentException.class,()->TransportImpactWindows.combine(weather,List.of(event),"A",1L,2L));
    }
}
