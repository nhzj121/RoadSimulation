package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.service.ProductionExecutionService;
import org.example.roadsimulation.service.ProductionPlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production-plans")
public class ProductionPlanController {

    private final ProductionPlanningService planningService;
    private final ProductionExecutionService executionService;

    public ProductionPlanController(
            ProductionPlanningService planningService,
            ProductionExecutionService executionService
    ) {
        this.planningService = planningService;
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<ProductionPlanResponse> createRandomPlan(
            @RequestBody CreateProductionPlanRequest request
    ) {
        return ResponseEntity.ok(planningService.createRandomPlan(request));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<ProductionPlanResponse> getPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(planningService.getPlan(planId));
    }

    @PostMapping("/{planId}/release")
    public ResponseEntity<ProductionBatchResponse> releasePlan(
            @PathVariable Long planId,
            @RequestParam(required = false) String actor
    ) {
        return ResponseEntity.ok(planningService.releasePlan(planId, actor));
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ProductionBatchResponse> getBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(executionService.getBatch(batchId));
    }
}
