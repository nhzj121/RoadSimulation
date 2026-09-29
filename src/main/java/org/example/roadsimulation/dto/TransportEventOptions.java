package org.example.roadsimulation.dto;

/** Optional start-time overrides; absence preserves defaults, not weather metadata. */
public record TransportEventOptions(Boolean enabled, Boolean autoEnabled, String rootSeed,
        WeatherScenarioDTO.EventParameters congestion, WeatherScenarioDTO.EventParameters breakdown,
        WeatherScenarioDTO.BreakdownPolicy breakdownPolicy) {}
