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
    @Test void v3IsDeterministicAndProducesFixedReplacementWaitRange() {
        var config=new WeatherScenarioDTO.BreakdownPolicy("breakdown-v3",.6,30,60,30,60,60,120,.1,60,90);
        var policy=new BreakdownDecisionPolicy();
        var seen=java.util.EnumSet.noneOf(TransportRandomEvent.BreakdownLevel.class);
        for(long vehicle=1;vehicle<=200;vehicle++){
            var a=policy.decide(77,9,vehicle,config);assertEquals(a,policy.decide(77,9,vehicle,config));seen.add(a.level());
            if(a.level()==TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED)
                assertTrue(a.replacementWaitMinutes()>=60&&a.replacementWaitMinutes()<=90);
        }
        assertEquals(java.util.EnumSet.allOf(TransportRandomEvent.BreakdownLevel.class),seen);
    }
}
