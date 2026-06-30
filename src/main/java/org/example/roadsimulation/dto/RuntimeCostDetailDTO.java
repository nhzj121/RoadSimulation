package org.example.roadsimulation.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RuntimeCostDetailDTO {

    private LocalDateTime generatedAt;
    private RuntimeCostDTO summary;
    private CostADetail costA;
    private CostBDetail costB;
    private CostCDetail costC;
    private CostDDetail costD;
    private CostEDetail costE;
    private WindowDetail window;
    private BaselineDetail baseline;

    @Data
    public static class CostADetail {
        private Double totalWaitingHours;
        private Double totalEmptyDistanceKm;
        private Double waitingCostContribution;
        private Double emptyDistanceCostContribution;
    }

    @Data
    public static class CostBDetail {
        private Double emptyMileageRatio;
        private Double assignedWaitingTransportRatio;
        private Double worstWaitingTransportRatio;
        private Double idleWaitPenalty;
        private Double emptyMileageContribution;
        private Double waitingTransportContribution;
        private Double worstWaitingContribution;
        private Double idleWaitContribution;
    }

    @Data
    public static class CostCDetail {
        private Double totalTheoryCapacity;
        private Double totalActualCapacity;
        private Double theoryActualCapacityGap;
        private Double worstTheoryRealityCapacity;
        private Double capacityGapContribution;
        private Double worstCapacityContribution;
    }

    @Data
    public static class CostDDetail {
        private Double capacityRatio;
        private Double utilizationWasteCost;
        private Double assignedTimeEconomicLoss;
        private Double idleSpaceEconomicLoss;
        private Double worstEconomicLoss;
        private Double utilizationWasteContribution;
        private Double assignedTimeContribution;
        private Double idleSpaceContribution;
        private Double worstEconomicContribution;
    }

    @Data
    public static class CostEDetail {
        private Double averageWorkload;
        private Double workloadStandardDeviation;
        private Double workloadVariationCoefficient;
    }

    @Data
    public static class WindowDetail {
        private String windowId;
        private String strategy;
        private LocalDateTime windowStartTime;
        private LocalDateTime windowEndTime;
        private Double taskScale;
        private Long generatedShipmentItems;
        private Long notAssignedItemsAtStart;
        private Double startCostA;
        private Double startCostB;
        private Double startCostC;
        private Double startCostD;
        private Double startCostE;
        private Double endCostA;
        private Double endCostB;
        private Double endCostC;
        private Double endCostD;
        private Double endCostE;
        private Double unitCostA;
        private Double unitCostB;
        private Double unitCostC;
        private Double unitCostD;
        private Double unitCostE;
    }

    @Data
    public static class BaselineDetail {
        private String baselinePercentile;
        private String baselineStrategy;
        private Double baselineCostA;
        private Double baselineCostB;
        private Double baselineCostC;
        private Double baselineCostD;
        private Double baselineCostE;
        private Double runtimeWeightA;
        private Double runtimeWeightB;
        private Double runtimeWeightC;
        private Double runtimeWeightD;
        private Double runtimeWeightE;
    }
}
