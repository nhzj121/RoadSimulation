package org.example.roadsimulation;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.roadsimulation.entity.Enrollment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.service.GoodsPOIGenerateService;
import org.example.roadsimulation.service.POIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
 * 数据库相关工厂类的POI点数据获取，具体货物的对应实现，货物生成函数的实现
 * 目前实现程度：
 * 1. 基于 玻璃生产厂 和 家具制造厂 之间的运输路线
 * 2. 每个玻璃生产厂每5秒可能自动生成存在货物， 这个时候匹配一个 家具制造厂 与之对应
 *  2.1. 这里需要注意的是，最客观的模拟应该是 家具制造厂 先提出一个需求，然后再匹配一个相应的某货物生产地产生货物，然后生成分配路线
 *  2.2. 因为我们还没有进行具体的 货物 和 POI 对应关系的考虑，所以只能先进行简单的模拟
 * 3. 每个POI点每12秒进行随机的货物删除，模拟车辆运出货物
 * 4. 每个POI点只生成一次货物
 *
 */

@Component
public class DataInitializer{

    private final GoodsPOIGenerateService goodsPOIGenerateService;
    private final EnrollmentRepository enrollmentRepository;
    private final GoodsRepository goodsRepository;
    private final POIRepository poiRepository;
    private final PlatformTransactionManager transactionManager;

    public List<POI> goalFactoryList;
    public List<POI> CementPlantList; // 水泥厂
    public List<POI> MaterialMarketList; // 建材市场

    public Goods goodsForTest;
    public Goods Cement; // 水泥

//    public List<POI> goalNeedGoodsPOIList = getFilteredPOI("家具", POI.POIType.FACTORY);
    // POI的判断状态和计数
    private final Map<POI, Boolean> poiIsWithGoods = new ConcurrentHashMap<>();
    private final Map<POI, Integer> poiTrueCount = new ConcurrentHashMap<>();

    // 限制条件
    private final int maxTrueCount = 45; // 最大为真的数量
    private double trueProbability = 0.009; // 判断为真的概率

    @Autowired
    public DataInitializer(GoodsPOIGenerateService goodsPOIGenerateService, EnrollmentRepository enrollmentRepository, GoodsRepository goodsRepository,
                           POIRepository poiRepository, PlatformTransactionManager transactionManager) {
        this.goodsPOIGenerateService = goodsPOIGenerateService;
        this.enrollmentRepository = enrollmentRepository;
        this.goodsRepository = goodsRepository;
        this.poiRepository = poiRepository;
        this.transactionManager = transactionManager;
    }

    @PostConstruct
    public void initialize(){
        // 初始化 POI 列表
        this.goalFactoryList = getFilteredPOI("水泥", POI.POIType.FACTORY);
        this.goodsForTest = getGoodsForTest("CEMENT");
        System.out.println("DataInitializer 初始化完成，共加载 " + goalFactoryList.size() + " 个POI");

        initalizePOIStatus();

        System.out.println("DataInitializer 初始化完成");


    }

    /**
     * POI 状态表示的初始化
     */
    private void initalizePOIStatus(){ //List<POI> goalPOITypeList
        /// 测试用例
        for(POI poi: goalFactoryList){
            poiIsWithGoods.put(poi, false);
            poiTrueCount.put(poi, 0);
        }
        /* ----------------- */
        ///  对相关POI进行初始化操作
//        for(POI poi: goalPOITypeList){
//            poiIsWithGoods.put(poi, true);
//            poiTrueCount.put(poi, 0);
//        }
    }

    /**
     * 清理已存在的Enrollment数据
     */
    private void cleanupExistingEnrollments() {
        try {
            List<Enrollment> existingEnrollments = enrollmentRepository.findAll();
            int size = existingEnrollments.size();
            for (Enrollment enrollment : existingEnrollments) {
                if (enrollment.getPoi() != null) {
                    enrollment.getPoi().getEnrollments().remove(enrollment);
                }
                if (enrollment.getGoods() != null) {
                    enrollment.getGoods().getEnrollments().remove(enrollment);
                }
                enrollmentRepository.delete(enrollment);
                System.out.println("删除关系[" + enrollment.getGoods().getName()+","+ enrollment.getPoi().getName() + "]");
            }
            System.out.println("清理完成，共删除 " + size + " 条旧记录");
        } catch (Exception e) {
            System.err.println("清理旧数据时出错: " + e.getMessage());
        }
    }


    /// 测试 关键词检索 获取 模拟所需POI
//    @PostConstruct
//    public void initFactory(String KeyWord){
//        List<POI> factory = poiService.searchByName(KeyWord);
//        AtomicInteger index = new AtomicInteger(1);
//        this.goalFactoryList = factory.stream()
//                .filter(poi -> poi.getPoiType().equals(POI.POIType.FACTORY))
//                .collect(Collectors.toList());
//
//        System.out.println("找到 " + goalFactoryList.size() + " 个石材工厂：");
//        goalFactoryList.forEach(poi -> System.out.println("工厂: " + (index.getAndIncrement()) + poi.getName()));
//    }

    /**
     * 根据 关键字姓名模糊化搜素 与 种类限制 进行POI数据的筛选
     */
    public List<POI> getFilteredPOI(String keyword, POI.POIType goalPOIType) {
        return poiRepository.findByNameContainingIgnoreCase(keyword).stream()
                .filter(poi -> poi.getPoiType().equals(goalPOIType))
                .collect(Collectors.toList());
    }

    /**
     * 根据 sku 进行货物的获取
     */
    public Goods getGoodsForTest(String sku) {
        Optional<Goods> existingGoods = goodsRepository.findBySku(sku);
        Goods goalGoods = null;
        if (existingGoods.isPresent()) {
            goalGoods = existingGoods.get();
            System.out.println("从数据库加载货物: " + goalGoods.getName());
        } else{
            // 如果不存在，创建并保存
            goalGoods = new Goods("玻璃", "00001");
            goodsRepository.save(goalGoods);
            System.out.println("创建新货物: " + goalGoods.getName());
        }
        return goalGoods;
    }

    /**
     *  周期性的随机判断 - 每5秒执行一次
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void periodicJudgement(){
        if (goalFactoryList.isEmpty()) {
            return;
        }

        System.out.println("开始新一轮的POI判断周期...");
        // 对所有POI进行判断

        for (POI poi : goalFactoryList) {
            // 如果当前POI已经为真，跳过判断
            if (poiIsWithGoods.get(poi)) {
                continue;
            }
            long currentTrueCount = poiIsWithGoods.values().stream()
                    .filter(status -> status)
                    .count();
            // 伪随机判断
            if(pseudoRandomJudgement(poi)){
                // 检查是否超过最大限度
                if(canSetToTrue()){
                    setPoiToTrue(poi);
                    System.out.println("POI [" + poi.getName() + "] 判断为真");

//                    this.trueProbability -= 0.005;

                    // 这里可以添加其他业务逻辑，比如初始化关系
                    // ToDo
                    Integer generateQuantity = generateRandomQuantity();
                    // 因为懒加载的原因，在关系建立上运行存在问题，暂时搁置
                    // ToDo
                    initRelationForTest(poi, goodsForTest, generateQuantity);
                }
            }
        }
        printCurrentStatus();
    }

    /**
     * 周期性的重置判断 - 每12秒执行一次
     */
    @Scheduled(fixedRate = 15000) // 12秒一个周期
    @Transactional
    public void periodicReset() {
        if (goalFactoryList.isEmpty()) {
            return;
        }

        System.out.println("开始重置POI判断状态...");

        // 随机选择一个为真的POI重置为假
        List<POI> truePois = getCurrentTruePois();
        if (!truePois.isEmpty()) {
            Random random = new Random();
            POI poiToReset = truePois.get(random.nextInt(truePois.size()));
            // 因为懒加载的原因，在关系建立上运行存在问题，暂时搁置
            // ToDo
            deleteRelationForTest(poiToReset);
            setPoiToFalse(poiToReset);
            System.out.println("POI [" + poiToReset.getName() + "] 已被重置为假");
        } else{
            System.out.println("无可重置的POI数据");
        }

        printCurrentStatus();
    }

    /**
     * 伪随机判断逻辑
     */
    private boolean pseudoRandomJudgement(POI poi) {
        Random random = new Random();
        // 基于概率的简单判断
        if (random.nextDouble() < trueProbability) {
            // 可以在这里添加更复杂的判断逻辑
            // 比如基于POI的属性、历史数据等
            // ToDo
            return true;
        }

        return false;
    }

    /**
     * 检查是否可以设置新的POI为真
     */
    private boolean canSetToTrue() {
        long currentTrueCount = poiIsWithGoods.values().stream()
                .filter(status -> status)
                .count();
        return currentTrueCount < maxTrueCount;
    }

    /**
     * 设置POI为真状态
     */
    private void setPoiToTrue(POI poi) {
        poiIsWithGoods.put(poi, true);
        poiTrueCount.put(poi, poiTrueCount.get(poi) + 1);
    }

    /**
     * 设置POI为假状态
     */
    private void setPoiToFalse(POI poi) {
        poiIsWithGoods.put(poi, false);
    }

    /**
     * 获取当前为真的POI列表
     */
    public List<POI> getCurrentTruePois() {
        return poiIsWithGoods.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取POI的判断统计信息
     */
    public Map<POI, Integer> getPoiJudgementStatistics() {
        return new HashMap<>(poiTrueCount);
    }

    /**
     * 打印当前状态
     */
    private void printCurrentStatus() {
        long trueCount = poiIsWithGoods.values().stream()
                .filter(status -> status)
                .count();

        System.out.println("===================================");
        System.out.println("当前状态 - 为真POI数量: " + trueCount + "/" + maxTrueCount);
        System.out.println("为真的POI列表: " +
                getCurrentTruePois().stream()
                        .map(POI::getName)
                        .collect(Collectors.joining(", ")));
        System.out.println("===================================");
    }

    /**
     * 生成随机货物数量
     */
    private Integer generateRandomQuantity() {
        Random random = new Random();
        return random.nextInt(60) + 10; // 100-600之间的随机数
    }

    @Transactional
    public void initRelationForTest(POI poiForTest, Goods goodsForTest, Integer generateQuantity) {
        try{
            Enrollment enrollmentForTest = new Enrollment(poiForTest, goodsForTest, generateQuantity);
            enrollmentRepository.save(enrollmentForTest);
            poiForTest.addGoodsEnrollment(enrollmentForTest);
            goodsForTest.addPOIEnrollment(enrollmentForTest);

            System.out.println("为POI [" + poiForTest.getName() + "] 初始化关系，数量: " + generateQuantity);
        } catch (Exception e) {
            System.err.println("生成货物关系失败: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteRelationForTest(POI poiForTest) {
        List<Enrollment> goalEnrollment = new ArrayList<>(poiForTest.getEnrollments());

        // 目前只有 POI 的 enrollment 中一个 enrollment, 即一个 POI点 只生成一份 货物
        for (Enrollment enrollment : goalEnrollment) {
            if (poiIsWithGoods.get(poiForTest) && enrollment.getGoods() != null){
                Goods goalGoods = enrollment.getGoods();

                poiForTest.removeGoodsEnrollment(enrollment);
                goalGoods.removePOIEnrollment(enrollment);

                // 删除Enrollment
                enrollmentRepository.delete(enrollment);

                System.out.println("已删除" + poiForTest.getName() + "中的货物" + goalGoods.getName());
            }
            poiRepository.save(poiForTest);
            goodsRepository.save(goodsForTest);
        }
    }

    /**
     * 项目关闭时对所有的 Enrollments 进行清理
     */
    @PreDestroy
    public void cleanupOnShutdown() {
        System.out.println("项目关闭，清理模拟数据...");
        try {
            // 使用编程式事务
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.execute(status -> {
                cleanupExistingEnrollments();
                return null;
            });
            System.out.println("模拟数据清理完成");
        } catch (Exception e) {
            System.err.println("清理数据时出错: " + e.getMessage());
        }
    }
//    @PreDestroy
//    public void cleanupOnShutdown() {
//        System.out.println("项目关闭，清理模拟数据...");
//        cleanupExistingEnrollments();
//        System.out.println("模拟数据清理完成");
//    }

    /// 和其它模块的对接
    /**
     * 获取当前可以展示的POI列表，只展示有货物的POI
     */
    public List<POI> getPOIAbleToShow(){
        return getCurrentTruePois();
    }
}


