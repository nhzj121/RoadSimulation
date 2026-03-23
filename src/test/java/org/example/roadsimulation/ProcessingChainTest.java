package org.example.roadsimulation;

import org.example.roadsimulation.dto.CreateProcessingOrderRequest;
import org.example.roadsimulation.dto.ProcessingChainDTO;
import org.example.roadsimulation.dto.ProcessingOrderDTO;
import org.example.roadsimulation.dto.ProcessingOrderStatusDTO;
import org.example.roadsimulation.dto.ProcessingStageDTO;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingOrder;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingTask;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingOrderRepository;
import org.example.roadsimulation.service.ProcessingChainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加工链系统测试类
 * 测试完整的加工链创建、订单管理、加工执行流程
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.class)
@DisplayName("加工链系统测试")
class ProcessingChainTest {

    @Autowired
    private ProcessingChainService processingChainService;

    @Autowired
    private ProcessingChainRepository processingChainRepository;

    @Autowired
    private ProcessingOrderRepository processingOrderRepository;

    @Autowired
    private POIRepository poiRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    private Long testChainId;
    private Long testOrderId;

    @BeforeEach
    void setUp() {
        System.out.println("========== 测试准备 ==========");
    }

    @Test
    @Order(1)
    @DisplayName("1. 创建加工链")
    @Transactional
    @Rollback(false)
    void testCreateProcessingChain() {
        System.out.println("\n=== 测试：创建加工链 ===");

        // 1. 创建加工链
        ProcessingChain chain = new ProcessingChain();
        chain.setChainCode("CHAIN_TEST_" + System.currentTimeMillis());
        chain.setChainName("测试加工链");
        chain.setDescription("用于测试的完整加工链");
        chain.setYieldRate(0.85);
        chain.setInputWeightPerCycle(100.0);
        chain.setOutputWeightPerCycle(85.0);

        // 2. 获取或创建测试 POI
        POI factory1 = getOrCreatePOI("测试工厂 1", POI.POIType.FACTORY);
        POI factory2 = getOrCreatePOI("测试工厂 2", POI.POIType.FACTORY);
        POI factory3 = getOrCreatePOI("测试工厂 3", POI.POIType.FACTORY);

        // 3. 获取或创建测试货物
        Goods rawMaterial = getOrCreateGoods("测试原材料", "TEST_RAW_001", "原材料");
        Goods semiProduct = getOrCreateGoods("测试半成品", "TEST_SEMI_001", "半成品");
        Goods finishedProduct = getOrCreateGoods("测试成品", "TEST_FINISHED_001", "成品");

        // 4. 创建工序
        ProcessingStage stage1 = new ProcessingStage();
        stage1.setStageOrder(1);
        stage1.setStageName("原材料加工");
        stage1.setProcessingPOI(factory1);
        stage1.setOutputGoods(rawMaterial);
        stage1.setOutputGoodsSku("TEST_RAW_001");
        stage1.setOutputWeightRatio(1.0);
        stage1.setProcessingTimeMinutes(30);
        stage1.setMaxCapacityPerCycle(100.0);
        chain.addStage(stage1);

        ProcessingStage stage2 = new ProcessingStage();
        stage2.setStageOrder(2);
        stage2.setStageName("半成品制造");
        stage2.setProcessingPOI(factory2);
        stage2.setInputGoods(rawMaterial);
        stage2.setInputGoodsSku("TEST_RAW_001");
        stage2.setOutputGoods(semiProduct);
        stage2.setOutputGoodsSku("TEST_SEMI_001");
        stage2.setOutputWeightRatio(0.9);
        stage2.setProcessingTimeMinutes(45);
        stage2.setMaxCapacityPerCycle(90.0);
        chain.addStage(stage2);

        ProcessingStage stage3 = new ProcessingStage();
        stage3.setStageOrder(3);
        stage3.setStageName("成品组装");
        stage3.setProcessingPOI(factory3);
        stage3.setInputGoods(semiProduct);
        stage3.setInputGoodsSku("TEST_SEMI_001");
        stage3.setOutputGoods(finishedProduct);
        stage3.setOutputGoodsSku("TEST_FINISHED_001");
        stage3.setOutputWeightRatio(1.0);
        stage3.setProcessingTimeMinutes(60);
        stage3.setMaxCapacityPerCycle(80.0);
        chain.addStage(stage3);

        // 5. 保存加工链
        ProcessingChain savedChain = processingChainService.createChain(chain);

        // 6. 断言
        assertNotNull(savedChain.getId(), "加工链 ID 不应为空");
        assertTrue(savedChain.getChainCode().startsWith("CHAIN_TEST_"));
        assertEquals(3, savedChain.getStages().size(), "应该有 3 个工序");
        assertEquals(135, savedChain.getTotalProcessingTimeMinutes(), "总加工时间应为 135 分钟");

        testChainId = savedChain.getId();
        System.out.println("✓ 加工链创建成功，ID: " + testChainId);
        System.out.println("  - 链编码：" + savedChain.getChainCode());
        System.out.println("  - 工序数量：" + savedChain.getStages().size());
        System.out.println("  - 总加工时间：" + savedChain.getTotalProcessingTimeMinutes() + "分钟");
        System.out.println("  - 产出率：" + savedChain.getYieldRate() * 100 + "%");
    }

    @Test
    @Order(2)
    @DisplayName("2. 查询加工链")
    @Transactional
    void testGetProcessingChain() {
        System.out.println("\n=== 测试：查询加工链 ===");

        // 先创建一个测试链
        if (testChainId == null) {
            testCreateProcessingChain();
        }

        ProcessingChainDTO chainDTO = processingChainService.getChainDTO(testChainId);

        assertNotNull(chainDTO, "加工链 DTO 不应为空");
        assertEquals("测试加工链", chainDTO.getChainName());
        assertEquals(3, chainDTO.getStages().size());

        System.out.println("✓ 加工链查询成功");
        System.out.println("  - 名称：" + chainDTO.getChainName());
        System.out.println("  - 状态：" + chainDTO.getStatus());
        System.out.println("  - 工序列表:");
        for (ProcessingStageDTO stage : chainDTO.getStages()) {
            System.out.println("    " + stage.getStageOrder() + ". " + stage.getStageName() +
                    " (" + stage.getProcessingTimeMinutes() + "分钟)");
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. 创建加工订单")
    @Transactional
    @Rollback(false)
    void testCreateProcessingOrder() {
        System.out.println("\n=== 测试：创建加工订单 ===");

        // 先创建一个测试链
        if (testChainId == null) {
            testCreateProcessingChain();
        }

        // 创建加工订单
        ProcessingOrder order = processingChainService.createProcessingOrder(
                testChainId,
                100.0,  // 输入重量 100 吨
                "test_user"
        );

        assertNotNull(order.getId(), "订单 ID 不应为空");
        assertEquals(ProcessingOrder.OrderStatus.PENDING, order.getStatus());
        assertEquals(100.0, order.getInputWeight());
        assertNotNull(order.getTasks());
        assertEquals(3, order.getTasks().size(), "应该有 3 个加工任务");

        testOrderId = order.getId();
        System.out.println("✓ 加工订单创建成功，ID: " + testOrderId);
        System.out.println("  - 订单号：" + order.getOrderNo());
        System.out.println("  - 状态：" + order.getStatus());
        System.out.println("  - 输入重量：" + order.getInputWeight() + "吨");
        System.out.println("  - 预期产出：" + order.getExpectedOutputWeight() + "吨");
        System.out.println("  - 任务数量：" + order.getTasks().size());
    }

    @Test
    @Order(4)
    @DisplayName("4. 开始加工")
    @Transactional
    @Rollback(false)
    void testStartProcessing() {
        System.out.println("\n=== 测试：开始加工 ===");

        if (testOrderId == null) {
            testCreateProcessingOrder();
        }

        // 开始加工
        processingChainService.startProcessing(testOrderId);

        ProcessingOrder order = processingOrderRepository.findById(testOrderId).orElse(null);
        assertNotNull(order);
        assertEquals(ProcessingOrder.OrderStatus.IN_PROCESS, order.getStatus());

        // 检查第一个任务是否开始
        ProcessingTask firstTask = order.getTasks().stream()
                .filter(t -> t.getStage().getStageOrder() == 1)
                .findFirst()
                .orElse(null);

        assertNotNull(firstTask);
        assertEquals(ProcessingTask.TaskStatus.PROCESSING, firstTask.getStatus());

        System.out.println("✓ 加工已开始");
        System.out.println("  - 订单状态：" + order.getStatus());
        System.out.println("  - 第一道工序：" + firstTask.getStage().getStageName());
        System.out.println("  - 工序状态：" + firstTask.getStatus());
    }

    @Test
    @Order(5)
    @DisplayName("5. 更新加工进度")
    @Transactional
    void testUpdateProcessingProgress() {
        System.out.println("\n=== 测试：更新加工进度 ===");

        if (testOrderId == null) {
            testCreateProcessingOrder();
            processingChainService.startProcessing(testOrderId);
        }

        // 模拟时间推进（推进 60 分钟）
        LocalDateTime simNow = LocalDateTime.now().plusMinutes(60);
        int minutesPerLoop = 30;

        processingChainService.updateProcessingProgress(simNow, minutesPerLoop);

        ProcessingOrderStatusDTO status = processingChainService.getOrderStatus(testOrderId);
        assertNotNull(status);

        System.out.println("✓ 加工进度已更新");
        System.out.println("  - 订单状态：" + status.getStatus());
        System.out.println("  - 当前工序：" + status.getCurrentStageName());
        System.out.println("  - 整体进度：" + status.getOverallProgress() + "%");
    }

    @Test
    @Order(6)
    @DisplayName("6. 获取订单状态")
    @Transactional
    void testGetOrderStatus() {
        System.out.println("\n=== 测试：获取订单状态 ===");

        if (testOrderId == null) {
            testCreateProcessingOrder();
        }

        ProcessingOrderStatusDTO status = processingChainService.getOrderStatus(testOrderId);

        assertNotNull(status);
        assertNotNull(status.getOrderNo());
        assertNotNull(status.getStatus());

        System.out.println("✓ 订单状态查询成功");
        System.out.println("  - 订单号：" + status.getOrderNo());
        System.out.println("  - 状态：" + status.getStatus());
        System.out.println("  - 当前阶段：" + status.getCurrentStageName());
        System.out.println("  - 进度：" + status.getOverallProgress() + "%");
    }

    @Test
    @Order(7)
    @DisplayName("7. 获取加工链统计")
    @Transactional
    void testGetChainStats() {
        System.out.println("\n=== 测试：获取加工链统计 ===");

        if (testChainId == null) {
            testCreateProcessingChain();
        }

        var stats = processingChainService.getChainStats(testChainId);

        assertNotNull(stats);
        assertEquals("测试加工链", stats.getChainName());
        assertNotNull(stats.getTotalOrders());

        System.out.println("✓ 加工链统计查询成功");
        System.out.println("  - 链名称：" + stats.getChainName());
        System.out.println("  - 总订单数：" + stats.getTotalOrders());
        System.out.println("  - 待处理订单：" + stats.getPendingOrders());
        System.out.println("  - 加工中订单：" + stats.getInProcessOrders());
        System.out.println("  - 已完成订单：" + stats.getCompletedOrders());
    }

    @Test
    @Order(8)
    @DisplayName("8. 取消加工订单")
    @Transactional
    @Rollback(false)
    void testCancelProcessing() {
        System.out.println("\n=== 测试：取消加工订单 ===");

        // 确保有可用的测试链
        if (testChainId == null) {
            testCreateProcessingChain();
        }

        // 创建一个新的订单用于取消测试
        ProcessingOrder order = processingChainService.createProcessingOrder(
                testChainId,
                50.0,
                "test_user"
        );

        Long orderIdToCancel = order.getId();

        // 先开始加工
        processingChainService.startProcessing(orderIdToCancel);

        // 然后取消
        processingChainService.cancelProcessing(orderIdToCancel);

        ProcessingOrder cancelledOrder = processingOrderRepository.findById(orderIdToCancel).orElse(null);
        assertNotNull(cancelledOrder);
        assertEquals(ProcessingOrder.OrderStatus.CANCELLED, cancelledOrder.getStatus());

        System.out.println("✓ 加工订单已取消");
        System.out.println("  - 订单号：" + cancelledOrder.getOrderNo());
        System.out.println("  - 最终状态：" + cancelledOrder.getStatus());
    }

    @Test
    @Order(9)
    @DisplayName("9. 查询所有加工链")
    @Transactional
    void testGetAllChains() {
        System.out.println("\n=== 测试：查询所有加工链 ===");

        List<ProcessingChainDTO> chains = processingChainService.getAllChainDTOs();

        assertNotNull(chains);
        System.out.println("✓ 查询成功，共 " + chains.size() + " 条加工链");

        for (ProcessingChainDTO chain : chains) {
            System.out.println("  - [" + chain.getId() + "] " + chain.getChainName() +
                    " (" + chain.getStatus() + ")");
        }
    }

    @Test
    @Order(10)
    @DisplayName("10. 完整流程测试")
    @Transactional
    @Rollback(false)
    void testFullWorkflow() {
        System.out.println("\n=== 完整流程测试 ===");

        // 1. 创建新的测试加工链
        ProcessingChain chain = new ProcessingChain();
        chain.setChainCode("CHAIN_FULL_TEST_" + System.currentTimeMillis());
        chain.setChainName("完整流程测试链");
        chain.setDescription("测试从创建到完成的完整流程");
        chain.setYieldRate(0.90);

        POI poi = getOrCreatePOI("全流程测试工厂", POI.POIType.FACTORY);
        Goods inputGoods = getOrCreateGoods("输入物料", "FULL_TEST_INPUT", "物料");
        Goods outputGoods = getOrCreateGoods("输出产品", "FULL_TEST_OUTPUT", "产品");

        ProcessingStage stage = new ProcessingStage();
        stage.setStageOrder(1);
        stage.setStageName("完整测试工序");
        stage.setProcessingPOI(poi);
        stage.setInputGoods(inputGoods);
        stage.setInputGoodsSku("FULL_TEST_INPUT");
        stage.setOutputGoods(outputGoods);
        stage.setOutputGoodsSku("FULL_TEST_OUTPUT");
        stage.setOutputWeightRatio(0.9);
        stage.setProcessingTimeMinutes(10); // 短时间用于测试
        chain.addStage(stage);

        ProcessingChain savedChain = processingChainService.createChain(chain);
        System.out.println("✓ 步骤 1：创建加工链完成");

        // 2. 创建订单
        ProcessingOrder order = processingChainService.createProcessingOrder(
                savedChain.getId(),
                100.0,
                "full_test"
        );
        System.out.println("✓ 步骤 2：创建订单完成，订单号：" + order.getOrderNo());

        // 3. 开始加工
        processingChainService.startProcessing(order.getId());
        System.out.println("✓ 步骤 3：开始加工完成");

        // 4. 模拟时间推进（超过工序时间）
        LocalDateTime simNow = LocalDateTime.now().plusMinutes(15);
        processingChainService.updateProcessingProgress(simNow, 30);
        System.out.println("✓ 步骤 4：进度更新完成");

        // 5. 检查订单状态
        ProcessingOrderStatusDTO finalStatus = processingChainService.getOrderStatus(order.getId());
        System.out.println("✓ 步骤 5：获取最终状态");
        System.out.println("  - 最终状态：" + finalStatus.getStatus());
        System.out.println("  - 最终进度：" + finalStatus.getOverallProgress() + "%");

        // 断言
        assertNotNull(finalStatus);
        assertTrue(finalStatus.getOverallProgress() >= 100, "进度应该达到或超过 100%");
    }

    // ==================== 辅助方法 ====================

    private POI getOrCreatePOI(String name, POI.POIType poiType) {
        return poiRepository.findByNameAndPoiType(name, poiType).stream().findFirst().orElseGet(() -> {
            POI poi = new POI();
            poi.setName(name);
            poi.setPoiType(poiType);
            poi.setLongitude(java.math.BigDecimal.valueOf(116.4074));
            poi.setLatitude(java.math.BigDecimal.valueOf(39.9042));
            return poiRepository.save(poi);
        });
    }

    private Goods getOrCreateGoods(String name, String sku, String category) {
        return goodsRepository.findBySku(sku).orElseGet(() -> {
            Goods goods = new Goods(name, sku);
            goods.setCategory(category);
            goods.setWeightPerUnit(1.0);
            goods.setVolumePerUnit(1.0);
            return goodsRepository.save(goods);
        });
    }

    @Test
    @Order(99)
    @DisplayName("99. 清理测试数据")
    @Transactional
    void cleanupTestData() {
        System.out.println("\n=== 清理测试数据 ===");
        // 这里可以选择性清理测试数据
        System.out.println("✓ 测试完成");
    }
}
