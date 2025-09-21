package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GoodsTransportStats;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ShipmentItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface GoodsService {
    // 创建货物（需要校验SKU的唯一性）
    Goods createGoods(Goods goods);
    // 更新货物信息
    Goods updateGoods(Long id, Goods goodsDetails);
    // 获取所有货物
    List<Goods> getAllGoods();
    // 分页获取货物
    Page<Goods> getAllGoods(Pageable pageable);
    // 根据ID获取货物
    Optional<Goods> getGoodsById(Long id);
    // 根据SKU获取货物
    Optional<Goods> getGoodsBySku(String sku);
    // 根据名称搜索货物
    List<Goods> searchGoodsByName(String name);
    // 根据类别获取货物
    List<Goods> getGoodsByCategory(String category);

    // 查询货物对应的所有运单项
    List<ShipmentItem> getShipmentItemsByGoodsId(Long goodsId);
    Page<ShipmentItem> getShipmentItemsByGoodsId(Long goodsId, Pageable pageable);

    // 查询货物对应的所有分配任务（通过运单项关联）
    List<Assignment> getAssignmentsByGoodsId(Long goodsId);
    Page<Assignment> getAssignmentsByGoodsId(Long goodsId, Pageable pageable);

    // 综合查询
    Page<Goods> searchGoods(String name, String category, Boolean requireTemp,
                            String hazmatLevel, Double minWeight, Double maxWeight,
                            Pageable pageable);

    // 查询货物对应的运单、分配任务
    GoodsTransportStats getTransportStats(Long goodsId);

    // 删除货物（需要检查是否被引用）
    void deleteGoods(Long id);

    // 检查SKU是否存在
    boolean existsBySku(String sku);
}
