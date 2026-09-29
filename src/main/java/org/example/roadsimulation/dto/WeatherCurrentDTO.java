package org.example.roadsimulation.dto;
import java.time.LocalDateTime;
public record WeatherCurrentDTO(Long scenarioId, String runId, String weatherType, double speedFactor,
        LocalDateTime nextChangeTime, boolean autoEvents, boolean manuallyIntervened, boolean locked,
        LocalDateTime simulationTime) {}
