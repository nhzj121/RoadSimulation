package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.*;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SandboxRunCompilerV2Test {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    CompiledSandboxScenario scenario;
    SandboxScenarioRevisionV1 revision;
    SandboxRunSpecificationV2 spec;
    @BeforeEach void setup() {
        var baseline=new SandboxBaselineLoader(json).load(new ClassPathResource("sandbox/baseline/baseline-v1.json"));
        var compiler=new SandboxScenarioCompiler(json);
        scenario=compiler.compile(baseline,compiler.readDefinition(new ClassPathResource("sandbox/scenarios/default-all-eligible-v1.json")));
        revision=new SandboxScenarioRevisionV1(SandboxScenarioRevisionV1.ARTIFACT_VERSION,scenario.normalizedDefinition().scenarioKey(),
                1,scenario.normalizedDefinition(),scenario.resolvedSelection(),new SandboxScenarioRevisionV1.Fingerprints(
                SandboxScenarioCodec.CANONICALIZATION,scenario.scenarioDefinitionSha256(),scenario.effectiveData().baseDataProjectionSha256(),
                scenario.effectiveData().effectiveScenarioDataSha256()),Instant.parse("2026-01-01T00:00:00Z"));
        var template=new SandboxRunCompilerV2(json).read(new ClassPathResource("sandbox/runs/default-production-original-v2.json"));
        spec=new SandboxRunSpecificationV2(template.artifactVersion(),template.runSpecKey(),template.displayName(),template.description(),
                new SandboxRunSpecificationV1.ScenarioReference(revision.scenarioKey(),revision.revision(),revision.fingerprints().scenarioDefinitionSha256(),
                        revision.fingerprints().effectiveScenarioDataSha256()),template.simulationClock(),template.demand(),template.dispatch(),
                template.weather(),template.events(),template.vehicleInitialization(),template.driverBehavior(),template.random());
    }
    CompiledSandboxRunSpecificationV2 compile(SandboxRunSpecificationV2 source) {return new SandboxRunCompilerV2(json).compile(source,revision,scenario);}
    SandboxRunSpecificationV2 changed(SandboxRunSpecificationV2.Weather weather,SandboxRunSpecificationV2.Events events,int loops,String description) {
        return new SandboxRunSpecificationV2(spec.artifactVersion(),spec.runSpecKey(),spec.displayName(),description,spec.scenario(),
                new SandboxRunSpecificationV1.SimulationClock(spec.simulationClock().startLocalDateTime(),spec.simulationClock().tickDurationSeconds(),loops),
                spec.demand(),spec.dispatch(),weather,events,spec.vehicleInitialization(),spec.driverBehavior(),spec.random());
    }
    @Test void templateGeneratesFullWindowAndStableHashesIndependentOfDateSerialization() {
        var a=compile(spec);var b=new SandboxRunCompilerV2(json.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).compile(spec,revision,scenario);
        assertEquals(a,b);assertEquals(85,a.vehicleInitialStates().size());
        assertEquals(spec.simulationClock().totalLoops()*spec.simulationClock().tickDurationSeconds(),
                a.weatherTimeline().get(a.weatherTimeline().size()-1).endMinute()*60);
        assertEquals(a.runSpecificationSha256(),compile(changed(spec.weather(),spec.events(),spec.simulationClock().totalLoops(),"renamed")).runSpecificationSha256());
        assertFalse(json.valueToTree(a.normalizedSpecification()).has("environment"));
    }
    @Test void extendingHorizonPreservesWeatherPrefixAndOtherRandomDomains() {
        var shortRun=compile(changed(spec.weather(),spec.events(),48,null));
        var longRun=compile(changed(spec.weather(),spec.events(),96,null));
        assertEquals(shortRun.weatherTimeline(),longRun.weatherTimeline().subList(0,shortRun.weatherTimeline().size()));
        assertEquals(shortRun.vehicleInitialStates(),longRun.vehicleInitialStates());
        assertNotEquals(shortRun.runSpecificationSha256(),longRun.runSpecificationSha256());
    }
    @Test void eventToggleDoesNotChangeWeatherOrVehicleDraws() {
        var e=spec.events();
        var off=new SandboxRunSpecificationV2.Events(e.ruleVersion(),false,false,e.seedPolicy(),e.congestion(),e.breakdown(),e.breakdownPolicy());
        var a=compile(spec);var b=compile(changed(spec.weather(),off,spec.simulationClock().totalLoops(),null));
        assertEquals(a.weatherTimeline(),b.weatherTimeline());assertEquals(a.vehicleInitialStates(),b.vehicleInitialStates());
        assertNotEquals(a.eventConfigurationSha256(),b.eventConfigurationSha256());assertNotEquals(a.runSpecificationSha256(),b.runSpecificationSha256());
    }
    @Test void explicitTimelineRequiresCoverageAndContinuityAndNeverAcceptsGeneratorParameters() {
        var shortWeather=new SandboxRunSpecificationV2.Weather("EXPLICIT_TIMELINE",null,null,null,null,
                List.of(new WeatherScenarioDTO.TimeSlice(0,1,WeatherScenarioDTO.WeatherType.SUNNY,1)));
        assertEquals("WEATHER_HORIZON_TOO_SHORT",assertThrows(SandboxRunException.class,()->compile(changed(shortWeather,spec.events(),48,null))).errorCode());
        var explicit=new SandboxRunSpecificationV2.Weather("EXPLICIT_TIMELINE",null,null,null,null,
                List.of(new WeatherScenarioDTO.TimeSlice(0,1440,WeatherScenarioDTO.WeatherType.RAIN,.8)));
        assertEquals(explicit.timeSlices(),compile(changed(explicit,spec.events(),48,null)).weatherTimeline());
        var gap=new SandboxRunSpecificationV2.Weather("EXPLICIT_TIMELINE",null,null,null,null,
                List.of(new WeatherScenarioDTO.TimeSlice(1,1440,WeatherScenarioDTO.WeatherType.RAIN,.8)));
        assertEquals("INVALID_WEATHER_TIMELINE",assertThrows(SandboxRunException.class,()->compile(changed(gap,spec.events(),48,null))).errorCode());
        var mixed=new SandboxRunSpecificationV2.Weather("EXPLICIT_TIMELINE","weather-v2",60,null,null,explicit.timeSlices());
        assertEquals("INVALID_WEATHER_SOURCE",assertThrows(SandboxRunException.class,()->compile(changed(mixed,spec.events(),48,null))).errorCode());
    }
    @Test void v1ArtifactCannotMasqueradeAsV2() {
        var tree=json.valueToTree(spec);((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("artifactVersion","sandbox-run-specification/v1");
        var invalid=json.convertValue(tree,SandboxRunSpecificationV2.class);
        assertEquals("UNSUPPORTED_RUN_SPEC_VERSION",assertThrows(SandboxRunException.class,()->compile(invalid)).errorCode());
    }
}
