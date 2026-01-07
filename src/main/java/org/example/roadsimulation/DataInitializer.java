package org.example.roadsimulation;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import org.example.roadsimulation.dto.POIPairDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.GoodsPOIGenerateService;
import org.example.roadsimulation.service.ShipmentItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
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
 * 货物与车辆对应设置：
 * 水泥：
 *     20袋 1t级别：金杯T3
 *     100袋 5t级别：重汽HOWO统帅 仓栅式轻卡
 *     200袋 10t级别：中国重汽HOWO G5X 中卡
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
    private final ShipmentItemService shipmentItemService;
    private final VehicleRepository vehicleRepository;

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
                           AssignmentRepository assignmentRepository,
                           VehicleRepository vehicleRepository,
                           ShipmentItemService shipmentItemService) {
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
        this.vehicleRepository = vehicleRepository;
        this.shipmentItemService = shipmentItemService;
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

                    // ToDo 初始化货物对应车辆，这里默认为水泥
                    // 1. 查询适合货物的车辆（多个）
                    List<Vehicle> vehicles = vehicleRepository.findBySuitableGoods("CEMENT");
                    if(vehicles.isEmpty()){
                        System.out.println("警告：没有适配水泥的车辆，跳过此次配对");
                        setPoiToFalse(poi); // 重置状态
                        continue;
                    }
                    // 2. 筛选空闲车辆
                    List<Vehicle> idleVehicles = vehicles.stream()
                            .filter(v -> v.getCurrentStatus() == Vehicle.VehicleStatus.IDLE)
                            .collect(Collectors.toList());

                    if(idleVehicles.isEmpty()){
                        System.out.println("警告：所有适配车辆都在忙碌中，跳过此次配对");
                        setPoiToFalse(poi); // 重置状态
                        continue;
                    }
                    Route route = initializeRoute(poi, endPOI); // 初始化路径
                    // 初始化的运单，运单清单，记录
                    Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = createCompleteGoodsTransport(poi, endPOI, Cement, generateQuantity, idleVehicles);


                    // 初始化运输任务
                    List<Assignment> goalAssignments = initalizeAssignment(vehicleShipmentItemMap, route);

                    // ToDo 车辆分配运输任务逻辑，这里先简单实现进行流程信息完整性测试
                    // 完整建立车辆与任务的双向关联
                    establishVehicleAssignmentRelationship(goalAssignments, poi, endPOI);

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
        return random.nextInt(250) + 50; // 100-600之间的随机数
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
    public Map<Vehicle, ShipmentItem> createCompleteGoodsTransport(POI startPOI, POI endPOI, Goods goods, Integer quantity, List<Vehicle> vehicles) {
        // 1. 创建Shipment
        Shipment shipment = initalizeShipment(startPOI, endPOI, goods, quantity);

        Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = splitAndCreateShipmentItems(shipment, goods, quantity, vehicles);

        // 3. 建立POI与Goods的Enrollment关系
        initRelationBetweenPOIAndGoods(startPOI, goods, quantity);

        return vehicleShipmentItemMap;
    }

    // ToDo 对于剩余货物暂时没有车辆可以用于运输的情况需要另外考虑
    private Map<Vehicle, ShipmentItem> splitAndCreateShipmentItems(
            Shipment shipment, Goods goods, Integer totalQuantity, List<Vehicle> sortedVehicles) {

        Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = new LinkedHashMap<>();
        int remainingQuantity = totalQuantity;

        System.out.println("开始拆分货物，总数量: " + totalQuantity + "，可用车辆: " + sortedVehicles.size());

        // 为每辆车分配货物，直到货物全部分配完或没有可用车辆
        for (Vehicle vehicle : sortedVehicles) {
            if (remainingQuantity <= 0) {
                break;
            }

            // 计算这辆车能运输的最大货物量（基于载重限制）
            Double maxLoad = vehicle.getMaxLoadCapacity();
            if (maxLoad == null || goods.getWeightPerUnit() == null) {
                System.out.println("车辆 " + vehicle.getLicensePlate() + " 缺少载重或货物重量信息，跳过");
                continue;
            }

            // 计算车辆能承载的货物数量（向下取整）
            int capacityInUnits = (int) Math.floor(maxLoad / goods.getWeightPerUnit());

            // 本次分配的数量 = min(车辆容量, 剩余货物量)
            int assignQuantity = Math.min(capacityInUnits, remainingQuantity);

            if (assignQuantity > 0) {
                // 为这辆车创建运单清单
                ShipmentItem shipmentItem = shipmentItemService.initalizeShipmentItem(shipment, goods, assignQuantity);
                vehicleShipmentItemMap.put(vehicle, shipmentItem);

                // 更新剩余货物量
                remainingQuantity -= assignQuantity;

                System.out.printf(
                        "车辆 %s (载重%.2ft) 分配 %d 件货物，剩余 %d 件%n",
                        vehicle.getLicensePlate(), maxLoad, assignQuantity, remainingQuantity
                );
            }
        }

        // 检查是否还有剩余货物
        if (remainingQuantity > 0) {
            System.out.println("警告: 仍有 " + remainingQuantity + " 件货物未分配，需要更多车辆或更大载重车辆");
            // 为剩余货物创建一个运单清单（不指定车辆）
            ShipmentItem remainingItem = shipmentItemService.initalizeShipmentItem(shipment, goods, remainingQuantity);
            vehicleShipmentItemMap.put(null, remainingItem); // 车辆为null表示未分配
        }

        System.out.println("货物拆分完成，共创建 " + vehicleShipmentItemMap.size() + " 个运单清单");
        return vehicleShipmentItemMap;
    }

    @Transactional
    public List<Assignment> initalizeAssignment(Map<Vehicle, ShipmentItem> vehicleShipmentItemMap, Route route) {
        List<Assignment> assignments = new ArrayList<>();
        for (Map.Entry<Vehicle, ShipmentItem> entry : vehicleShipmentItemMap.entrySet()) {
            Vehicle vehicle = entry.getKey();
            ShipmentItem shipmentItem = entry.getValue();

            if (shipmentItem == null) {
                throw new IllegalArgumentException("运单清单为空");
            } else if (route == null) {
                throw new IllegalArgumentException("运输线路规划出错");
            } else {
                Assignment assignment = new Assignment(shipmentItem, route);

                // 关键：如果车辆不为null，则分配给任务
                if (vehicle != null) {
                    assignment.setAssignedVehicle(vehicle);
                }

                assignmentRepository.save(assignment);
                assignments.add(assignment);
            }
        }
        return assignments;
    }

    /**
     * 建立车辆与任务的双向关联
     */
    // ToDO 这里的逻辑是基于车辆在起点来实现的，具体的车辆匹配函数需要后续再完善。
    private void establishVehicleAssignmentRelationship(List<Assignment> assignments, POI startPOI, POI endPOI) {
        try {
            // 1. 重新从数据库加载POI实体
            POI managedStartPOI = poiRepository.findById(startPOI.getId())
                    .orElseThrow(() -> new RuntimeException("起点POI不存在: " + startPOI.getId()));
            POI managedEndPOI = poiRepository.findById(endPOI.getId())
                    .orElseThrow(() -> new RuntimeException("终点POI不存在: " + endPOI.getId()));

            // 2. 获取所有空闲车辆（用于匹配）
            List<Vehicle> allIdleVehicles = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);

            // 如果没有空闲车辆，记录警告
            if (allIdleVehicles.isEmpty()) {
                System.out.println("警告：没有空闲车辆可用，无法分配任务");
                return;
            }

            for (Assignment assignment : assignments) {
                Vehicle matchedVehicle = assignment.getAssignedVehicle();

                // 检查是否有分配的车辆，如果没有则进行匹配
                if (matchedVehicle == null) {
                    System.out.println("Assignment " + assignment.getId() + " 没有分配车辆，开始匹配最近的空闲车辆...");

                    // 3. 计算任务总重量（用于车辆容量检查）
                    double assignmentTotalWeight = calculateAssignmentTotalWeight(assignment);

                    // 4. 匹配最近的空闲车辆（考虑容量）
                    matchedVehicle = matchNearestIdleVehicleWithCapacity(managedStartPOI, allIdleVehicles, assignmentTotalWeight);

                    if (matchedVehicle == null) {
                        System.out.println("警告：无法为Assignment " + assignment.getId() + " 找到合适的车辆（容量不足或没有空闲车辆）");
                        continue;
                    }

                    System.out.println("为Assignment " + assignment.getId() + " 匹配到车辆: " +
                            matchedVehicle.getLicensePlate() + "，距离: " +
                            calculateDistance(matchedVehicle, managedStartPOI) + "公里");

                    // 设置匹配的车辆到Assignment
                    assignment.setAssignedVehicle(matchedVehicle);
                } else {
                    System.out.println("Assignment " + assignment.getId() + " 已有分配的车辆: " + matchedVehicle.getLicensePlate());
                }

                // 5. 从空闲车辆列表中移除已分配的车辆（使用车辆ID的局部变量）
                final Long vehicleIdToRemove = matchedVehicle.getId(); // 创建局部final变量
                allIdleVehicles.removeIf(v -> v.getId().equals(vehicleIdToRemove));

                // 6. 重新加载车辆实体（确保是托管状态）
                Vehicle managedVehicle = vehicleRepository.findById(matchedVehicle.getId())
                        .orElseThrow(() -> new RuntimeException("车辆不存在: "));

                // 7. 双向关联：车辆添加任务
                managedVehicle.addAssignment(assignment);

                // 8. 更新车辆状态
                managedVehicle.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);
                managedVehicle.setPreviousStatus(Vehicle.VehicleStatus.IDLE);
                managedVehicle.setStatusStartTime(LocalDateTime.now());
                managedVehicle.setStatusDurationSeconds(0L);

                // 9. 设置当前位置
                managedVehicle.setCurrentPOI(managedStartPOI);
                if (managedStartPOI.getLongitude() != null && managedStartPOI.getLatitude() != null) {
                    managedVehicle.setCurrentLongitude(managedStartPOI.getLongitude());
                    managedVehicle.setCurrentLatitude(managedStartPOI.getLatitude());
                }

                // 10. 设置任务状态
                assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
                assignment.setUpdatedTime(LocalDateTime.now());
                assignment.setUpdatedBy("DataInitializer -- 运输任务成功分配");

                // 11. 更新车辆信息
                managedVehicle.setUpdatedBy("DataInitializer -- 车辆接收运输任务");
                managedVehicle.setUpdatedTime(LocalDateTime.now());

                // 12. 保存所有更改
                vehicleRepository.save(managedVehicle);
                assignmentRepository.save(assignment);

                System.out.println("成功分配车辆 " + managedVehicle.getLicensePlate() +
                        " 给任务，从 " + managedStartPOI.getName() + " 到 " + managedEndPOI.getName());
            }

            // 13. 输出匹配统计信息
            System.out.println("车辆分配完成。已分配车辆: " + assignments.size() + " 辆，剩余空闲车辆: " + allIdleVehicles.size() + " 辆");

        } catch (Exception e) {
            System.err.println("建立车辆任务关联失败: " + e.getMessage());
            throw new RuntimeException("车辆任务关联失败", e);
        }
    }
    /**
     * 计算Assignment的总重量
     */
    private double calculateAssignmentTotalWeight(Assignment assignment) {
        if (assignment == null || assignment.getShipmentItems() == null) {
            return 0.0;
        }

        return assignment.getShipmentItems().stream()
                .filter(item -> item.getWeight() != null)
                .mapToDouble(ShipmentItem::getWeight)
                .sum();
    }

    /**
     * 匹配最近的空闲车辆（考虑载重能力）
     * @param targetPOI 目标POI
     * @param allVehicles 所有车辆列表
     * @param requiredCapacity 需要的载重能力（吨）
     * @return 匹配到的车辆，如果找不到则返回null
     */
    private Vehicle matchNearestIdleVehicleWithCapacity(POI targetPOI,
                                                        List<Vehicle> allVehicles,
                                                        double requiredCapacity) {
        if (targetPOI == null || allVehicles == null || allVehicles.isEmpty()) {
            return null;
        }

        Vehicle nearestVehicle = null;
        double minDistance = Double.MAX_VALUE;

        // 遍历所有车辆
        for (Vehicle vehicle : allVehicles) {
            // 步骤1：判断车辆是否空闲
            if (vehicle.getCurrentStatus() != Vehicle.VehicleStatus.IDLE) {
                continue;
            }

            // 步骤2：检查载重能力是否足够
            if (vehicle.getMaxLoadCapacity() == null ||
                    vehicle.getMaxLoadCapacity() < requiredCapacity) {
                // 载重能力不足，跳过
                continue;
            }

            // 步骤3：计算距离
            double distance = calculateDistance(vehicle, targetPOI);

            // 如果距离无法计算，跳过
            if (distance >= Double.MAX_VALUE) {
                continue;
            }

            // 步骤4：检查是否比当前记录的最小距离更小
            if (distance < minDistance) {
                minDistance = distance;
                nearestVehicle = vehicle;
            }
        }

        return nearestVehicle;
    }

    /**
     * 计算车辆到POI的距离
     * @param vehicle 车辆
     * @param targetPOI 目标POI
     * @return 距离（公里），如果无法计算返回Double.MAX_VALUE
     */
    private double calculateDistance(Vehicle vehicle, POI targetPOI) {
        if (vehicle == null || targetPOI == null) {
            return Double.MAX_VALUE;
        }

        // 优先使用车辆当前坐标
        if (vehicle.getCurrentLatitude() != null && vehicle.getCurrentLongitude() != null) {
            return haversineDistance(
                    vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude(),
                    targetPOI.getLatitude(), targetPOI.getLongitude()
            );
        }

        // 如果车辆没有当前坐标，但所在POI有坐标
        if (vehicle.getCurrentPOI() != null) {
            return haversineDistance(
                    vehicle.getCurrentPOI().getLatitude(), vehicle.getCurrentPOI().getLongitude(),
                    targetPOI.getLatitude(), targetPOI.getLongitude()
            );
        }

        // 没有位置信息，无法计算距离
        return Double.MAX_VALUE;
    }

    /**
     * 使用Haversine公式计算两点间距离（公里）
     * @param lat1 纬度1
     * @param lon1 经度1
     * @param lat2 纬度2
     * @param lon2 经度2
     * @return 距离（公里）
     */
    private double haversineDistance(BigDecimal lat1, BigDecimal lon1,
                                     BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }

        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return 6371.0 * c; // 地球半径6371公里
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
                        Shipment freshShipment = shipmentRepository.findById(shipment.getId()).orElse(null);

                        if (freshShipment != null) {
                            // 先删除ShipmentItems（使用新的查询方式，避免直接操作集合）
                            List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(freshShipment.getId());
                            for (ShipmentItem item : items) {
                                // 先清除关联
                                Assignment assignment = item.getAssignment();
                                if (assignment != null) {
                                    if (assignment.getAssignedVehicle() != null){
                                        Vehicle assignedVehicle = vehicleRepository.findById(
                                                assignment.getAssignedVehicle().getId()
                                        ).orElse(null);
                                        if (assignedVehicle != null) {
                                            // 解除双向关联
                                            assignedVehicle.removeAssignment(assignment);
                                            // 检查车辆是否还有其他进行中的任务
                                            boolean hasOtherActiveAssignments = assignedVehicle.getAssignments()
                                                    .stream()
                                                    .anyMatch(a ->
                                                            a.getStatus() == Assignment.AssignmentStatus.ASSIGNED ||
                                                                    a.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS
                                                    );

                                            // 如果没有其他进行中的任务，才重置状态
                                            if (!hasOtherActiveAssignments) {
                                                assignedVehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
                                                assignedVehicle.setPreviousStatus(Vehicle.VehicleStatus.ORDER_DRIVING);
                                                assignedVehicle.setStatusStartTime(LocalDateTime.now());
                                                assignedVehicle.setCurrentPOI(null);
                                            }
                                            assignedVehicle.setUpdatedTime(LocalDateTime.now());
                                            vehicleRepository.save(assignedVehicle);
                                        }
                                    } else {
                                        // 如果没有分配车辆，记录日志
                                        System.out.println("Assignment " + assignment.getId() + " 没有分配车辆");
                                    }
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
            // 重置所有车辆到成都市中心
            cleanupService.resetAllVehiclesToChengduCenter();
            // 清理模拟数据（Enrollment，Assignment，ShipmentItem，Shipment）
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


