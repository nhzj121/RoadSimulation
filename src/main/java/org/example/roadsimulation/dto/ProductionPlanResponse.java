package org.example.roadsimulation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductionPlanResponse(
        Long id,
        String planNo,
        Long chainId,
        String chainCode,
        String chainName,
        String finalSku,
        Double finalDemandWeight,
        Long sourcePoiId,
        String sourcePoiName,
        String status,
        Long randomSeed,
        LocalDateTime createdAt,
        List<NodeResponse> nodes,
        List<FlowResponse> flows
) {
    public record NodeResponse(
            Long id,
            Long stageId,
            Integer stageOrder,
            String stageName,
            String inputSku,
            String outputSku,
            Double plannedInputWeight,
            Double plannedOutputWeight,
            String status
    ) {}

    public record FlowResponse(
            Long id,
            Long fromNodeId,
            Long toNodeId,
            String inputKey,
            String sku,
            Double plannedWeight,
            Long sourcePoiId,
            String sourcePoiName
    ) {}
}
