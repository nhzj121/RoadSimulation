package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class WeatherRun {
    @Id private String id;
    private Long scenarioId;
    private String externalExperimentId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private boolean manuallyIntervened;
    @Lob @Column(columnDefinition = "LONGTEXT") private String frozenScenarioJson;
    @Lob @Column(columnDefinition = "LONGTEXT") private String frozenEventConfigurationJson;
    @Column(length = 64) private String weatherTimelineSha256;
    @Lob @Column(columnDefinition = "LONGTEXT") private String eventHistoryJson;
    @Lob @Column(columnDefinition = "LONGTEXT") private String drivingHistoryJson;
    @Lob @Column(columnDefinition = "LONGTEXT") private String executionSegmentHistoryJson;
    @Lob @Column(columnDefinition = "LONGTEXT") private String replacementAttemptHistoryJson;
}
