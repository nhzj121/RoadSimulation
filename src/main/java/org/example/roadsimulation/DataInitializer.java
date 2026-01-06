package org.example.roadsimulation;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import org.example.roadsimulation.dto.POIPairDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.GoodsPOIGenerateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
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
    private final AssignmentRepository assignmentRepository;
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
    // 记录每个配对的状态
    private final Map<String, PairStatus> pairStatusMap = new ConcurrentHashMap<>();
    private static class PairStatus {
        @Getter
        private final String pairId;
        @Getter
        private final LocalDateTime createdAt;
        @Setter
        @Getter
        private LocalDateTime lastUpdated;
        @Setter
        @Getter
        private boolean isActive;
        @Setter
        @Getter
        private boolean isDrawn; // 是否已被前端绘制
        @Setter
        @Getter
        private Long shipmentId;

        public PairStatus(String pairId, Long shipmentId) {
            this.pairId = pairId;
            this.shipmentId = shipmentId;
            this.createdAt = LocalDateTime.now();
            this.lastUpdated = this.createdAt;
            this.isActive = true;
            this.isDrawn = false;
        }
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
                           SimulationDataCleanupService cleanupService,
                           AssignmentRepository assignmentRepository) {
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
        this.assignmentRepository = assignmentRepository;
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
        this.Cement = getGoodsForTest("CEMENT");
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
     * 在创建配对时记录状态
     */
    private void createPairStatus(POI startPOI, POI endPOI, Shipment shipment) {
        String pairId = generatePoiPairKey(startPOI, endPOI);
        PairStatus status = new PairStatus(pairId, shipment.getId());
        pairStatusMap.put(pairId, status);
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
                    Vehicle vehicle = null; // ToDo 初始化货物对应车辆，这里暂时设置为null
                    Route route = initializeRoute(poi, endPOI); // 初始化路径
                    // 初始化的运单，运单清单，记录
                    ShipmentItem goalShipmentItem = createCompleteGoodsTransport(poi, endPOI, Cement, generateQuantity);
                    // 初始化运输任务
                    Assignment goalAssignment = initalizeAssignment(goalShipmentItem, route);
                    startToEndMapping.put(poi, endPOI); // 保存对应关系

                    // 记录配对关系
                    String key = generatePoiPairKey(poi, endPOI);
                    Shipment shipment = poiPairShipmentMapping.get(key);
                    if (shipment != null) {
                        createPairStatus(poi, endPOI, shipment);
                    }
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
            POI selectedPoi = truePois.get(random.nextInt(truePois.size()));

            // 关键：从数据库中重新加载POI，而不是使用map中的旧引用
            POI freshSelectedPoi = poiRepository.findById(selectedPoi.getId())
                    .orElseThrow(() -> new RuntimeException("POI not found: " + selectedPoi.getId()));

            // 使用重新加载的POI
            deleteRelationBetweenPOIAndGoods(selectedPoi);

            // 更新映射关系
            POI correspondingEndPOI = null;
            for (Map.Entry<POI, POI> entry : startToEndMapping.entrySet()) {
                if (entry.getKey().getId().equals(freshSelectedPoi.getId())) {
                    correspondingEndPOI = entry.getValue();
                    break;
                }
            }

            if (correspondingEndPOI != null) {
                startToEndMapping.keySet().removeIf(key -> key.getId().equals(freshSelectedPoi.getId()));
                System.out.println("同时移除对应的终点POI: " + correspondingEndPOI.getName());
            }

            trueProbability = trueProbability / 0.95;

            // 更新状态，使用freshSelectedPoi
            setPoiToFalse(selectedPoi);
            System.out.println("POI [" + freshSelectedPoi.getName() + "] 已被重置为假");
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
    public Route initializeRoute(POI startpoi, POI endPOI) {
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
            return route;
        } else{
            Route route = goalRoute.get(0);
            System.out.println("使用现有路径：" + route.getRouteCode());
            return route;
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

    // 货物，货物清单，货物清单的完善
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
    public ShipmentItem createCompleteGoodsTransport(POI startPOI, POI endPOI, Goods goods, Integer quantity) {
        // 1. 创建Shipment
        Shipment shipment = initalizeShipment(startPOI, endPOI, goods, quantity);

        // 2. 创建ShipmentItem并关联
        ShipmentItem shipmentItem = initalizeShipmentItem(shipment, goods, quantity);

        // 3. 建立POI与Goods的Enrollment关系
        initRelationBetweenPOIAndGoods(startPOI, goods, quantity);

        return shipmentItem;
    }

    @Transactional
    public Assignment initalizeAssignment(ShipmentItem shipmentItem, Route route) {
        if(shipmentItem == null){
            throw new IllegalArgumentException("运单清单为空");
        } else if(route == null){
            throw new IllegalArgumentException("运输线路规划出错");
        } else{
            Assignment assignment = new Assignment(shipmentItem, route);
            assignmentRepository.save(assignment);
            return assignment;
        }
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
        // 只重新加载POI，其他保持不变
        POI freshPOI = poiRepository.findById(poiForTest.getId())
                .orElseThrow(() -> new RuntimeException("POI not found: " + poiForTest.getId()));

        // 使用freshPOI而不是poiForTest
        List<Enrollment> goalEnrollment = new ArrayList<>(freshPOI.getEnrollments());

        for (Enrollment enrollment : goalEnrollment) {
            if (enrollment.getGoods() != null){
                Goods goalGoods = enrollment.getGoods();

                // 找到相关的Shipment并删除
                POI endPOI = startToEndMapping.get(poiForTest); // 仍然用旧对象从map获取
                if (endPOI != null) {
                    String key = generatePoiPairKey(freshPOI, endPOI); // 但生成key用freshPOI
                    Shipment shipment = poiPairShipmentMapping.remove(key);

                    if (shipment != null) {
                        // 重新加载Shipment以确保它在当前持久化上下文中
                        Shipment freshShipment = shipmentRepository.findById(shipment.getId())
                                .orElse(null);

                        if (freshShipment != null) {
                            // 先删除ShipmentItems（使用新的查询方式，避免直接操作集合）
                            List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(freshShipment.getId());
                            for (ShipmentItem item : items) {
                                // 先清除关联
                                Assignment assignment = item.getAssignment();
                                if (assignment != null) {
                                    if (assignment.getShipmentItems() != null) {
                                        assignment.getShipmentItems().remove(item);
                                    }
                                    item.setAssignment(null);
                                    if (assignment.getShipmentItems().isEmpty()) {
                                        // 解除与Vehicle和Driver的关联
                                        assignment.setAssignedVehicle(null);
                                        assignment.setAssignedDriver(null);
                                        // 删除这个Assignment
                                        assignmentRepository.delete(assignment);
                                        System.out.println("删除空Assignment: " + assignment.getId());
                                    } else {
                                        // 如果还有其他item，则保存更新
                                        assignmentRepository.save(assignment);
                                    }
                                }
                                // 删除ShipmentItem
                                shipmentItemRepository.delete(item);
                            }

                            // 清除Shipment的items集合（如果使用双向关联）
                            freshShipment.getItems().clear();
                            shipmentRepository.save(freshShipment); // 确保状态同步

                            // 最后删除Shipment
                            shipmentRepository.delete(freshShipment);
                            System.out.println("已删除相关运单: " + freshShipment.getRefNo());
                        }
                    }
                }

                // 关键修改：使用freshPOI而不是poiForTest
                freshPOI.removeGoodsEnrollment(enrollment);
                goalGoods.removePOIEnrollment(enrollment);
                enrollmentRepository.delete(enrollment);

                System.out.println("已删除" + freshPOI.getName() + "中的货物" + goalGoods.getName());

                // 保存更新
                poiRepository.save(freshPOI);
                goodsRepository.save(goalGoods);
            }
        }
        POI endPOI = startToEndMapping.get(poiForTest);
        if (endPOI != null) {
            String pairId = generatePoiPairKey(freshPOI, endPOI);
            markPairAsCompleted(pairId);

            // 从映射中移除
            startToEndMapping.remove(poiForTest);
        }

        // 更新本地map状态
        poiIsWithGoods.put(freshPOI, false);
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

    /**
     * 获取当前所有POI配对
     */
    public List<POIPairDTO> getCurrentPOIPairs() {
        List<POIPairDTO> pairs = new ArrayList<>();

        for (Map.Entry<POI, POI> entry : startToEndMapping.entrySet()) {
            POI startPOI = entry.getKey();
            POI endPOI = entry.getValue();

            String pairId = generatePoiPairKey(startPOI, endPOI);
            PairStatus status = pairStatusMap.get(pairId);

            // 如果配对不存在或未激活，跳过
            if (status == null || !status.isActive()) {
                continue;
            }

            // 从数据库中重新加载确保数据最新
            POI freshStartPOI = poiRepository.findById(startPOI.getId())
                    .orElse(null);
            POI freshEndPOI = poiRepository.findById(endPOI.getId())
                    .orElse(null);

            if (freshStartPOI != null && freshEndPOI != null) {
                POIPairDTO pair = new POIPairDTO();
                pair.setPairId(pairId);
                pair.setStartPOIId(freshStartPOI.getId());
                pair.setStartPOIName(freshStartPOI.getName());
                pair.setStartLng(freshStartPOI.getLongitude());
                pair.setStartLat(freshStartPOI.getLatitude());
                pair.setStartPOIType(freshStartPOI.getPoiType().toString());

                pair.setEndPOIId(freshEndPOI.getId());
                pair.setEndPOIName(freshEndPOI.getName());
                pair.setEndLng(freshEndPOI.getLongitude());
                pair.setEndLat(freshEndPOI.getLatitude());
                pair.setEndPOIType(freshEndPOI.getPoiType().toString());

                // 获取货物信息（通过Enrollment）
                Optional<Enrollment> enrollment = enrollmentRepository
                        .findByPoiAndGoods(freshStartPOI, Cement);
                enrollment.ifPresent(e -> {
                    pair.setGoodsName(Cement.getName());
                    pair.setQuantity(e.getQuantity());
                });

                // 获取运单信息
                String key = generatePoiPairKey(freshStartPOI, freshEndPOI);
                Shipment shipment = poiPairShipmentMapping.get(key);
                if (shipment != null) {
                    pair.setShipmentRefNo(shipment.getRefNo());
                }

                pair.setCreatedAt(status.getCreatedAt());
                pair.setLastUpdated(status.getLastUpdated());
                pair.setStatus(status.isActive() ? "ACTIVE" : "COMPLETED");
                pairs.add(pair);
            }
        }

        return pairs;
    }

    /**
     * 获取新增的POI配对（未被标记为已绘制的）
     */
    public List<POIPairDTO> getNewPOIPairs() {
        return getCurrentPOIPairs().stream()
                .filter(pair -> {
                    PairStatus status = pairStatusMap.get(pair.getPairId());
                    return status != null && status.isActive() && !status.isDrawn();
                })
                .collect(Collectors.toList());
    }

    /**
     * 标记配对为已绘制
     */
    public void markPairAsDrawn(String pairId) {
        PairStatus status = pairStatusMap.get(pairId);
        if (status != null) {
            status.setDrawn(true);
            status.setLastUpdated(LocalDateTime.now());
        }
    }

    /**
     * 标记配对为已完成（货物已送达）
     */
    public void markPairAsCompleted(String pairId) {
        PairStatus status = pairStatusMap.get(pairId);
        if (status != null) {
            status.setActive(false);
            status.setDrawn(false); // 允许前端清理绘制
            status.setLastUpdated(LocalDateTime.now());
        }
    }
}


