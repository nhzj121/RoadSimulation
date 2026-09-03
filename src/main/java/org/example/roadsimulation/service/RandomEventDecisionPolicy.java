package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.TransportRandomEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.SplittableRandom;

@Component
public class RandomEventDecisionPolicy {

    public record Decision(
            TransportRandomEvent.EventType eventType,
            int durationMinutes,
            double speedFactor
    ) {
    }

    public double toStepProbability(double hourlyProbability, int minutesPerLoop) {
        double boundedProbability = Math.max(0.0, Math.min(1.0, hourlyProbability));
        if (minutesPerLoop <= 0 || boundedProbability == 0.0) {
            return 0.0;
        }
        return 1.0 - Math.pow(1.0 - boundedProbability, minutesPerLoop / 60.0);
    }

    public Optional<Decision> decide(
            long seed,
            int loopCount,
            long vehicleId,
            int minutesPerLoop,
            double congestionHourlyProbability,
            double breakdownHourlyProbability,
            int congestionMinMinutes,
            int congestionMaxMinutes,
            int breakdownMinMinutes,
            int breakdownMaxMinutes,
            double congestionSpeedFactor
    ) {
        SplittableRandom random = new SplittableRandom(mixSeed(seed, loopCount, vehicleId));
        double breakdownProbability = toStepProbability(breakdownHourlyProbability, minutesPerLoop);
        double congestionProbability = toStepProbability(congestionHourlyProbability, minutesPerLoop);
        double draw = random.nextDouble();

        if (draw < breakdownProbability) {
            return Optional.of(new Decision(
                    TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                    randomDuration(random, breakdownMinMinutes, breakdownMaxMinutes),
                    0.0
            ));
        }
        if (draw < breakdownProbability + congestionProbability) {
            return Optional.of(new Decision(
                    TransportRandomEvent.EventType.TRAFFIC_CONGESTION,
                    randomDuration(random, congestionMinMinutes, congestionMaxMinutes),
                    Math.max(0.0, Math.min(1.0, congestionSpeedFactor))
            ));
        }
        return Optional.empty();
    }

    private int randomDuration(SplittableRandom random, int minimum, int maximum) {
        int min = Math.max(1, Math.min(minimum, maximum));
        int max = Math.max(min, Math.max(minimum, maximum));
        return min == max ? min : random.nextInt(min, max + 1);
    }

    private long mixSeed(long seed, int loopCount, long vehicleId) {
        long mixed = seed ^ (vehicleId * 0x9E3779B97F4A7C15L);
        return mixed ^ ((long) loopCount * 0xBF58476D1CE4E5B9L);
    }
}
