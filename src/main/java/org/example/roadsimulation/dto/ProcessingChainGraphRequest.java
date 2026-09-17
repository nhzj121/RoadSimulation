package org.example.roadsimulation.dto;

import java.util.List;

public record ProcessingChainGraphRequest(
        String chainCode,
        String chainName,
        String description,
        List<StageRequest> stages,
        List<EdgeRequest> edges
) {
    public record StageRequest(
            Integer stageOrder,
            String stageKey,
            String stageName,
            String description,
            Long processingPoiId,
            String outputGoodsSku,
            Double outputWeightRatio,
            Integer processingTimeMinutes,
            Double minBatchSize,
            Double maxCapacityPerCycle,
            List<InputRequest> inputs
    ) {}

    public record InputRequest(
            String inputKey,
            String sku,
            Double inputShare
    ) {}

    public record EdgeRequest(
            String fromStageKey,
            String toStageKey,
            String toInputKey
    ) {}
}
