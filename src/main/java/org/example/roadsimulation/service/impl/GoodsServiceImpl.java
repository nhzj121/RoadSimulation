package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.GoodsTransportStats;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.exception.GoodsAlreadyExistsException;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class GoodsServiceImpl implements GoodsService {
    private GoodsRepository goodsRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    //private final AssignmentRepository assignmentRepository;

    @Autowired
    public GoodsServiceImpl(GoodsRepository goodsRepository,
                            ShipmentItemRepository shipmentItemRepository
                            /*,AssignmentRepository assignmentRepository*/) {
        this.goodsRepository = goodsRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        //this.assignmentRepository = assignmentRepository;
    }

    // 创建货物
    @Override
    public Goods createGoods(Goods goods) {
        // 检查SKU是否已存在
        if (goods.getSku() != null && goodsRepository.existsBySku(goods.getSku())) {
            // 获取已存在的货物信息
            Goods existingGoods = goodsRepository.findBySku(goods.getSku())
                    .orElseThrow(() -> new IllegalStateException("数据不一致：SKU存在但找不到对应货物"));

            // 抛出包含详细信息的自定义异常
            throw new GoodsAlreadyExistsException(
                    "货物SKU已存在: " + goods.getSku(),
                    goods.getSku(),
                    existingGoods.getId(),
                    existingGoods.getName(),
                    existingGoods.getCategory()
            );
        }

        // 设置创建时间
        goods.setCreatedAt(LocalDateTime.now());
        goods.setUpdatedAt(LocalDateTime.now());

        return goodsRepository.save(goods);
    }

    @Override
    public Goods updateGoods(Long id, Goods goodsDetails){
        return goodsRepository.findById(id)
                .map(goods -> {
                    // 1. 如果SKU被修改，检查新SKU是否唯一（排除自身）
                    if (!goods.getSku().equals(goodsDetails.getSku()) &&
                            goodsRepository.existsBySku(goodsDetails.getSku())) {
                        throw new IllegalArgumentException("SKU已存在: " + goodsDetails.getSku());
                    }

                    // 2. 更新字段
                    goods.setName(goodsDetails.getName());
                    goods.setSku(goodsDetails.getSku());
                    goods.setCategory(goodsDetails.getCategory());
                    goods.setDescription(goodsDetails.getDescription());
                    goods.setWeightPerUnit(goodsDetails.getWeightPerUnit());
                    goods.setVolumePerUnit(goodsDetails.getVolumePerUnit());
                    goods.setRequireTemp(goodsDetails.getRequireTemp());
                    goods.setHazmatLevel(goodsDetails.getHazmatLevel());
                    goods.setShelfLifeDays(goodsDetails.getShelfLifeDays());

                    // 3. 保存更新
                    return goodsRepository.save(goods);
                })
                .orElseThrow(() -> new RuntimeException("货物不存在，ID: " + id));
    }
    @Override
    @Transactional(readOnly = true)
    public List<Goods> getAllGoods(){
        return goodsRepository.findAll();
    }

    // 分页获取货物
    @Override
    @Transactional(readOnly = true)
    public Page<Goods> getAllGoods(Pageable pageable){
        return goodsRepository.findAll(pageable);
    }
    // 根据ID获取货物
    @Override
    @Transactional(readOnly = true)
    public Optional<Goods> getGoodsById(Long id){
        return goodsRepository.findById(id);
    }
    // 根据SKU获取货物
    @Override
    @Transactional(readOnly = true)
    public Optional<Goods> getGoodsBySku(String sku){
        return goodsRepository.findBySku(sku);
    }
    // 根据名称搜索货物
    @Override
    @Transactional(readOnly = true)
    public List<Goods> searchGoodsByName(String name){
        return goodsRepository.findByNameContainingIgnoreCase(name);
    }
    // 根据类别获取货物
    @Override
    @Transactional(readOnly = true)
    public List<Goods> getGoodsByCategory(String category){
        return goodsRepository.findByCategory(category);
    }

    // 查询货物对应的所有运单项 ToDo
    @Override
    @Transactional(readOnly = true)
    public List<ShipmentItem> getShipmentItemsByGoodsId(Long goodsId){
        return null;
    }
    @Override
    @Transactional(readOnly = true)
    public Page<ShipmentItem> getShipmentItemsByGoodsId(Long goodsId, Pageable pageable){
        return null;
    }

    // 查询货物对应的所有分配任务（通过运单项关联） ToDo
    @Override
    @Transactional(readOnly = true)
    public List<Assignment> getAssignmentsByGoodsId(Long goodsId){
        return null;
    }
    @Override
    @Transactional(readOnly = true)
    public Page<Assignment> getAssignmentsByGoodsId(Long goodsId, Pageable pageable){
        return null;
    }

    // 综合查询
    @Override
    @Transactional(readOnly = true)
    public Page<Goods> searchGoods(String name, String category, Boolean requireTemp,
                                   String hazmatLevel, Double minWeight, Double maxWeight,
                                   Pageable pageable) {

        // 参数预处理和验证
        if (minWeight != null && minWeight < 0) {
            throw new IllegalArgumentException("最小重量不能为负数");
        }

        if (maxWeight != null && maxWeight < 0) {
            throw new IllegalArgumentException("最大重量不能为负数");
        }

        if (minWeight != null && maxWeight != null && minWeight > maxWeight) {
            throw new IllegalArgumentException("最小重量不能大于最大重量");
        }

        // 执行查询
        return goodsRepository.searchGoods(
                StringUtils.hasText(name) ? name.trim() : null,
                StringUtils.hasText(category) ? category.trim() : null,
                requireTemp,
                StringUtils.hasText(hazmatLevel) ? hazmatLevel.trim() : null,
                minWeight,
                maxWeight,
                pageable
        );
    }

    @Override
    public void deleteGoods(Long id) {
        Goods goods = goodsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("货物不存在，ID: " + id));

        // 检查是否被运单项引用
        if (!goods.getShipmentItems().isEmpty()) {
            throw new IllegalStateException("无法删除货物，存在关联的运单项");
        }

        goodsRepository.delete(goods);
    }

    @Override
    public boolean existsBySku(String sku){
        return goodsRepository.existsBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsTransportStats getTransportStats(Long goodsId) {
        // 验证货物是否存在
        if (!goodsRepository.existsById(goodsId)) {
            throw new RuntimeException("货物不存在，ID: " + goodsId);
        }

        // 获取所有关联的运单项
        List<ShipmentItem> items = shipmentItemRepository.findByGoodsIdWithAssignment(goodsId);

        // 计算统计数据
        GoodsTransportStats stats = new GoodsTransportStats();
        stats.setTotalShipments(items.size());

        // 按状态统计
        Map<Assignment.AssignmentStatus, Long> statusCount = items.stream()
                .filter(item -> item.getAssignment() != null)
                .map(item -> item.getAssignment().getStatus())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        stats.setStatusCount(statusCount);

        // 计算总运输量
        double totalWeight = items.stream()
                .mapToDouble(item -> item.getWeight() != null ? item.getWeight() : 0)
                .sum();
        stats.setTotalWeight(totalWeight);

        double totalVolume = items.stream()
                .mapToDouble(item -> item.getVolume() != null ? item.getVolume() : 0)
                .sum();
        stats.setTotalVolume(totalVolume);

        // 找到最近的运输时间
        Optional<LocalDateTime> latestDate = items.stream()
                .filter(item -> item.getAssignment() != null && item.getAssignment().getStartTime() != null)
                .map(item -> item.getAssignment().getStartTime())
                .max(LocalDateTime::compareTo);
        latestDate.ifPresent(stats::setLastTransportDate);

        return stats;
    }

}
