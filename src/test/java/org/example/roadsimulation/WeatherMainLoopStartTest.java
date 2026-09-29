package org.example.roadsimulation;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.service.WeatherEnvironmentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherMainLoopStartTest {
    @Test void validatesSceneThenConfiguresBeforePublishingRunning() {
        var clock = new SimulationContext();
        var weather = mock(WeatherEnvironmentService.class);
        var loop = new SimulationMainLoop(null, null, clock, null,
                mock(org.example.roadsimulation.evaluation.EvaluationSnapshotService.class), null);
        ReflectionTestUtils.setField(loop, "weatherEnvironmentService", weather);
        boolean[] configured = {false};
        loop.startWithWeather(1L, "batch", () -> {
            verify(weather).start(1L, "batch");
            assertFalse(clock.isRunning());
            configured[0] = true;
        });
        assertTrue(configured[0]);
        assertTrue(clock.isRunning());
        loop.stop();
        doThrow(new IllegalArgumentException("invalid scene")).when(weather).start(2L, "batch");
        assertThrows(IllegalArgumentException.class,
                () -> loop.startWithWeather(2L, "batch", () -> fail("Invalid scene must not change dispatch config")));
        assertFalse(clock.isRunning());
    }
}
