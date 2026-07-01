package org.example.roadsimulation.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchComparisonVisualRunResultDTO {
    private Long runId;
    private String experimentId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private StrategyResult original;
    private StrategyResult heuristic;

    @Data
    public static class StrategyResult {
        private String strategy;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private Integer loopCount;
        private Integer completedItems;
        private Integer totalItems;
        private Integer vehicleUsedCount;
        private Integer assignmentCount;
        private Double costA;
        private Double costB;
        private Double costC;
        private Double costD;
        private Double costE;
        private Double allCost;
        private Double normalizedCostA;
        private Double normalizedCostB;
        private Double normalizedCostC;
        private Double normalizedCostD;
        private Double normalizedCostE;
        private Double normalizedAllCost;
        private Double experimentNormalizedCostA;
        private Double experimentNormalizedCostB;
        private Double experimentNormalizedCostC;
        private Double experimentNormalizedCostD;
        private Double experimentNormalizedCostE;
        private Double experimentNormalizedAllCost;
        private String experimentNormalizationBaselineStrategy;
        private String experimentNormalizationBaselinePercentile;
        private String experimentNormalizationScope;
        private List<CostPoint> costTrend = new ArrayList<>();
    }

    @Data
    public static class CostPoint {
        private Integer loopCount;
        private LocalDateTime simTime;
        private Integer completedItems;
        private Integer totalItems;
        private Double allCost;
        private Double normalizedAllCost;
    }
}
