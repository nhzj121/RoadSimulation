package org.example.roadsimulation.controller;

import lombok.RequiredArgsConstructor;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.CreateProcessingOrderRequest;
import org.example.roadsimulation.dto.ProcessingChainDTO;
import org.example.roadsimulation.dto.ProcessingChainStatsDTO;
import org.example.roadsimulation.dto.ProcessingOrderDTO;
import org.example.roadsimulation.dto.ProcessingOrderStatusDTO;
import org.example.roadsimulation.dto.ProcessingStageDTO;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingOrder;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.service.ProcessingChainService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/processing-chain")
@RequiredArgsConstructor
public class ProcessingChainController {
    
    private final ProcessingChainService processingChainService;
    
    // ================= 加工链管理 =================
    
    /**
     * 创建加工链
     */
    @PostMapping
    public ResponseEntity<ProcessingChainDTO> createChain(@RequestBody ProcessingChain chain) {
        ProcessingChain saved = processingChainService.createChain(chain);
        return ResponseEntity.ok(processingChainService.getChainDTO(saved.getId()));
    }
    
    /**
     * 获取加工链详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessingChainDTO> getChain(@PathVariable Long id) {
        ProcessingChainDTO dto = processingChainService.getChainDTO(id);
        return ResponseEntity.ok(dto);
    }
    
    /**
     * 获取所有加工链
     */
    @GetMapping
    public ResponseEntity<List<ProcessingChainDTO>> getAllChains() {
        return ResponseEntity.ok(processingChainService.getAllChainDTOs());
    }
    
    /**
     * 更新加工链状态
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProcessingChainDTO> updateChainStatus(
            @PathVariable Long id,
            @RequestParam ProcessingChain.ChainStatus status) {
        ProcessingChain updated = processingChainService.updateChainStatus(id, status);
        return ResponseEntity.ok(processingChainService.getChainDTO(updated.getId()));
    }
    
    /**
     * 删除加工链
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChain(@PathVariable Long id) {
        processingChainService.deleteChain(id);
        return ResponseEntity.noContent().build();
    }
    
    // ================= 加工工序管理 =================
    
    /**
     * 创建加工工序
     */
    @PostMapping("/{chainId}/stage")
    public ResponseEntity<ProcessingStageDTO> createStage(
            @PathVariable Long chainId,
            @RequestBody ProcessingStage stage) {
        ProcessingStage saved = processingChainService.createStage(chainId, stage);
        ProcessingStageDTO dto = new ProcessingStageDTO();
        dto.setId(saved.getId());
        dto.setStageOrder(saved.getStageOrder());
        dto.setStageName(saved.getStageName());
        dto.setProcessingTimeMinutes(saved.getProcessingTimeMinutes());
        if (saved.getProcessingPOI() != null) {
            dto.setPoiId(saved.getProcessingPOI().getId());
            dto.setPoiName(saved.getProcessingPOI().getName());
        }
        return ResponseEntity.ok(dto);
    }
    
    /**
     * 更新加工工序
     */
    @PutMapping("/stage/{stageId}")
    public ResponseEntity<ProcessingStageDTO> updateStage(
            @PathVariable Long stageId,
            @RequestBody ProcessingStage stageDetails) {
        ProcessingStage updated = processingChainService.updateStage(stageId, stageDetails);
        ProcessingStageDTO dto = new ProcessingStageDTO();
        dto.setId(updated.getId());
        dto.setStageOrder(updated.getStageOrder());
        dto.setStageName(updated.getStageName());
        dto.setProcessingTimeMinutes(updated.getProcessingTimeMinutes());
        return ResponseEntity.ok(dto);
    }
    
    /**
     * 删除加工工序
     */
    @DeleteMapping("/stage/{stageId}")
    public ResponseEntity<Void> deleteStage(@PathVariable Long stageId) {
        processingChainService.deleteStage(stageId);
        return ResponseEntity.noContent().build();
    }
    
    // ================= 加工订单管理 =================
    
    /**
     * 创建加工订单
     */
    @PostMapping("/{chainId}/order")
    public ResponseEntity<ProcessingOrderDTO> createOrder(
            @PathVariable Long chainId,
            @RequestBody CreateProcessingOrderRequest request) {
        ProcessingOrder order = processingChainService.createProcessingOrder(
                chainId, 
                request.getInputWeight(), 
                request.getCreatedBy());
        return ResponseEntity.ok(processingChainService.getOrderDTO(order.getId()));
    }
    
    /**
     * 获取加工订单详情
     */
    @GetMapping("/order/{id}")
    public ResponseEntity<ProcessingOrderDTO> getOrder(@PathVariable Long id) {
        ProcessingOrderDTO dto = processingChainService.getOrderDTO(id);
        return ResponseEntity.ok(dto);
    }
    
    /**
     * 开始加工
     */
    @PostMapping("/order/{id}/start")
    public ResponseEntity<ApiResponse<String>> startOrder(@PathVariable Long id) {
        processingChainService.startProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("加工已开始"));
    }
    
    /**
     * 取消加工
     */
    @PostMapping("/order/{id}/cancel")
    public ResponseEntity<ApiResponse<String>> cancelOrder(@PathVariable Long id) {
        processingChainService.cancelProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("加工已取消"));
    }
    
    /**
     * 获取订单状态
     */
    @GetMapping("/order/{id}/status")
    public ResponseEntity<ProcessingOrderStatusDTO> getOrderStatus(@PathVariable Long id) {
        ProcessingOrderStatusDTO status = processingChainService.getOrderStatus(id);
        return ResponseEntity.ok(status);
    }
    
    // ================= 统计查询 =================
    
    /**
     * 获取加工链统计
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ProcessingChainStatsDTO> getChainStats(@PathVariable Long id) {
        ProcessingChainStatsDTO stats = processingChainService.getChainStats(id);
        return ResponseEntity.ok(stats);
    }
}
