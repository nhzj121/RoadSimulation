package org.example.roadsimulation.controller;

import lombok.RequiredArgsConstructor;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.ProcessingChainStatsDTO;
import org.example.roadsimulation.dto.ProcessingItemStatusDTO;
import org.example.roadsimulation.dto.ProcessingOrderStatusDTO;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.service.ProcessingChainServiceV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 加工链控制器 V3（简化版：直接使用 Shipment）
 */
@RestController
@RequestMapping("/api/v3/processing-chain")
@RequiredArgsConstructor
public class ProcessingChainControllerV3 {

    private final ProcessingChainServiceV2 processingChainService;

    // ================= 加工链管理 =================

    @PostMapping
    public ResponseEntity<ProcessingChain> createChain(@RequestBody ProcessingChain chain) {
        ProcessingChain saved = processingChainService.createChain(chain);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessingChain> getChain(@PathVariable Long id) {
        ProcessingChain chain = processingChainService.getChainById(id)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + id));
        return ResponseEntity.ok(chain);
    }

    @GetMapping
    public ResponseEntity<List<ProcessingChain>> getAllChains() {
        return ResponseEntity.ok(processingChainService.getAllChains());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProcessingChain> updateChainStatus(
            @PathVariable Long id,
            @RequestParam ProcessingChain.ChainStatus status) {
        ProcessingChain updated = processingChainService.updateChainStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteChain(@PathVariable Long id) {
        processingChainService.deleteChain(id);
        return ResponseEntity.ok(ApiResponse.success("加工链已删除"));
    }

    // ================= 加工工序管理 =================

    @PostMapping("/{chainId}/stage")
    public ResponseEntity<ProcessingStage> createStage(
            @PathVariable Long chainId,
            @RequestBody ProcessingStage stage) {
        ProcessingStage saved = processingChainService.createStage(chainId, stage);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/stage/{stageId}")
    public ResponseEntity<ProcessingStage> updateStage(
            @PathVariable Long stageId,
            @RequestBody ProcessingStage stageDetails) {
        ProcessingStage updated = processingChainService.updateStage(stageId, stageDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/stage/{stageId}")
    public ResponseEntity<ApiResponse<String>> deleteStage(@PathVariable Long stageId) {
        processingChainService.deleteStage(stageId);
        return ResponseEntity.ok(ApiResponse.success("工序已删除"));
    }

    // ================= 加工运单管理 =================

    @PostMapping("/{chainId}/shipment")
    public ResponseEntity<Shipment> createProcessingShipment(
            @PathVariable Long chainId,
            @RequestBody CreateProcessingShipmentRequest request) {
        Shipment shipment = processingChainService.createProcessingShipment(
                chainId,
                request.getInputWeight(),
                request.getCreatedBy()
        );
        return ResponseEntity.ok(shipment);
    }

    @GetMapping("/shipment/{id}")
    public ResponseEntity<Shipment> getProcessingShipment(@PathVariable Long id) {
        Shipment shipment = processingChainService.getProcessingShipmentById(id)
                .orElseThrow(() -> new RuntimeException("加工运单不存在：" + id));
        return ResponseEntity.ok(shipment);
    }

    @GetMapping("/chain/{chainId}/shipments")
    public ResponseEntity<List<Shipment>> getShipmentsByChainId(@PathVariable Long chainId) {
        return ResponseEntity.ok(processingChainService.getShipmentsByChainId(chainId));
    }

    @GetMapping("/shipments")
    public ResponseEntity<Page<Shipment>> getShipments(Pageable pageable) {
        return ResponseEntity.ok(processingChainService.getShipments(pageable));
    }

    // ================= 加工执行控制 =================

    @PostMapping("/shipment/{id}/start")
    public ResponseEntity<ApiResponse<String>> startProcessing(@PathVariable Long id) {
        processingChainService.startProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("加工已开始"));
    }

    @PostMapping("/shipment/{id}/cancel")
    public ResponseEntity<ApiResponse<String>> cancelProcessing(@PathVariable Long id) {
        processingChainService.cancelProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("加工已取消"));
    }

    @GetMapping("/shipment/{id}/status")
    public ResponseEntity<ProcessingOrderStatusDTO> getShipmentStatus(@PathVariable Long id) {
        ProcessingOrderStatusDTO status = processingChainService.getShipmentStatus(id);
        return ResponseEntity.ok(status);
    }

    // ================= 加工物料项管理 =================

    @GetMapping("/shipment/{id}/items")
    public ResponseEntity<List<ShipmentItem>> getProcessingItems(@PathVariable Long id) {
        List<ShipmentItem> items = processingChainService.getProcessingItemsByShipmentId(id);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/item/{id}")
    public ResponseEntity<ShipmentItem> getProcessingItem(@PathVariable Long id) {
        ShipmentItem item = processingChainService.getProcessingItemById(id)
                .orElseThrow(() -> new RuntimeException("加工物料项不存在：" + id));
        return ResponseEntity.ok(item);
    }

    @GetMapping("/item/{id}/status")
    public ResponseEntity<ProcessingItemStatusDTO> getItemStatus(@PathVariable Long id) {
        ProcessingItemStatusDTO status = processingChainService.getItemStatus(id);
        return ResponseEntity.ok(status);
    }

    @PatchMapping("/item/{id}/progress")
    public ResponseEntity<ShipmentItem> updateItemProgress(
            @PathVariable Long id,
            @RequestParam Integer progressPercent) {
        ShipmentItem item = processingChainService.updateItemProgress(id, progressPercent);
        return ResponseEntity.ok(item);
    }

    // ================= 统计查询 =================

    @GetMapping("/{id}/stats")
    public ResponseEntity<ProcessingChainStatsDTO> getChainStats(@PathVariable Long id) {
        ProcessingChainStatsDTO stats = processingChainService.getChainStats(id);
        return ResponseEntity.ok(stats);
    }

    // ================= 请求 DTO =================

    public static class CreateProcessingShipmentRequest {
        private Double inputWeight;
        private String createdBy;

        public Double getInputWeight() { return inputWeight; }
        public void setInputWeight(Double inputWeight) { this.inputWeight = inputWeight; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }
}
