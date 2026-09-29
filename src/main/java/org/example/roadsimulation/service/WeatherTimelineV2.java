package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure, prefix-stable weather generation. Horizon and database identity are not seed keys. */
public final class WeatherTimelineV2 {
    public static final String VERSION = "weather-v2";
    private final SandboxRandomProtocol random;

    public WeatherTimelineV2(ObjectMapper mapper) {
        random = new SandboxRandomProtocol(mapper);
    }

    public List<WeatherScenarioDTO.TimeSlice> generate(String rootSeed, int intervalMinutes,
            int horizonMinutes, List<Integer> weights,
            Map<WeatherScenarioDTO.WeatherType, Double> speedFactors) {
        SandboxRandomProtocol.validateRootSeed(rootSeed);
        if (intervalMinutes < 1 || horizonMinutes < 1 || horizonMinutes > 525600
                || ((long) horizonMinutes + intervalMinutes - 1) / intervalMinutes > 10000) {
            throw new IllegalArgumentException("Invalid weather interval or horizon");
        }
        if (weights == null || weights.size() != 4
                || weights.stream().anyMatch(w -> w == null || w < 0 || w > 10000)
                || weights.stream().mapToInt(Integer::intValue).sum() == 0) {
            throw new IllegalArgumentException("Four nonnegative weather weights with positive sum required");
        }
        for (var type : WeatherScenarioDTO.WeatherType.values()) {
            Double speed = speedFactors == null ? null : speedFactors.get(type);
            if (speed == null || !Double.isFinite(speed) || speed <= 0 || speed > 1) {
                throw new IllegalArgumentException("Weather speed factors must be in (0,1]");
            }
        }
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        List<WeatherScenarioDTO.TimeSlice> result = new ArrayList<>();
        for (long start = 0, index = 0; start < horizonMinutes; start += intervalMinutes, index++) {
            int draw = random.random(rootSeed, SandboxRandomDomain.WEATHER_TIMELINE,
                    Map.of("generatorVersion", VERSION, "intervalIndex", index,
                            "intervalMinutes", intervalMinutes)).nextInt(total);
            int selected = 0;
            while (selected < 3 && draw >= weights.get(selected)) {
                draw -= weights.get(selected++);
            }
            var type = WeatherScenarioDTO.WeatherType.values()[selected];
            result.add(new WeatherScenarioDTO.TimeSlice(start,
                    Math.min(start + intervalMinutes, horizonMinutes), type, speedFactors.get(type)));
        }
        return List.copyOf(result);
    }

    public static void requireCoverage(List<WeatherScenarioDTO.TimeSlice> slices, long requiredSeconds) {
        if (requiredSeconds <= 0 || slices == null || slices.isEmpty()
                || Math.multiplyExact(slices.get(slices.size() - 1).endMinute(), 60L) < requiredSeconds) {
            throw new IllegalArgumentException("WEATHER_TIMELINE_TOO_SHORT: weather must cover the full run window");
        }
    }
}
