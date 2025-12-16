package org.example.roadsimulation;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.GoodsPOIGenerateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
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
    private final RouteRepository routeRepository;
    private final CustomerRepository customerRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final SimulationDataCleanupService cleanupService;

    private final Map<POI, POI> startToEndMapping = new ConcurrentHashMap<>(); // 起点到终点的映射关系
    // 修改成员变量，使用起点-终点对作为键
    private final Map<String, Shipment> poiPairShipmentMapping = new ConcurrentHashMap<>();
    // 生成唯一键的方法
    private String generatePoiPairKey(POI startPOI, POI endPOI) {
        return startPOI.getId() + "_" + endPOI.getId();
    }

    public List<POI> CementPlantList; // 水泥厂
    public List<POI> MaterialMarketList; // 建材市场

    public Goods Cement; // 水泥

//    public List<POI> goalNeedGoodsPOIList = getFilteredPOI("家具", POI.POIType.FACTORY);
    // POI的判断状态和计数
    private final Map<POI, Boolean> poiIsWithGoods = new ConcurrentHashMap<>();
    private final Map<POI, Integer> poiTrueCount = new ConcurrentHashMap<>();

    // 限制条件
    private final int maxTrueCount = 45; // 最大为真的数量
    private double trueProbability = 0.009; // 判断为真的概率

    @Autowired
    public DataInitializer(GoodsPOIGenerateService goodsPOIGenerateService,
                           EnrollmentRepository enrollmentRepository,
                           GoodsRepository goodsRepository,
                           POIRepository poiRepository,
                           PlatformTransactionManager transactionManager,
                           RouteRepository routeRepository,
                           CustomerRepository customerRepository,
                           ShipmentRepository shipmentRepository,
                           ShipmentItemRepository shipmentItemRepository,
                           SimulationDataCleanupService cleanupService) {
        this.goodsPOIGenerateService = goodsPOIGenerateService;
        this.enrollmentRepository = enrollmentRepository;
        this.goodsRepository = goodsRepository;
        this.poiRepository = poiRepository;
        this.transactionManager = transactionManager;
        this.routeRepository = routeRepository;
        this.customerRepository = customerRepository;
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.cleanupService = cleanupService;
    }

    /**
     * 生成货物 - 由主循环调用
     */
    @Transactional
    public void generateGoods(int loopCount) {
        if (CementPlantList.isEmpty() || MaterialMarketList.isEmpty()) {
            System.out.println("生成工厂为空");
            return;
        }

        System.out.println("开始货物生成检查（循环 " + loopCount + "）");

        periodicJudgement();
    }

    /**
     * 运出货物 - 由主循环调用
     */
    @Transactional
    public void shipOutGoods(int loopCount) {
        List<POI> truePois = getCurrentTruePois();

        if (truePois.isEmpty()) {
            System.out.println("当前没有可运出的货物");
            return;
        }

        periodicReset();
    }

    /**
     * 打印仿真状态 - 由主循环调用
     */
    public void printSimulationStatus(int loopCount) {
        int trueCount = getCurrentTruePois().size();

        System.out.println("===================================");
        System.out.println("仿真状态报告");
        System.out.println("当前循环: " + loopCount);
        System.out.println("模拟时间: " + (loopCount * 30 / 60.0) + " 小时");
        System.out.println("有货物POI数量: " + trueCount + "/" + maxTrueCount);

        if (trueCount > 0) {
            System.out.println("有货物POI列表:");
            getCurrentTruePois().forEach(poi -> {
                POI endPOI = startToEndMapping.get(poi);
                System.out.println("  - " + poi.getName() +
                        (endPOI != null ? " → " + endPOI.getName() : ""));
            });
        }

        System.out.println("===================================");
    }

    @PostConstruct
    public void initialize(){
        // 初始化 POI 列表
        this.CementPlantList = getFilteredPOIByNameAndType("水泥", POI.POIType.FACTORY);
        this.MaterialMarketList = getFilterdPOIByType(POI.POIType.MATERIAL_MARKET);
        // this.goalFactoryList = getFilteredPOIByNameAndType("水泥", POI.POIType.FACTORY);
        this.Cement = getGoodsForTest("CEMENT_001");  // 避免与旧数据冲突
        System.out.println("DataInitializer 初始化完成，共加载 " + CementPlantList.size() + " 个起点POI 和 " + MaterialMarketList.size() + "个终点POI");

        initalizePOIStatus();

        System.out.println("DataInitializer 初始化完成");
    }

    /**
     * POI 状态表示的初始化
     */
    private void initalizePOIStatus(){ //List<POI> goalPOITypeList
        /// 测试用例
        for(POI poi: CementPlantList){
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
    public List<POI> getFilteredPOIByNameAndType(String keyword, POI.POIType goalPOIType) {
        return poiRepository.findByNameContainingIgnoreCase(keyword).stream()
                .filter(poi -> poi.getPoiType().equals(goalPOIType))
                .collect(Collectors.toList());
    }
    /**
     * 根据 种类 进行POI数据的筛选
     */
    public List<POI> getFilterdPOIByType(POI.POIType goalPOIType) {
        return new ArrayList<>(poiRepository.findByPoiType(goalPOIType));
    }

    /**
     * 根据 sku 进行货物的获取
     */
//    public Goods getGoodsForTest(String sku) {
//        Optional<Goods> existingGoods = goodsRepository.findBySku(sku);
//        if (existingGoods.isPresent()) {
//            Goods goods = existingGoods.get();
//            System.out.println("从数据库加载货物: " + goods.getName() + " (SKU: " + sku + ")");
//            return goods;
//        }
//
//        // 根据传入 SKU 创建对应货物，避免硬编码
//        String name = switch (sku) {
//            case "CEMENT" -> "水泥";
//            case "GLASS"  -> "玻璃";
//            // 可扩展其他类型
//            default       -> "未知货物_" + sku;
//        };
//
//        Goods newGoods = new Goods(name, sku);  // 假设 Goods 构造函数为 (name, sku)
//        // 可选：设置其他必要字段，如 weightPerUnit、volumePerUnit 等
//        // newGoods.setWeightPerUnit(...);
//        // newGoods.setVolumePerUnit(...);
//
//        Goods savedGoods = goodsRepository.save(newGoods);
//        System.out.println("创建新货物: " + savedGoods.getName() + " (SKU: " + sku + ")");
//        return savedGoods;
//    }
    /**
     * 根据 sku 进行货物的获取
     */
    public Goods getGoodsForTest(String sku) {
        Optional<Goods> existingGoods = goodsRepository.findBySku(sku);
        Goods goods;
        if (existingGoods.isPresent()) {
            goods = existingGoods.get();
            System.out.println("从数据库加载货物: " + goods.getName() + " (SKU: " + sku + ")");
        } else {
            String name = switch (sku) {
                case "CEMENT_001" -> "水泥";  // 修正为实际使用的SKU
                case "GLASS"     -> "玻璃";
                default          -> "未知货物_" + sku;
            };

            goods = new Goods(name, sku);
            goodsRepository.save(goods);  // 先保存以获取ID
            System.out.println("创建新货物: " + goods.getName() + " (SKU: " + sku + ")");
        }

        // ===== 新增：强制设置水泥的默认单位重量和体积 =====
        if ("CEMENT_001".equals(sku)) {
            if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() == 0.0) {
                goods.setWeightPerUnit(1.0);
            }
            if (goods.getVolumePerUnit() == null || goods.getVolumePerUnit() == 0.0) {
                goods.setVolumePerUnit(0.7);
            }
            goodsRepository.save(goods);
            System.out.println("为水泥货物设置默认单位重量(1.0吨)和体积(0.7m³)");
        }
        // ===== 结束新增 =====

        return goods;
    }

    /**
     *  周期性的随机判断 - 每5秒执行一次
     *  用于随机选择 起点POI
     */
    //@Scheduled(fixedRate = 10000)
    @Transactional
    public void periodicJudgement(){
        if (CementPlantList.isEmpty() ||  MaterialMarketList.isEmpty()) {
            return;
        }

        System.out.println("开始新一轮的POI判断周期...");
        // 对所有POI进行判断

        for (POI poi : CementPlantList) {
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

                    // 调整判断的因素数值
                    trueProbability = trueProbability * 0.95;

                    System.out.println("POI [" + poi.getName() + "] 判断为真");

                    Random random = new Random();
                    // 这里可以添加其他业务逻辑，比如初始化关系
                    // ToDo
                    // 随机获取终点POI
                    POI endPOI = this.MaterialMarketList.get(random.nextInt(this.MaterialMarketList.size()));

                    Integer generateQuantity = generateRandomQuantity();

                    initializeRoute(poi, endPOI); // 初始化路径
                    Shipment goalShipment = createCompleteGoodsTransport(poi, endPOI, Cement, generateQuantity);
                    // Assignment goalAssignment = initalizeAssignment();
                    startToEndMapping.put(poi, endPOI); // 保存对应关系
                    // 与货物通过Enrollment建立联系
                    initRelationBetweenPOIAndGoods(poi, Cement, generateQuantity);
                }
            }
        }
        printCurrentStatus();
    }

    /**
     * 周期性的重置判断 - 每12秒执行一次
     */
    //@Scheduled(fixedRate = 15000) // 12秒一个周期
    @Transactional
    public void periodicReset() {
        if (CementPlantList.isEmpty() || MaterialMarketList.isEmpty()) {
            return;
        }

        System.out.println("开始重置POI判断状态...");

        // 随机选择一个为真的POI重置为假
        List<POI> truePois = getCurrentTruePois();
        if (!truePois.isEmpty()) {
            Random random = new Random();
            POI poiToReset = truePois.get(random.nextInt(truePois.size()));

            POI correspondingEndPOI = startToEndMapping.remove(poiToReset);
            if (correspondingEndPOI != null) {
                System.out.println("同时移除对应的终点POI: " + correspondingEndPOI.getName());
            }

            trueProbability = trueProbability / 0.95;

            deleteRelationBetweenPOIAndGoods(poiToReset);
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
        return random.nextInt(25) + 10; // 100-600之间的随机数
    }

    // 起点与终点之间通过 route 实现的关系建立
    @Transactional
    public void initializeRoute(POI startpoi, POI endPOI) {
        List<Route> goalRoute = routeRepository.findByStartPOIIdAndEndPOIId(startpoi.getId(), endPOI.getId());

        // 现在先默认选择id最小的route
        if (goalRoute.isEmpty()) {
            Route route = new Route(startpoi, endPOI);
            // 如果名称长度小于3，使用完整名称；否则截取前3个字符
            String startName = startpoi.getName();
            String endName = endPOI.getName();
            String startAbbr = startName.length() >= 3 ? startName.substring(0, 3) : startName;
            String endAbbr = endName.length() >= 3 ? endName.substring(0, 3) : endName;

            route.setName(startAbbr + "-" + endAbbr);
            route.setRouteCode(startpoi.getId() + "_" + endPOI.getId());
            route.setRouteType("road");
            route.setDistance(calculateDistance(startpoi, endPOI));
            route.setEstimatedTime(calculateEstimatedTime(startpoi, endPOI));
            routeRepository.save(route);
            System.out.println("新建路径：" + route.getRouteCode());
        } else{
            Route route = goalRoute.get(0);
            System.out.println("使用现有路径：" + route.getRouteCode());
        }

    }

    // 计算两个 POI点 之间的距离
    // 可以用于进行 随机生成的对应两点 是否可以作为仿真模拟需要路径的 简单判断
    private Double calculateDistance(POI startPOI, POI endPOI) {
        // ToDo 测试需要，先随便返回一个值
        return 0.0;
    }

    // 计算两点该路线的预期运输时间
    private Double calculateEstimatedTime(POI startPOI, POI endPOI) {
        // ToDo 测试需要，先随便返回一个值
        return 0.0;
    }

     //货物，货物清单，货物清单的完善
    @Transactional(rollbackFor = Exception.class)
    public Shipment initalizeShipment(POI startPOI, POI endPOI, Goods goods, Integer quantity) {
        try {
            String refNo = generateUniqueRefNo(goods.getSku());
            if (goods.getWeightPerUnit() == null || goods.getVolumePerUnit() == null) {
                throw new IllegalArgumentException("货物单位重量或体积不能为空");
            }
            Double totalWeight = quantity * goods.getWeightPerUnit();
            Double totalVolume = quantity * goods.getVolumePerUnit();

            Shipment shipment = new Shipment(refNo, startPOI, endPOI, totalWeight, totalVolume);
            // 设置状态为已创建
            shipment.setStatus(Shipment.ShipmentStatus.CREATED);

            Shipment savedShipment = shipmentRepository.save(shipment);

            // 添加到映射中，便于后续查找
            String key = generatePoiPairKey(startPOI, endPOI);
            poiPairShipmentMapping.put(key, savedShipment);

            return savedShipment;

        } catch (Exception e) {
            System.out.print("生成运单失败 - 起点: "+ startPOI.getName() + ", 终点: "+endPOI.getName()+", 货物: "+goods.getName());
            throw new RuntimeException("生成运单失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ShipmentItem initalizeShipmentItem(Shipment shipment, Goods goods, Integer quantity) {
        try {
            ShipmentItem shipmentItem = new ShipmentItem(
                    shipment,
                    goods.getName(),
                    quantity,
                    goods.getSku(),
                    goods.getWeightPerUnit() * quantity,
                    goods.getVolumePerUnit() * quantity
            );

            // 关键：关联Goods实体
            shipmentItem.setGoods(goods);

            // 关键：确保双向关系（ShipmentItem构造函数中已调用setShipment）
            // 但Shipment一侧也需要添加item（构造函数已处理）

            ShipmentItem savedItem = shipmentItemRepository.save(shipmentItem);

            return savedItem;

        } catch (Exception e) {
            System.out.println("生成运单明细失败 - 运单: " + shipment.getRefNo() + ", 货物: " + goods.getName());
            throw new RuntimeException("生成运单明细失败", e);
        }
    }

    private String generateUniqueRefNo(String sku) {
        // 生成唯一refNo，例如: CEMENT_20240101_123456
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String random = String.format("%06d", new Random().nextInt(1000000));
        return sku + "_" + timestamp + "_" + random;
    }

    @Transactional(rollbackFor = Exception.class)
    public Shipment createCompleteGoodsTransport(POI startPOI, POI endPOI, Goods goods, Integer quantity) {
        // 1. 创建Shipment
        Shipment shipment = initalizeShipment(startPOI, endPOI, goods, quantity);

        // 2. 创建ShipmentItem并关联
        ShipmentItem shipmentItem = initalizeShipmentItem(shipment, goods, quantity);

        // 3. 建立POI与Goods的Enrollment关系
        initRelationBetweenPOIAndGoods(startPOI, goods, quantity);

        return shipment;
    }

    @Transactional
    public Assignment initalizeAssignment(Shipment shipment, Goods goods, Integer Quantity) {
        return null;
    }

    // POI点与货物关系的建立与删除
    @Transactional
    public void initRelationBetweenPOIAndGoods(POI poiForTest, Goods goodsForTest, Integer generateQuantity) {
        try{
            Enrollment enrollmentForTest = new Enrollment(poiForTest, goodsForTest, generateQuantity);
            enrollmentRepository.save(enrollmentForTest);
            // 添加双向关联
            if (!poiForTest.getEnrollments().contains(enrollmentForTest)) {
                poiForTest.addGoodsEnrollment(enrollmentForTest);
            }

            if (!goodsForTest.getEnrollments().contains(enrollmentForTest)) {
                goodsForTest.addPOIEnrollment(enrollmentForTest);
            }

            System.out.println("为POI [" + poiForTest.getName() + "] 初始化关系，数量: " + generateQuantity);
        } catch (Exception e) {
            System.err.println("生成货物关系失败: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteRelationBetweenPOIAndGoods(POI poiForTest) {
        List<Enrollment> goalEnrollment = new ArrayList<>(poiForTest.getEnrollments());

        for (Enrollment enrollment : goalEnrollment) {
            if (enrollment.getGoods() != null){
                Goods goalGoods = enrollment.getGoods();

                // 找到相关的Shipment并删除
                POI endPOI = startToEndMapping.get(poiForTest);
                if (endPOI != null) {
                    String key = generatePoiPairKey(poiForTest, endPOI);
                    Shipment shipment = poiPairShipmentMapping.remove(key);

                    if (shipment != null) {
                        // 先删除ShipmentItem（级联或手动）
                        for (ShipmentItem item : shipment.getItems()) {
                            // 如果有Assignment关联，需要先解除
                            if (item.getAssignment() != null) {
                                item.setAssignment(null);
                            }
                            shipmentItemRepository.delete(item);
                        }
                        // 删除Shipment
                        shipmentRepository.delete(shipment);
                        System.out.println("已删除相关运单: " + shipment.getRefNo());
                    }
                }

                // 删除Enrollment
                poiForTest.removeGoodsEnrollment(enrollment);
                goalGoods.removePOIEnrollment(enrollment);
                enrollmentRepository.delete(enrollment);

                System.out.println("已删除" + poiForTest.getName() + "中的货物" + goalGoods.getName());

                // 保存更新
                poiRepository.save(poiForTest);
                goodsRepository.save(goalGoods);
            }
        }
    }

    /**
     * 项目关闭时对所有的 Enrollments 进行清理
     */
    @PreDestroy
    public void cleanupOnShutdown() {
        System.out.println("项目关闭，清理模拟数据...");
        try {
            // 使用专门的清理服务
            cleanupService.cleanupAllSimulationData();
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
        List<POI> AbleToShow = new ArrayList<>();

        List<POI> goalStartPOI = getCurrentTruePois();
        AbleToShow.addAll(goalStartPOI);

        for(POI poi : goalStartPOI){
            POI endPOI = startToEndMapping.get(poi);
            if(endPOI != null && !AbleToShow.contains(endPOI)){
                AbleToShow.add(endPOI);
            }
        }
        return AbleToShow;
    }
}


