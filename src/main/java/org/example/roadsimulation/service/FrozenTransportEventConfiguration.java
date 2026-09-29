package org.example.roadsimulation.service;

import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;

/** Per-run immutable event settings, independent of the weather preset. */
public record FrozenTransportEventConfiguration(boolean enabled, boolean autoEnabled, String rootSeed,
        WeatherScenarioDTO.EventParameters congestion, WeatherScenarioDTO.EventParameters breakdown,
        WeatherScenarioDTO.BreakdownPolicy breakdownPolicy) {
    public FrozenTransportEventConfiguration {
        SandboxRandomProtocol.validateRootSeed(rootSeed);
        validate(congestion, false);
        validate(breakdown, true);
        BreakdownDecisionPolicy.validate(breakdownPolicy);
    }

    public static FrozenTransportEventConfiguration snapshot(RandomEventProperties source) {
        return new FrozenTransportEventConfiguration(source.isEnabled(), source.isAutoEnabled(),
                Long.toString(source.getSeed()), copy(source.getCongestion()), copy(source.getBreakdown()),
                source.getBreakdownPolicy());
    }

    private static WeatherScenarioDTO.EventParameters copy(RandomEventProperties.EventSettings s) {
        return new WeatherScenarioDTO.EventParameters(s.getHourlyProbability(), s.getMinDurationMinutes(),
                s.getMaxDurationMinutes(), s.getSpeedFactor());
    }

    private static void validate(WeatherScenarioDTO.EventParameters p, boolean breakdown) {
        if (p == null || !Double.isFinite(p.hourlyProbability()) || p.hourlyProbability() < 0
                || p.hourlyProbability() > 1 || p.minDurationMinutes() < 1
                || p.maxDurationMinutes() < p.minDurationMinutes() || p.maxDurationMinutes() > 1440
                || !Double.isFinite(p.speedFactor()) || p.speedFactor() < 0 || p.speedFactor() > 1
                || (breakdown ? p.speedFactor() != 0 : p.speedFactor() <= 0)) {
            throw new IllegalArgumentException("Invalid transport event configuration");
        }
    }
}
