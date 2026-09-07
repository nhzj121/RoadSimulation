package org.example.roadsimulation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductionBatchResponse(
        Long id,
        String batchNo,
        Long planId,
        String planNo,
        Long chainId,
        String chainCode,
        String status,
        Double plannedFinalOutputWeight,
        Double actualFinalOutputWeight,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<ExecutionResponse> executions
) {
    public record ExecutionResponse(
            Long id,
            Long planNodeId,
            Long stageId,
            Integer stageOrder,
            String stageName,
            Long processingPoiId,
            String processingPoiName,
            String status,
            Double actualInputWeight,
            Double actualOutputWeight,
            Integer progressPercent,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long inboundShipmentId,
            Long outboundShipmentId
    ) {}
}
