package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.POIShipmentRecord;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;

import java.util.List;

/**
 * POI运单管理器 —— 统一管理所有存在运输清单的POI点。
 *
 * <p>替代 DataInitializer 中零散的 Map 结构，提供集中式的：
 * <ul>
 *   <li>POI阻塞状态管理（哪个POI已有活跃运单，不再生成新货物）</li>
 *   <li>POI配对→运单映射</li>
 *   <li>超时运单清理（解决货物生成后永不运输的问题）</li>
 * </ul>
 */
public interface POIShipmentManager {

    // ========== 阻塞状态管理 ==========

    /** 标记POI为"有货"状态，阻止对该POI重复生成货物 */
    void blockPOI(POI poi);

    /** 释放POI的"有货"状态，允许下一轮生成货物 */
    void releasePOI(POI poi);

    /** 判断POI是否被阻塞（已有活跃运单） */
    boolean isPOIBlocked(POI poi);

    /** 获取当前被阻塞的POI数量 */
    int getBlockedPOICount();

    /** 获取当前最大允许的阻塞POI数量 */
    int getMaxBlockedCount();

    /** 判断是否还可以阻塞更多POI */
    boolean canBlockMore();

    /** 获取当前被阻塞的POI列表 */
    List<POI> getBlockedPOIs();

    // ========== 配对运单管理 ==========

    /** 注册一个POI配对的运单 */
    void registerShipment(POI source, POI dest, Shipment shipment);

    /** 货物送达后注销运单，释放POI */
    void unregisterShipment(POI source, POI dest);

    /** 根据起点POI获取终点POI */
    POI getDestination(POI source);

    /** 根据POI配对Key获取运单 */
    Shipment getShipment(POI source, POI dest);

    /** 获取POI配对Key */
    String getPairKey(POI source, POI dest);

    /** 获取运单记录 */
    POIShipmentRecord getRecord(POI source, POI dest);

    // ========== POI选择计数 ==========

    /** 记录POI被选中的次数 */
    void incrementSelectionCount(POI poi);

    /** 获取POI被选中的总次数 */
    int getSelectionCount(POI poi);

    // ========== 超时管理 ==========

    /**
     * 扫掠过期的运单记录，释放超时未完成运输的POI。
     * @param timeoutMinutes 超时分钟数（仿真时间）
     * @return 被清理的运单记录列表
     */
    List<POIShipmentRecord> sweepExpiredShipments(int timeoutMinutes);

    // ========== 概率管理 ==========

    /** 衰减判断概率 */
    void decayProbability();

    /** 获取当前判断概率 */
    double getCurrentProbability();

    // ========== 全量管理 ==========

    /** 清空所有状态 */
    void reset();

    /** 获取活跃运单总数 */
    int getActiveShipmentCount();
}
