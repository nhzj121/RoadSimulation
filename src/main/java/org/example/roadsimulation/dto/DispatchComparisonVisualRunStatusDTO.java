package org.example.roadsimulation.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchComparisonVisualRunStatusDTO {
    private Long runId;
    private String experimentId;
    private String status;
    private String currentStrategy;
    private Integer currentLoop;
    private Integer maxLoops;
    private Integer completedItems;
    private Integer totalItems;
    private Double latestNormalizedAllCost;
    private Double latestAllCost;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
