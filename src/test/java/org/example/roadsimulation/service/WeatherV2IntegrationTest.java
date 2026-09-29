package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.entity.WeatherRun;
import org.example.roadsimulation.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.example.roadsimulation.dto.WeatherScenarioDTO.WeatherType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeatherV2IntegrationTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();

    @Test void archiveAndManualSaveFailuresDoNotMutateTheLiveFrozenRun(){
        var runs=mock(WeatherRunRepository.class);
        when(runs.save(any())).thenAnswer(i->i.getArgument(0));
        var clock=new SimulationContext();var progress=mock(DrivingProgressRepository.class);
        var service=new WeatherEnvironmentService(mock(WeatherScenarioRepository.class),runs,mapper,clock,
                new RandomEventProperties(),mock(TransportRandomEventRepository.class),progress);
        service.start(null,null);String id=service.runId();
        var saved=org.mockito.ArgumentCaptor.forClass(WeatherRun.class);verify(runs).save(saved.capture());
        when(runs.save(any())).thenThrow(new IllegalStateException("storage failed"));
        assertThrows(IllegalStateException.class,service::manualIntervention);
        assertFalse(service.current().manuallyIntervened());assertFalse(saved.getValue().isManuallyIntervened());
        assertThrows(IllegalStateException.class,()->service.archiveAndReset(clock.getCurrentSimTime()));
        assertEquals(id,service.runId());assertNull(saved.getValue().getEndedAt());
        assertNull(saved.getValue().getEventHistoryJson());verify(progress,never()).deleteAllInBatch();
    }

    @Test void generatedHorizonDoesNotChangeWeatherPrefixOrConsumeOtherDomains(){
        var generator=new WeatherTimelineV2(mapper);
        var factors=Map.of(SUNNY,1.0,RAIN,.8,SNOW,.5,FOG,.6);
        var weights=List.of(50,25,10,15);
        var shortTimeline=generator.generate("123",120,1440,weights,factors);
        var longTimeline=generator.generate("123",120,2880,weights,factors);
        assertEquals(shortTimeline,longTimeline.subList(0,shortTimeline.size()));
        assertEquals(shortTimeline,generator.generate("123",120,1440,weights,factors));
        var clipped=generator.generate("123",120,1500,weights,factors);
        assertEquals(longTimeline.get(12).weatherType(),clipped.get(12).weatherType());
        assertEquals(1500,clipped.get(12).endMinute());
        assertThrows(IllegalArgumentException.class,()->generator.generate("-1",120,1440,weights,factors));
        assertThrows(IllegalArgumentException.class,()->generator.generate("1",0,1440,weights,factors));
        assertThrows(IllegalArgumentException.class,()->generator.generate("1",1,10001,weights,factors));
    }

    @Test void explicitCoverageIsCheckedWithoutFillingGapsOrChangingTimeline(){
        var scene=new WeatherScenarioDTO();scene.setName("custom");
        scene.setTimeSlices(List.of(new WeatherScenarioDTO.TimeSlice(0,1440,RAIN,.75)));
        WeatherEnvironmentService.validate(scene);
        WeatherTimelineV2.requireCoverage(scene.getTimeSlices(),86400);
        assertThrows(IllegalArgumentException.class,()->WeatherTimelineV2.requireCoverage(scene.getTimeSlices(),172800));
        assertEquals(List.of(new WeatherScenarioDTO.TimeSlice(0,1440,RAIN,.75)),scene.getTimeSlices());
    }

    @Test void integratesEachWeatherBoundaryAndOnlyChargesConsumedWindow(){
        var start=LocalDateTime.of(2026,1,1,0,0);
        var windows=List.of(new WeatherDrivingIntegrator.Window(start,start.plusSeconds(600),1),
                new WeatherDrivingIntegrator.Window(start.plusSeconds(600),start.plusSeconds(1800),.5));
        var partial=WeatherDrivingIntegrator.integrate(1800,1800,0,windows);
        assertEquals(1200,partial.executedDistance(),1e-9);
        assertEquals(1800,partial.consumedSeconds());assertFalse(partial.completed());
        var finished=WeatherDrivingIntegrator.integrate(900,900,0,windows);
        assertTrue(finished.completed());assertEquals(1200,finished.consumedSeconds());
        assertEquals(2,finished.increments().size());
        assertEquals(1,finished.increments().get(0).travelTimeFactor());
        assertEquals(2,finished.increments().get(1).travelTimeFactor());
        assertThrows(IllegalArgumentException.class,()->WeatherDrivingIntegrator.integrate(10,10,0,
                List.of(windows.get(1),windows.get(0))));
    }

    @Test void defaultWeatherUsesMasterRunAndFreezesEventsIndependently() throws Exception {
        var runs=mock(WeatherRunRepository.class);
        when(runs.save(any())).thenAnswer(i->i.getArgument(0));
        var clock=new SimulationContext();var settings=new RandomEventProperties();
        settings.setAutoEnabled(true);settings.setSeed(456);
        var service=new WeatherEnvironmentService(mock(WeatherScenarioRepository.class),runs,mapper,clock,
                settings,mock(TransportRandomEventRepository.class),mock(DrivingProgressRepository.class));
        service.start(null,null);
        assertEquals(clock.getSimulationRunId().orElseThrow(),service.runId());
        assertEquals(1,service.current().speedFactor());assertTrue(service.current().locked());
        assertTrue(service.current().autoEvents());
        settings.setAutoEnabled(false);settings.setSeed(999);
        assertTrue(service.current().autoEvents());assertEquals("456",service.eventConfiguration().rootSeed());
        var saved=org.mockito.ArgumentCaptor.forClass(WeatherRun.class);
        verify(runs).save(saved.capture());
        assertNotNull(saved.getValue().getFrozenScenarioJson());
        assertNotNull(saved.getValue().getFrozenEventConfigurationJson());
        assertEquals(64,saved.getValue().getWeatherTimelineSha256().length());
        String id=service.runId();when(runs.findById(id)).thenReturn(java.util.Optional.of(saved.getValue()));
        assertEquals("BASELINE",((com.fasterxml.jackson.databind.JsonNode)service.exportRun(id).get("scenario")).get("preset").asText());
    }

    @Test void saveFailureDoesNotPublishWeatherRun(){
        var runs=mock(WeatherRunRepository.class);when(runs.save(any())).thenThrow(new IllegalStateException("storage failed"));
        var clock=new SimulationContext();
        var service=new WeatherEnvironmentService(mock(WeatherScenarioRepository.class),runs,mapper,clock,
                new RandomEventProperties(),mock(TransportRandomEventRepository.class),mock(DrivingProgressRepository.class));
        assertThrows(IllegalStateException.class,()->service.start(null,null));
        assertNull(service.runId());assertNull(service.eventConfiguration());assertFalse(clock.isRunning());
    }

    @Test void explicitEventOptionsAreValidatedFrozenAndCannotChangeOnResume(){
        var runs=mock(WeatherRunRepository.class);when(runs.save(any())).thenAnswer(i->i.getArgument(0));
        var service=new WeatherEnvironmentService(mock(WeatherScenarioRepository.class),runs,mapper,new SimulationContext(),
                new RandomEventProperties(),mock(TransportRandomEventRepository.class),mock(DrivingProgressRepository.class));
        var requested=service.resolveEventOptions(new org.example.roadsimulation.dto.TransportEventOptions(
                null,true,"789",null,null,null));
        service.start(null,null,requested);
        assertTrue(service.current().autoEvents());assertEquals("789",service.eventConfiguration().rootSeed());
        service.start(null,null,requested);
        var changed=service.resolveEventOptions(new org.example.roadsimulation.dto.TransportEventOptions(
                null,false,null,null,null,null));
        assertThrows(IllegalStateException.class,()->service.start(null,null,changed));
        assertThrows(IllegalArgumentException.class,()->service.resolveEventOptions(
                new org.example.roadsimulation.dto.TransportEventOptions(null,null,"-1",null,null,null)));
    }
}
