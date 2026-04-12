//package org.example.roadsimulation;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.example.roadsimulation.entity.POI;
//import org.example.roadsimulation.service.POIImportService;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.annotation.Order;
//
//import java.io.File;
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * POI 数据导入配置
// * 用于在应用启动时导入 POI 数据
// */
//@Configuration
//public class POIImportConfig {
//
//    @Value("${poi.import.file:poi_data.json}")
//    private String importFile;
//
//    /**
//     * 创建 POI 数据导入的 CommandLineRunner
//     * 仅在配置开启时执行
//     */
//    @Bean
//    @Order(2)
//    @ConditionalOnProperty(name = "app.poi.import.enabled", havingValue = "true")
//    public CommandLineRunner poiImportRunner(POIImportService poiImportService) {
//        return args -> {
//            System.out.println("========================================");
//            System.out.println("开始导入 POI 数据...");
//            System.out.println("========================================");
//
//            File file = new File(importFile);
//
//            if (!file.exists()) {
//                System.err.println("POI 数据文件不存在：" + importFile);
//                return;
//            }
//
//            try {
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode rootNode = mapper.readTree(file);
//
//                if (!rootNode.isArray()) {
//                    System.err.println("POI 数据文件格式错误，应为 JSON 数组");
//                    return;
//                }
//
//                List<POIImportService.POIData> poiDataList = new ArrayList<>();
//
//                for (JsonNode node : rootNode) {
//                    String name = node.has("name") ? node.get("name").asText() : "未知";
//                    double longitude = node.has("longitude") ? node.get("longitude").asDouble() : 0.0;
//                    double latitude = node.has("latitude") ? node.get("latitude").asDouble() : 0.0;
//                    String typeStr = node.has("poi_type") ? node.get("poi_type").asText() : "WAREHOUSE";
//
//                    // 转换 POI 类型
//                    POI.POIType poiType;
//                    try {
//                        poiType = POI.POIType.valueOf(typeStr);
//                    } catch (IllegalArgumentException e) {
//                        poiType = POI.POIType.WAREHOUSE; // 默认类型
//                    }
//
//                    poiDataList.add(new POIImportService.POIData(
//                            name,
//                            BigDecimal.valueOf(longitude),
//                            BigDecimal.valueOf(latitude),
//                            poiType
//                    ));
//                }
//
//                int savedCount = poiImportService.batchImportPOIs(poiDataList);
//                System.out.println("========================================");
//                System.out.println("POI 数据导入完成！");
//                System.out.println("总共读取：" + poiDataList.size() + " 条");
//                System.out.println("成功导入：" + savedCount + " 条");
//                System.out.println("========================================");
//
//            } catch (IOException e) {
//                System.err.println("读取 POI 数据文件失败：" + e.getMessage());
//                e.printStackTrace();
//            }
//        };
//    }
//}
