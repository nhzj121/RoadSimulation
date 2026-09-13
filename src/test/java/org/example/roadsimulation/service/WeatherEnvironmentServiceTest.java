package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.example.roadsimulation.dto.WeatherScenarioDTO.WeatherType.*;
import static org.junit.jupiter.api.Assertions.*;

class WeatherEnvironmentServiceTest {
    @Test void checkedInScenesAreValidAndAutomaticExampleMatchesGenerator() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String name : List.of("baseline", "demo", "auto")) {
            var scene = mapper.readValue(new java.io.File("docs/examples/weather-" + name + ".json"), WeatherScenarioDTO.class);
            WeatherEnvironmentService.validate(scene);
            if (name.equals("auto")) {
                var request = new WeatherScenarioDTO(); request.setPreset("AUTO"); request.setSeed(scene.getSeed());
                assertEquals(scene.getTimeSlices(), WeatherEnvironmentService.preset(request).getTimeSlices());
            }
        }
    }
    @Test void savedSceneLocksRunReplaysExactlyAndArchivesOnlyItsOwnRecords() {
        var scenes = org.mockito.Mockito.mock(org.example.roadsimulation.repository.WeatherScenarioRepository.class);
        var runs = org.mockito.Mockito.mock(org.example.roadsimulation.repository.WeatherRunRepository.class);
        var events = org.mockito.Mockito.mock(org.example.roadsimulation.repository.TransportRandomEventRepository.class);
        var progress = org.mockito.Mockito.mock(org.example.roadsimulation.repository.DrivingProgressRepository.class);
        var clock = new org.example.roadsimulation.core.SimulationContext();
        var config = new org.example.roadsimulation.config.RandomEventProperties();
        var saved = new java.util.HashMap<Long, org.example.roadsimulation.entity.WeatherScenario>();
        org.mockito.Mockito.when(scenes.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> {
            org.example.roadsimulation.entity.WeatherScenario s = i.getArgument(0);
            s.setId(1L); saved.put(1L, s); return s;
        });
        org.mockito.Mockito.when(scenes.findById(1L)).thenAnswer(i -> java.util.Optional.ofNullable(saved.get(1L)));
        org.mockito.Mockito.when(runs.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        var service = new WeatherEnvironmentService(scenes, runs, new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(), clock, config, events, progress);
        var request = new WeatherScenarioDTO(); request.setPreset("AUTO"); request.setSeed(314159);
        var scene = service.create(request, false);
        var exported = service.load(scene.getId());
        assertEquals(scene.getTimeSlices(), exported.getTimeSlices());
        service.start(1L, "experiment-A");
        assertEquals("breakdown-v2", service.breakdownPolicy().version());
        String firstRun = service.runId();
        assertTrue(service.current().locked()); assertTrue(config.isAutoEnabled()); assertEquals(314159, config.getSeed());
        service.start(1L, "experiment-A"); assertEquals(firstRun, service.runId());
        assertThrows(IllegalStateException.class, () -> service.start(2L, "experiment-B"));
        service.manualIntervention(); assertTrue(service.current().manuallyIntervened());
        var beforePause = service.current(); clock.setRunning(false); assertEquals(beforePause, service.current());
        service.archiveAndReset(clock.getCurrentSimTime());
        assertNull(service.breakdownPolicy());
        assertNull(service.runId()); assertFalse(config.isAutoEnabled()); assertEquals(20260903L, config.getSeed());
        org.mockito.Mockito.verify(events).findByRunId(firstRun);
        org.mockito.Mockito.verify(events, org.mockito.Mockito.never()).findAll();
        assertEquals(exported.getTimeSlices(), service.load(1L).getTimeSlices());
        service.start(1L, "experiment-B"); assertNotEquals(firstRun, service.runId());
        assertEquals(exported.getTimeSlices(), service.load(1L).getTimeSlices());
    }

    @Test void configuredWeightsAndFactorsAreSavedAndUsed() {
        var request = new WeatherScenarioDTO(); request.setPreset("AUTO");
        request.setWeights(List.of(0,100,0,0));
        request.setSpeedFactors(java.util.Map.of(SUNNY,1.0,RAIN,.7,SNOW,.5,FOG,.6));
        var scene = WeatherEnvironmentService.preset(request);
        assertTrue(scene.getTimeSlices().stream().allMatch(s -> s.weatherType()==RAIN && s.speedFactor()==.7));
        scene.setWeights(List.of(0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> WeatherEnvironmentService.validate(scene));
    }
    @Test void seededTimelineIsIndependentAndReproducible() {
        var a=new WeatherScenarioDTO();a.setPreset("AUTO");a.setSeed(123);
        var b=new WeatherScenarioDTO();b.setPreset("AUTO");b.setSeed(123);
        var x=WeatherEnvironmentService.preset(a);
        new java.util.Random().nextLong();
        assertEquals(x.getTimeSlices(),WeatherEnvironmentService.preset(b).getTimeSlices());
        assertEquals(12,x.getTimeSlices().size());assertTrue(x.isAutoEvents());
        WeatherEnvironmentService.validate(x);
        assertEquals("breakdown-v2", x.getBreakdownPolicy().version());
    }
    @Test void importedLegacySceneStaysLegacyWhilePresetGetsV2Policy() {
        var legacy = new WeatherScenarioDTO();
        legacy.setName("legacy");
        legacy.setBreakdownPolicy(null);
        legacy.setTimeSlices(List.of(new WeatherScenarioDTO.TimeSlice(0, 60, SUNNY, 1)));
        WeatherEnvironmentService.validate(legacy);
        assertNull(legacy.getBreakdownPolicy());

        var fresh = WeatherEnvironmentService.preset(new WeatherScenarioDTO());
        assertEquals(new WeatherScenarioDTO.BreakdownPolicy("breakdown-v2", .7, 30, 60, 30, 60, 60, 120),
                fresh.getBreakdownPolicy());
    }
    @Test void scenarioJsonRejectsNonIntegerPolicyMinuteTokens() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = """
                {"version":"breakdown-v2","minorProbability":0.7,"minorRepairMin":30.5,
                 "minorRepairMax":60,"rescueWaitMin":30,"rescueWaitMax":60,
                 "assistanceRepairMin":60,"assistanceRepairMax":120}
                """;
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.readValue(json, WeatherScenarioDTO.BreakdownPolicy.class));
    }
    @Test void weatherBoundariesAreHalfOpenAndEndFallsBackToSunny() {
        var d=WeatherEnvironmentService.preset(new WeatherScenarioDTO());
        assertEquals(SUNNY,WeatherEnvironmentService.sliceAt(d,3599).weatherType());
        assertEquals(RAIN,WeatherEnvironmentService.sliceAt(d,3600).weatherType());
        assertEquals(FOG,WeatherEnvironmentService.sliceAt(d,10800).weatherType());
        assertNull(WeatherEnvironmentService.sliceAt(d,86400));
        assertFalse(d.isAutoEvents());
    }
    @Test void rejectsGapOverlapInvalidTypeAndNonFiniteFactors() {
        var d=new WeatherScenarioDTO();d.setName("import");
        for(var slice:List.of(new WeatherScenarioDTO.TimeSlice(1,30,RAIN,.8),
                new WeatherScenarioDTO.TimeSlice(0,0,RAIN,.8),new WeatherScenarioDTO.TimeSlice(0,30,null,.8),
                new WeatherScenarioDTO.TimeSlice(0,30,RAIN,Double.NaN),new WeatherScenarioDTO.TimeSlice(0,30,RAIN,0))) {
            d.setTimeSlices(List.of(slice));assertThrows(IllegalArgumentException.class,()->WeatherEnvironmentService.validate(d));
        }
        d.setTimeSlices(List.of(new WeatherScenarioDTO.TimeSlice(0,60,RAIN,.8),new WeatherScenarioDTO.TimeSlice(30,90,FOG,.6)));
        assertThrows(IllegalArgumentException.class,()->WeatherEnvironmentService.validate(d));
    }
}
