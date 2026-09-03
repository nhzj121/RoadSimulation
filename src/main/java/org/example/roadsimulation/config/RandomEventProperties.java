package org.example.roadsimulation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.simulation.random-events")
@Getter
@Setter
public class RandomEventProperties {
    private boolean enabled = true;
    private boolean autoEnabled = false;
    private long seed = 20260903L;
    private final EventSettings congestion = new EventSettings(0.08, 30, 90, 0.4);
    private final EventSettings breakdown = new EventSettings(0.02, 60, 120, 0.0);

    @Getter
    @Setter
    public static class EventSettings {
        private double hourlyProbability;
        private int minDurationMinutes;
        private int maxDurationMinutes;
        private double speedFactor;

        public EventSettings(double hourlyProbability, int minDurationMinutes, int maxDurationMinutes, double speedFactor) {
            this.hourlyProbability = hourlyProbability;
            this.minDurationMinutes = minDurationMinutes;
            this.maxDurationMinutes = maxDurationMinutes;
            this.speedFactor = speedFactor;
        }
    }
}
