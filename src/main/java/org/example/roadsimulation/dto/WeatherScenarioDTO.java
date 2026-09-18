package org.example.roadsimulation.dto;
import lombok.Data;
import java.util.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Data
public class WeatherScenarioDTO {
    public enum WeatherType { SUNNY, RAIN, SNOW, FOG }
    private Long id;
    private String name;
    private String preset;
    private long seed = 20260911L;
    private String generatorVersion = "weather-v1";
    private boolean autoEvents;
    private int generationIntervalMinutes = 120;
    private int generationHorizonMinutes = 1440;
    private List<Integer> weights = List.of(50,25,10,15);
    private Map<WeatherType, Double> speedFactors = Map.of(WeatherType.SUNNY, 1.0, WeatherType.RAIN, .8, WeatherType.SNOW, .5, WeatherType.FOG, .6);
    private EventParameters congestion = new EventParameters(.08, 30, 90, .4);
    private EventParameters breakdown = new EventParameters(.02, 60, 120, 0);
    private BreakdownPolicy breakdownPolicy;
    private String afterTimeline = "SUNNY";
    private List<TimeSlice> timeSlices = new ArrayList<>();
    public record TimeSlice(long startMinute, long endMinute, WeatherType weatherType, double speedFactor) {}
    public record EventParameters(double hourlyProbability, int minDurationMinutes, int maxDurationMinutes, double speedFactor) {}
    public record BreakdownPolicy(
            String version,
            double minorProbability,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int minorRepairMin,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int minorRepairMax,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int rescueWaitMin,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int rescueWaitMax,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int assistanceRepairMin,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int assistanceRepairMax,
            double replacementProbability,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int replacementWaitMin,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) int replacementWaitMax
    ) {
        public BreakdownPolicy(String version,double minorProbability,int minorRepairMin,int minorRepairMax,
                int rescueWaitMin,int rescueWaitMax,int assistanceRepairMin,int assistanceRepairMax){
            this(version,minorProbability,minorRepairMin,minorRepairMax,rescueWaitMin,rescueWaitMax,
                    assistanceRepairMin,assistanceRepairMax,0,0,0);
        }
    }
}
