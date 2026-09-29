package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.TransportRandomEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.SplittableRandom;

@Component
public class RandomEventDecisionPolicy {
    /** New runs use isolated streams; the long-seed overload remains a legacy reader/test adapter. */
    public Optional<Decision> decide(FrozenTransportEventConfiguration config,int loopCount,
            long vehicleId,int minutesPerLoop) {
        if(loopCount<0||vehicleId<=0||minutesPerLoop<=0)throw new IllegalArgumentException("Invalid event business key");
        if(!config.enabled()||!config.autoEnabled())return Optional.empty();
        var protocol=new org.example.roadsimulation.sandbox.random.SandboxRandomProtocol(
                new com.fasterxml.jackson.databind.ObjectMapper());
        var key=java.util.Map.<String,Object>of("loopIndex",loopCount,"vehicleId",vehicleId,
                "ruleVersion","transport-events-v2");
        double draw=protocol.random(config.rootSeed(),
                org.example.roadsimulation.sandbox.random.SandboxRandomDomain.TRANSPORT_EVENT_DECISION,key).nextDouble();
        double breakdownProbability=toStepProbability(config.breakdown().hourlyProbability(),minutesPerLoop);
        double congestionProbability=toStepProbability(config.congestion().hourlyProbability(),minutesPerLoop);
        TransportRandomEvent.EventType type;
        org.example.roadsimulation.dto.WeatherScenarioDTO.EventParameters parameters;
        if(draw<breakdownProbability){type=TransportRandomEvent.EventType.VEHICLE_BREAKDOWN;parameters=config.breakdown();}
        else if(draw<breakdownProbability+congestionProbability){type=TransportRandomEvent.EventType.TRAFFIC_CONGESTION;parameters=config.congestion();}
        else return Optional.empty();
        var durationKey=new java.util.HashMap<String,Object>(key);durationKey.put("eventType",type.name());
        int duration=parameters.minDurationMinutes()+protocol.random(config.rootSeed(),
                org.example.roadsimulation.sandbox.random.SandboxRandomDomain.TRANSPORT_EVENT_DURATION,durationKey)
                .nextInt(parameters.maxDurationMinutes()-parameters.minDurationMinutes()+1);
        return Optional.of(new Decision(type,duration,parameters.speedFactor()));
    }

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
