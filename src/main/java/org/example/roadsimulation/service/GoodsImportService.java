package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.repository.GoodsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 货物数据导入服务
 * 用于批量导入货物数据到数据库
 */
@Service
public class GoodsImportService {

    private final GoodsRepository goodsRepository;

    @Autowired
    public GoodsImportService(GoodsRepository goodsRepository) {
        this.goodsRepository = goodsRepository;
    }

    /**
     * 批量导入货物数据
     * @param goodsDataList 货物数据列表
     * @return 成功导入的数量
     */
    @Transactional
    public int batchImportGoods(List<GoodsData> goodsDataList) {
        List<Goods> goodsToSave = new ArrayList<>();
        int savedCount = 0;
        int skippedCount = 0;

        for (GoodsData data : goodsDataList) {
            // 检查是否已存在相同 SKU 的货物
            if (goodsRepository.existsBySku(data.sku)) {
                System.out.println("跳过已存在的货物：" + data.name + " (SKU: " + data.sku + ")");
                skippedCount++;
                continue;
            }

            Goods goods = new Goods();
            goods.setName(data.name);
            goods.setSku(data.sku);
            goods.setCategory(data.category);
            goods.setDescription(data.description);
            goods.setWeightPerUnit(data.weightPerUnit);
            goods.setVolumePerUnit(data.volumePerUnit);
            goods.setRequireTemp(data.requireTemp);
            goods.setHazmatLevel(data.hazmatLevel);
            goods.setShelfLifeDays(data.shelfLifeDays);
            goods.setCreatedAt(LocalDateTime.now());
            goods.setUpdatedAt(LocalDateTime.now());

            goodsToSave.add(goods);

            // 每 100 条批量保存一次
            if (goodsToSave.size() >= 100) {
                goodsRepository.saveAll(goodsToSave);
                savedCount += goodsToSave.size();
                goodsToSave.clear();
                System.out.println("已导入 " + savedCount + " 条货物数据");
            }
        }

        // 保存剩余数据
        if (!goodsToSave.isEmpty()) {
            goodsRepository.saveAll(goodsToSave);
            savedCount += goodsToSave.size();
        }

        System.out.println("货物导入统计：成功 " + savedCount + " 条，跳过 " + skippedCount + " 条");
        return savedCount;
    }

    /**
     * 货物数据内部类
     */
    public static class GoodsData {
        public String name;
        public String sku;
        public String category;
        public String description;
        public Double weightPerUnit;
        public Double volumePerUnit;
        public Boolean requireTemp;
        public String hazmatLevel;
        public Integer shelfLifeDays;

        public GoodsData(String name, String sku, String category, String description,
                        Double weightPerUnit, Double volumePerUnit, Boolean requireTemp,
                        String hazmatLevel, Integer shelfLifeDays) {
            this.name = name;
            this.sku = sku;
            this.category = category;
            this.description = description;
            this.weightPerUnit = weightPerUnit;
            this.volumePerUnit = volumePerUnit;
            this.requireTemp = requireTemp;
            this.hazmatLevel = hazmatLevel;
            this.shelfLifeDays = shelfLifeDays;
        }
    }
}
