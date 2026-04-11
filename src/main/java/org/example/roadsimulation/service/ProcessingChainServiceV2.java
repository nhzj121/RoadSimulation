package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.ProcessingChainStatsDTO;
import org.example.roadsimulation.dto.ProcessingItemStatusDTO;
import org.example.roadsimulation.dto.ProcessingOrderStatusDTO;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 加工链服务接口（V3 - 简化版：直接使用 Shipment）
 */
public interface ProcessingChainServiceV2 {

    // ================= 加工链管理 =================

    ProcessingChain createChain(ProcessingChain chain);
    Optional<ProcessingChain> getChainById(Long id);
    List<ProcessingChain> getAllChains();
    ProcessingChain updateChainStatus(Long id, ProcessingChain.ChainStatus status);
    void deleteChain(Long id);

    // ================= Y 形加工链合并管理 =================

    /**
     * 创建合并运单（Y 形加工链的下游运单）
     * @param upstreamShipmentIds 上游运单 ID 列表
     * @param downstreamChainId 下游加工链 ID
     * @param createdBy 创建人
     * @return 合并运单
     */
    Shipment createMergeShipment(List<Long> upstreamShipmentIds, Long downstreamChainId, String createdBy);

    /**
     * 检查并自动创建合并运单
     * 当上游所有运单完成后，自动创建下游合并运单
     * @param completedShipmentId 刚完成的运单 ID
     */
    void checkAndAutoCreateMergeShipment(Long completedShipmentId);

    // ================= 加工工序管理 =================

    org.example.roadsimulation.entity.ProcessingStage createStage(Long chainId, org.example.roadsimulation.entity.ProcessingStage stage);
    org.example.roadsimulation.entity.ProcessingStage updateStage(Long stageId, org.example.roadsimulation.entity.ProcessingStage stageDetails);
    void deleteStage(Long stageId);

    // ================= 加工运单管理 =================

    /**
     * 创建加工运单（直接创建 Shipment）
     */
    Shipment createProcessingShipment(Long chainId, Double inputWeight, String createdBy);

    /**
     * 获取加工运单
     */
    Optional<Shipment> getProcessingShipmentById(Long shipmentId);

    /**
     * 根据加工链 ID 获取所有运单
     */
    List<Shipment> getShipmentsByChainId(Long chainId);

    /**
     * 分页查询所有运单
     */
    Page<Shipment> getShipments(org.springframework.data.domain.Pageable pageable);

    // ================= 加工执行控制 =================

    /**
     * 开始加工
     */
    void startProcessing(Long shipmentId);

    /**
     * 取消加工
     */
    void cancelProcessing(Long shipmentId);

    /**
     * 更新所有加工中运单的进度
     */
    void updateProcessingProgress(LocalDateTime simNow, int minutesPerLoop);

    /**
     * 完成单个工序
     */
    void completeStage(ShipmentItem currentItem, LocalDateTime simNow);

    // ================= 加工物料项管理 =================

    /**
     * 获取运单的所有加工物料项
     */
    List<ShipmentItem> getProcessingItemsByShipmentId(Long shipmentId);

    /**
     * 获取加工物料项
     */
    Optional<ShipmentItem> getProcessingItemById(Long itemId);

    /**
     * 更新加工进度
     */
    ShipmentItem updateItemProgress(Long itemId, Integer progressPercent);

    // ================= 查询与统计 =================

    /**
     * 获取运单状态
     */
    ProcessingOrderStatusDTO getShipmentStatus(Long shipmentId);

    /**
     * 获取加工链统计
     */
    ProcessingChainStatsDTO getChainStats(Long chainId);

    /**
     * 获取物料项状态
     */
    ProcessingItemStatusDTO getItemStatus(Long itemId);
}
