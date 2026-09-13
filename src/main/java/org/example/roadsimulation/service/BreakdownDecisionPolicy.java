package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.springframework.stereotype.Component;
import java.util.SplittableRandom;

@Component
public class BreakdownDecisionPolicy {
    public static final String VERSION="breakdown-v2";
    public record Decision(TransportRandomEvent.BreakdownLevel level, int rescueWaitMinutes, int repairMinutes) {}
    public Decision decide(long seed, int loop, long vehicleId, WeatherScenarioDTO.BreakdownPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("breakdownPolicy is required");
        }
        validate(policy);
        long mixedSeed = seed ^ 0x425245414B444F57L
                ^ vehicleId * 0x94D049BB133111EBL
                ^ (long) loop * 0xD6E8FEB86659FD93L;
        SplittableRandom random = new SplittableRandom(mixedSeed);
        if (random.nextDouble() < policy.minorProbability()) {
            return new Decision(TransportRandomEvent.BreakdownLevel.MINOR, 0,
                    between(random, policy.minorRepairMin(), policy.minorRepairMax()));
        }
        return new Decision(TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED,
                between(random, policy.rescueWaitMin(), policy.rescueWaitMax()),
                between(random, policy.assistanceRepairMin(), policy.assistanceRepairMax()));
    }
    private int between(SplittableRandom r,int min,int max){return min==max?min:r.nextInt(min,max+1);}
    public static void validate(WeatherScenarioDTO.BreakdownPolicy policy) {
        if (policy == null) {
            return; // Missing means an imported/stored legacy scene.
        }
        boolean invalidProbability = !Double.isFinite(policy.minorProbability())
                || policy.minorProbability() < 0 || policy.minorProbability() > 1;
        boolean invalidRange = bad(policy.minorRepairMin(), policy.minorRepairMax())
                || bad(policy.rescueWaitMin(), policy.rescueWaitMax())
                || bad(policy.assistanceRepairMin(), policy.assistanceRepairMax());
        if (!VERSION.equals(policy.version()) || invalidProbability || invalidRange
                || policy.rescueWaitMax() + policy.assistanceRepairMax() > 240) {
            throw new IllegalArgumentException("Invalid breakdownPolicy");
        }
    }
    private static boolean bad(int min,int max){return min<30||max<min||max>180;}
}
