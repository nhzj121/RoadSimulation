package org.example.roadsimulation.dto;

import java.util.List;

public record ProcessingChainGraphResponse(
        Long id,
        String chainCode,
        String chainName,
        String status,
        String description,
        List<StageResponse> stages,
        List<EdgeResponse> edges
) {
    public record StageResponse(
            Long id,
            Integer stageOrder,
            String stageKey,
            String stageName,
            String description,
            Long processingPoiId,
            String processingPoiName,
            String outputGoodsSku,
            Double outputWeightRatio,
            Integer processingTimeMinutes,
            Double minBatchSize,
            Double maxCapacityPerCycle,
            List<InputResponse> inputs
    ) {}

    public record InputResponse(
            Long id,
            String inputKey,
            String sku,
            Double inputShare
    ) {}

    public record EdgeResponse(
            Long id,
            Long fromStageId,
            Long toStageId,
            Long toStageInputId,
            String fromStageKey,
            String toStageKey,
            String toInputKey
    ) {}
}
