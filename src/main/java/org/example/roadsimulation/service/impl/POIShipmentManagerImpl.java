package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.POIShipmentRecord;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.service.POIShipmentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * POI运单管理器实现 —— 线程安全的集中式POI-运单状态管理。
 *
 * <p>使用 ConcurrentHashMap 保证并发安全，所有写操作都是原子性的。
 */
@Component
public class POIShipmentManagerImpl implements POIShipmentManager {

    private static final Logger logger = LoggerFactory.getLogger(POIShipmentManagerImpl.class);

    /** 初始判断概率 */
    private static final double INITIAL_PROBABILITY = 0.049;

    /** 概率衰减因子 */
    private static final double PROBABILITY_DECAY = 0.95;

    /** 最大同时阻塞的POI数量 */
    private static final int MAX_BLOCKED_COUNT = 45;

    /** POI id → 是否被阻塞（有活跃运单） */
    private final Map<Long, Boolean> poiBlockedStatus = new ConcurrentHashMap<>();

    /** 配对Key → 运单记录 */
    private final Map<String, POIShipmentRecord> activeRecords = new ConcurrentHashMap<>();

    /** 起点POI id → 终点POI id */
    private final Map<Long, Long> sourceToDestMap = new ConcurrentHashMap<>();

    /** 配对Key → Shipment */
    private final Map<String, Shipment> pairShipmentMap = new ConcurrentHashMap<>();

    /** POI id → 被选中次数 */
    private final Map<Long, Integer> poiSelectionCount = new ConcurrentHashMap<>();

    /** 当前判断概率 */
    private volatile double currentProbability = INITIAL_PROBABILITY;

    // ========== 阻塞状态管理 ==========

    @Override
    public void blockPOI(POI poi) {
        poiBlockedStatus.put(poi.getId(), true);
        logger.debug("POI [{}] 已标记为阻塞（有货）", poi.getName());
    }

    @Override
    public void releasePOI(POI poi) {
        poiBlockedStatus.remove(poi.getId());
        sourceToDestMap.remove(poi.getId());
        logger.debug("POI [{}] 已释放阻塞状态", poi.getName());
    }

    @Override
    public boolean isPOIBlocked(POI poi) {
        return Boolean.TRUE.equals(poiBlockedStatus.getOrDefault(poi.getId(), false));
    }

    @Override
    public int getBlockedPOICount() {
        return (int) poiBlockedStatus.values().stream().filter(Boolean::booleanValue).count();
    }

    @Override
    public int getMaxBlockedCount() {
        return MAX_BLOCKED_COUNT;
    }

    @Override
    public boolean canBlockMore() {
        return getBlockedPOICount() < getMaxBlockedCount();
    }

    @Override
    public List<POI> getBlockedPOIs() {
        // 返回的是POI id列表，实际使用时需要通过repository加载
        // 这里只返回id列表，调用方需要自己通过repository加载POI实体
        return new ArrayList<>(); // 调用方应使用 getBlockedPOICount + isPOIBlocked
    }

    // ========== 配对运单管理 ==========

    @Override
    public void registerShipment(POI source, POI dest, Shipment shipment) {
        String key = buildKey(source, dest);
        POIShipmentRecord record = new POIShipmentRecord(source, dest, shipment);
        activeRecords.put(key, record);
        pairShipmentMap.put(key, shipment);
        sourceToDestMap.put(source.getId(), dest.getId());
        logger.debug("注册POI配对运单: {} → {}", source.getName(), dest.getName());
    }

    @Override
    public void unregisterShipment(POI source, POI dest) {
        String key = buildKey(source, dest);
        POIShipmentRecord record = activeRecords.remove(key);
        if (record != null) {
            record.setActive(false);
            record.setLastUpdated(java.time.LocalDateTime.now());
        }
        pairShipmentMap.remove(key);
        sourceToDestMap.remove(source.getId());
        logger.debug("注销POI配对运单: {} → {}", source.getName(), dest.getName());
    }

    @Override
    public POI getDestination(POI source) {
        Long destId = sourceToDestMap.get(source.getId());
        // 调用方需要通过repository加载POI实体
        return null; // 只提供id查询，实体由调用方加载
    }

    @Override
    public Shipment getShipment(POI source, POI dest) {
        return pairShipmentMap.get(buildKey(source, dest));
    }

    @Override
    public String getPairKey(POI source, POI dest) {
        return buildKey(source, dest);
    }

    @Override
    public POIShipmentRecord getRecord(POI source, POI dest) {
        return activeRecords.get(buildKey(source, dest));
    }

    // ========== POI选择计数 ==========

    @Override
    public void incrementSelectionCount(POI poi) {
        poiSelectionCount.merge(poi.getId(), 1, Integer::sum);
    }

    @Override
    public int getSelectionCount(POI poi) {
        return poiSelectionCount.getOrDefault(poi.getId(), 0);
    }

    // ========== 超时管理 ==========

    @Override
    public List<POIShipmentRecord> sweepExpiredShipments(int timeoutMinutes) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<POIShipmentRecord> expired = activeRecords.values().stream()
                .filter(record -> record.isActive() && record.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        for (POIShipmentRecord record : expired) {
            record.setActive(false);
            record.setLastUpdated(java.time.LocalDateTime.now());
            activeRecords.remove(record.getPairKey());
            pairShipmentMap.remove(record.getPairKey());

            // 清理 sourceToDestMap 中对应的起点POI
            sourceToDestMap.entrySet().removeIf(e ->
                    (record.getSourcePoiId() + "_" + e.getValue()).equals(record.getPairKey())
                            || record.getSourcePoiId().equals(e.getKey()));

            // 释放POI阻塞状态
            poiBlockedStatus.remove(record.getSourcePoiId());

            logger.info("超时清理运单: pairKey={}, 创建时间={}, 超时阈值={}分钟",
                    record.getPairKey(), record.getCreatedAt(), timeoutMinutes);
        }

        if (!expired.isEmpty()) {
            logger.info("本轮超时清理完成: 共清理 {} 条过期运单记录", expired.size());
        }

        return expired;
    }

    // ========== 概率管理 ==========

    @Override
    public void decayProbability() {
        currentProbability = currentProbability * PROBABILITY_DECAY;
        logger.debug("概率衰减: 当前概率={}", String.format("%.4f", currentProbability));
    }

    @Override
    public double getCurrentProbability() {
        return currentProbability;
    }

    // ========== 全量管理 ==========

    @Override
    public void reset() {
        poiBlockedStatus.clear();
        activeRecords.clear();
        sourceToDestMap.clear();
        pairShipmentMap.clear();
        poiSelectionCount.clear();
        currentProbability = INITIAL_PROBABILITY;
        logger.info("POIShipmentManager 状态已全部重置");
    }

    @Override
    public int getActiveShipmentCount() {
        return (int) activeRecords.values().stream().filter(POIShipmentRecord::isActive).count();
    }

    // ========== 内部辅助 ==========

    private String buildKey(POI source, POI dest) {
        return source.getId() + "_" + dest.getId();
    }

    /** 根据起点POI id获取该POI当前对应的配对Key（用于超时清理时查找） */
    public String getActiveKeyForSource(Long sourcePoiId) {
        Long destId = sourceToDestMap.get(sourcePoiId);
        if (destId != null) {
            return sourcePoiId + "_" + destId;
        }
        return null;
    }
}
