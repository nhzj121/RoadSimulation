package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.springframework.stereotype.Component;
import java.util.SplittableRandom;

@Component
public class BreakdownDecisionPolicy {
    public static final String VERSION="breakdown-v2";
    public static final String VERSION_V3="breakdown-v3";
    public record Decision(TransportRandomEvent.BreakdownLevel level, int rescueWaitMinutes, int repairMinutes,
            int replacementWaitMinutes) {
        public Decision(TransportRandomEvent.BreakdownLevel level,int rescueWaitMinutes,int repairMinutes){this(level,rescueWaitMinutes,repairMinutes,0);}
    }
    public Decision decide(long seed, int loop, long vehicleId, WeatherScenarioDTO.BreakdownPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("breakdownPolicy is required");
        }
        validate(policy);
        long mixedSeed = seed ^ 0x425245414B444F57L
                ^ vehicleId * 0x94D049BB133111EBL
                ^ (long) loop * 0xD6E8FEB86659FD93L;
        SplittableRandom random = new SplittableRandom(mixedSeed);
        if(VERSION_V3.equals(policy.version())){
            double draw=random.nextDouble();
            if(draw<policy.minorProbability())return new Decision(TransportRandomEvent.BreakdownLevel.MINOR,0,
                    between(random,policy.minorRepairMin(),policy.minorRepairMax()));
            if(draw<1-policy.replacementProbability())return new Decision(TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED,
                    between(random,policy.rescueWaitMin(),policy.rescueWaitMax()),
                    between(random,policy.assistanceRepairMin(),policy.assistanceRepairMax()));
            return new Decision(TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,0,0,
                    between(random,policy.replacementWaitMin(),policy.replacementWaitMax()));
        }
        if (random.nextDouble() < policy.minorProbability()) {
            return new Decision(TransportRandomEvent.BreakdownLevel.MINOR, 0,
                    between(random, policy.minorRepairMin(), policy.minorRepairMax()));
        }
        return new Decision(TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED,
                between(random, policy.rescueWaitMin(), policy.rescueWaitMax()),
                between(random, policy.assistanceRepairMin(), policy.assistanceRepairMax()));
    }
    public Decision decide(String rootSeed,int loop,long vehicleId,WeatherScenarioDTO.BreakdownPolicy policy){
        if(policy==null||loop<0||vehicleId<=0)throw new IllegalArgumentException("Invalid breakdown facts");
        validate(policy);
        var protocol=new org.example.roadsimulation.sandbox.random.SandboxRandomProtocol(
                new com.fasterxml.jackson.databind.ObjectMapper());
        var random=protocol.random(rootSeed,
                org.example.roadsimulation.sandbox.random.SandboxRandomDomain.TRANSPORT_BREAKDOWN_DETAIL,
                java.util.Map.of("loopIndex",loop,"vehicleId",vehicleId,"ruleVersion",policy.version()));
        double draw=random.nextDouble();
        if(draw<policy.minorProbability())return new Decision(TransportRandomEvent.BreakdownLevel.MINOR,0,
                policy.minorRepairMin()+random.nextInt(policy.minorRepairMax()-policy.minorRepairMin()+1));
        if(VERSION_V3.equals(policy.version())&&draw>=1-policy.replacementProbability())
            return new Decision(TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,0,0,
                    policy.replacementWaitMin()+random.nextInt(policy.replacementWaitMax()-policy.replacementWaitMin()+1));
        return new Decision(TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED,
                policy.rescueWaitMin()+random.nextInt(policy.rescueWaitMax()-policy.rescueWaitMin()+1),
                policy.assistanceRepairMin()+random.nextInt(policy.assistanceRepairMax()-policy.assistanceRepairMin()+1));
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
        boolean v2=VERSION.equals(policy.version());
        boolean v3=VERSION_V3.equals(policy.version())&&Math.abs(policy.minorProbability()-.6)<1e-12
                &&Math.abs(policy.replacementProbability()-.1)<1e-12
                &&!bad(policy.replacementWaitMin(),policy.replacementWaitMax());
        if ((!v2&&!v3) || invalidProbability || invalidRange
                || policy.rescueWaitMax() + policy.assistanceRepairMax() > 240) {
            throw new IllegalArgumentException("Invalid breakdownPolicy");
        }
    }
    private static boolean bad(int min,int max){return min<30||max<min||max>180;}
}
