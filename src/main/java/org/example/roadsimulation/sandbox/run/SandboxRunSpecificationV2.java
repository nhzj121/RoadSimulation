package org.example.roadsimulation.sandbox.run;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.service.WeatherTimelineV2;
import java.util.List;
import java.util.Map;

/** New contract: no periodic-environment field and no implicit conversion from v1. */
public record SandboxRunSpecificationV2(
        String artifactVersion, String runSpecKey, String displayName, String description,
        SandboxRunSpecificationV1.ScenarioReference scenario,
        SandboxRunSpecificationV1.SimulationClock simulationClock,
        SandboxRunSpecificationV1.Demand demand,
        SandboxRunSpecificationV1.Dispatch dispatch,
        Weather weather,
        Events events,
        SandboxRunSpecificationV1.VehicleInitialization vehicleInitialization,
        SandboxRunSpecificationV1.DriverBehavior driverBehavior,
        SandboxRunSpecificationV1.RandomProtocol random) {
    public static final String ARTIFACT_VERSION="sandbox-run-specification/v2";
    public record Weather(String sourceMode, String generatorVersion, Integer intervalMinutes,
            List<Integer> weights, Map<WeatherScenarioDTO.WeatherType,Double> speedFactors,
            List<WeatherScenarioDTO.TimeSlice> timeSlices) {
        public Weather {
            weights=weights==null?null:List.copyOf(weights);
            speedFactors=speedFactors==null?null:Map.copyOf(speedFactors);
            timeSlices=timeSlices==null?List.of():List.copyOf(timeSlices);
        }
    }
    public record Events(String ruleVersion, boolean enabled, boolean autoEnabled, String seedPolicy,
            WeatherScenarioDTO.EventParameters congestion, WeatherScenarioDTO.EventParameters breakdown,
            WeatherScenarioDTO.BreakdownPolicy breakdownPolicy) {}
}
