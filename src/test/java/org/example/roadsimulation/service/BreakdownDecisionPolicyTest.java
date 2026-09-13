package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreakdownDecisionPolicyTest {
    @Test void sameInputsReproduceWithoutChangingExistingEventDecision() {
        var config = new WeatherScenarioDTO.BreakdownPolicy("breakdown-v2", .7, 30, 60, 30, 60, 60, 120);
        var policy = new BreakdownDecisionPolicy();
        var a = policy.decide(77, 9, 12, config);
        var b = policy.decide(77, 9, 12, config);
        assertEquals(a, b);
        assertNotNull(a.level());
        assertTrue(a.rescueWaitMinutes() == 0 || a.rescueWaitMinutes() >= 30);
        assertTrue(a.rescueWaitMinutes() + a.repairMinutes() <= 240);
    }

    @Test void rejectsInvalidPolicy() {
        assertThrows(IllegalArgumentException.class, () -> BreakdownDecisionPolicy.validate(
                new WeatherScenarioDTO.BreakdownPolicy("wrong", .7, 30, 60, 30, 60, 60, 120)));
    }
}
