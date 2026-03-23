package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingOrder;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.dto.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProcessingChainService {
    
    // ================= 加工链管理 =================
    ProcessingChain createChain(ProcessingChain chain);
    Optional<ProcessingChain> getChainById(Long id);
    List<ProcessingChain> getAllChains();
    ProcessingChain updateChainStatus(Long id, ProcessingChain.ChainStatus status);
    void deleteChain(Long id);
    
    // ================= 加工工序管理 =================
    ProcessingStage createStage(Long chainId, ProcessingStage stage);
    ProcessingStage updateStage(Long stageId, ProcessingStage stage);
    void deleteStage(Long stageId);
    
    // ================= 加工订单管理 =================
    ProcessingOrder createProcessingOrder(Long chainId, Double inputWeight, String createdBy);
    Optional<ProcessingOrder> getOrderById(Long id);
    List<ProcessingOrder> getOrdersByChainId(Long chainId);
    List<ProcessingOrder> getOrdersByStatus(ProcessingOrder.OrderStatus status);
    
    // ================= 加工执行控制 =================
    void startProcessing(Long orderId);
    void cancelProcessing(Long orderId);
    void updateProcessingProgress(LocalDateTime simNow, int minutesPerLoop);
    
    // ================= 查询与统计 =================
    ProcessingOrderStatusDTO getOrderStatus(Long orderId);
    ProcessingChainStatsDTO getChainStats(Long chainId);
    
    // ================= DTO 查询 =================
    ProcessingChainDTO getChainDTO(Long id);
    List<ProcessingChainDTO> getAllChainDTOs();
    ProcessingOrderDTO getOrderDTO(Long id);
}
