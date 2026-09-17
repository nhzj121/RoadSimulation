package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.dto.ProductionPlanResponse;

public interface ProductionPlanningService {
    ProductionPlanResponse createRandomPlan(CreateProductionPlanRequest request);

    ProductionPlanResponse getPlan(Long planId);

    ProductionBatchResponse releasePlan(Long planId, String actor);
}
