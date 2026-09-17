package org.example.roadsimulation.dto;

import java.util.Map;

public record CreateProductionPlanRequest(
        Long chainId,
        Double minFinalWeight,
        Double maxFinalWeight,
        Double lotSize,
        Long randomSeed,
        Long sourcePoiId,
        String createdBy,
        Map<String, Long> sourcePois
) {
    public CreateProductionPlanRequest(
            Long chainId,
            Double minFinalWeight,
            Double maxFinalWeight,
            Double lotSize,
            Long randomSeed,
            Long sourcePoiId,
            String createdBy
    ) {
        this(chainId, minFinalWeight, maxFinalWeight, lotSize, randomSeed,
                sourcePoiId, createdBy, Map.of());
    }
}
