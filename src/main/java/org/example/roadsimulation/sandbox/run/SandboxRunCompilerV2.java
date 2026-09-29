package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.example.roadsimulation.service.*;
import org.springframework.core.io.Resource;
import java.util.*;

/** Common constraints reuse the v1 validator; no v1 artifact is rewritten or published. */
public final class SandboxRunCompilerV2 {
    public static final String EVENT_RULE_VERSION="transport-events-v2";
    private final ObjectMapper json;
    private final SandboxRunCompiler common;
    private final LexicographicJsonSha256 hash;
    public SandboxRunCompilerV2(ObjectMapper json) {
        this.json=json.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        common=new SandboxRunCompiler(this.json);hash=new LexicographicJsonSha256(this.json);
    }
    public SandboxRunSpecificationV2 read(Resource source) {
        try(var input=source.getInputStream()) { return json.readValue(input,SandboxRunSpecificationV2.class); }
        catch(java.io.IOException ex) { throw new SandboxRunException("RUN_SPEC_READ_FAILED","Cannot read v2 run specification",ex); }
    }
    public CompiledSandboxRunSpecificationV2 compile(SandboxRunSpecificationV2 source,
            SandboxScenarioRevisionV1 revision,CompiledSandboxScenario scenario) {
        require(source!=null && SandboxRunSpecificationV2.ARTIFACT_VERSION.equals(source.artifactVersion()),
                "UNSUPPORTED_RUN_SPEC_VERSION","A new formal run requires an explicit v2 specification");
        require(source.weather()!=null && source.events()!=null,"MISSING_WEATHER_OR_EVENTS","weather and events are required");
        // This private validation view is never persisted. The environment placeholder is
        // not applied, not hashed, and not an automatic conversion of any published v1 input.
        var checked=common.compile(commonValidationView(source),revision,scenario);
        var normalizedCommon=checked.normalizedSpecification();
        long seconds;
        try { seconds=Math.multiplyExact(source.simulationClock().tickDurationSeconds(),source.simulationClock().totalLoops()); }
        catch(ArithmeticException ex) { throw new SandboxRunException("INVALID_WEATHER_HORIZON","Simulation horizon overflows",ex); }
        require(seconds>0 && seconds<=525600L*60,"INVALID_WEATHER_HORIZON","v2 supports at most one year of weather");
        var weather=source.weather();
        List<WeatherScenarioDTO.TimeSlice> timeline;
        try {
            switch(weather.sourceMode()==null?"":weather.sourceMode()) {
                case "SEEDED_GENERATION" -> {
                    require(WeatherTimelineV2.VERSION.equals(weather.generatorVersion()) && weather.timeSlices().isEmpty(),
                            "INVALID_WEATHER_SOURCE","Generated weather requires weather-v2 and forbids explicit slices");
                    require(weather.intervalMinutes()!=null && weather.weights()!=null && weather.speedFactors()!=null,
                            "INVALID_WEATHER_SOURCE","Generated weather parameters are required");
                    timeline=new WeatherTimelineV2(json).generate(normalizedCommon.random().rootSeed(),weather.intervalMinutes(),
                            Math.toIntExact((seconds+59)/60),weather.weights(),weather.speedFactors());
                }
                case "EXPLICIT_TIMELINE" -> {
                    require(weather.generatorVersion()==null && weather.intervalMinutes()==null
                            && weather.weights()==null && weather.speedFactors()==null,
                            "INVALID_WEATHER_SOURCE","Explicit weather forbids generator parameters");
                    require(!weather.timeSlices().isEmpty() && weather.timeSlices().size()<=10000,
                            "INVALID_WEATHER_TIMELINE","Explicit weather slices are required");
                    long end=0;
                    for(var slice:weather.timeSlices()) {
                        require(slice!=null && slice.weatherType()!=null && slice.startMinute()==end
                                && slice.endMinute()>end && slice.endMinute()<=525600
                                && Double.isFinite(slice.speedFactor()) && slice.speedFactor()>0 && slice.speedFactor()<=1,
                                "INVALID_WEATHER_TIMELINE","Weather must be continuous, start at zero and use valid speed factors");
                        end=slice.endMinute();
                    }
                    timeline=weather.timeSlices();
                    require(end*60L>=seconds,"WEATHER_HORIZON_TOO_SHORT","Explicit timeline does not cover the fixed run horizon");
                }
                default -> throw new SandboxRunException("INVALID_WEATHER_SOURCE","Unsupported weather sourceMode");
            }
        } catch(IllegalArgumentException ex) { throw new SandboxRunException("INVALID_WEATHER_CONFIGURATION",ex.getMessage(),ex); }
        var events=source.events();
        require(EVENT_RULE_VERSION.equals(events.ruleVersion()) && "DERIVED_FROM_ROOT".equals(events.seedPolicy())
                        && events.breakdownPolicy()!=null,"INVALID_EVENT_PROTOCOL","Event rules and derived seed policy must be explicit");
        FrozenTransportEventConfiguration frozen;
        try { frozen=new FrozenTransportEventConfiguration(events.enabled(),events.autoEnabled(),normalizedCommon.random().rootSeed(),
                events.congestion(),events.breakdown(),events.breakdownPolicy()); }
        catch(IllegalArgumentException ex) { throw new SandboxRunException("INVALID_EVENT_CONFIGURATION",ex.getMessage(),ex); }
        var normalized=new SandboxRunSpecificationV2(SandboxRunSpecificationV2.ARTIFACT_VERSION,normalizedCommon.runSpecKey(),
                normalizedCommon.displayName(),normalizedCommon.description(),normalizedCommon.scenario(),normalizedCommon.simulationClock(),
                normalizedCommon.demand(),normalizedCommon.dispatch(),weather,events,normalizedCommon.vehicleInitialization(),
                normalizedCommon.driverBehavior(),normalizedCommon.random());
        var definition=json.valueToTree(normalized);
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)definition;
        payload.remove(List.of("runSpecKey","displayName","description"));
        payload.set("algorithmProfile",json.valueToTree(checked.algorithmProfile()));
        payload.set("resolvedWeatherTimeline",json.valueToTree(timeline));
        payload.put("afterTimelinePolicy","REJECT");
        payload.set("frozenEventConfiguration",json.valueToTree(frozen));
        String specHash=hash.hash(payload);
        String weatherHash=hash.hashObject(Map.of("timeSlices",timeline,"afterTimelinePolicy","REJECT"));
        String eventHash=hash.hashObject(frozen);
        String prepared=new SandboxRunCodec(json).preparedRunFactsHash(scenario.effectiveData().effectiveScenarioDataSha256(),
                specHash,checked.resolvedVehicleInitialStateSha256());
        return new CompiledSandboxRunSpecificationV2(normalized,checked.algorithmProfile(),checked.vehicleInitialStates(),
                checked.eligibleVehicleInitialPoiCount(),timeline,frozen,weatherHash,eventHash,specHash,
                checked.resolvedVehicleInitialStateSha256(),prepared,"sandbox-"+specHash.substring(0,16));
    }
    static SandboxRunSpecificationV1 commonValidationView(SandboxRunSpecificationV2 source) {
        return new SandboxRunSpecificationV1(SandboxRunSpecificationV1.ARTIFACT_VERSION,source.runSpecKey(),source.displayName(),
                source.description(),source.scenario(),source.simulationClock(),source.demand(),source.dispatch(),
                new SandboxRunSpecificationV1.Environment("internal-common-validation","1",1,1,"PROGRESS_AFFECTING","DERIVED_FROM_ROOT"),
                source.vehicleInitialization(),source.driverBehavior(),source.random());
    }
    private static void require(boolean condition,String code,String message) {
        if(!condition)throw new SandboxRunException(code,message);
    }
}
