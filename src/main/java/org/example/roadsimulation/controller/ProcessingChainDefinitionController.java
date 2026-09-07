package org.example.roadsimulation.controller;

import lombok.RequiredArgsConstructor;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.service.ProcessingChainDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD API for static processing-chain definitions.
 * Processing execution is exposed through the production-plan API.
 */
@RestController
@RequestMapping("/api/v1/processing-chains")
@RequiredArgsConstructor
public class ProcessingChainDefinitionController {

    private final ProcessingChainDefinitionService processingChainDefinitionService;

    @PostMapping
    public ResponseEntity<ProcessingChain> createChain(@RequestBody ProcessingChain chain) {
        return ResponseEntity.ok(processingChainDefinitionService.createChain(chain));
    }

    @GetMapping
    public ResponseEntity<List<ProcessingChain>> getAllChains() {
        return ResponseEntity.ok(processingChainDefinitionService.getAllChains());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessingChain> getChain(@PathVariable Long id) {
        return ResponseEntity.ok(processingChainDefinitionService.getChainById(id)
                .orElseThrow(() -> new IllegalArgumentException("加工链不存在：" + id)));
    }

    @GetMapping("/{id}/stages")
    public ResponseEntity<List<ProcessingStage>> getStages(@PathVariable Long id) {
        return ResponseEntity.ok(processingChainDefinitionService.getStages(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProcessingChain> updateChainStatus(
            @PathVariable Long id,
            @RequestParam ProcessingChain.ChainStatus status
    ) {
        return ResponseEntity.ok(processingChainDefinitionService.updateChainStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteChain(@PathVariable Long id) {
        processingChainDefinitionService.deleteChain(id);
        return ResponseEntity.ok(ApiResponse.success("加工链已删除"));
    }

    @PostMapping("/{chainId}/stages")
    public ResponseEntity<ProcessingStage> createStage(
            @PathVariable Long chainId,
            @RequestBody ProcessingStage stage
    ) {
        return ResponseEntity.ok(processingChainDefinitionService.createStage(chainId, stage));
    }

    @PutMapping("/stages/{stageId}")
    public ResponseEntity<ProcessingStage> updateStage(
            @PathVariable Long stageId,
            @RequestBody ProcessingStage stageDetails
    ) {
        return ResponseEntity.ok(processingChainDefinitionService.updateStage(stageId, stageDetails));
    }

    @DeleteMapping("/stages/{stageId}")
    public ResponseEntity<ApiResponse<String>> deleteStage(@PathVariable Long stageId) {
        processingChainDefinitionService.deleteStage(stageId);
        return ResponseEntity.ok(ApiResponse.success("工序已删除"));
    }
}
