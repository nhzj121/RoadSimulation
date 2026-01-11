package org.example.roadsimulation;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.Setter;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.dto.AssignmentStatusDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.GoodsPOIGenerateService;
import org.example.roadsimulation.service.ShipmentItemService;
import org.example.roadsimulation.service.ShipmentProgressService;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final ShipmentProgressService shipmentProgressService;
    private final GoodsPOIGenerateService goodsPOIGenerateService;
    private final EnrollmentRepository enrollmentRepository;
    private final GoodsRepository goodsRepository;
    private final POIRepository poiRepository;
    private final RouteRepository routeRepository;
    private final CustomerRepository customerRepository;
    private final AssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final SimulationDataCleanupService cleanupService;
    private final ShipmentItemService shipmentItemService;
    private final VehicleRepository vehicleRepository;
    private final VehicleMatchingService vehicleMatchingService;

    private final Map<POI, POI> startToEndMapping = new ConcurrentHashMap<>(); // 起点到终点的映射关系
    // 修改成员变量，使用起点-终点对作为键
    private final Map<String, Shipment> poiPairShipmentMapping = new ConcurrentHashMap<>();
    // 生成唯一键的方法
    private String generatePoiPairKey(POI startPOI, POI endPOI) {
        return startPOI.getId() + "_" + endPOI.getId();
    }

    // Assignment状态记录配对
    private final Map<String, AssignmentStatusDTO> assignmentStatusMap = new ConcurrentHashMap<>();
    // 管理 Assignment 状态
    private final Map<Long, AssignmentBriefDTO> assignmentBriefMap = new ConcurrentHashMap<>();

    // ToDO 等待合适时删除中间代码
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
    // ToDO

    public List<POI> CementPlantList; // 水泥厂
    public List<POI> MaterialMarketList; // 建材市场

    public Goods Cement; // 水泥

//    public List<POI> goalNeedGoodsPOIList = getFilteredPOI("家具", POI.POIType.FACTORY);
    // POI的判断状态和计数
    private final Map<POI, Boolean> poiIsWithGoods = new ConcurrentHashMap<>();
    private final Map<POI, Integer> poiTrueCount = new ConcurrentHashMap<>();

    // 限制条件
    private final int maxTrueCount = 300; // 最大为真的数量
    private double trueProbability = 0.1; // 判断为真的概率

    @Autowired
    public DataInitializer(GoodsPOIGenerateService goodsPOIGenerateService,
                           EnrollmentRepository enrollmentRepository,
                           GoodsRepository goodsRepository,
                           POIRepository poiRepository,
                           RouteRepository routeRepository,
                           CustomerRepository customerRepository,
                           ShipmentRepository shipmentRepository,
                           ShipmentItemRepository shipmentItemRepository,
                           SimulationDataCleanupService cleanupService,
                           AssignmentRepository assignmentRepository,
                           VehicleRepository vehicleRepository,
                           ShipmentItemService shipmentItemService,
                           VehicleMatchingService vehicleMatchingService,
                           @Lazy ShipmentProgressService shipmentProgressService) {
        this.goodsPOIGenerateService = goodsPOIGenerateService;
        this.enrollmentRepository = enrollmentRepository;
        this.goodsRepository = goodsRepository;
        this.poiRepository = poiRepository;
        this.routeRepository = routeRepository;
        this.customerRepository = customerRepository;
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.cleanupService = cleanupService;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.shipmentItemService = shipmentItemService;
        this.vehicleMatchingService = vehicleMatchingService;
        this.shipmentProgressService = shipmentProgressService;
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

//    /**
//     * 运出货物 - 由主循环调用
//     */
//    @Transactional
//    public void shipOutGoods(int loopCount) {
//        List<POI> truePois = getCurrentTruePois();
//
//        if (truePois.isEmpty()) {
//            System.out.println("当前没有可运出的货物");
//            return;
//        }
//
//        periodicReset();
//    }

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

        // 1. 收集所有需要生成货物的POI
        List<POI> poisToGenerateGoods = new ArrayList<>();
        for (POI poi : CementPlantList) {
            // 如果当前POI已经为真，跳过判断
            if (poiIsWithGoods.get(poi)) {
                continue;
            }

            // 伪随机判断
            if (pseudoRandomJudgement(poi)) {
                // 检查是否超过最大限度
                if (canSetToTrue()) {
                    poisToGenerateGoods.add(poi);
                }
            }
        }

        if (poisToGenerateGoods.isEmpty()) {
            System.out.println("本轮没有需要生成货物的POI");
            return;
        }

        System.out.println("本轮有 " + poisToGenerateGoods.size() + " 个POI需要生成货物");

        // 2. 批量获取空闲车辆（只查询一次）
        List<Vehicle> allIdleVehicles = vehicleRepository.findBySuitableGoodsAndCurrentStatus(
                "CEMENT", Vehicle.VehicleStatus.IDLE);

        if (allIdleVehicles.isEmpty()) {
            System.out.println("警告：没有适配水泥的空闲车辆，跳过此次周期");
            return;
        }

        System.out.println("获取到 " + allIdleVehicles.size() + " 辆空闲水泥运输车辆");

        // 3. 为每个POI批量处理货物生成
        for (POI poi : poisToGenerateGoods) {
            try {
                System.out.println("为POI [" + poi.getName() + "] 生成货物");
                setPoiToTrue(poi);
                trueProbability = trueProbability * 0.98;

                Random random = new Random();
                // 随机获取终点POI
                POI endPOI = this.MaterialMarketList.get(random.nextInt(this.MaterialMarketList.size()));
                Integer generateQuantity = generateRandomQuantity();

                // 从总空闲车辆列表中创建一个副本用于本次POI
                List<Vehicle> availableVehicles = new ArrayList<>(allIdleVehicles);

                // 计算需要的总重量
                Double requiredWeight = Cement.getWeightPerUnit() * generateQuantity;

                Route route = initializeRoute(poi, endPOI);

                // 批量创建货物运输
                Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = createCompleteGoodsTransport(
                        poi, endPOI, Cement, generateQuantity, availableVehicles);

                List<Assignment> goalAssignments = initalizeAssignment(vehicleShipmentItemMap, route);

                // 建立车辆分配关系
                establishVehicleAssignmentRelationship(goalAssignments, poi, endPOI);

                // 从总列表中移除已分配的车辆
                for (Vehicle assignedVehicle : vehicleShipmentItemMap.keySet()) {
                    if (assignedVehicle != null) {
                        allIdleVehicles.removeIf(v -> v.getId().equals(assignedVehicle.getId()));
                    }
                }

                // 记录状态信息
                for (Assignment assignment : goalAssignments) {
                    Vehicle assignedVehicle = assignment.getAssignedVehicle();
                    if (assignedVehicle != null) {
                        Shipment shipment = null;
                        for (ShipmentItem item : assignment.getShipmentItems()) {
                            if (item.getShipment() != null) {
                                shipment = item.getShipment();
                                break;
                            }
                        }
                        createAssignmentStatusRecord(assignment, poi, endPOI, shipment);
                    }
                }

                startToEndMapping.put(poi, endPOI);

                String key = generatePoiPairKey(poi, endPOI);
                Shipment shipment = poiPairShipmentMapping.get(key);
                if (shipment != null) {
                    createPairStatus(poi, endPOI, shipment);
                }

            } catch (Exception e) {
                System.err.println("为POI [" + poi.getName() + "] 生成货物失败: " + e.getMessage());
                setPoiToFalse(poi); // 重置状态
            }
        }

        printCurrentStatus();
    }

    /**
     * 运出货物 - 这个方法不再被主循环调用，改为由车辆到达终点触发
     * 保留方法，但移除 @Scheduled 注解和循环调用
     */
    @Transactional
    public void shipOutGoodsWhenVehicleArrives(POI startPOI, POI endPOI, Vehicle vehicle) {
        try {
            System.out.println("车辆 " + vehicle.getLicensePlate() +
                    " 已到达终点 " + endPOI.getName() +
                    "，开始执行货物运出操作");

            // 使用重新加载的POI
            POI freshStartPOI = poiRepository.findById(startPOI.getId())
                    .orElseThrow(() -> new RuntimeException("POI not found: " + startPOI.getId()));

            // 执行删除操作
            deleteRelationBetweenPOIAndGoods(freshStartPOI, vehicle);

            System.out.println("POI [" + freshStartPOI.getName() +
                    "] 的货物已由车辆 " + vehicle.getLicensePlate() + " 送达并删除");

        } catch (Exception e) {
            System.err.println("车辆到达终点时删除货物失败: " + e.getMessage());
            throw new RuntimeException("货物删除失败", e);
        }
    }

//    /**
//     * 周期性的重置判断 - 每12秒执行一次
//     */
//    //@Scheduled(fixedRate = 15000) // 12秒一个周期
//    @Transactional
//    public void periodicReset() {
//        if (CementPlantList.isEmpty() || MaterialMarketList.isEmpty()) {
//            return;
//        }
//
//        System.out.println("开始重置POI判断状态...");
//
//        // 随机选择一个为真的POI重置为假
//        List<POI> truePois = getCurrentTruePois();
//        if (!truePois.isEmpty()) {
//            Random random = new Random();
//            POI selectedPoi = truePois.get(random.nextInt(truePois.size()));
//
//            // 关键：从数据库中重新加载POI，而不是使用map中的旧引用
//            POI freshSelectedPoi = poiRepository.findById(selectedPoi.getId())
//                    .orElseThrow(() -> new RuntimeException("POI not found: " + selectedPoi.getId()));
//
//            // 使用重新加载的POI
//            deleteRelationBetweenPOIAndGoods(selectedPoi);
//
//            // 更新映射关系
//            POI correspondingEndPOI = null;
//            for (Map.Entry<POI, POI> entry : startToEndMapping.entrySet()) {
//                if (entry.getKey().getId().equals(freshSelectedPoi.getId())) {
//                    correspondingEndPOI = entry.getValue();
//                    break;
//                }
//            }
//
//            if (correspondingEndPOI != null) {
//                startToEndMapping.keySet().removeIf(key -> key.getId().equals(freshSelectedPoi.getId()));
//                System.out.println("同时移除对应的终点POI: " + correspondingEndPOI.getName());
//            }
//
//            trueProbability = trueProbability / 0.95;
//
//            // 更新状态，使用freshSelectedPoi
//            setPoiToFalse(selectedPoi);
//            System.out.println("POI [" + freshSelectedPoi.getName() + "] 已被重置为假");
//        } else{
//            System.out.println("无可重置的POI数据");
//        }
//
//        printCurrentStatus();
//    }

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
     * 创建Assignment状态记录
     */
    private void createAssignmentStatusRecord(Assignment assignment, POI startPOI, POI endPOI, Shipment shipment) {
        try {
            // 1. 生成配对ID（兼容旧系统）
            String pairId = generatePoiPairKey(startPOI, endPOI);

            // 2. 创建AssignmentStatusDTO
            AssignmentStatusDTO statusDTO = new AssignmentStatusDTO(
                    assignment.getId(),
                    pairId,
                    assignment.getAssignedVehicle().getId(),
                    shipment != null ? shipment.getId() : null
            );

            // 保存到状态映射表
            assignmentStatusMap.put(pairId, statusDTO);

            // 3. 创建AssignmentBriefDTO
            AssignmentBriefDTO briefDTO = createAssignmentBriefDTO(assignment, startPOI, endPOI, shipment);

            // 保存到简要信息映射表
            assignmentBriefMap.put(assignment.getId(), briefDTO);

            // 4. 如果存在运单，保存到运单映射表（兼容旧系统）
            if (shipment != null) {
                String key = generatePoiPairKey(startPOI, endPOI);
                poiPairShipmentMapping.put(key, shipment);
            }

            System.out.println("创建Assignment状态记录: " + assignment.getId() +
                    ", 车辆: " + assignment.getAssignedVehicle().getLicensePlate());

        } catch (Exception e) {
            System.err.println("创建Assignment状态记录失败: " + e.getMessage());
        }
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
        return random.nextInt(500) + 100; // 100-600之间的随机数
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
//            route.setDistance(calculateDistance(startpoi, endPOI));
//            route.setEstimatedTime(calculateEstimatedTime(startpoi, endPOI));
            routeRepository.save(route);
            System.out.println("新建路径：" + route.getRouteCode());
            return route;
        } else{
            Route route = goalRoute.get(0);
            System.out.println("使用现有路径：" + route.getRouteCode());
            return route;
        }

    }

    /**
     * 智能选择车辆 - 基于距离和载重优化
     */
    private Vehicle selectOptimalVehicle(List<Vehicle> candidateVehicles, POI startPOI,
                                         Double requiredWeight, Integer requiredQuantity) {
        return selectVehicleByDistance(candidateVehicles, startPOI, requiredWeight);
    }

    /**
     * 按距离选择车辆（备用策略）
     */
    private Vehicle selectVehicleByDistance(List<Vehicle> candidateVehicles, POI startPOI,
                                            Double requiredWeight) {
        Vehicle selectedVehicle = null;
        double minDistance = Double.MAX_VALUE;

        for (Vehicle vehicle : candidateVehicles) {
            // 计算车辆当前位置到起点的距离
            double distance = calculateVehicleDistance(vehicle, startPOI);

            // 检查车辆载重是否满足要求
            if (vehicle.getMaxLoadCapacity() != null &&
                    vehicle.getMaxLoadCapacity() >= requiredWeight) {

                // 选择距离最近的车辆
                if (distance < minDistance) {
                    minDistance = distance;
                    selectedVehicle = vehicle;
                }
            }
        }

        if (selectedVehicle != null) {
            System.out.println("选择车辆: " + selectedVehicle.getLicensePlate() +
                    ", 距离起点: " + minDistance + "km");
        }

        return selectedVehicle;
    }

    /**
     * 计算车辆到POI的距离
     */
    private double calculateVehicleDistance(Vehicle vehicle, POI poi) {
        try {
            // 如果车辆有当前位置坐标
            if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null &&
                    poi.getLongitude() != null && poi.getLatitude() != null) {

                return calculateHaversineDistance(
                        vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude(),
                        poi.getLatitude(), poi.getLongitude()
                );
            }

            // 如果车辆有关联的POI
            if (vehicle.getCurrentPOI() != null) {
                POI vehiclePOI = vehicle.getCurrentPOI();
                if (vehiclePOI.getLongitude() != null && vehiclePOI.getLatitude() != null) {
                    return calculateHaversineDistance(
                            vehiclePOI.getLatitude(), vehiclePOI.getLongitude(),
                            poi.getLatitude(), poi.getLongitude()
                    );
                }
            }

            // 无法计算距离，返回默认值
            return 9999.0;

        } catch (Exception e) {
            return 9999.0;
        }
    }

    /**
     * 使用Haversine公式计算两点间距离（公里）
     */
    private double calculateHaversineDistance(BigDecimal lat1, BigDecimal lon1,
                                              BigDecimal lat2, BigDecimal lon2) {
        // 检查参数是否为null
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            throw new IllegalArgumentException("坐标参数不能为null");
        }

        // 将BigDecimal转换为double进行计算
        return calculateHaversineDistance(lat1.doubleValue(), lon1.doubleValue(),
                lat2.doubleValue(), lon2.doubleValue());
    }

    private double calculateHaversineDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        final int R = 6371; // 地球半径（公里）

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
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
            BigDecimal weightPerUnitBD = new BigDecimal(goods.getWeightPerUnit().toString());
            BigDecimal quantityBD = new BigDecimal(quantity);
            BigDecimal totalWeightBD = weightPerUnitBD.multiply(quantityBD).setScale(2, RoundingMode.HALF_UP);
            BigDecimal volumePerUnitBD = new BigDecimal(goods.getWeightPerUnit().toString());
            BigDecimal totalVolumeBD = volumePerUnitBD.multiply(quantityBD).setScale(2, RoundingMode.HALF_UP);
            ShipmentItem shipmentItem = new ShipmentItem(
                    shipment,
                    goods.getName(),
                    quantity,
                    goods.getSku(),
                    totalWeightBD.doubleValue(),
                    totalVolumeBD.doubleValue()
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

        Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = splitAndCreateShipmentItemsWithSmartMatching(shipment, goods, quantity, vehicles, startPOI);

        // 3. 建立POI与Goods的Enrollment关系
        initRelationBetweenPOIAndGoods(startPOI, goods, quantity);

        return vehicleShipmentItemMap;
    }

    // ToDo 对于剩余货物暂时没有车辆可以用于运输的情况需要另外考虑
    private Map<Vehicle, ShipmentItem> splitAndCreateShipmentItemsWithSmartMatching(
            Shipment shipment, Goods goods, Integer totalQuantity, List<Vehicle> candidateVehicles,
            POI startPOI) {

        Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = new LinkedHashMap<>();
        int remainingQuantity = totalQuantity;

        System.out.println("开始智能拆分货物，总数量: " + totalQuantity);
        System.out.println("候选车辆数量: " + candidateVehicles.size());

        // 计算货物总重量

        Double totalWeight = goods.getWeightPerUnit() * totalQuantity;
        System.out.println("货物总重量: " + totalWeight + "吨");

        // 用于记录已分配的车辆，避免重复分配
        Set<Long> assignedVehicleIds = new HashSet<>();

        while (remainingQuantity > 0 && !candidateVehicles.isEmpty()) {
            // 从候选车辆中选择最优车辆
            Double remainingWeight = goods.getWeightPerUnit() * remainingQuantity;
            Vehicle selectedVehicle = selectOptimalVehicle(candidateVehicles, startPOI,
                    remainingWeight, remainingQuantity);

            if (selectedVehicle == null) {
                System.out.println("没有合适的车辆可用");
                break;
            }

            // 检查车辆是否已被分配
            if (assignedVehicleIds.contains(selectedVehicle.getId())) {
                // 从候选列表中移除已分配车辆
                candidateVehicles.removeIf(v -> v.getId().equals(selectedVehicle.getId()));
                continue;
            }

            // 计算这辆车能运输的最大货物量
            Double maxLoad = selectedVehicle.getMaxLoadCapacity();
            if (maxLoad == null || goods.getWeightPerUnit() == null) {
                System.out.println("车辆 " + selectedVehicle.getLicensePlate() +
                        " 缺少载重信息，跳过");
                candidateVehicles.remove(selectedVehicle);
                continue;
            }

            // 计算车辆能承载的货物数量（考虑安全余量）
            int capacityInUnits = (int) Math.floor(maxLoad / goods.getWeightPerUnit()) - 2;

            // 本次分配的数量 = min(车辆容量, 剩余货物量)
            int assignQuantity = Math.min(capacityInUnits, remainingQuantity);

            if (assignQuantity > 0) {
                // 为这辆车创建运单清单
                ShipmentItem shipmentItem = shipmentItemService.initalizeShipmentItem(
                        shipment, goods, assignQuantity);
                vehicleShipmentItemMap.put(selectedVehicle, shipmentItem);

                // 更新车辆载重
                BigDecimal assignedWeight = BigDecimal.valueOf(goods.getWeightPerUnit())
                        .multiply(BigDecimal.valueOf(assignQuantity))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal assignedVolume = BigDecimal.valueOf(goods.getVolumePerUnit())
                        .multiply(BigDecimal.valueOf(assignQuantity))
                        .setScale(2, RoundingMode.HALF_UP);
                selectedVehicle.setCurrentLoad(assignedWeight.doubleValue());
                selectedVehicle.setCurrentVolumn(assignedVolume.doubleValue());

                // 标记车辆已分配
                assignedVehicleIds.add(selectedVehicle.getId());

                // 从候选列表中移除已分配车辆
                candidateVehicles.remove(selectedVehicle);

                // 更新剩余货物量
                remainingQuantity -= assignQuantity;

                System.out.printf("车辆 %s (载重%.2ft) 分配 %d 件货物，剩余 %d 件，距离起点: %.2fkm%n",
                        selectedVehicle.getLicensePlate(), maxLoad, assignQuantity,
                        remainingQuantity, calculateVehicleDistance(selectedVehicle, startPOI));
            } else {
                System.out.println("车辆 " + selectedVehicle.getLicensePlate() +
                        " 容量不足，跳过");
                candidateVehicles.remove(selectedVehicle);
            }
        }

        // 处理剩余货物
        if (remainingQuantity > 0) {
            System.out.println("警告: 仍有 " + remainingQuantity + " 件货物未分配");
            ShipmentItem remainingItem = shipmentItemService.initalizeShipmentItem(
                    shipment, goods, remainingQuantity);
            vehicleShipmentItemMap.put(null, remainingItem);
        }

        System.out.println("货物拆分完成，共使用 " + vehicleShipmentItemMap.size() + " 辆车");
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

            for (Assignment assignment : assignments) {
                Vehicle vehicle = assignment.getAssignedVehicle();

                // 检查是否有分配的车辆
                if (vehicle == null) {
                    System.out.println("警告：Assignment " + assignment.getId() + " 没有分配车辆，跳过");
                    continue;
                }

                // 2. 重新加载车辆实体
                Vehicle managedVehicle = vehicleRepository.findById(vehicle.getId())
                        .orElseThrow(() -> new RuntimeException("车辆不存在: " + vehicle.getId()));

                // 3. 记录车辆分配任务前的原始位置信息
                POI originalPOI = managedVehicle.getCurrentPOI();
                BigDecimal originalLng = null;
                BigDecimal originalLat = null;

                // 记录车辆当前位置信息到日志
                if (originalPOI != null) {
                    originalLng = managedVehicle.getCurrentPOI().getLongitude();
                    originalLat = managedVehicle.getCurrentPOI().getLatitude();
                } else if (managedVehicle.getCurrentLongitude() != null && managedVehicle.getCurrentLatitude() != null) {
                    originalLng = managedVehicle.getCurrentLongitude();
                    originalLat = managedVehicle.getCurrentLatitude();
                }

                // 3. 双向关联：车辆添加任务
                managedVehicle.addAssignment(assignment);

                // 4. 更新车辆状态
                managedVehicle.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);
                managedVehicle.setPreviousStatus(Vehicle.VehicleStatus.IDLE);
                managedVehicle.setStatusStartTime(LocalDateTime.now());
                managedVehicle.setStatusDurationSeconds(0L);

                // 5. 设置当前位置
                managedVehicle.setCurrentLongitude(originalLng);
                managedVehicle.setCurrentLatitude(originalLat);

                // 6. 设置任务状态
                assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
                assignment.setUpdatedTime(LocalDateTime.now());
                assignment.setUpdatedBy("DataInitializer -- 运输任务成功分配");

                // 7. 更新车辆信息
                managedVehicle.setUpdatedBy("DataInitializer -- 车辆接收运输任务");
                managedVehicle.setUpdatedTime(LocalDateTime.now());

                // 8. 保存所有更改
                vehicleRepository.save(managedVehicle);
                assignmentRepository.save(assignment);

                System.out.println("成功分配车辆 " + managedVehicle.getLicensePlate() +
                        " 给任务，从 " + managedStartPOI.getName() + " 到 " + managedEndPOI.getName());

                // 记录车辆起始位置到AssignmentBriefDTO
                updateAssignmentBriefWithVehicleStartPosition(assignment.getId(), managedVehicle, originalLng, originalLat);
            }
        } catch (Exception e) {
            System.err.println("建立车辆任务关联失败: " + e.getMessage());
            throw new RuntimeException("车辆任务关联失败", e);
        }
    }

    // 更新AssignmentBriefDTO的车辆起始位置信息
    private void updateAssignmentBriefWithVehicleStartPosition(Long assignmentId, Vehicle vehicle, BigDecimal originalLng, BigDecimal originalLat) {
        AssignmentBriefDTO brief = assignmentBriefMap.get(assignmentId);
        if (brief != null && vehicle != null) {
            if (originalLng != null && originalLat != null) {
                brief.setVehicleStartLng(originalLng.doubleValue());
                brief.setVehicleStartLat(originalLat.doubleValue());
            }

            if (vehicle.getCurrentPOI() != null) {
                POI currentPOI = vehicle.getCurrentPOI();
            }

            assignmentBriefMap.put(assignmentId, brief);
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
    public void deleteRelationBetweenPOIAndGoods(POI startPOI, Vehicle vehicle) {
        POI freshStartPOI = poiRepository.findById(startPOI.getId())
                .orElseThrow(() -> new RuntimeException("POI not found: " + startPOI.getId()));

        List<Enrollment> goalEnrollment = new ArrayList<>(freshStartPOI.getEnrollments());

        for (Enrollment enrollment : goalEnrollment) {
            if (enrollment.getGoods() != null){
                Goods goalGoods = enrollment.getGoods();

                // 找到相关的Shipment并删除
                POI endPOI = startToEndMapping.get(startPOI);
                if (endPOI != null) {
                    String key = generatePoiPairKey(freshStartPOI, endPOI);
                    Shipment shipment = poiPairShipmentMapping.remove(key);

                    if (shipment != null) {
                        Shipment freshShipment = shipmentRepository.findById(shipment.getId()).orElse(null);

                        if (freshShipment != null) {
                            // 只删除与当前车辆相关的ShipmentItems
                            List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(freshShipment.getId());
                            for (ShipmentItem item : items) {
                                Assignment assignment = item.getAssignment();
                                if (assignment != null && assignment.getAssignedVehicle() != null
                                        && assignment.getAssignedVehicle().getId().equals(vehicle.getId())) {

                                    // 标记 Assignment 为已完成
                                    markAssignmentAsCompleted(assignment.getId());

                                    // 解除车辆与任务的关联
                                    Vehicle assignedVehicle = vehicleRepository.findById(vehicle.getId())
                                            .orElse(null);
                                    if (assignedVehicle != null) {
                                        assignedVehicle.removeAssignment(assignment);

                                        // 检查车辆是否还有其他进行中的任务
                                        boolean hasOtherActiveAssignments = assignedVehicle.getAssignments()
                                                .stream()
                                                .anyMatch(a ->
                                                        a.getStatus() == Assignment.AssignmentStatus.ASSIGNED ||
                                                                a.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS
                                                );

                                        // 如果没有其他进行中的任务，重置状态
                                        if (!hasOtherActiveAssignments) {
                                            assignedVehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
                                            assignedVehicle.setPreviousStatus(Vehicle.VehicleStatus.UNLOADING);
                                            assignedVehicle.setStatusStartTime(LocalDateTime.now());
                                            assignedVehicle.setCurrentPOI(endPOI);
                                            assignedVehicle.setCurrentLongitude(endPOI.getLongitude());
                                            assignedVehicle.setCurrentLatitude(endPOI.getLatitude());
                                            assignedVehicle.setCurrentLoad(0.0);
                                            assignedVehicle.setCurrentVolumn(0.0);
                                        }
                                        assignedVehicle.setUpdatedTime(LocalDateTime.now());
                                        vehicleRepository.save(assignedVehicle);
                                    }

                                    // 删除ShipmentItem
                                    item.setAssignment(null);
                                    assignment.getShipmentItems().remove(item);

                                    if (assignment.getShipmentItems().isEmpty()) {
                                        // 删除这个Assignment
                                        assignmentRepository.delete(assignment);
                                        System.out.println("删除空Assignment: " + assignment.getId());
                                    } else {
                                        assignmentRepository.save(assignment);
                                    }

                                    shipmentItemRepository.delete(item);
                                }
                            }

                            // 检查Shipment是否还有items，如果没有则删除
                            List<ShipmentItem> remainingItems = shipmentItemRepository.findByShipmentId(freshShipment.getId());
                            if (remainingItems.isEmpty()) {
                                freshShipment.getItems().clear();
                                shipmentRepository.save(freshShipment);
                                shipmentRepository.delete(freshShipment);
                                System.out.println("已删除相关运单: " + freshShipment.getRefNo());
                            } else {
                                // 更新Shipment的总重量和体积
                                double totalWeight = remainingItems.stream()
                                        .mapToDouble(ShipmentItem::getWeight)
                                        .sum();
                                double totalVolume = remainingItems.stream()
                                        .mapToDouble(ShipmentItem::getVolume)
                                        .sum();
                                freshShipment.setTotalWeight(totalWeight);
                                freshShipment.setTotalVolume(totalVolume);
                                shipmentRepository.save(freshShipment);
                            }
                        }
                    }
                }

                // 减少Enrollment中的货物数量，而不是直接删除
                // 这里假设每个Enrollment对应一个起点POI的货物
                int remainingQuantity = enrollment.getQuantity();
                if (remainingQuantity > 0) {
                    // 计算车辆运输的货物数量
                    // 这里需要根据实际情况调整，这里简化处理
                    enrollment.setQuantity(remainingQuantity - 1); // 假设每次运1单位
                    if (enrollment.getQuantity() <= 0) {
                        // 如果货物全部运完，删除Enrollment
                        freshStartPOI.removeGoodsEnrollment(enrollment);
                        goalGoods.removePOIEnrollment(enrollment);
                        enrollmentRepository.delete(enrollment);
                        System.out.println("已删除" + freshStartPOI.getName() + "中的货物" + goalGoods.getName());
                    } else {
                        enrollmentRepository.save(enrollment);
                    }
                }

                poiRepository.save(freshStartPOI);
                goodsRepository.save(goalGoods);
            }
        }

        // 检查是否还有Enrollment，如果没有，则移除配对关系
        List<Enrollment> remainingEnrollments = new ArrayList<>(freshStartPOI.getEnrollments());
        if (remainingEnrollments.isEmpty()) {
            POI endPOI = startToEndMapping.get(startPOI);
            if (endPOI != null) {
                String pairId = generatePoiPairKey(freshStartPOI, endPOI);
                markPairAsCompleted(pairId);
                startToEndMapping.remove(startPOI);

                // 更新POI状态
                poiIsWithGoods.put(freshStartPOI, false);
                trueProbability = trueProbability / 0.98;
            }
        }
    }

    // 添加一个新方法，供前端通知车辆到达终点
    @Transactional
    public void vehicleArrivedAtDestination(Long vehicleId, Long endPOIId) {
        try {
            logger.info("车辆到达目的地，车辆ID: {}, 终点POI ID: {}", vehicleId, endPOIId);
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new RuntimeException("车辆不存在: " + vehicleId));

            POI endPOI = poiRepository.findById(endPOIId)
                    .orElseThrow(() -> new RuntimeException("终点POI不存在: " + endPOIId));

            // 找到车辆当前的Assignment
            List<Assignment> vehicleAssignments = assignmentRepository.findByAssignedVehicleId(vehicleId);
            Assignment activeAssignment = vehicleAssignments.stream()
                    .filter(a -> a.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS)
                    .findFirst()
                    .orElse(null);

            if (activeAssignment != null) {
                // 获取起点POI
                POI startPOI = activeAssignment.getRoute().getStartPOI();

                // 使用新方法处理送货，而不是删除
                processVehicleDelivery(startPOI, vehicle, endPOI);

                System.out.println("车辆 " + vehicle.getLicensePlate() +
                        " 已确认到达终点 " + endPOI.getName());
            } else {
                System.out.println("车辆 " + vehicle.getLicensePlate() +
                        " 没有活跃的运输任务");
            }

            // 更新相关的运单进度
            updateShipmentProgressForVehicle(vehicleId);

        } catch (Exception e) {
            System.err.println("处理车辆到达终点时出错: " + e.getMessage());
            throw new RuntimeException("处理车辆到达失败", e);
        }
    }

    @Transactional
    public void processVehicleDelivery(POI startPOI, Vehicle vehicle, POI endPOI) {
        try {
            POI freshStartPOI = poiRepository.findById(startPOI.getId())
                    .orElseThrow(() -> new RuntimeException("POI not found: " + startPOI.getId()));

            List<Enrollment> goalEnrollment = new ArrayList<>(freshStartPOI.getEnrollments());

            for (Enrollment enrollment : goalEnrollment) {
                if (enrollment.getGoods() != null) {
                    Goods goalGoods = enrollment.getGoods();

                    // 找到相关的Shipment
                    String key = generatePoiPairKey(freshStartPOI, endPOI);
                    Shipment shipment = poiPairShipmentMapping.remove(key);

                    if (shipment != null) {
                        Shipment freshShipment = shipmentRepository.findById(shipment.getId())
                                .orElseThrow(() -> new RuntimeException("Shipment not found: " + shipment.getId()));

                        // 只处理与当前车辆相关的ShipmentItems
                        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(freshShipment.getId());
                        for (ShipmentItem item : items) {
                            Assignment assignment = item.getAssignment();
                            if (assignment != null && assignment.getAssignedVehicle() != null
                                    && assignment.getAssignedVehicle().getId().equals(vehicle.getId())) {

                                // =========== 修复：更新状态而不是删除 ===========

                                // 1. 更新ShipmentItem状态为DELIVERED
                                item.setStatus(ShipmentItem.ShipmentItemStatus.DELIVERED);
                                item.setUpdatedTime(LocalDateTime.now());
                                shipmentItemRepository.save(item);

                                // 2. 更新Assignment状态为COMPLETED
                                assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
                                assignment.setEndTime(LocalDateTime.now());
                                assignmentRepository.save(assignment);

                                // 3. 更新Vehicle状态为IDLE
                                Vehicle assignedVehicle = vehicleRepository.findById(vehicle.getId())
                                        .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicle.getId()));

                                assignedVehicle.setPreviousStatus(assignedVehicle.getCurrentStatus());
                                assignedVehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
                                assignedVehicle.setStatusStartTime(LocalDateTime.now());
                                assignedVehicle.setCurrentPOI(endPOI);
                                assignedVehicle.setCurrentLongitude(endPOI.getLongitude());
                                assignedVehicle.setCurrentLatitude(endPOI.getLatitude());
                                assignedVehicle.setCurrentLoad(0.0);
                                assignedVehicle.setCurrentVolumn(0.0);
                                assignedVehicle.setUpdatedTime(LocalDateTime.now());
                                vehicleRepository.save(assignedVehicle);

                                // 4. 检查并更新Shipment状态
                                checkAndUpdateShipmentStatus(freshShipment);
                            }
                        }

                        // 5. 减少Enrollment中的货物数量
                        int remainingQuantity = enrollment.getQuantity();
                        if (remainingQuantity > 0) {
                            enrollment.setQuantity(remainingQuantity - 1);
                            if (enrollment.getQuantity() <= 0) {
                                // 如果货物全部运完，删除Enrollment
                                freshStartPOI.removeGoodsEnrollment(enrollment);
                                goalGoods.removePOIEnrollment(enrollment);
                                enrollmentRepository.delete(enrollment);
                                System.out.println("已删除" + freshStartPOI.getName() + "中的货物" + goalGoods.getName());
                            } else {
                                enrollmentRepository.save(enrollment);
                            }
                        }

                        poiRepository.save(freshStartPOI);
                        goodsRepository.save(goalGoods);
                    }
                }
            }

            // 检查是否还有Enrollment，如果没有，则移除配对关系
            List<Enrollment> remainingEnrollments = new ArrayList<>(freshStartPOI.getEnrollments());
            if (remainingEnrollments.isEmpty()) {
                String pairId = generatePoiPairKey(freshStartPOI, endPOI);
                markPairAsCompleted(pairId);
                startToEndMapping.remove(startPOI);

                // 更新POI状态
                poiIsWithGoods.put(freshStartPOI, false);
                trueProbability = trueProbability / 0.98;
            }

        } catch (Exception e) {
            System.err.println("处理车辆送货失败: " + e.getMessage());
            throw new RuntimeException("车辆送货处理失败", e);
        }
    }

    // 新增：检查和更新Shipment状态
    private void checkAndUpdateShipmentStatus(Shipment shipment) {
        // 检查该Shipment的所有Item是否都是DELIVERED
        boolean allDelivered = true;
        for (ShipmentItem item : shipment.getItems()) {
            if (item.getStatus() != ShipmentItem.ShipmentItemStatus.DELIVERED) {
                allDelivered = false;
                break;
            }
        }

        // 如果所有Item都已完成，更新Shipment状态为DELIVERED
        if (allDelivered) {
            shipment.setStatus(Shipment.ShipmentStatus.DELIVERED);
            shipment.setUpdatedAt(LocalDateTime.now());
            shipmentRepository.save(shipment);

            logger.info("Shipment {} 所有Item已完成，状态更新为DELIVERED",
                    shipment.getId());
        }
    }

    /**
     * 更新与车辆相关的运单进度
     */
    private void updateShipmentProgressForVehicle(Long vehicleId) {
        try {
            // 获取车辆当前的任务（Assignment）
            // 注意：这里需要根据你的数据结构来获取车辆当前的Assignment
            // 假设我们已经有了一个方法 getCurrentAssignmentByVehicleId
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + vehicleId));
            Assignment currentAssignment = vehicle.getCurrentAssignment();

            if (currentAssignment != null) {
                // 获取该Assignment关联的ShipmentItem
                Set<ShipmentItem> shipmentItems = currentAssignment.getShipmentItems();

                // 对于每个ShipmentItem，找到其所属的Shipment，并更新进度
                Set<Long> shipmentIds = new HashSet<>();
                for (ShipmentItem item : shipmentItems) {
                    if (item.getShipment() != null) {
                        shipmentIds.add(item.getShipment().getId());
                    }
                }

                // 对每个相关的Shipment更新进度
                for (Long shipmentId : shipmentIds) {
                    shipmentProgressService.updateShipmentProgress(shipmentId);
                    logger.info("已更新运单进度，运单ID: {}", shipmentId);
                }
            }
        } catch (Exception e) {
            logger.error("更新车辆相关运单进度失败，车辆ID: {}", vehicleId, e);
        }
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

    // 生成配对键（兼容旧系统）
    private String generatePairKey(POI startPOI, POI endPOI) {
        return startPOI.getId() + "_" + endPOI.getId();
    }

    // 创建 Assignment 状态记录
    private void createAssignmentStatus(Assignment assignment, POI startPOI, POI endPOI, Shipment shipment) {
        String pairId = generatePairKey(startPOI, endPOI);
        AssignmentStatusDTO status = new AssignmentStatusDTO(
                assignment.getId(),
                pairId,
                assignment.getAssignedVehicle() != null ? assignment.getAssignedVehicle().getId() : null,
                shipment.getId()
        );
        assignmentStatusMap.put(pairId, status);

        // 创建简要信息
        AssignmentBriefDTO brief = createAssignmentBriefDTO(assignment, startPOI, endPOI, shipment);
        assignmentBriefMap.put(assignment.getId(), brief);
    }

    // 创建 AssignmentBriefDTO
    private AssignmentBriefDTO createAssignmentBriefDTO(Assignment assignment, POI startPOI, POI endPOI, Shipment shipment) {
        AssignmentBriefDTO brief = new AssignmentBriefDTO();
        brief.setAssignmentId(assignment.getId());
        brief.setStatus(assignment.getStatus() != null ? assignment.getStatus().toString() : "WAITING");
        brief.setCreatedTime(assignment.getCreatedTime());
        brief.setStartTime(assignment.getStartTime());

        // 车辆信息
        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null) {
            brief.setVehicleId(vehicle.getId());
            brief.setLicensePlate(vehicle.getLicensePlate());
            brief.setVehicleStatus(vehicle.getCurrentStatus() != null ?
                    vehicle.getCurrentStatus().toString() : "IDLE");

            // 载重信息
            brief.setCurrentLoad(vehicle.getCurrentLoad());
            brief.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());

            // 关键：获取车辆当前位置信息
            if (vehicle.getCurrentPOI() != null) {
                POI vehiclePOI = vehicle.getCurrentPOI();
                brief.setVehicleStartLng(vehiclePOI.getLongitude().doubleValue());
                brief.setVehicleStartLat(vehiclePOI.getLatitude().doubleValue());
            } else if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
                // 或者使用车辆的经纬度坐标
                brief.setVehicleStartLng(vehicle.getCurrentLongitude().doubleValue());
                brief.setVehicleStartLat(vehicle.getCurrentLatitude().doubleValue());
            }

        }

        // 路线信息
        Route route = assignment.getRoute();
        if (route != null) {
            brief.setRouteId(route.getId());
            brief.setRouteName(route.getName());
        }

        // 起点信息
        brief.setStartPOIId(startPOI.getId());
        brief.setStartPOIName(startPOI.getName());
        brief.setStartLng(startPOI.getLongitude());
        brief.setStartLat(startPOI.getLatitude());

        // 终点信息
        brief.setEndPOIId(endPOI.getId());
        brief.setEndPOIName(endPOI.getName());
        brief.setEndLng(endPOI.getLongitude());
        brief.setEndLat(endPOI.getLatitude());

        // 货物信息
        Set<ShipmentItem> items = assignment.getShipmentItems();
        if (items != null && !items.isEmpty()) {
            ShipmentItem firstItem = items.iterator().next();
            brief.setGoodsName(firstItem.getName());
            brief.setQuantity(firstItem.getQty());

            // 货物单位重量和体积
            if (firstItem.getGoods() != null) {
                Goods goods = firstItem.getGoods();
                brief.setGoodsWeightPerUnit(goods.getWeightPerUnit());
                brief.setGoodsVolumePerUnit(goods.getVolumePerUnit());
            }
        }

        // 运单信息
        if (shipment != null) {
            brief.setShipmentRefNo(shipment.getRefNo());
        }

        // 兼容字段
        brief.setPairId(generatePairKey(startPOI, endPOI));
        brief.setDrawn(false);

        return brief;
    }

    /// 数据转换相关方法
    /**
     * 将 Assignment 转换为完整的 AssignmentDTO
     */
    public AssignmentDTO convertToAssignmentDTO(Assignment assignment) {
        if (assignment == null) return null;

        AssignmentDTO dto = new AssignmentDTO();
        dto.setId(assignment.getId());
        dto.setStatus(assignment.getStatus().toString());
        dto.setCreatedTime(assignment.getCreatedTime());
        dto.setUpdatedTime(assignment.getUpdatedTime());
        dto.setStartTime(assignment.getStartTime());
        dto.setEndTime(assignment.getEndTime());

        // 车辆信息
        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle != null) {
            dto.setVehicle(convertToVehicleDTO(vehicle));
        }

        // 路线信息
        Route route = assignment.getRoute();
        if (route != null) {
            dto.setRoute(convertToRouteDTO(route));
        }

        // 货物清单信息
        Set<ShipmentItem> items = assignment.getShipmentItems();
        if (items != null && !items.isEmpty()) {
            List<ShipmentItemDTO> itemDTOs = items.stream()
                    .map(this::convertToShipmentItemDTO)
                    .collect(Collectors.toList());
            dto.setShipmentItems(itemDTOs);

            // 设置简要货物信息
            ShipmentItem firstItem = items.iterator().next();
            dto.setGoodsName(firstItem.getName());
            dto.setTotalQuantity(firstItem.getQty());

            // 获取运单号
            if (firstItem.getShipment() != null) {
                dto.setShipmentRefNo(firstItem.getShipment().getRefNo());
            }
        }

        // 状态跟踪
        AssignmentBriefDTO brief = assignmentBriefMap.get(assignment.getId());
        if (brief != null) {
            dto.setIsDrawn(brief.isDrawn());
            dto.setLastDrawnTime(brief.getLastDrawnTime());
        }

        return dto;
    }

    /**
     * 将 Vehicle 转换为 VehicleDTO
     */
    private VehicleDTO convertToVehicleDTO(Vehicle vehicle) {
        if (vehicle == null) return null;

        VehicleDTO dto = new VehicleDTO();
        dto.setId(vehicle.getId());
        dto.setLicensePlate(vehicle.getLicensePlate());
        dto.setBrand(vehicle.getBrand());
        dto.setModelType(vehicle.getModelType());
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());
        dto.setCurrentLoad(vehicle.getCurrentLoad());
        dto.setSuitableGoods(vehicle.getSuitableGoods());

        // 状态信息
        if (vehicle.getCurrentStatus() != null) {
            dto.setCurrentStatus(vehicle.getCurrentStatus().toString());
        }
        if (vehicle.getPreviousStatus() != null) {
            dto.setPreviousStatus(vehicle.getPreviousStatus().toString());
        }

        dto.setStatusStartTime(vehicle.getStatusStartTime());
        dto.setStatusDurationSeconds(vehicle.getStatusDurationSeconds());

        // 位置信息
        if (vehicle.getCurrentPOI() != null) {
            dto.setCurrentPOIId(vehicle.getCurrentPOI().getId());
            dto.setCurrentPOIName(vehicle.getCurrentPOI().getName());
        }

        dto.setCurrentLongitude(vehicle.getCurrentLongitude());
        dto.setCurrentLatitude(vehicle.getCurrentLatitude());
        dto.setDriverName(vehicle.getDriverName());

        // 任务信息
        Assignment currentAssignment = vehicle.getCurrentAssignment();
        if (currentAssignment != null) {
            dto.setCurrentAssignmentId(currentAssignment.getId());
            dto.setHasActiveAssignment(true);
        }

        dto.setCreatedTime(vehicle.getCreatedTime());
        dto.setUpdatedTime(vehicle.getUpdatedTime());
        dto.setUpdatedBy(vehicle.getUpdatedBy());

        // 状态显示文本和颜色
        Map<String, String> statusConfig = getVehicleStatusConfig(vehicle);
        dto.setStatusText(statusConfig.get("text"));
        dto.setStatusColor(statusConfig.get("color"));

        return dto;
    }

    /**
     * 将 Route 转换为 RouteDTO
     */
    private RouteDTO convertToRouteDTO(Route route) {
        if (route == null) return null;

        RouteDTO dto = new RouteDTO();
        dto.setId(route.getId());
        dto.setRouteCode(route.getRouteCode());
        dto.setName(route.getName());
        dto.setDistance(route.getDistance());
        dto.setEstimatedTime(route.getEstimatedTime());
        dto.setRouteType(route.getRouteType());
        dto.setStatus(route.getStatus() != null ? route.getStatus().toString() : null);
        dto.setDescription(route.getDescription());

        // 起点信息
        POI startPOI = route.getStartPOI();
        if (startPOI != null) {
            dto.setStartPOIId(startPOI.getId());
            dto.setStartPOIName(startPOI.getName());
            dto.setStartLng(startPOI.getLongitude());
            dto.setStartLat(startPOI.getLatitude());
            dto.setStartPOIType(startPOI.getPoiType().toString());
        }

        // 终点信息
        POI endPOI = route.getEndPOI();
        if (endPOI != null) {
            dto.setEndPOIId(endPOI.getId());
            dto.setEndPOIName(endPOI.getName());
            dto.setEndLng(endPOI.getLongitude());
            dto.setEndLat(endPOI.getLatitude());
            dto.setEndPOIType(endPOI.getPoiType().toString());
        }

        dto.setTollCost(route.getTollCost());
        dto.setFuelConsumption(route.getFuelConsumption());
        dto.setCreatedTime(route.getCreatedTime());
        dto.setUpdatedTime(route.getUpdatedTime());

        return dto;
    }

    /**
     * 将 ShipmentItem 转换为 ShipmentItemDTO
     */
    private ShipmentItemDTO convertToShipmentItemDTO(ShipmentItem item) {
        if (item == null) return null;

        ShipmentItemDTO dto = new ShipmentItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setSku(item.getSku());
        dto.setQty(item.getQty());
        dto.setWeight(item.getWeight());
        dto.setVolume(item.getVolume());

        // 关联信息
        if (item.getShipment() != null) {
            dto.setShipmentId(item.getShipment().getId());
            dto.setShipmentRefNo(item.getShipment().getRefNo());
        }

        if (item.getGoods() != null) {
            dto.setGoodsId(item.getGoods().getId());
            dto.setGoodsName(item.getGoods().getName());
        }

        dto.setAssignmentId(item.getAssignment() != null ? item.getAssignment().getId() : null);

        // 位置信息（从关联的 Assignment 获取）
        if (item.getAssignment() != null && item.getAssignment().getRoute() != null) {
            Route route = item.getAssignment().getRoute();
            if (route.getStartPOI() != null) {
                dto.setOriginPOIId(route.getStartPOI().getId());
                dto.setOriginPOIName(route.getStartPOI().getName());
            }
            if (route.getEndPOI() != null) {
                dto.setDestPOIId(route.getEndPOI().getId());
                dto.setDestPOIName(route.getEndPOI().getName());
            }
        }

        dto.setCreatedTime(item.getCreatedTime());
        dto.setUpdatedTime(item.getUpdatedTime());

        return dto;
    }

    /**
     * 获取车辆状态显示配置
     */
    private Map<String, String> getVehicleStatusConfig(Vehicle vehicle) {
        Map<String, String> config = new HashMap<>();

        if (vehicle.getCurrentStatus() == null) {
            config.put("text", "未知");
            config.put("color", "#ccc");
            return config;
        }

        switch (vehicle.getCurrentStatus()) {
            case IDLE:
                config.put("text", "空闲");
                config.put("color", "#95a5a6");
                break;
            case ORDER_DRIVING:
                config.put("text", "前往接货");
                config.put("color", "#f39c12");
                break;
            case LOADING:
                config.put("text", "装货中");
                config.put("color", "#f39c12");
                break;
            case TRANSPORT_DRIVING:
                config.put("text", "运输中");
                config.put("color", "#2ecc71");
                break;
            case UNLOADING:
                config.put("text", "卸货中");
                config.put("color", "#f39c12");
                break;
            case WAITING:
                config.put("text", "等待中");
                config.put("color", "#e74c3c");
                break;
            case BREAKDOWN:
                config.put("text", "故障");
                config.put("color", "#e74c3c");
                break;
            default:
                config.put("text", vehicle.getCurrentStatus().toString());
                config.put("color", "#ccc");
        }

        return config;
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

    // ToDo 等待合适时候删除中间代码
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
    // ToDO

    /// Assignment对应数据
    /**
     * 获取当前所有的 Assignment 信息
     */
    public List<AssignmentBriefDTO> getCurrentAssignments() {
        return new ArrayList<>(assignmentBriefMap.values());
    }

    /**
     * 获取新增的 Assignment（用于前端绘制）
     */
    public List<AssignmentBriefDTO> getNewAssignmentsForDrawing() {
        List<AssignmentBriefDTO> newAssignments = getNewAssignments();

        // 标记为已绘制
        newAssignments.forEach(dto -> {
            dto.setDrawn(true);
            dto.setLastDrawnTime(LocalDateTime.now());
            assignmentBriefMap.put(dto.getAssignmentId(), dto);

            // 同时更新 status map
            if (dto.getPairId() != null) {
                AssignmentStatusDTO status = assignmentStatusMap.get(dto.getPairId());
                if (status != null) {
                    status.setDrawn(true);
                    status.setLastUpdated(LocalDateTime.now());
                }
            }
        });

        return newAssignments;
    }

    /**
     * 标记 Assignment 为已绘制
     */
    public void markAssignmentAsDrawn(Long assignmentId) {
        AssignmentBriefDTO dto = assignmentBriefMap.get(assignmentId);
        if (dto != null) {
            dto.setDrawn(true);
            dto.setLastDrawnTime(LocalDateTime.now());
            assignmentBriefMap.put(assignmentId, dto);
        }
    }

    /**
     * 标记 Assignment 为已完成
     */
    public void markAssignmentAsCompleted(Long assignmentId) {
        AssignmentBriefDTO dto = assignmentBriefMap.get(assignmentId);
        if (dto != null) {
            dto.setStatus("COMPLETED");
            assignmentBriefMap.put(assignmentId, dto);
        }
    }

    /**
     * 获取当前活跃的 Assignment 列表
     */
    public List<AssignmentBriefDTO> getActiveAssignments() {
        return assignmentBriefMap.values().stream()
                .filter(dto -> {
                    // 活跃状态：ASSIGNED, IN_PROGRESS
                    return "ASSIGNED".equals(dto.getStatus()) || "IN_PROGRESS".equals(dto.getStatus());
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取新增的 Assignment（尚未被前端绘制的）
     */
    public List<AssignmentBriefDTO> getNewAssignments() {
        return assignmentBriefMap.values().stream()
                .filter(dto -> {
                    // 活跃且未绘制
                    return ("ASSIGNED".equals(dto.getStatus()) || "IN_PROGRESS".equals(dto.getStatus()))
                            && !dto.isDrawn();
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取需要清理的 Assignment（已完成的）
     */
    public List<Long> getCompletedAssignments() {
        List<Long> completedIds = new ArrayList<>();

        assignmentBriefMap.forEach((id, dto) -> {
            if ("COMPLETED".equals(dto.getStatus()) || "CANCELLED".equals(dto.getStatus())) {
                completedIds.add(id);
            }
        });

        return completedIds;
    }

    /**
     * 获取车辆的当前位置信息
     */
    public Map<Long, double[]> getVehicleCurrentPositions() {
        Map<Long, double[]> positions = new HashMap<>();

        // 从assignmentBriefMap中获取车辆信息
        for (AssignmentBriefDTO brief : assignmentBriefMap.values()) {
            if (brief.getVehicleId() != null) {
                double[] position = new double[2];
                position[0] = brief.getVehicleStartLng() != null ? brief.getVehicleStartLng().doubleValue() : 0.0;
                position[1] = brief.getVehicleStartLat() != null ? brief.getVehicleStartLat().doubleValue() : 0.0;
                positions.put(brief.getVehicleId(), position);
            }
        }

        return positions;
    }
}


