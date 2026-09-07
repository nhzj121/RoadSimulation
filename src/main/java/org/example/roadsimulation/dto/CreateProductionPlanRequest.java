package org.example.roadsimulation.dto;

public record CreateProductionPlanRequest(
        Long chainId,
        Double minFinalWeight,
        Double maxFinalWeight,
        Double lotSize,
        Long randomSeed,
        Long sourcePoiId,
        String createdBy
) {}
