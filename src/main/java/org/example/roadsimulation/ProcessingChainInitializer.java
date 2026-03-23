package org.example.roadsimulation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.service.ProcessingChainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 加工链数据初始化器
 * 在系统启动时自动创建示例加工链
 */
@Component
@RequiredArgsConstructor
public class ProcessingChainInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessingChainInitializer.class);
    
    private final ProcessingChainService processingChainService;
    private final ProcessingChainRepository processingChainRepository;
    private final POIRepository poiRepository;
    private final GoodsRepository goodsRepository;
    
    @PostConstruct
    public void init() {
        logger.info("开始初始化加工链数据...");
        
        // 检查是否已存在加工链
        if (processingChainRepository.existsByChainCode("CHAIN_STEEL_FURNITURE_001")) {
            logger.info("加工链已存在，跳过初始化");
            return;
        }
        
        createSteelFurnitureChain();
        
        logger.info("加工链数据初始化完成");
    }
    
    /**
     * 创建钢铁家具加工链
     */
    private void createSteelFurnitureChain() {
        logger.info("创建钢铁家具加工链...");
        
        // 1. 创建加工链
        ProcessingChain chain = new ProcessingChain();
        chain.setChainCode("CHAIN_STEEL_FURNITURE_001");
        chain.setChainName("钢铁→家具加工链");
        chain.setDescription("铁矿→冶炼→钢材加工→家具制造");
        chain.setYieldRate(0.60);  // 综合产出率 60%
        chain.setInputWeightPerCycle(100.0);  // 100 吨铁矿石
        chain.setOutputWeightPerCycle(60.0);  // 60 吨家具
        
        // 2. 获取/创建 POI
        POI ironMine = getPoiByType(POI.POIType.IRON_MINE);
        POI steelMill = getPoiByType(POI.POIType.STEEL_MILL);
        POI steelProcessing = getPoiByType(POI.POIType.STEEL_PROCESSING_PLANT);
        POI furnitureFactory = getPoiByType(POI.POIType.FURNITURE_FACTORY);
        
        // 如果找不到对应的 POI，使用第一个 FACTORY 类型的 POI 作为替代
        if (ironMine == null) ironMine = getPoiByType(POI.POIType.FACTORY);
        if (steelMill == null) steelMill = ironMine;
        if (steelProcessing == null) steelProcessing = ironMine;
        if (furnitureFactory == null) furnitureFactory = ironMine;
        
        logger.info("使用 POI: 铁矿={}, 冶炼厂={}, 钢材加工厂={}, 家具厂={}", 
                poiName(ironMine), poiName(steelMill), 
                poiName(steelProcessing), poiName(furnitureFactory));
        
        // 3. 获取/创建货物
        Goods ironOre = getOrCreateGoods("铁矿石", "RAW_IRON_ORE", "原材料");
        Goods steelBillet = getOrCreateGoods("钢坯", "SEMIF_STEEL_BILLET", "半成品");
        Goods steelMaterial = getOrCreateGoods("钢材", "SEMIF_STEEL_MATERIAL", "半成品");
        Goods furniture = getOrCreateGoods("家具", "PROD_FURNITURE", "成品");
        
        // 4. 创建工序
        // 工序 1: 铁矿开采
        ProcessingStage stage1 = createStage(chain, 1, "铁矿开采", ironMine, 
                null, null, ironOre, "RAW_IRON_ORE", 1.0, 0, 100.0);
        
        // 工序 2: 冶炼
        ProcessingStage stage2 = createStage(chain, 2, "钢铁冶炼", steelMill,
                ironOre, "RAW_IRON_ORE", steelBillet, "SEMIF_STEEL_BILLET", 
                0.8, 90, 100.0);
        
        // 工序 3: 钢材加工
        ProcessingStage stage3 = createStage(chain, 3, "钢材加工", steelProcessing,
                steelBillet, "SEMIF_STEEL_BILLET", steelMaterial, "SEMIF_STEEL_MATERIAL",
                0.75, 60, 80.0);
        
        // 工序 4: 家具制造
        ProcessingStage stage4 = createStage(chain, 4, "家具制造", furnitureFactory,
                steelMaterial, "SEMIF_STEEL_MATERIAL", furniture, "PROD_FURNITURE",
                1.0, 45, 60.0);
        
        // 5. 保存加工链
        processingChainService.createChain(chain);
        logger.info("钢铁家具加工链创建完成：chainCode={}, stages={}", 
                chain.getChainCode(), chain.getStages().size());
    }
    
    private ProcessingStage createStage(ProcessingChain chain, int order, String name, POI poi, 
                                        Goods inputGoods, String inputSku,
                                        Goods outputGoods, String outputSku,
                                        double outputRatio, int timeMinutes, Double maxCapacity) {
        ProcessingStage stage = new ProcessingStage();
        stage.setProcessingChain(chain);
        stage.setStageOrder(order);
        stage.setStageName(name);
        stage.setProcessingPOI(poi);
        stage.setInputGoods(inputGoods);
        stage.setInputGoodsSku(inputSku);
        stage.setOutputGoods(outputGoods);
        stage.setOutputGoodsSku(outputSku);
        stage.setOutputWeightRatio(outputRatio);
        stage.setProcessingTimeMinutes(timeMinutes);
        stage.setMaxCapacityPerCycle(maxCapacity);
        chain.addStage(stage);
        return stage;
    }
    
    private POI getPoiByType(POI.POIType type) {
        return poiRepository.findAll().stream()
                .filter(poi -> poi.getPoiType() == type)
                .findFirst()
                .orElse(null);
    }
    
    private String poiName(POI poi) {
        return poi != null ? poi.getName() + "(" + poi.getPoiType() + ")" : "null";
    }
    
    private Goods getOrCreateGoods(String name, String sku, String category) {
        Optional<Goods> existing = goodsRepository.findBySku(sku);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Goods goods = new Goods(name, sku);
        goods.setCategory(category);
        return goodsRepository.save(goods);
    }
}
