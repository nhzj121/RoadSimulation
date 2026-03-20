package org.example.roadsimulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.service.GoodsImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 货物数据导入配置
 * 用于在应用启动时导入货物数据
 */
@Configuration
public class GoodsImportConfig {

    @Value("${goods.import.file:goods_data.json}")
    private String importFile;

    /**
     * 创建货物数据导入的 CommandLineRunner
     * 仅在配置开启时执行
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "app.goods.import.enabled", havingValue = "true")
    public CommandLineRunner goodsImportRunner(GoodsImportService goodsImportService) {
        return args -> {
            System.out.println("========================================");
            System.out.println("开始导入货物数据...");
            System.out.println("========================================");

            File file = new File(importFile);

            if (!file.exists()) {
                System.err.println("货物数据文件不存在：" + importFile);
                return;
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(file);

                if (!rootNode.isArray()) {
                    System.err.println("货物数据文件格式错误，应为 JSON 数组");
                    return;
                }

                List<GoodsImportService.GoodsData> goodsDataList = new ArrayList<>();

                for (JsonNode node : rootNode) {
                    String name = node.has("name") ? node.get("name").asText() : "未知货物";
                    String sku = node.has("sku") ? node.get("sku").asText() : "";
                    String category = node.has("category") ? node.get("category").asText() : "普通货物";
                    String description = node.has("description") ? node.get("description").asText("") : "";
                    Double weightPerUnit = node.has("weight_per_unit") ? node.get("weight_per_unit").asDouble() : null;
                    Double volumePerUnit = node.has("volume_per_unit") ? node.get("volume_per_unit").asDouble() : null;
                    Boolean requireTemp = node.has("require_temp") ? node.get("require_temp").asBoolean(false) : false;
                    String hazmatLevel = node.has("hazmat_level") ? node.get("hazmat_level").asText("") : "";
                    Integer shelfLifeDays = node.has("shelf_life_days") ? node.get("shelf_life_days").asInt() : null;

                    // 如果 SKU 为空，使用名称生成
                    if (sku == null || sku.trim().isEmpty()) {
                        sku = generateSkuFromName(name);
                    }

                    goodsDataList.add(new GoodsImportService.GoodsData(
                            name,
                            sku,
                            category,
                            description,
                            weightPerUnit,
                            volumePerUnit,
                            requireTemp,
                            hazmatLevel,
                            shelfLifeDays
                    ));
                }

                int savedCount = goodsImportService.batchImportGoods(goodsDataList);
                System.out.println("========================================");
                System.out.println("货物数据导入完成！");
                System.out.println("总共读取：" + goodsDataList.size() + " 条");
                System.out.println("成功导入：" + savedCount + " 条");
                System.out.println("========================================");

            } catch (IOException e) {
                System.err.println("读取货物数据文件失败：" + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    /**
     * 根据名称生成 SKU
     */
    private String generateSkuFromName(String name) {
        // 取名称前 3 个字符的拼音首字母（简化处理，使用大写）
        String prefix = name.length() >= 3 ? 
            name.substring(0, 3).toUpperCase() : 
            name.toUpperCase();
        // 添加时间戳后缀保证唯一性
        return prefix + "_" + System.currentTimeMillis();
    }
}
