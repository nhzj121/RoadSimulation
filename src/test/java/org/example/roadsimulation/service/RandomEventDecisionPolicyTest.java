package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.TransportRandomEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomEventDecisionPolicyTest {

    private final RandomEventDecisionPolicy policy = new RandomEventDecisionPolicy();

    @Test
    void convertsHourlyProbabilityToThirtyMinuteStepProbability() {
        assertEquals(0.5, policy.toStepProbability(0.75, 30), 1e-9);
    }

    @Test
    void producesSameDecisionForSameSeedLoopAndVehicle() {
        RandomEventDecisionPolicy.Decision first = policy.decide(
                20260903L, 8, 42L, 30,
                1.0, 0.0,
                30, 90, 60, 120, 0.4
        ).orElseThrow();
        RandomEventDecisionPolicy.Decision second = policy.decide(
                20260903L, 8, 42L, 30,
                1.0, 0.0,
                30, 90, 60, 120, 0.4
        ).orElseThrow();

        assertEquals(first, second);
        assertEquals(TransportRandomEvent.EventType.TRAFFIC_CONGESTION, first.eventType());
        assertTrue(first.durationMinutes() >= 30 && first.durationMinutes() <= 90);
        assertEquals(0.4, first.speedFactor(), 1e-9);
    }
}
