package org.example.roadsimulation;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.Setter;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.dto.AssignmentStatusDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.*;
import org.example.roadsimulation.service.BatchDirectVehicleAssignmentService.BatchMatchResult;
import org.example.roadsimulation.service.BatchDirectVehicleAssignmentService.DirectAssignmentRequest;
import org.example.roadsimulation.service.BatchDirectVehicleAssignmentService.VehicleAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private static final int STARTUP_SHIPMENT_MIN_QUANTITY = 25;
    private static final int STARTUP_SHIPMENT_MAX_QUANTITY = 35;
    private final ShipmentProgressService shipmentProgressService;
    private final EnrollmentRepository enrollmentRepository;
    private final GoodsRepository goodsRepository;
    private final POIRepository poiRepository;
    private final RouteRepository routeRepository;
    private final AssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final ProcessingChainRepository processingChainRepository;
    private final SimulationDataCleanupService cleanupService;
    private final ShipmentItemService shipmentItemService;
    private final VehicleRepository vehicleRepository;
    private final RoutePlanningService routePlanningService;
    private final GetCostService getCostService;
    private final CargoChunkService cargoChunkService;
    private final POIShipmentManager poiShipmentManager;
    private final TransportMetricsService transportMetricsService;
    private final SimulationContext simulationContext;
    private final BatchDirectVehicleAssignmentService batchDirectVehicleAssignmentService;
    private final OriginalVrpDispatchPolicy originalVrpDispatchPolicy;
    private final TransportLifecycleService transportLifecycleService;

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

    public List<POI> sourcePoiList; // 当前货源起点 POI 列表
    public List<POI> targetPoiList; // 当前货运终点 POI 列表
    public Goods currentGoods; // 当前使用的货物
    private ProcessingChainSegmentSelection currentProcessingSegmentSelection;

//    public List<POI> goalNeedGoodsPOIList = getFilteredPOI("家具", POI.POIType.FACTORY);
    // POI的判断状态和计数
    private final Map<POI, Boolean> poiIsWithGoods = new ConcurrentHashMap<>();
    private final Map<POI, Integer> poiTrueCount = new ConcurrentHashMap<>();

    // 限制条件
    private final int maxTrueCount = 45; // 最大为真的数量
    private double trueProbability = 0.049; // 判断为真的概率

    // 当前轮次
    private int currentLoopCount;
    private final Random random = new Random();
    private boolean startupProcessingShipmentsGenerated = false;

    @Autowired
    private org.example.roadsimulation.optimizer.OptimizerBridge optimizerBridge;

    @Autowired
    public DataInitializer(EnrollmentRepository enrollmentRepository,
                           GoodsRepository goodsRepository,
                           POIRepository poiRepository,
                           RouteRepository routeRepository,
                           ShipmentRepository shipmentRepository,
                           ShipmentItemRepository shipmentItemRepository,
                           ProcessingChainRepository processingChainRepository,
                           SimulationDataCleanupService cleanupService,
                           AssignmentRepository assignmentRepository,
                           VehicleRepository vehicleRepository,
                           ShipmentItemService shipmentItemService,
                           @Lazy ShipmentProgressService shipmentProgressService,
                           RoutePlanningService routePlanningService,
                            GetCostService getCostService,
                            CargoChunkService cargoChunkService,
                            POIShipmentManager poiShipmentManager,
                            TransportMetricsService transportMetricsService,
                            SimulationContext simulationContext,
                            BatchDirectVehicleAssignmentService batchDirectVehicleAssignmentService,
                            OriginalVrpDispatchPolicy originalVrpDispatchPolicy,
                            TransportLifecycleService transportLifecycleService) {
        this.enrollmentRepository = enrollmentRepository;
        this.goodsRepository = goodsRepository;
        this.poiRepository = poiRepository;
        this.routeRepository = routeRepository;
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.processingChainRepository = processingChainRepository;
        this.cleanupService = cleanupService;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.shipmentItemService = shipmentItemService;
        this.shipmentProgressService = shipmentProgressService;
        this.routePlanningService = routePlanningService;
        this.getCostService = getCostService;
        this.cargoChunkService = cargoChunkService;
        this.poiShipmentManager = poiShipmentManager;
        this.transportMetricsService = transportMetricsService;
        this.simulationContext = simulationContext;
        this.batchDirectVehicleAssignmentService = batchDirectVehicleAssignmentService;
        this.originalVrpDispatchPolicy = originalVrpDispatchPolicy;
        this.transportLifecycleService = transportLifecycleService;
    }

    /**
     * 生成货物 - 由主循环调用
     */
    @Transactional
    public void generateGoods(int loopCount) {
        if (shouldAbortSimulationWork()) {
            return;
        }
        if (sourcePoiList.isEmpty() || targetPoiList.isEmpty()) {
            System.out.println("生成工厂为空");
            return;
        }
        currentLoopCount = loopCount;

        System.out.println("开始货物生成检查（循环 " + loopCount + "）");

        if (shouldAbortSimulationWork()) {
            return;
        }
        periodicJudgement();
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

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("Spring Boot 启动完毕，开始执行 DataInitializer...");

        initializeFromRandomProcessingSegment();
        initalizePOIStatus();

        System.out.println("DataInitializer 初始化完成");
    }

    private void initializeFromRandomProcessingSegment() {
        Optional<ProcessingChainSegmentSelection> selectionOpt = getRandomProcessingChainSegmentSelection();
        if (selectionOpt.isPresent() && selectionOpt.get().getGoods() != null) {
            ProcessingChainSegmentSelection selection = selectionOpt.get();
            this.currentProcessingSegmentSelection = selection;
            this.sourcePoiList = getFilterdPOIByType(selection.getFromPoiType());
            this.targetPoiList = getFilterdPOIByType(selection.getToPoiType());
            this.currentGoods = selection.getGoods();

            System.out.println("DataInitializer 基于加工链段初始化: chain=" + selection.getChainCode()
                    + ", stage=" + selection.getFromStageName() + " -> " + selection.getToStageName()
                    + ", goods=" + selection.getGoods().getSku()
                    + ", 起点类型=" + selection.getFromPoiType()
                    + ", 终点类型=" + selection.getToPoiType());
        } else {
            this.currentProcessingSegmentSelection = null;
            this.sourcePoiList = poiRepository.findByPoiType(POI.POIType.GAS_STATION);
            this.targetPoiList = getFilterdPOIByType(POI.POIType.REST_AREA);
            this.currentGoods = getGoodsForTest("CEMENT");
            System.out.println("未找到有效加工链段，回退到默认初始化配置");
        }

        System.out.println("DataInitializer 初始化完成，共加载 " + sourcePoiList.size() + " 个起点POI 和 "
                + targetPoiList.size() + " 个终点POI，货物=" + (currentGoods != null ? currentGoods.getSku() : "null"));
    }

    /**
     * POI 状态表示的初始化
     */
    private void initalizePOIStatus(){ //List<POI> goalPOITypeList
        poiIsWithGoods.clear();
        poiTrueCount.clear();
        /// 测试用例
        for(POI poi: sourcePoiList){
            poiIsWithGoods.put(poi, false);
            poiTrueCount.put(poi, 0);
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
                .filter(poi -> poi.getPoiType() != null && poi.getPoiType().equals(goalPOIType))
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
        } else {
            // 如果不存在，创建并保存（修复：使用入参sku，名称改为水泥）
            goalGoods = new Goods("水泥", sku);
            goodsRepository.save(goalGoods);
            System.out.println("创建新货物: " + goalGoods.getName());
        }
        return goalGoods;
    }

    /**
     * 随机选择一段加工链，返回该链段的起点类型、终点类型和运输货物。
     * 这里的“链段”定义为相邻两道工序之间的运输段：
     * 当前工序产出 -> 下一工序输入。
     */
    public Optional<ProcessingChainSegmentSelection> getRandomProcessingChainSegmentSelection() {
        List<ProcessingChain> candidateChains = processingChainRepository.findAll().stream()
                .filter(chain -> chain.getStatus() == ProcessingChain.ChainStatus.ACTIVE)
                .filter(chain -> chain.getStages() != null && chain.getStages().size() >= 2)
                .toList();

        if (candidateChains.isEmpty()) {
            logger.warn("当前没有可用于随机选择的有效加工链段");
            return Optional.empty();
        }

        ProcessingChain selectedChain = candidateChains.get(random.nextInt(candidateChains.size()));
        List<ProcessingStage> stages = selectedChain.getStages();
        int startIndex = random.nextInt(stages.size() - 1);

        ProcessingStage fromStage = stages.get(startIndex);
        ProcessingStage toStage = stages.get(startIndex + 1);
        Goods segmentGoods = resolveSegmentGoods(fromStage, toStage);

        if (fromStage.getProcessingPOI() == null || toStage.getProcessingPOI() == null) {
            logger.warn("加工链 {} 存在未绑定 POI 的工序，跳过本次随机链段选择", selectedChain.getChainCode());
            return Optional.empty();
        }

        POI.POIType fromPoiType;
        POI.POIType toPoiType;
        try {
            fromPoiType = fromStage.getProcessingPOI().getPoiType();
            toPoiType = toStage.getProcessingPOI().getPoiType();
        } catch (IllegalArgumentException e) {
            logger.warn("加工链 {} 的 POI 存在无效的 poi_type (数据库值为空或非法), 跳过本次随机链段选择. 错误: {}",
                    selectedChain.getChainCode(), e.getMessage());
            return Optional.empty();
        }

        if (fromPoiType == null || toPoiType == null) {
            logger.warn("加工链 {} 的 POI 的 poi_type 为 null, 跳过本次随机链段选择", selectedChain.getChainCode());
            return Optional.empty();
        }

        ProcessingChainSegmentSelection selection = new ProcessingChainSegmentSelection(
                selectedChain.getId(),
                selectedChain.getChainCode(),
                selectedChain.getChainName(),
                fromStage.getId(),
                fromStage.getStageName(),
                fromStage.getStageOrder(),
                fromStage.getProcessingPOI(),
                fromPoiType,
                toStage.getId(),
                toStage.getStageName(),
                toStage.getStageOrder(),
                toStage.getProcessingPOI(),
                toPoiType,
                segmentGoods
        );

        logger.info("随机选择加工链段: chain={}, {}({}) -> {}({}), goods={}",
                selection.getChainCode(),
                selection.getFromStageName(),
                selection.getFromPoiType(),
                selection.getToStageName(),
                selection.getToPoiType(),
                selection.getGoods() != null ? selection.getGoods().getSku() : "null");

        return Optional.of(selection);
    }

    private Goods resolveSegmentGoods(ProcessingStage fromStage, ProcessingStage toStage) {
        if (fromStage.getOutputGoods() != null) {
            return fromStage.getOutputGoods();
        }
        if (toStage.getInputGoods() != null) {
            return toStage.getInputGoods();
        }
        if (fromStage.getOutputGoodsSku() != null && !fromStage.getOutputGoodsSku().isBlank()) {
            return goodsRepository.findBySku(fromStage.getOutputGoodsSku()).orElse(null);
        }
        if (toStage.getInputGoodsSku() != null && !toStage.getInputGoodsSku().isBlank()) {
            return goodsRepository.findBySku(toStage.getInputGoodsSku()).orElse(null);
        }
        return null;
    }

    public static class ProcessingChainSegmentSelection {
        @Getter
        private final Long chainId;
        @Getter
        private final String chainCode;
        @Getter
        private final String chainName;
        @Getter
        private final Long fromStageId;
        @Getter
        private final String fromStageName;
        @Getter
        private final Integer fromStageOrder;
        @Getter
        private final POI fromPOI;
        @Getter
        private final POI.POIType fromPoiType;
        @Getter
        private final Long toStageId;
        @Getter
        private final String toStageName;
        @Getter
        private final Integer toStageOrder;
        @Getter
        private final POI toPOI;
        @Getter
        private final POI.POIType toPoiType;
        @Getter
        private final Goods goods;

        public ProcessingChainSegmentSelection(Long chainId,
                                               String chainCode,
                                               String chainName,
                                               Long fromStageId,
                                               String fromStageName,
                                               Integer fromStageOrder,
                                               POI fromPOI,
                                               POI.POIType fromPoiType,
                                               Long toStageId,
                                               String toStageName,
                                               Integer toStageOrder,
                                               POI toPOI,
                                               POI.POIType toPoiType,
                                               Goods goods) {
            this.chainId = chainId;
            this.chainCode = chainCode;
            this.chainName = chainName;
            this.fromStageId = fromStageId;
            this.fromStageName = fromStageName;
            this.fromStageOrder = fromStageOrder;
            this.fromPOI = fromPOI;
            this.fromPoiType = fromPoiType;
            this.toStageId = toStageId;
            this.toStageName = toStageName;
            this.toStageOrder = toStageOrder;
            this.toPOI = toPOI;
            this.toPoiType = toPoiType;
            this.goods = goods;
        }
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
        if (shouldAbortSimulationWork()) {
            return;
        }
        System.out.println("开始新一轮的POI判断周期...");
        // 对所有POI进行判断

        // 1. 随机选取一个加工链段（包含了起点类型、终点类型和货物）
        Optional<ProcessingChainSegmentSelection> selectionOpt = getRandomProcessingChainSegmentSelection();
        if (selectionOpt.isEmpty() || selectionOpt.get().getGoods() == null) {
            System.out.println("本轮未获取到有效的加工链段或货物为空，跳过");
            return;
        }

        ProcessingChainSegmentSelection selection = selectionOpt.get();
        Goods dynamicGoods = selection.getGoods();

        // 2. 【核心优化】按需从数据库拉取对应类型的起点和终点 POI
        // 这一步是高效的，因为利用了数据库的类型索引，比内存里全量过滤快得多
        List<POI> dynamicSourcePois = getFilterdPOIByType(selection.getFromPoiType());
        List<POI> dynamicTargetPois = getFilterdPOIByType(selection.getToPoiType());

        if (dynamicSourcePois.isEmpty() || dynamicTargetPois.isEmpty()) {
            System.out.println("警告：该加工链段对应的起点或终点POI列表为空，跳过本轮");
            return;
        }

        System.out.println("🎯 本轮选中链段: [" + selection.getFromPoiType() + "] -> [" + selection.getToPoiType() +
                "]，货物: " + dynamicGoods.getSku());

        // 1. 收集所有需要生成货物的POI
        List<POI> poisToGenerateGoods = new ArrayList<>();
        for (POI poi : dynamicSourcePois) {
            // 使用POIShipmentManager统一管理POI阻塞状态
            if (poiShipmentManager.isPOIBlocked(poi)) {
                continue; // 肚子里有货还没运走，跳过
            }

            // 伪随机判断
            if (pseudoRandomJudgement(poi)) {
                if (poiShipmentManager.canBlockMore()) {
                    poisToGenerateGoods.add(poi);
                }
            }
        }

        if (poisToGenerateGoods.isEmpty()) {
            System.out.println("本轮没有需要生成货物的POI");
            return;
        }

        System.out.println("本轮有 " + poisToGenerateGoods.size() + " 个POI需要生成货物");

        // 4. 批量获取空闲车辆（适配动态随机出来的货物）
        String targetGoodsSku = dynamicGoods.getSku();
        String targetVehicleType = dynamicGoods.getVehicleFit() != null ? dynamicGoods.getVehicleFit() : "载货车";
        List<Vehicle> allIdleVehicles = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE); // ToDo 只考虑车辆状态，暂时不考虑适配性

        if (allIdleVehicles.isEmpty()) {
            System.out.println("警告：没有适配货物 " + targetGoodsSku + " 的空闲车辆，本轮只生成待分配运单项");
        } else {
            System.out.println("获取到 " + allIdleVehicles.size() + " 辆适配货物 " + targetGoodsSku + " 的空闲车辆");
        }

        List<DirectAssignmentRequest> directAssignmentRequests = new ArrayList<>();

        // 3. 为每个POI批量处理货物生成
        for (POI poi : poisToGenerateGoods) {
            if (shouldAbortSimulationWork()) {
                return;
            }
            try {
                System.out.println("为POI [" + poi.getName() + "] 生成货物");
                // 使用POIShipmentManager统一管理POI阻塞状态
                poiShipmentManager.blockPOI(poi);
                poiTrueCount.put(poi, poiTrueCount.getOrDefault(poi, 0) + 1);
                poiShipmentManager.decayProbability();
                // 同步旧字段（兼容过渡期）
                setPoiToTrue(poi);
                trueProbability = poiShipmentManager.getCurrentProbability();

                // 随机获取终点POI
                POI endPOI = dynamicTargetPois.get(random.nextInt(dynamicTargetPois.size()));
                Integer generateQuantity = generateRandomQuantity();

                POI managedStartPOI = poiRepository.findById(poi.getId())
                        .orElseThrow(() -> new RuntimeException("起点POI状态失效"));
                POI managedEndPOI = poiRepository.findById(endPOI.getId())
                        .orElseThrow(() -> new RuntimeException("终点POI状态失效"));

                if (shouldAbortSimulationWork()) {
                    return;
                }
                Route route = initializeRoute(managedStartPOI, managedEndPOI);

                // 批量创建货物运输
                if (shouldAbortSimulationWork()) {
                    return;
                }
                directAssignmentRequests.addAll(createCompleteGoodsTransport(
                        managedStartPOI,
                        managedEndPOI,
                        dynamicGoods,
                        generateQuantity,
                        route,
                        directAssignmentRequests.size()
                ));

                startToEndMapping.put(poi, endPOI);

                String key = generatePoiPairKey(poi, endPOI);
                Shipment shipment = poiPairShipmentMapping.get(key);
                if (shipment != null) {
                    createPairStatus(poi, endPOI, shipment);
                }

            } catch (Exception e) {
                System.err.println("为POI [" + poi.getName() + "] 生成货物失败: " + e.getMessage());
                setPoiToFalse(poi); // 重置旧状态
                poiShipmentManager.releasePOI(poi); // 同步释放新管理器状态
            }
        }

        if (shouldAbortSimulationWork()) {
            return;
        }
        dispatchBatchDirectAssignments(directAssignmentRequests, allIdleVehicles);

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

    /**
     * 手动批量生成运单，同步生成库存，并直接唤醒 VRP 大脑派车
     */
    @Transactional
    public int generateManualShipments(int count) {
        int successCount = 0;
        System.out.println("====== [手动干预] 开始批量生成 " + count + " 票随机运单 ======");

        for (int i = 0; i < count; i++) {
            try {
                // 1. 从真实的加工链中随机抽取合法的业务线
                Optional<ProcessingChainSegmentSelection> selectionOpt = getRandomProcessingChainSegmentSelection();
                if (!selectionOpt.isPresent() || selectionOpt.get().getGoods() == null) {
                    continue;
                }
                ProcessingChainSegmentSelection selection = selectionOpt.get();

                // 2. 随机抽取具体的起点和终点 POI
                List<POI> sourcePois = getFilterdPOIByType(selection.getFromPoiType());
                List<POI> targetPois = getFilterdPOIByType(selection.getToPoiType());
                if (sourcePois.isEmpty() || targetPois.isEmpty()) continue;

                POI detachedStartPOI = sourcePois.get(random.nextInt(sourcePois.size()));
                POI detachedEndPOI = targetPois.get(random.nextInt(targetPois.size()));

                // 【架构级修复】：立刻将“游离态”洗白为“受管态(Managed)”，防止 Hibernate 报错
                POI managedStartPOI = poiRepository.findById(detachedStartPOI.getId())
                        .orElseThrow(() -> new RuntimeException("起点POI状态失效"));
                POI managedEndPOI = poiRepository.findById(detachedEndPOI.getId())
                        .orElseThrow(() -> new RuntimeException("终点POI状态失效"));

                Goods goods = selection.getGoods();
                Integer quantity = generateRandomQuantity();

                // 3. 🚨 核心排雷：同步生成或累加起点的库存 (Enrollment)，防止车辆到达时结算空指针！
                Optional<Enrollment> existingEnrollment = enrollmentRepository.findByPoiAndGoods(managedStartPOI, goods);
                if (existingEnrollment.isPresent()) {
                    Enrollment e = existingEnrollment.get();
                    e.setQuantity(e.getQuantity() + quantity); // 累加库存
                    enrollmentRepository.save(e);
                } else {
                    Enrollment newEnrollment = new Enrollment(managedStartPOI, goods, quantity);
                    enrollmentRepository.save(newEnrollment);
                    managedStartPOI.addGoodsEnrollment(newEnrollment); // 维护双向关系
                }

                // 4. 生成大运单 (Shipment)
                Shipment shipment = initalizeShipment(managedStartPOI, managedEndPOI, goods, quantity);

                // 5. 生成运单明细 (ShipmentItem)
                ShipmentItem item = shipmentItemService.initalizeShipmentItem(shipment, goods, quantity);

                // 🌟 最关键的一步：将状态设为 NOT_ASSIGNED，把它推入 VRP 算法的全局待接单池！
                item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
                shipmentItemRepository.save(item);

                // 记录状态映射关系
                startToEndMapping.put(detachedStartPOI, detachedEndPOI);
                String key = generatePoiPairKey(detachedStartPOI, detachedEndPOI);
                poiPairShipmentMapping.put(key, shipment);
                createPairStatus(detachedStartPOI, detachedEndPOI, shipment);
                // 同步注册到POI运单管理器
                poiShipmentManager.registerShipment(managedStartPOI, managedEndPOI, shipment);

                successCount++;
            } catch (Exception e) {
                System.err.println("❌ 手动生成单条运单失败: " + e.getMessage());
            }
        }

        System.out.println("====== [手动干预] 生成结束，成功向路网注入: " + successCount + " 票运单 ======");

        // 6. 🚀 打铁趁热：如果成功生成了货，立刻强制唤醒 VRP 大脑！
        // 这样前端点完按钮，瞬间就能看到紫车（多点拼载车）蜂拥而出
        if (successCount > 0) {
            System.out.println("====== [手动干预] 正在强制唤醒 VRP 大脑进行瞬间吞吐... ======");
            try {
                vrpDispatchingCycle();
            } catch (Exception e) {
                System.err.println("❌ VRP 瞬间唤醒失败: " + e.getMessage());
            }
        }

        return successCount;
    }

    @Transactional
    public synchronized StartupShipmentGenerationResult generateStartupProcessingShipments(int targetCount) {
        StartupShipmentGenerationResult result = new StartupShipmentGenerationResult(targetCount);
        if (targetCount <= 0) {
            result.addFailureReason("targetCount must be greater than 0");
            return result;
        }
        if (startupProcessingShipmentsGenerated) {
            logger.info("[StartupShipments] Already generated for this simulation lifecycle, skip.");
            result.setAlreadyGenerated(true);
            result.addFailureReason("startup processing shipments were already generated for this simulation lifecycle");
            return result;
        }

        List<StartupShipmentPlan> viablePlans = buildStartupShipmentPlans(result.getFailureReasons());
        result.setViableSegmentCount(viablePlans.size());
        if (viablePlans.isEmpty()) {
            startupProcessingShipmentsGenerated = true;
            logger.warn("[StartupShipments] No viable processing chain segment found.");
            result.addFailureReason("no viable processing chain segment found");
            return result;
        }

        Collections.shuffle(viablePlans, random);
        List<StartupShipmentPlan> selectedPlans = new ArrayList<>();
        int firstPassCount = Math.min(targetCount, viablePlans.size());
        selectedPlans.addAll(viablePlans.subList(0, firstPassCount));

        if (viablePlans.size() > targetCount) {
            logger.info("[StartupShipments] Active segment count {} exceeds target {}, covering random {} segments.",
                    viablePlans.size(), targetCount, targetCount);
        }

        while (selectedPlans.size() < targetCount) {
            selectedPlans.add(viablePlans.get(random.nextInt(viablePlans.size())));
        }

        int successCount = 0;
        for (StartupShipmentPlan plan : selectedPlans) {
            try {
                createStartupShipment(plan);
                successCount++;
            } catch (Exception e) {
                logger.warn("[StartupShipments] Failed to create shipment for chain={}, {} -> {}: {}",
                        plan.chain.getChainCode(),
                        plan.fromStage.getStageName(),
                        plan.toStage.getStageName(),
                        e.getMessage());
                result.addFailureReason("failed to create shipment for chain=" + plan.chain.getChainCode()
                        + ", segment=" + plan.fromStage.getStageName() + " -> " + plan.toStage.getStageName()
                        + ": " + e.getMessage());
            }
        }

        startupProcessingShipmentsGenerated = true;
        result.setGeneratedCount(successCount);
        result.setSelectedSegmentCount(selectedPlans.size());
        logger.info("[StartupShipments] Generated {} startup shipments, target={}, viableSegments={}.",
                successCount, targetCount, viablePlans.size());
        if (successCount == 0) {
            result.addFailureReason("all selected startup shipment plans failed during creation");
        }
        return result;
    }

    @Transactional
    public synchronized StartupAssignmentGenerationResult generateStartupProcessingAssignments(int targetCount) {
        StartupAssignmentGenerationResult result = new StartupAssignmentGenerationResult(targetCount);
        if (targetCount <= 0) {
            result.addFailureReason("targetCount must be greater than 0");
            return result;
        }
        if (startupProcessingShipmentsGenerated) {
            logger.info("[StartupAssignments] Already generated for this simulation lifecycle, skip.");
            result.setAlreadyGenerated(true);
            result.addFailureReason("startup processing assignments were already generated for this simulation lifecycle");
            return result;
        }
        if (!canRegisterFrontendAssignment()) {
            result.addFailureReason("simulation is not running, startup assignments were not generated");
            return result;
        }

        List<StartupShipmentPlan> viablePlans = buildStartupShipmentPlans(result.getFailureReasons());
        result.setViableSegmentCount(viablePlans.size());
        if (viablePlans.isEmpty()) {
            startupProcessingShipmentsGenerated = true;
            result.addFailureReason("no viable processing chain segment found");
            return result;
        }

        Collections.shuffle(viablePlans, random);
        List<StartupShipmentPlan> selectedPlans = new ArrayList<>();
        int firstPassCount = Math.min(targetCount, viablePlans.size());
        selectedPlans.addAll(viablePlans.subList(0, firstPassCount));
        while (selectedPlans.size() < targetCount) {
            selectedPlans.add(viablePlans.get(random.nextInt(viablePlans.size())));
        }

        for (StartupShipmentPlan plan : selectedPlans) {
            if (!canRegisterFrontendAssignment()) {
                result.addFailureReason("simulation stopped while generating startup assignments");
                break;
            }

            try {
                StartupShipmentCreation creation = createStartupShipment(plan);
                result.incrementShipmentGeneratedCount();
                if (creation.getItems().isEmpty()) {
                    result.addFailureReason("startup shipment created no items for chain=" + plan.chain.getChainCode());
                    continue;
                }

                List<Vehicle> idleVehicles = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);
                ShipmentItem item = creation.getItems().get(0);
                Optional<Vehicle> vehicle = selectOriginalDispatchVehicleForItem(item, idleVehicles);
                if (vehicle.isEmpty()) {
                    result.addFailureReason("no IDLE vehicle can carry startup item id=" + item.getId());
                    continue;
                }

                Assignment assignment = dispatchSingleItemWithOriginalStrategy(item, vehicle.get(), "startup-processing");
                if (assignment != null && assignment.getId() != null) {
                    result.incrementAssignmentGeneratedCount();
                    if (assignmentBriefMap.containsKey(assignment.getId())) {
                        result.incrementFrontendRegisteredCount();
                    }
                }
            } catch (Exception e) {
                logger.warn("[StartupAssignments] Failed to create assignment for chain={}, {} -> {}: {}",
                        plan.chain.getChainCode(),
                        plan.fromStage.getStageName(),
                        plan.toStage.getStageName(),
                        e.getMessage());
                result.addFailureReason("failed to create startup assignment for chain=" + plan.chain.getChainCode()
                        + ", segment=" + plan.fromStage.getStageName() + " -> " + plan.toStage.getStageName()
                        + ": " + e.getMessage());
            }
        }

        startupProcessingShipmentsGenerated = true;
        result.setSelectedSegmentCount(selectedPlans.size());
        if (result.getAssignmentGeneratedCount() == 0) {
            result.addFailureReason("all selected startup assignment plans failed during creation");
        }
        logger.info("[StartupAssignments] Generated {} shipments, {} assignments, {} frontend registrations, target={}.",
                result.getShipmentGeneratedCount(),
                result.getAssignmentGeneratedCount(),
                result.getFrontendRegisteredCount(),
                targetCount);
        return result;
    }

    public synchronized void resetStartupProcessingShipmentsFlag() {
        startupProcessingShipmentsGenerated = false;
    }

    public synchronized boolean isStartupProcessingShipmentsGenerated() {
        return startupProcessingShipmentsGenerated;
    }

    public void clearFrontendRuntimeAssignments() {
        assignmentBriefMap.clear();
        assignmentStatusMap.clear();
    }

    private boolean canRegisterFrontendAssignment() {
        return simulationContext != null && simulationContext.isRunning();
    }

    private boolean shouldAbortSimulationWork() {
        return simulationContext != null && simulationContext.shouldAbortSimulationWork();
    }

    private LocalDateTime currentSimTimeOrNow() {
        return simulationContext == null ? LocalDateTime.now() : simulationContext.getCurrentSimTime();
    }

    public synchronized void resetSimulationRuntimeData() {
        startupProcessingShipmentsGenerated = false;
        startToEndMapping.clear();
        poiPairShipmentMapping.clear();
        assignmentStatusMap.clear();
        assignmentBriefMap.clear();
        pairStatusMap.clear();
        poiIsWithGoods.clear();
        poiTrueCount.clear();
        currentProcessingSegmentSelection = null;
        if (poiShipmentManager != null) {
            poiShipmentManager.reset();
        }
        cleanupService.cleanupAllSimulationData();
        cleanupService.resetAllVehiclesToRandomInitializationPOIs();
    }

    private List<StartupShipmentPlan> buildStartupShipmentPlans(List<String> failureReasons) {
        List<StartupShipmentPlan> plans = new ArrayList<>();
        List<ProcessingChain> activeChains = processingChainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE);
        if (activeChains.isEmpty()) {
            addStartupFailureReason(failureReasons, "no ACTIVE processing chains");
        }
        for (ProcessingChain chain : activeChains) {
            List<ProcessingStage> stages = chain.getStages() == null
                    ? Collections.emptyList()
                    : chain.getStages().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ProcessingStage::getStageOrder,
                            Comparator.nullsLast(Integer::compareTo)))
                    .collect(Collectors.toList());

            if (stages.size() < 2) {
                addStartupFailureReason(failureReasons, "chain=" + chain.getChainCode()
                        + " has fewer than 2 processing stages");
                continue;
            }

            for (int i = 0; i < stages.size() - 1; i++) {
                ProcessingStage fromStage = stages.get(i);
                ProcessingStage toStage = stages.get(i + 1);
                Optional<StartupShipmentPlan> plan = buildStartupShipmentPlan(
                        chain, fromStage, toStage, failureReasons);
                plan.ifPresent(plans::add);
            }
        }
        return plans;
    }

    private Optional<StartupShipmentPlan> buildStartupShipmentPlan(
            ProcessingChain chain,
            ProcessingStage fromStage,
            ProcessingStage toStage,
            List<String> failureReasons
    ) {
        if (fromStage.getProcessingPOI() == null || toStage.getProcessingPOI() == null) {
            logger.warn("[StartupShipments] Skip segment with missing POI: chain={}, {} -> {}",
                    chain.getChainCode(), fromStage.getStageName(), toStage.getStageName());
            addStartupFailureReason(failureReasons, "chain=" + chain.getChainCode()
                    + ", segment=" + fromStage.getStageName() + " -> " + toStage.getStageName()
                    + " skipped because stage POI is missing");
            return Optional.empty();
        }

        Goods goods = resolveSegmentGoods(fromStage, toStage);
        if (goods == null) {
            logger.warn("[StartupShipments] Skip segment with missing goods: chain={}, {} -> {}",
                    chain.getChainCode(), fromStage.getStageName(), toStage.getStageName());
            addStartupFailureReason(failureReasons, "chain=" + chain.getChainCode()
                    + ", segment=" + fromStage.getStageName() + " -> " + toStage.getStageName()
                    + " skipped because segment goods is missing");
            return Optional.empty();
        }

        if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() <= 0
                || goods.getVolumePerUnit() == null || goods.getVolumePerUnit() <= 0) {
            logger.warn("[StartupShipments] Skip goods {} because unit weight/volume is missing or invalid.",
                    goods.getSku());
            addStartupFailureReason(failureReasons, "goods=" + goods.getSku()
                    + " skipped because weightPerUnit or volumePerUnit is missing/invalid");
            return Optional.empty();
        }

        return Optional.of(new StartupShipmentPlan(
                chain,
                fromStage,
                toStage,
                goods,
                STARTUP_SHIPMENT_MIN_QUANTITY,
                STARTUP_SHIPMENT_MAX_QUANTITY
        ));
    }

    private void addStartupFailureReason(List<String> failureReasons, String reason) {
        if (failureReasons == null || reason == null || reason.isBlank()) {
            return;
        }
        if (failureReasons.size() < 50) {
            failureReasons.add(reason);
        }
    }

    public static class StartupShipmentGenerationResult {
        private final int targetCount;
        private int generatedCount;
        private int viableSegmentCount;
        private int selectedSegmentCount;
        private boolean alreadyGenerated;
        private final List<String> failureReasons = new ArrayList<>();

        public StartupShipmentGenerationResult(int targetCount) {
            this.targetCount = targetCount;
        }

        public int getTargetCount() {
            return targetCount;
        }

        public int getGeneratedCount() {
            return generatedCount;
        }

        public void setGeneratedCount(int generatedCount) {
            this.generatedCount = generatedCount;
        }

        public int getViableSegmentCount() {
            return viableSegmentCount;
        }

        public void setViableSegmentCount(int viableSegmentCount) {
            this.viableSegmentCount = viableSegmentCount;
        }

        public int getSelectedSegmentCount() {
            return selectedSegmentCount;
        }

        public void setSelectedSegmentCount(int selectedSegmentCount) {
            this.selectedSegmentCount = selectedSegmentCount;
        }

        public boolean isAlreadyGenerated() {
            return alreadyGenerated;
        }

        public void setAlreadyGenerated(boolean alreadyGenerated) {
            this.alreadyGenerated = alreadyGenerated;
        }

        public List<String> getFailureReasons() {
            return failureReasons;
        }

        public void addFailureReason(String reason) {
            if (failureReasons.size() < 50 && reason != null && !reason.isBlank()) {
                failureReasons.add(reason);
            }
        }

        public static StartupShipmentGenerationResult skipped(int targetCount, String reason) {
            StartupShipmentGenerationResult result = new StartupShipmentGenerationResult(targetCount);
            result.addFailureReason(reason);
            return result;
        }
    }

    public static class StartupAssignmentGenerationResult {
        private final int targetCount;
        private int shipmentGeneratedCount;
        private int assignmentGeneratedCount;
        private int frontendRegisteredCount;
        private int viableSegmentCount;
        private int selectedSegmentCount;
        private boolean alreadyGenerated;
        private final List<String> failureReasons = new ArrayList<>();

        public StartupAssignmentGenerationResult(int targetCount) {
            this.targetCount = targetCount;
        }

        public int getTargetCount() {
            return targetCount;
        }

        public int getShipmentGeneratedCount() {
            return shipmentGeneratedCount;
        }

        public void incrementShipmentGeneratedCount() {
            this.shipmentGeneratedCount++;
        }

        public int getAssignmentGeneratedCount() {
            return assignmentGeneratedCount;
        }

        public void incrementAssignmentGeneratedCount() {
            this.assignmentGeneratedCount++;
        }

        public int getFrontendRegisteredCount() {
            return frontendRegisteredCount;
        }

        public void incrementFrontendRegisteredCount() {
            this.frontendRegisteredCount++;
        }

        public int getViableSegmentCount() {
            return viableSegmentCount;
        }

        public void setViableSegmentCount(int viableSegmentCount) {
            this.viableSegmentCount = viableSegmentCount;
        }

        public int getSelectedSegmentCount() {
            return selectedSegmentCount;
        }

        public void setSelectedSegmentCount(int selectedSegmentCount) {
            this.selectedSegmentCount = selectedSegmentCount;
        }

        public boolean isAlreadyGenerated() {
            return alreadyGenerated;
        }

        public void setAlreadyGenerated(boolean alreadyGenerated) {
            this.alreadyGenerated = alreadyGenerated;
        }

        public List<String> getFailureReasons() {
            return failureReasons;
        }

        public void addFailureReason(String reason) {
            if (failureReasons.size() < 50 && reason != null && !reason.isBlank()) {
                failureReasons.add(reason);
            }
        }
    }

    private Optional<VehicleCapacityChoice> chooseStartupCapacity(Goods goods, List<Vehicle> allVehicles) {
        String vehicleFit = goods.getVehicleFit();
        if (vehicleFit != null && !vehicleFit.isBlank()) {
            List<Vehicle> typedCandidates = allVehicles.stream()
                    .filter(vehicle -> vehicleFit.equals(vehicle.getVehicleType()))
                    .collect(Collectors.toList());
            if (!typedCandidates.isEmpty()) {
                Optional<VehicleCapacityChoice> typedChoice = chooseSmallestCapableVehicle(goods, typedCandidates);
                if (typedChoice.isPresent()) {
                    return typedChoice;
                }
            }
        }
        return chooseSmallestCapableVehicle(goods, allVehicles);
    }

    private Optional<VehicleCapacityChoice> chooseSmallestCapableVehicle(Goods goods, List<Vehicle> vehicles) {
        return vehicles.stream()
                .filter(vehicle -> vehicle.getMaxLoadCapacity() >= goods.getWeightPerUnit())
                .filter(vehicle -> vehicle.getCargoVolume() >= goods.getVolumePerUnit())
                .sorted(Comparator.comparing(Vehicle::getMaxLoadCapacity)
                        .thenComparing(Vehicle::getCargoVolume))
                .findFirst()
                .map(vehicle -> {
                    int maxByWeight = (int) Math.floor(vehicle.getMaxLoadCapacity() / goods.getWeightPerUnit());
                    int maxByVolume = (int) Math.floor(vehicle.getCargoVolume() / goods.getVolumePerUnit());
                    return new VehicleCapacityChoice(Math.min(maxByWeight, maxByVolume));
                })
                .filter(choice -> choice.maxQuantity > 0);
    }

    private StartupShipmentCreation createStartupShipment(StartupShipmentPlan plan) {
        int quantity = randomQuantityInRange(plan.minQuantity, plan.maxQuantity);

        POI managedStartPOI = poiRepository.findById(plan.fromStage.getProcessingPOI().getId())
                .orElseThrow(() -> new RuntimeException("Start POI not found: "
                        + plan.fromStage.getProcessingPOI().getId()));
        POI managedEndPOI = poiRepository.findById(plan.toStage.getProcessingPOI().getId())
                .orElseThrow(() -> new RuntimeException("End POI not found: "
                        + plan.toStage.getProcessingPOI().getId()));

        upsertStartupEnrollment(managedStartPOI, plan.goods, quantity);

        Shipment shipment = initalizeShipment(managedStartPOI, managedEndPOI, plan.goods, quantity);
        List<ShipmentItem> items = createStartupShipmentItems(
                shipment,
                plan.goods,
                quantity,
                plan.toStage,
                managedEndPOI
        );
        if (items.isEmpty()) {
            throw new RuntimeException("No startup shipment items were created");
        }

        startToEndMapping.put(managedStartPOI, managedEndPOI);
        String key = generatePoiPairKey(managedStartPOI, managedEndPOI);
        poiPairShipmentMapping.put(key, shipment);
        createPairStatus(managedStartPOI, managedEndPOI, shipment);
        poiShipmentManager.registerShipment(managedStartPOI, managedEndPOI, shipment);

        logger.info("[StartupShipments] Created shipment refNo={}, chain={}, segment={} -> {}, goods={}, qty={}",
                shipment.getRefNo(),
                plan.chain.getChainCode(),
                plan.fromStage.getStageName(),
                plan.toStage.getStageName(),
                plan.goods.getSku(),
                quantity);
        return new StartupShipmentCreation(shipment, items);
    }

    private List<ShipmentItem> createStartupShipmentItems(
            Shipment shipment,
            Goods goods,
            int quantity,
            ProcessingStage targetStage,
            POI processingPOI
    ) {
        List<CargoChunk> chunks = createStartupCargoChunks(goods, quantity);
        List<ShipmentItem> items = new ArrayList<>();

        for (CargoChunk chunk : chunks) {
            if (chunk == null || chunk.getQuantity() <= 0) {
                continue;
            }

            if (chunk.isExceptional()) {
                List<ShipmentItem> fragments = shipmentItemService.shatterChunkToVrpPool(
                        shipment,
                        goods,
                        chunk.getQuantity()
                );
                for (ShipmentItem fragment : fragments) {
                    applyStartupProcessingFields(fragment, targetStage, processingPOI);
                    items.add(fragment);
                }
                continue;
            }

            ShipmentItem item = shipmentItemService.initalizeShipmentItem(shipment, goods, chunk.getQuantity());
            applyStartupProcessingFields(item, targetStage, processingPOI);
            items.add(item);
        }

        return items;
    }

    private List<CargoChunk> createStartupCargoChunks(Goods goods, int quantity) {
        try {
            List<CargoChunk> chunks = cargoChunkService.chunkCargo(goods, quantity);
            if (chunks != null && !chunks.isEmpty()) {
                return chunks;
            }
        } catch (Exception e) {
            logger.warn("[StartupShipments] Cargo chunk split failed for goods={}, quantity={}: {}",
                    goods != null ? goods.getSku() : null,
                    quantity,
                    e.getMessage());
        }

        return Collections.singletonList(new CargoChunk(quantity, null, null, null, 0.0, true));
    }

    private void applyStartupProcessingFields(
            ShipmentItem item,
            ProcessingStage targetStage,
            POI processingPOI
    ) {
        item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
        item.setAssignment(null);
        item.setStage(targetStage);
        item.setStageOrder(targetStage != null ? targetStage.getStageOrder() : null);
        item.setStageName(targetStage != null ? targetStage.getStageName() : null);
        item.setProcessingPOI(processingPOI);
        item.setProcessingStatus(ShipmentItem.ProcessingItemStatus.WAITING);
        item.setProgressPercent(0);
        shipmentItemRepository.save(item);
    }

    private Optional<Vehicle> selectOriginalDispatchVehicleForItem(
            ShipmentItem item,
            List<Vehicle> candidateVehicles
    ) {
        if (item == null || item.getShipment() == null || item.getShipment().getOriginPOI() == null
                || candidateVehicles == null || candidateVehicles.isEmpty()) {
            return Optional.empty();
        }

        double itemWeight = safeDouble(item.getWeight());
        double itemVolume = safeDouble(item.getVolume());
        POI origin = item.getShipment().getOriginPOI();

        return candidateVehicles.stream()
                .filter(Objects::nonNull)
                .filter(vehicle -> Vehicle.VehicleStatus.IDLE.equals(vehicle.getCurrentStatus()))
                .filter(vehicle -> safeDouble(vehicle.getMaxLoadCapacity()) >= itemWeight)
                .filter(vehicle -> safeDouble(vehicle.getCargoVolume()) >= itemVolume)
                .sorted(Comparator.comparingDouble(vehicle -> estimateVehicleToPoiDistanceKm(vehicle, origin)))
                .findFirst();
    }

    private Assignment dispatchSingleItemWithOriginalStrategy(
            ShipmentItem item,
            Vehicle vehicle,
            String source
    ) {
        if (item == null || item.getShipment() == null) {
            throw new IllegalArgumentException("shipment item is missing shipment");
        }
        if (vehicle == null || vehicle.getId() == null) {
            throw new IllegalArgumentException("vehicle is required");
        }

        Shipment shipment = item.getShipment();
        POI origin = shipment.getOriginPOI();
        POI destination = shipment.getDestPOI();
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("shipment origin or destination POI is missing");
        }

        Route route = initializeRoute(origin, destination);
        Assignment assignment = new Assignment(item, route);
        assignment.setAssignedVehicle(vehicle);
        assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
        assignment.setCreatedTime(simulationContext != null ? simulationContext.getCurrentSimTime() : LocalDateTime.now());
        assignment.setStartTime(assignment.getCreatedTime());
        assignment.setCurrentActionIndex(0);
        assignment.setOriginPOI(origin);
        assignment.setDestPOI(destination);
        assignment.setUpdatedBy(source);
        assignment.setUpdatedTime(LocalDateTime.now());

        AssignmentNode loadNode = new AssignmentNode(
                assignment,
                0,
                origin,
                AssignmentNode.NodeActionType.LOAD,
                item,
                item.getWeight(),
                item.getVolume()
        );
        assignment.addNode(loadNode);

        AssignmentNode unloadNode = new AssignmentNode(
                assignment,
                1,
                destination,
                AssignmentNode.NodeActionType.UNLOAD,
                item,
                -safeDouble(item.getWeight()),
                -safeDouble(item.getVolume())
        );
        assignment.addNode(unloadNode);

        Assignment saved = assignmentRepository.save(assignment);
        saved = transportLifecycleService.startAssignmentExecution(
                saved,
                vehicle,
                currentSimTimeOrNow(),
                source
        );

        registerAssignmentForFrontend(saved);
        return saved;
    }

    private double estimateVehicleToPoiDistanceKm(Vehicle vehicle, POI poi) {
        if (vehicle == null || poi == null || poi.getLatitude() == null || poi.getLongitude() == null) {
            return Double.MAX_VALUE;
        }

        BigDecimal vehicleLat = vehicle.getCurrentLatitude();
        BigDecimal vehicleLng = vehicle.getCurrentLongitude();
        if (vehicle.getCurrentPOI() != null) {
            vehicleLat = vehicle.getCurrentPOI().getLatitude();
            vehicleLng = vehicle.getCurrentPOI().getLongitude();
        }

        if (vehicleLat == null || vehicleLng == null) {
            return Double.MAX_VALUE;
        }

        try {
            return calculateHaversineDistance(vehicleLat, vehicleLng, poi.getLatitude(), poi.getLongitude());
        } catch (Exception ignored) {
            return Double.MAX_VALUE;
        }
    }

    private double safeDouble(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? 0.0 : value;
    }

    private void upsertStartupEnrollment(POI poi, Goods goods, int quantity) {
        Optional<Enrollment> existingEnrollment = enrollmentRepository.findByPoiAndGoods(poi, goods);
        if (existingEnrollment.isPresent()) {
            Enrollment enrollment = existingEnrollment.get();
            int currentQuantity = enrollment.getQuantity() == null ? 0 : enrollment.getQuantity();
            enrollment.setQuantity(currentQuantity + quantity);
            enrollmentRepository.save(enrollment);
            return;
        }

        Enrollment enrollment = new Enrollment(poi, goods, quantity);
        enrollmentRepository.save(enrollment);
        poi.addGoodsEnrollment(enrollment);
        goods.addPOIEnrollment(enrollment);
    }

    private int randomQuantityInRange(int minQuantity, int maxQuantity) {
        if (maxQuantity <= minQuantity) {
            return Math.max(1, minQuantity);
        }
        return random.nextInt(maxQuantity - minQuantity + 1) + minQuantity;
    }

    private static class StartupShipmentPlan {
        private final ProcessingChain chain;
        private final ProcessingStage fromStage;
        private final ProcessingStage toStage;
        private final Goods goods;
        private final int minQuantity;
        private final int maxQuantity;

        private StartupShipmentPlan(
                ProcessingChain chain,
                ProcessingStage fromStage,
                ProcessingStage toStage,
                Goods goods,
                int minQuantity,
                int maxQuantity
        ) {
            this.chain = chain;
            this.fromStage = fromStage;
            this.toStage = toStage;
            this.goods = goods;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
        }
    }

    private static class VehicleCapacityChoice {
        private final int maxQuantity;

        private VehicleCapacityChoice(int maxQuantity) {
            this.maxQuantity = maxQuantity;
        }
    }

    private static class StartupShipmentCreation {
        private final Shipment shipment;
        private final List<ShipmentItem> items;

        private StartupShipmentCreation(Shipment shipment, List<ShipmentItem> items) {
            this.shipment = shipment;
            this.items = items == null ? Collections.emptyList() : items;
        }

        public Shipment getShipment() {
            return shipment;
        }

        public List<ShipmentItem> getItems() {
            return items;
        }
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
        poiTrueCount.put(poi, poiTrueCount.getOrDefault(poi, 0) + 1);
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

    private double calculateOilLoss(double realityCapacity, double theoryCapacity) {
        if (theoryCapacity <= 0.0) {
            return 0.0;
        }

        double oilLoss = realityCapacity / theoryCapacity;
        if (!Double.isFinite(oilLoss)) {
            return 0.0;
        }
        return Math.max(0.0, oilLoss);
    }

    // 基于经纬度的 Haversine 公式计算两点之间的直线距离（公里）
    private Double calculateDistance(POI startPOI, POI endPOI) {
        if (startPOI == null || endPOI == null
                || startPOI.getLatitude() == null || startPOI.getLongitude() == null
                || endPOI.getLatitude() == null || endPOI.getLongitude() == null) {
            return 0.0;
        }
        double lat1 = startPOI.getLatitude().doubleValue();
        double lon1 = startPOI.getLongitude().doubleValue();
        double lat2 = endPOI.getLatitude().doubleValue();
        double lon2 = endPOI.getLongitude().doubleValue();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c; // 地球半径 6371 km
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
            BigDecimal volumePerUnitBD = new BigDecimal(goods.getVolumePerUnit().toString());
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
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%06d", new Random().nextInt(1000000));
        return sku + "_" + timestamp + "_" + random;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DirectAssignmentRequest> createCompleteGoodsTransport(
            POI startPOI,
            POI endPOI,
            Goods goods,
            Integer quantity,
            Route route,
            int originalOrderOffset
    ) {
        if (shouldAbortSimulationWork()) {
            return new ArrayList<>();
        }
        // 1. 创建Shipment
        Shipment shipment = initalizeShipment(startPOI, endPOI, goods, quantity);

        // 2. 智能拆分，只创建待分配运单项；车辆匹配在本轮所有需求生成后统一执行。
        if (shouldAbortSimulationWork()) {
            return new ArrayList<>();
        }
        List<DirectAssignmentRequest> directAssignmentRequests = splitAndCreateShipmentItemsWithSmartMatching(
                shipment, goods, quantity, startPOI, endPOI, route, originalOrderOffset);

        // 3. 建立POI与Goods的Enrollment关系
        if (shouldAbortSimulationWork()) {
            return directAssignmentRequests;
        }
        initRelationBetweenPOIAndGoods(startPOI, goods, quantity);

        // 4. 注册到POI运单管理器
        if (shouldAbortSimulationWork()) {
            return directAssignmentRequests;
        }
        poiShipmentManager.registerShipment(startPOI, endPOI, shipment);

        return directAssignmentRequests;
    }

    /**
     * 智能拆分货物 —— V3版本：使用 CargoChunkService 预拆分为标准块，再匹配车辆。
     *
     * <p>核心改进：不再逐车询问"你能装多少"，而是先算出"每类车最合适的装量"，
     * 拆成标准大小的块，然后为每块找最合适的车。这样保证了高装载率（≥60%），
     * 同时每块大小都 ≤ 最大车载重，VRP 能轻松处理剩余块。</p>
     */
    private List<DirectAssignmentRequest> splitAndCreateShipmentItemsWithSmartMatching(
            Shipment shipment,
            Goods goods,
            Integer totalQuantity,
            POI startPOI,
            POI endPOI,
            Route route,
            int originalOrderOffset
    ) {

        List<DirectAssignmentRequest> directAssignmentRequests = new ArrayList<>();

        System.out.println("开始智能拆分货物，总数量: " + totalQuantity);
        System.out.println("货物总重量: " + (goods.getWeightPerUnit() * totalQuantity) + "吨");

        double weightPerUnit = goods.getWeightPerUnit() != null ? goods.getWeightPerUnit() : 0.0;
        double volumePerUnit = goods.getVolumePerUnit() != null ? goods.getVolumePerUnit() : 0.0;
        System.out.println("货物总重量: " + (weightPerUnit * totalQuantity) + " 吨 | 总体积: " + (volumePerUnit * totalQuantity) + " m³");

        // ========== 1. 使用 CargoChunkService 预先拆分为标准块 ==========
        List<CargoChunk> chunks;
        try {
            chunks = cargoChunkService.chunkCargo(goods, totalQuantity);
        } catch (Exception e) {
            logger.error("CargoChunkService 拆分失败: {}", e.getMessage());
            // 兜底：整体作为一块
            chunks = new ArrayList<>();
            chunks.add(new CargoChunk(totalQuantity, null, null, null, 0.0, true));
        }
        System.out.println("货物预拆分: " + totalQuantity + "件 → " + chunks.size() + "个块");
        for (CargoChunk c : chunks) {
            System.out.println("  " + c);
        }

        // ========== 2. 为每个块创建待分配运单明细 ==========
        int unassignedCount = 0;
        int requestOrder = originalOrderOffset;

        for (CargoChunk chunk : chunks) {
            int chunkQty = chunk.getQuantity();

            if (chunk.isExceptional()) {
                // 异常货物：单件超重，无车能装
                ShipmentItem item = shipmentItemService.initalizeShipmentItem(
                        shipment, goods, chunkQty);
                item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
                shipmentItemRepository.save(item);
                unassignedCount++;
                System.out.println("异常块 " + chunk + " → VRP池（无车能装）");
                continue;
            }

            ShipmentItem shipmentItem = shipmentItemService.initalizeShipmentItem(
                    shipment, goods, chunkQty);
            shipmentItem.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            shipmentItemRepository.save(shipmentItem);
            directAssignmentRequests.add(new DirectAssignmentRequest(
                    shipmentItem,
                    startPOI,
                    endPOI,
                    route,
                    requestOrder++
            ));
        }

        System.out.println("货物拆分完成: 共 " + directAssignmentRequests.size() + " 个可直配候选明细, "
                + unassignedCount + " 个异常块进入VRP池");
        return directAssignmentRequests;
    }

    private void dispatchBatchDirectAssignments(
            List<DirectAssignmentRequest> directAssignmentRequests,
            List<Vehicle> allIdleVehicles
    ) {
        if (directAssignmentRequests == null || directAssignmentRequests.isEmpty()) {
            System.out.println("[批量直配] 本轮没有可直配候选运单项");
            return;
        }

        BatchMatchResult matchResult = batchDirectVehicleAssignmentService.match(
                directAssignmentRequests,
                allIdleVehicles
        );

        System.out.printf("[批量直配] 候选运单项=%d, 空闲车辆=%d, 匹配成功=%d, 未匹配=%d%n",
                directAssignmentRequests.size(),
                allIdleVehicles == null ? 0 : allIdleVehicles.size(),
                matchResult.getAssignments().size(),
                matchResult.getUnmatchedRequests().size());

        for (VehicleAssignment vehicleAssignment : matchResult.getAssignments()) {
            if (shouldAbortSimulationWork()) {
                return;
            }

            DirectAssignmentRequest request = vehicleAssignment.getRequest();
            Vehicle vehicle = vehicleAssignment.getVehicle();
            ShipmentItem item = request.getShipmentItem();
            Assignment assignment = null;

            try {
                updateDirectAssignmentRuntimeCost(vehicle, item, request.getStartPOI(), request.getEndPOI());

                Map<Vehicle, ShipmentItem> vehicleShipmentItemMap = new LinkedHashMap<>();
                vehicleShipmentItemMap.put(vehicle, item);

                List<Assignment> assignments = initalizeAssignment(vehicleShipmentItemMap, request.getRoute());
                if (assignments.isEmpty()) {
                    item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
                    shipmentItemRepository.save(item);
                    continue;
                }

                assignment = assignments.get(0);
                establishVehicleAssignmentRelationship(
                        assignments,
                        request.getStartPOI(),
                        request.getEndPOI()
                );

                Vehicle assignedVehicle = assignment.getAssignedVehicle();
                if (assignedVehicle != null) {
                    Shipment shipment = item.getShipment();
                    createAssignmentStatusRecord(
                            assignment,
                            request.getStartPOI(),
                            request.getEndPOI(),
                            shipment
                    );
                }

                System.out.printf("[批量直配] 车辆 %s -> ShipmentItem %s, cost=%.3f%n",
                        vehicle.getLicensePlate(), item.getId(), vehicleAssignment.getCost());
            } catch (Exception e) {
                System.err.println("[批量直配] 分配失败，ShipmentItem "
                        + (item != null ? item.getId() : null)
                        + " 回到待分配池: " + e.getMessage());
                rollbackAssignmentAllocation(
                        assignment,
                        vehicle,
                        item == null ? List.of() : List.of(item),
                        "Batch direct assignment failed: " + e.getMessage()
                );
            }
        }

        for (DirectAssignmentRequest unmatched : matchResult.getUnmatchedRequests()) {
            ShipmentItem item = unmatched.getShipmentItem();
            if (item == null) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            item.setAssignment(null);
            shipmentItemRepository.save(item);
        }
    }

    private void updateDirectAssignmentRuntimeCost(Vehicle vehicle, ShipmentItem item, POI startPOI, POI endPOI) {
        if (vehicle == null || item == null || startPOI == null || endPOI == null) {
            return;
        }

        double assignedWeight = item.getWeight() != null ? item.getWeight() : 0.0;
        double assignedVolume = item.getVolume() != null ? item.getVolume() : 0.0;

        vehicle.setCurrentLoad(BigDecimal.valueOf(assignedWeight)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue());
        vehicle.setCurrentVolumn(BigDecimal.valueOf(assignedVolume)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue());

        double mileage = calculateHaversineDistance(
                startPOI.getLatitude(),
                startPOI.getLongitude(),
                endPOI.getLatitude(),
                endPOI.getLongitude()
        );
        double mileageWithoutThings = estimateVehicleToPoiDistanceKm(vehicle, startPOI);
        if (mileageWithoutThings == Double.MAX_VALUE) {
            mileageWithoutThings = 0.0;
        }

        double maxLoad = vehicle.getMaxLoadCapacity() != null ? vehicle.getMaxLoadCapacity() : 0.0;
        double realityCapacity = assignedWeight * mileage;
        double theoryCapacity = maxLoad * mileage;
        double waitingTime = (currentLoopCount - vehicle.getLoopCount()) * 0.5;
        double transportTime = (mileage + mileageWithoutThings) / 20.0;
        double theoryRealityCapacity = theoryCapacity - realityCapacity;
        double waitingTransportTime = transportTime > 0.0 ? waitingTime / transportTime : 0.0;
        double oilLoss = calculateOilLoss(realityCapacity, theoryCapacity);
        double fixedLoss = waitingTime + transportTime;
        double loss = 0.5 * oilLoss + 0.3 * fixedLoss;

        CostEntity.totalMileage += mileage;
        CostEntity.totalTransportTime += transportTime;
        CostEntity.totalWaitingTime += waitingTime;
        CostEntity.totalMileageWithoutThings += mileageWithoutThings;
        CostEntity.totalRealityCapacity += realityCapacity;
        CostEntity.totalTheoryCapacity += theoryCapacity;

        if (CostEntity.WorstTheoryRealityCapacity == 0.0
                || CostEntity.WorstTheoryRealityCapacity < theoryRealityCapacity) {
            CostEntity.WorstTheoryRealityCapacity = theoryRealityCapacity;
        }
        if (CostEntity.WorstWaitingTransportTime == 0.0
                || CostEntity.WorstWaitingTransportTime < waitingTransportTime) {
            CostEntity.WorstWaitingTransportTime = waitingTransportTime;
        }
        if (CostEntity.WorstLoss == 0.0 || CostEntity.WorstLoss < loss) {
            CostEntity.WorstLoss = loss;
        }

        vehicle.setLoopCount(currentLoopCount);
        vehicleRepository.save(vehicle);
    }

    /**
     * 按容量选择最合适的车辆 —— 找能装下指定重量和体积的容量最小的车。
     * 保证高装载率（≥60%），避免大车拉小活。
     */
    private Vehicle selectVehicleByCapacity(List<Vehicle> vehicles,
                                            double requiredWeight, double requiredVolume) {
        Vehicle best = null;
        double bestCapacity = Double.MAX_VALUE;

        for (Vehicle v : vehicles) {
            Double maxLoad = v.getMaxLoadCapacity();
            Double maxVolume = v.getCargoVolume();
            if (maxLoad == null || maxVolume == null) continue;

            if (maxLoad >= requiredWeight && maxVolume >= requiredVolume) {
                double wf = requiredWeight / maxLoad;
                double vf = requiredVolume / maxVolume;
                double loadFactor = Math.max(wf, vf);
                if (loadFactor >= 0.60 && maxLoad < bestCapacity) {
                    best = v;
                    bestCapacity = maxLoad;
                }
            }
        }
        return best;
    }

    /**
     * 【核心粉碎引擎】辅助方法：将指定数量的大宗货块进行微粒度粉碎，无条件注入全局 VRP 待接单池
     * * @param quantityToFragment 需要被打碎的总件数
     * @param fragmentIdealQty 单个碎块容纳的最大标准件数（对应 <=1.4t 且 <=14m³）
     * @return 最终粉碎生成的微运单块总数
     */
    private int fragmentAndAddToVrpPool(Shipment shipment, Goods goods, int quantityToFragment,
                                        int fragmentIdealQty, Map<Vehicle, ShipmentItem> vehicleShipmentItemMap) {
        int remaining = quantityToFragment;
        int fragmentCount = 0;

        while (remaining > 0) {
            int currentFragmentQty = Math.min(fragmentIdealQty, remaining);

            // 1. 独立实例化一个精细微颗粒度运单明细 (ShipmentItem)
            ShipmentItem fragmentItem = shipmentItemService.initalizeShipmentItem(shipment, goods, currentFragmentQty);

            // 2. 核心标记：状态洗白为 NOT_ASSIGNED，代表该碎片暴露在全局待拼载池中
            fragmentItem.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            shipmentItemRepository.save(fragmentItem);

            // 3. 关键纽带：车辆绑定置为 null，推入待接单的大脑共享池
            vehicleShipmentItemMap.put(null, fragmentItem);

            remaining -= currentFragmentQty;
            fragmentCount++;
        }

        System.out.println("   └─> 物理推演：已成功将大单粉碎为 " + fragmentCount + " 个微标准碎块（每块独立上限: " + fragmentIdealQty + " 件），无缝兼容极小车型。");
        return fragmentCount;
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
                // ================== 新增：尾货/熔断货物的拦截处理 ==================
                if (vehicle == null) {
                    // 对于没有分配到车辆的货物（由于运力不足，或装载率低于60%被拦截的尾货）
                    // 我们【不】生成 Assignment，而是将明细状态重置为待分配，留在池子里给 VRP 算法用
                    shipmentItem.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
                    shipmentItemRepository.save(shipmentItem);
                    System.out.println("✅ 尾货成功进入全局待接单池，等待VRP算法拼载。ShipmentItem ID: " + shipmentItem.getId());
                    continue; // 关键：跳过 Assignment 的生成
                }
                // ===============================================================

                Assignment assignment = new Assignment(shipmentItem, route);
                if (route != null) {
                    assignment.setOriginPOI(route.getStartPOI());
                    assignment.setDestPOI(route.getEndPOI());
                } else{
                    logger.warn("道路起点与终点为空");
                }
                assignment.setAssignedVehicle(vehicle);
                assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);

                Assignment saved = assignmentRepository.save(assignment);
                saved = transportLifecycleService.startAssignmentExecution(
                        saved,
                        vehicle,
                        currentSimTimeOrNow(),
                        "DataInitializer -- 普通任务派车"
                );
                assignments.add(saved);
            }
        }
        return assignments;
    }

    @Transactional
    public void vrpDispatchingCycle() {
        System.out.println("====== [VRP 大脑] 开始扫描全局待接单池 ======");

        // 0. 超时运单清理：防止货物永久滞留
        try {
            List<POIShipmentRecord> expiredRecords = poiShipmentManager.sweepExpiredShipments(120);
            if (!expiredRecords.isEmpty()) {
                for (POIShipmentRecord record : expiredRecords) {
                    // 取消超时的ShipmentItems
                    Shipment shipment = shipmentRepository.findById(record.getShipmentId()).orElse(null);
                    if (shipment != null) {
                        transportLifecycleService.cancelShipment(
                                shipment,
                                "POI shipment timeout",
                                currentSimTimeOrNow(),
                                "DataInitializer -- timeout cleanup"
                        );
                    }
                    // 清理库存
                    POI sourcePoi = poiRepository.findById(record.getSourcePoiId()).orElse(null);
                    POI destPoi = poiRepository.findById(record.getDestPoiId()).orElse(null);
                    if (sourcePoi != null && destPoi != null) {
                        startToEndMapping.remove(sourcePoi);
                        poiShipmentManager.releasePOI(sourcePoi);
                        String key = generatePoiPairKey(sourcePoi, destPoi);
                        poiPairShipmentMapping.remove(key);
                        setPoiToFalse(sourcePoi);
                        System.out.println("超时清理: " + sourcePoi.getName() + " → " + destPoi.getName());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("超时清理过程出错: {}", e.getMessage());
        }

        // 1. 捞取所有待拼车的尾货，并按重量降序排序 (FFD 算法的核心第一步)
        List<ShipmentItem> pendingItems = shipmentItemRepository.findAll().stream()
                .filter(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED)
                .sorted((a, b) -> Double.compare(b.getWeight(), a.getWeight())) // 挑大的先运
                .collect(Collectors.toList());

        if (pendingItems.isEmpty()) {
            System.out.println("[VRP 大脑] 待接单池为空，进入休眠。");
            return;
        }

        // 2. 获取专属的 VRP 测试车队 (空闲状态)
        List<Vehicle> vrpVehicles = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);
        if (vrpVehicles.isEmpty()) {
            System.out.println("[VRP 大脑] 没有空闲的车辆。");
            return;
        }

        System.out.printf("[VRP 大脑] 发现 %d 个待拼订单，%d 辆空闲车辆。开始拼载计算...%n", pendingItems.size(), vrpVehicles.size());

        // 3. 遍历每辆车，进行贪心拼载
        for (Vehicle vehicle : vrpVehicles) {
            double currentVehicleCapacity = vehicle.getMaxLoadCapacity() != null ? vehicle.getMaxLoadCapacity() : 0.0;
            double currentVehicleVolume = vehicle.getCargoVolume() != null ? vehicle.getCargoVolume() : 0.0;
            if (currentVehicleCapacity <= 0 || currentVehicleVolume <= 0) continue;
            List<ShipmentItem> packedItems = new ArrayList<>();
            List<POI> pickupPois = new ArrayList<>();
            List<POI> dropoffPois = new ArrayList<>();

            double remainingCapacity = currentVehicleCapacity;
            double remainingVolume = currentVehicleVolume; // 追踪剩余体积
            double currentSimulatedCost = 0.0;
            double acceptedMileage = 0.0;
            double addedExtraMileage = 0.0;
            double addedWeight = 0.0;

            for (ShipmentItem item : pendingItems) {

                // 【第一关】：物理容量硬约束 (装不下直接跳过)
                double itemWeight = item.getWeight() != null ? item.getWeight() : 0.0;
                double itemVolume = item.getVolume() != null ? item.getVolume() : 0.0;

                if (itemWeight > remainingCapacity || itemVolume > remainingVolume) {
                    logger.info("[ORIGINAL_VRP] Skip item {} for vehicle {}: capacity exceeded. itemWeight={}, itemVolume={}, remainingWeight={}, remainingVolume={}",
                            item.getId(), vehicle.getLicensePlate(), itemWeight, itemVolume, remainingCapacity, remainingVolume);
                    continue;
                }

                if (item.getShipment() == null) {
                    logger.info("[ORIGINAL_VRP] Skip item {} for vehicle {}: missing shipment.",
                            item.getId(), vehicle.getLicensePlate());
                    continue;
                }
                POI itemOrigin = item.getShipment().getOriginPOI();
                POI itemDest = item.getShipment().getDestPOI();
                if (itemOrigin == null || itemDest == null) {
                    logger.info("[ORIGINAL_VRP] Skip item {} for vehicle {}: missing origin or destination POI.",
                            item.getId(), vehicle.getLicensePlate());
                    continue;
                }

                // 【第二关】：空间与绕路粗筛 (防走火入魔)
                // 如果新增货源离首个装货点过远，直接拒绝
                if (!pickupPois.isEmpty()) {
                    POI anchorPoi = pickupPois.get(0); // 锚点：首个装货点
                    double distToAnchor = calculateHaversineDistance(
                            anchorPoi.getLatitude(), anchorPoi.getLongitude(),
                            itemOrigin.getLatitude(), itemOrigin.getLongitude());

                    if (distToAnchor > originalVrpDispatchPolicy.getMaxAnchorDistanceKm()) {
                        logger.info("[ORIGINAL_VRP] Skip item {} for vehicle {}: anchor distance {}km exceeds {}km.",
                                item.getId(),
                                vehicle.getLicensePlate(),
                                String.format("%.2f", distToAnchor),
                                originalVrpDispatchPolicy.getMaxAnchorDistanceKm());
                        continue;
                    }
                }

                // 【第三关】：边际成本精算 (调用预估器)
                // 模拟加入这个订单后的新增里程和成本
                double simulatedDeltaMileage = 0.0;

                if (pickupPois.isEmpty()) {
                    simulatedDeltaMileage = calculateHaversineDistance(itemOrigin.getLatitude(), itemOrigin.getLongitude(), itemDest.getLatitude(), itemDest.getLongitude());
                } else {
                    // LIFO 模式下的断点永远在 "最新装货点" 和 "最新卸货点" 之间
                    POI lastPickup = pickupPois.get(pickupPois.size() - 1);
                    POI firstDropoff = dropoffPois.get(0);

                    // 旧距离： A装 -> A卸
                    double oldBridge = calculateHaversineDistance(lastPickup.getLatitude(), lastPickup.getLongitude(), firstDropoff.getLatitude(), firstDropoff.getLongitude());

                    // 新距离： A装 -> B装 -> B卸 -> A卸
                    double newBridge = calculateHaversineDistance(lastPickup.getLatitude(), lastPickup.getLongitude(), itemOrigin.getLatitude(), itemOrigin.getLongitude())
                            + calculateHaversineDistance(itemOrigin.getLatitude(), itemOrigin.getLongitude(), itemDest.getLatitude(), itemDest.getLongitude())
                            + calculateHaversineDistance(itemDest.getLatitude(), itemDest.getLongitude(), firstDropoff.getLatitude(), firstDropoff.getLongitude());

                    // 绕路距离就是新旧距离之差
                    simulatedDeltaMileage = newBridge - oldBridge;
                    if (simulatedDeltaMileage <= 0) simulatedDeltaMileage = 5.0; // 防止因为情况过于理想叠加各种情况导致负数
                }

                if (!packedItems.isEmpty()
                        && !originalVrpDispatchPolicy.isWorthAdding(item, simulatedDeltaMileage)) {
                    logger.info("[ORIGINAL_VRP] Skip item {} for vehicle {}: added tons per extra km {} is below threshold {}.",
                            item.getId(),
                            vehicle.getLicensePlate(),
                            String.format("%.4f", originalVrpDispatchPolicy.calculateAddedTonsPerExtraKm(item, simulatedDeltaMileage)),
                            originalVrpDispatchPolicy.getMinAddedTonsPerExtraKm());
                    continue;
                }

                double deltaTransportTime = simulatedDeltaMileage / 60.0;

                Double marginalCost = getCostService.estimateMarginalCost(
                        simulatedDeltaMileage, 0.0, deltaTransportTime, 0.5,
                        vehicle.getMaxLoadCapacity() * simulatedDeltaMileage,
                        item.getWeight() * simulatedDeltaMileage, 0.0, 0.0, 0.0);

                if (!originalVrpDispatchPolicy.acceptsMarginalCost(marginalCost)) {
                    logger.info("[ORIGINAL_VRP] Skip item {} for vehicle {}: marginalCost {} exceeds threshold {}.",
                            item.getId(),
                            vehicle.getLicensePlate(),
                            marginalCost,
                            originalVrpDispatchPolicy.getMaxMarginalCost());
                    continue;
                }

                if (!packedItems.isEmpty()) {
                    addedExtraMileage += originalVrpDispatchPolicy.effectiveExtraMileageKm(simulatedDeltaMileage);
                    addedWeight += itemWeight;
                }
                acceptedMileage += simulatedDeltaMileage;
                packedItems.add(item);
                pickupPois.add(itemOrigin);
                dropoffPois.add(0, itemDest);

                remainingCapacity -= itemWeight;
                remainingVolume -= itemVolume;
                currentSimulatedCost += marginalCost;
            }

            if (!packedItems.isEmpty()) {
                double finalLoadFactor = originalVrpDispatchPolicy.calculateLoadFactor(vehicle, packedItems);
                double totalPackedWeight = packedItems.stream()
                        .mapToDouble(packedItem -> packedItem.getWeight() != null ? packedItem.getWeight() : 0.0)
                        .sum();
                double totalPackedVolume = packedItems.stream()
                        .mapToDouble(packedItem -> packedItem.getVolume() != null ? packedItem.getVolume() : 0.0)
                        .sum();
                if (!originalVrpDispatchPolicy.meetsMinLoadFactor(vehicle, packedItems)) {
                    logger.info("[ORIGINAL_VRP] Reject vehicle {} pack: final load factor {} is below threshold {}. items={}, totalWeight={}, totalVolume={}",
                            vehicle.getLicensePlate(),
                            String.format("%.4f", finalLoadFactor),
                            originalVrpDispatchPolicy.getMinLoadFactor(),
                            packedItems.size(),
                            totalPackedWeight,
                            totalPackedVolume);
                    continue;
                }
                pendingItems.removeAll(packedItems);
                logger.info("[ORIGINAL_VRP] Dispatch vehicle {}: items={}, loadFactor={}, totalWeight={}, totalVolume={}, acceptedMileageKm={}, addedExtraMileageKm={}, addedTonsPerKm={}, simulatedCost={}",
                        vehicle.getLicensePlate(),
                        packedItems.size(),
                        String.format("%.4f", finalLoadFactor),
                        totalPackedWeight,
                        totalPackedVolume,
                        String.format("%.2f", acceptedMileage),
                        String.format("%.2f", addedExtraMileage),
                        addedExtraMileage > 0.0 ? String.format("%.4f", addedWeight / addedExtraMileage) : "N/A",
                        String.format("%.4f", currentSimulatedCost));
                dispatchVrpVehicle(vehicle, packedItems, pickupPois, dropoffPois);
            }
        }
        System.out.println("====== [VRP 大脑] 拼载计算结束 ======");
    }

    /**
     * VRP 专用的派车方法：将拼好的货物打包成多节点路线，并派发车辆
     */
    private void dispatchVrpVehicle(Vehicle vehicle, List<ShipmentItem> packedItems, List<POI> pickupPois, List<POI> dropoffPois) {
        if (shouldAbortSimulationWork()) {
            return;
        }
        // 为了避免 LIFO(后进先出) 的倒厢问题，VRP 调度通常采用 "先集中装，后集中卸" 的策略
        List<POI> fullRoutePois = new ArrayList<>();
        fullRoutePois.addAll(pickupPois);
        fullRoutePois.addAll(dropoffPois);

        // 创建多点 Route 实体 (复用你的路由逻辑，这里存首尾，中间的依靠 Node 表达)
        Route route = initializeRoute(fullRoutePois.get(0), fullRoutePois.get(fullRoutePois.size()-1));

        // 我们随便取一个 item 作为主关联 (在 VRP 中，Assignment 是对车的，不只对一个货)
        Assignment assignment = new Assignment(packedItems.get(0), route);
        assignment.setAssignedVehicle(vehicle);
        assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);

        assignment.setOriginPOI(fullRoutePois.get(0));
        assignment.setDestPOI(fullRoutePois.get(fullRoutePois.size() - 1));

        for (ShipmentItem item : packedItems) {
            assignment.addShipmentItem(item);
        }

        // --- 核心：生成 AssignmentNodes 行程单 ---
        int sequence = 0;

        // 1. 生成所有装货节点 (LOAD)
        for (ShipmentItem item : packedItems) {
            AssignmentNode loadNode = new AssignmentNode(assignment, sequence++,
                    item.getShipment().getOriginPOI(),
                    AssignmentNode.NodeActionType.LOAD,
                    item,
                    item.getWeight(), item.getVolume());
            assignment.addNode(loadNode);
        }

        // 2. 生成所有卸货节点 (UNLOAD)
        for (int i = packedItems.size() - 1; i >= 0; i--) {
            ShipmentItem item = packedItems.get(i);
            AssignmentNode unloadNode = new AssignmentNode(assignment, sequence++,
                    item.getShipment().getDestPOI(),
                    AssignmentNode.NodeActionType.UNLOAD,
                    item,
                    -item.getWeight(), -item.getVolume()); // 卸货为负数
            assignment.addNode(unloadNode);
        }

        // 保存 Assignment 及级联的 Nodes
        assignment = assignmentRepository.save(assignment);

        // 更新车辆状态与载重
        double totalAssignedWeight = packedItems.stream().mapToDouble(ShipmentItem::getWeight).sum();
        double totalAssignedVolume = packedItems.stream().mapToDouble(ShipmentItem::getVolume).sum();
        assignment = transportLifecycleService.startAssignmentExecution(
                assignment,
                vehicle,
                currentSimTimeOrNow(),
                "DataInitializer -- VRP派车"
        );

        try {
            double mileageWithoutThings = 0.0;
            POI firstPickup = pickupPois.get(0);

            // 1. 获取空车前往起点的距离 (使用高德API，带休眠防限流)
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (vehicle.getCurrentPOI() != null) {
                if (shouldAbortSimulationWork()) {
                    return;
                }
                GaodeRouteResponse response_2 = routePlanningService.planDrivingRouteByPois(vehicle.getCurrentPOI().getId(), firstPickup.getId(), "0");
                if (response_2 != null && response_2.getData() != null && response_2.getData().getTotalDistance() != null) {
                    mileageWithoutThings = response_2.getData().getTotalDistance() / 1000.0;
                } else {
                    mileageWithoutThings = calculateHaversineDistance(vehicle.getCurrentPOI().getLatitude(), vehicle.getCurrentPOI().getLongitude(), firstPickup.getLatitude(), firstPickup.getLongitude());
                }
            } else if (vehicle.getCurrentLatitude() != null && vehicle.getCurrentLongitude() != null) {
                mileageWithoutThings = calculateHaversineDistance(vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude(), firstPickup.getLatitude(), firstPickup.getLongitude());
            }

            // 2. 获取多点路线的真实载重和里程 (逐段物理拆解法)
            double mileage = 0.0;
            double realityCapacity = 0.0;
            double currentWeightInVehicle = 0.0;

            // 拼装完整的重量变化线 (正数为装，负数为卸)
            List<Double> weightChanges = new ArrayList<>();
            for (ShipmentItem item : packedItems) weightChanges.add(item.getWeight());
            for (int i = packedItems.size() - 1; i >= 0; i--) weightChanges.add(-packedItems.get(i).getWeight());

            // 遍历拼接好的整条节点折线
            for (int i = 0; i < fullRoutePois.size() - 1; i++) {
                POI p1 = fullRoutePois.get(i);
                POI p2 = fullRoutePois.get(i + 1);

                // 为防止多节点并发API把高德限流打爆，多点段间距离使用 直线*1.2 近似替代
                double segDist = calculateHaversineDistance(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude()) * 1.2;
                mileage += segDist;

                // 物理累加：驶入该路段前的车厢总重 × 该路段里程
                currentWeightInVehicle += weightChanges.get(i);
                realityCapacity += currentWeightInVehicle * segDist;
            }

            // 3. 计算各项基础指标
            double theoryCapacity = vehicle.getMaxLoadCapacity() * mileage;
            double waitingTime = (currentLoopCount - vehicle.getLoopCount()) * 0.5;
            double transportTime = (mileage + mileageWithoutThings) / 20.0;

            double theoryRealityCapacity = theoryCapacity - realityCapacity;
            double waitingTransportTime = transportTime > 0 ? waitingTime / transportTime : 0.0;

            double oilLoss = calculateOilLoss(realityCapacity, theoryCapacity);
            double fixedLoss = waitingTime + transportTime;
            double loss = 0.5 * oilLoss + 0.3 * fixedLoss;

            // 4. 累加到全局 CostEntity 监控面板
            CostEntity.totalMileage += mileage;
            CostEntity.totalTransportTime += transportTime;
            CostEntity.totalWaitingTime += waitingTime;
            CostEntity.totalMileageWithoutThings += mileageWithoutThings;
            CostEntity.totalRealityCapacity += realityCapacity;
            CostEntity.totalTheoryCapacity += theoryCapacity;

            if (CostEntity.WorstTheoryRealityCapacity == 0.0 || CostEntity.WorstTheoryRealityCapacity < theoryRealityCapacity) {
                CostEntity.WorstTheoryRealityCapacity = theoryRealityCapacity;
            }

            if (CostEntity.WorstWaitingTransportTime == 0.0 || CostEntity.WorstWaitingTransportTime < waitingTransportTime) {
                CostEntity.WorstWaitingTransportTime = waitingTransportTime;
            }

            if (CostEntity.WorstLoss == 0.0 || CostEntity.WorstLoss < loss) {
                CostEntity.WorstLoss = loss;
            }

            // 同步车辆最新的生命周期计步器
            vehicle.setLoopCount(currentLoopCount);
            vehicleRepository.save(vehicle);

        } catch (Exception e) {
            System.err.println("VRP 路线成本精算时发生异常: " + e.getMessage());
        }

        System.out.printf("🚀 [VRP 派车] 车辆 %s 成功拼载 %d 票货物 (总重 %.2ft)，生成多点行程单！%n",
                vehicle.getLicensePlate(), packedItems.size(), totalAssignedWeight);

        // ==================== 新增：将 VRP 任务组装为 DTO 并推入前端广播缓存 ====================
        if (shouldAbortSimulationWork()) {
            rollbackAssignmentAllocation(assignment, vehicle, packedItems,
                    "Simulation reset before frontend registration");
            return;
        }
        if (!rebuildTransportMetricsStrict(assignment)) {
            rollbackAssignmentAllocation(assignment, vehicle, packedItems,
                    "Strict route planning failed before frontend registration");
            return;
        }
        if (!canRegisterFrontendAssignment()) {
            rollbackAssignmentAllocation(assignment, vehicle, packedItems,
                    "Simulation stopped before frontend registration");
            return;
        }

        try {
            AssignmentBriefDTO brief = new AssignmentBriefDTO();
            brief.setAssignmentId(assignment.getId());
            brief.setStatus(assignment.getStatus().toString());
            brief.setCreatedTime(assignment.getCreatedTime());
            brief.setStartTime(assignment.getStartTime());
            brief.setDrawn(false); // 标记为未绘制，等待前端拉取

            // 标记这是一个 VRP 任务
            brief.setVrp(true);

            // 车辆信息
            brief.setVehicleId(vehicle.getId());
            brief.setLicensePlate(vehicle.getLicensePlate());
            brief.setVehicleStatus(vehicle.getCurrentStatus().toString());
            brief.setCurrentLoad(vehicle.getCurrentLoad());
            brief.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());
            brief.setCurrentVolume(vehicle.getCurrentVolumn());
            brief.setMaxVolumeCapacity(vehicle.getCargoVolume());

            // 车辆初始位置 (VRP专车的当前位置)
            if (vehicle.getCurrentPOI() != null) {
                brief.setVehicleStartLng(vehicle.getCurrentPOI().getLongitude().doubleValue());
                brief.setVehicleStartLat(vehicle.getCurrentPOI().getLatitude().doubleValue());
            } else if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
                brief.setVehicleStartLng(vehicle.getCurrentLongitude().doubleValue());
                brief.setVehicleStartLat(vehicle.getCurrentLatitude().doubleValue());
            }

            // 构建有序节点集合 NodeDTOs
            List<AssignmentBriefDTO.NodeDTO> nodeDTOs = new ArrayList<>();
            for (AssignmentNode node : assignment.getNodes()) {
                AssignmentBriefDTO.NodeDTO nodeDTO = new AssignmentBriefDTO.NodeDTO();
                nodeDTO.setSequenceIndex(node.getSequenceIndex());
                nodeDTO.setPoiId(node.getPoi().getId());
                nodeDTO.setPoiName(node.getPoi().getName());
                nodeDTO.setPoiType(node.getPoi().getPoiType() != null ? node.getPoi().getPoiType().name() : null);
                nodeDTO.setLng(node.getPoi().getLongitude());
                nodeDTO.setLat(node.getPoi().getLatitude());
                nodeDTO.setActionType(node.getActionType().name());
                nodeDTO.setWeightDelta(node.getWeightDelta());
                nodeDTO.setVolumeDelta(node.getVolumeDelta());
                nodeDTOs.add(nodeDTO);
            }
            brief.setNodes(nodeDTOs);

            // 将 VRP 任务放入缓存字典，供前端 /api/assignments/new 接口轮询
            assignmentBriefMap.put(assignment.getId(), brief);

            // 同步生成简易的 status 记录防止空指针
            AssignmentStatusDTO status = new AssignmentStatusDTO(
                    assignment.getId(),
                    "VRP_MULTI_POINT", // VRP任务不需要传统的start_end_pair
                    vehicle.getId(),
                    null
            );
            assignmentStatusMap.put("VRP_" + assignment.getId(), status);

            System.out.println("📡 [VRP 广播] VRP 行程单已推入缓存，等待前端拉取。Assignment ID: " + assignment.getId());
        } catch (Exception e) {
            System.err.println("❌ [VRP 广播] 推入缓存失败: " + e.getMessage());
        }
    }

    /**
     * 建立车辆与任务的双向关联
     */
    // ToDO 这里的逻辑是基于车辆在起点来实现的，具体的车辆匹配函数需要后续再完善。
    private void establishVehicleAssignmentRelationship(List<Assignment> assignments, POI startPOI, POI endPOI) {
        if (shouldAbortSimulationWork()) {
            return;
        }
        try {
            // 1. 重新从数据库加载POI实体
            POI managedStartPOI = poiRepository.findById(startPOI.getId())
                    .orElseThrow(() -> new RuntimeException("起点POI不存在: " + startPOI.getId()));
            POI managedEndPOI = poiRepository.findById(endPOI.getId())
                    .orElseThrow(() -> new RuntimeException("终点POI不存在: " + endPOI.getId()));

            for (Assignment assignment : assignments) {
                if (shouldAbortSimulationWork()) {
                    return;
                }
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

                // 3. 保留车辆接收任务前的当前位置
                managedVehicle.setCurrentLongitude(originalLng);
                managedVehicle.setCurrentLatitude(originalLat);

                // 4. 设置任务初始状态，再交由生命周期服务进入执行态
                assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
                assignment.setUpdatedTime(LocalDateTime.now());
                assignment.setUpdatedBy("DataInitializer -- 运输任务成功分配");

                managedVehicle.setUpdatedBy("DataInitializer -- 车辆接收运输任务");
                managedVehicle.setUpdatedTime(LocalDateTime.now());

                vehicleRepository.save(managedVehicle);
                assignmentRepository.save(assignment);
                transportLifecycleService.startAssignmentExecution(
                        assignment,
                        managedVehicle,
                        currentSimTimeOrNow(),
                        "DataInitializer -- 运输任务成功分配"
                );
                if (shouldAbortSimulationWork()) {
                    rollbackAssignmentAllocation(assignment, managedVehicle,
                            new ArrayList<>(assignment.getShipmentItems()),
                            "Simulation reset during assignment binding");
                    continue;
                }
                if (!rebuildTransportMetricsStrict(assignment)) {
                    rollbackAssignmentAllocation(assignment, managedVehicle,
                            new ArrayList<>(assignment.getShipmentItems()),
                            "Strict route planning failed during assignment binding");
                    continue;
                }

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
        try {
            // 先查数据库，这是最铁的证据
            Optional<Enrollment> existing = enrollmentRepository.findByPoiAndGoods(poiForTest, goodsForTest);

            if (existing.isPresent()) {
                System.out.println("警告：数据库中已存在该POI的货物关系，跳过插入，避免 Duplicate entry");
                // 这里也可以顺手修复一下你内存里错乱的 Map 状态
                poiIsWithGoods.put(poiForTest, true);
                return;
            }

            Enrollment enrollmentForTest = new Enrollment(poiForTest, goodsForTest, generateQuantity);
            enrollmentRepository.save(enrollmentForTest);
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
                                            assignedVehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, currentSimTimeOrNow(), Duration.ZERO);
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
                trueProbability = trueProbability / 0.95;

                // 同步POI运单管理器
                poiShipmentManager.unregisterShipment(freshStartPOI, endPOI);
                poiShipmentManager.releasePOI(freshStartPOI);
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
            Set<Long> completedAssignmentIds = new HashSet<>();

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
                                    && assignment.getAssignedVehicle().getId().equals(vehicle.getId())
                                    && assignment.getId() != null
                                    && completedAssignmentIds.add(assignment.getId())) {
                                transportLifecycleService.completeDelivery(
                                        assignment,
                                        vehicle,
                                        endPOI,
                                        currentSimTimeOrNow(),
                                        "DataInitializer -- 车辆到达结算"
                                );
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
            completedAssignmentIds.forEach(this::markAssignmentAsCompleted);

            List<Enrollment> remainingEnrollments = new ArrayList<>(freshStartPOI.getEnrollments());
            if (remainingEnrollments.isEmpty()) {
                String pairId = generatePoiPairKey(freshStartPOI, endPOI);
                markPairAsCompleted(pairId);
                startToEndMapping.remove(startPOI);

                // 更新POI状态
                poiIsWithGoods.put(freshStartPOI, false);
                trueProbability = trueProbability / 0.95;
            }

        } catch (Exception e) {
            System.err.println("处理车辆送货失败: " + e.getMessage());
            throw new RuntimeException("车辆送货处理失败", e);
        }
    }

    /**
     * VRP 多点配送专属结算大脑 (期末统一清算)
     */
    @Transactional
    public void processVrpVehicleDelivery(Assignment assignment, Vehicle vehicle, POI endPOI) {
        try {
            // 1. 遍历车上的每一票货物 (ShipmentItem)
            Set<ShipmentItem> items = assignment.getShipmentItems();
            for (ShipmentItem item : items) {

                // 追溯这票货物的源头，精准扣减库存
                Shipment shipment = item.getShipment();
                if (shipment != null) {
                    POI originPOI = shipment.getOriginPOI();
                    Goods goods = item.getGoods();

                    if (originPOI != null && goods != null) {
                        // 找到当年那个源头工厂的库存 (Enrollment)
                        Optional<Enrollment> enrollmentOpt = enrollmentRepository.findByPoiAndGoods(originPOI, goods);
                        if (enrollmentOpt.isPresent()) {
                            Enrollment enrollment = enrollmentOpt.get();
                            int remaining = enrollment.getQuantity();
                            if (remaining > 0) {
                                // 扣减库存 (保持你原本每次减1的逻辑，或者按真实重量扣减)
                                enrollment.setQuantity(remaining - 1);
                                if (enrollment.getQuantity() <= 0) {
                                    originPOI.removeGoodsEnrollment(enrollment);
                                    goods.removePOIEnrollment(enrollment);
                                    enrollmentRepository.delete(enrollment);
                                    System.out.println("📦 工厂 " + originPOI.getName() + " 的 " + goods.getName() + " 库存已彻底清空");
                                } else {
                                    enrollmentRepository.save(enrollment);
                                }
                            }
                        }
                    }
                }
            }

            transportLifecycleService.completeDelivery(
                    assignment,
                    vehicle,
                    endPOI,
                    currentSimTimeOrNow(),
                    "DataInitializer -- VRP车辆到达结算"
            );

            // 清理前端用来刷新的缓存
            if (assignmentBriefMap.containsKey(assignment.getId())) {
                AssignmentBriefDTO dto = assignmentBriefMap.get(assignment.getId());
                dto.setStatus("COMPLETED");
                assignmentBriefMap.put(assignment.getId(), dto);
            }

            System.out.println("✅ VRP 车辆 " + vehicle.getLicensePlate() + " 沿途所有账目清算完毕，车辆已释放！");

        } catch (Exception e) {
            System.err.println("❌ VRP 车辆送货清算失败: " + e.getMessage());
            throw new RuntimeException("VRP 车辆清算失败", e);
        }
    }

    // 新增：检查和更新Shipment状态
    private void checkAndUpdateShipmentStatus(Shipment shipment) {
        transportLifecycleService.refreshShipmentStatus(shipment);
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
            // 先清理运行期仿真数据，避免车辆重置时触发Assignment级联删除
            cleanupService.cleanupAllSimulationData();
            // 再随机重置所有车辆到仓库或配送中心
            cleanupService.resetAllVehiclesToRandomInitializationPOIs();
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

    public void registerAssignmentForFrontend(Assignment assignment) {
        if (assignment == null || assignment.getId() == null) {
            System.err.println("[FrontendAssignment] skip register: assignment is null or has no id");
            return;
        }

        try {
            assignment = assignmentRepository.findById(assignment.getId()).orElse(assignment);
            if (!canRegisterFrontendAssignment()) {
                rollbackAssignmentAllocation(assignment, assignment.getAssignedVehicle(),
                        new ArrayList<>(assignment.getShipmentItems()),
                        "Simulation stopped before frontend registration");
                return;
            }
            if (!hasTransportMetrics(assignment) && !rebuildTransportMetricsStrict(assignment)) {
                rollbackAssignmentAllocation(assignment, assignment.getAssignedVehicle(),
                        new ArrayList<>(assignment.getShipmentItems()),
                        "Strict route planning failed before frontend registration");
                return;
            }
            if (!canRegisterFrontendAssignment()) {
                rollbackAssignmentAllocation(assignment, assignment.getAssignedVehicle(),
                        new ArrayList<>(assignment.getShipmentItems()),
                        "Simulation stopped before frontend registration");
                return;
            }

            AssignmentBriefDTO brief = new AssignmentBriefDTO();
            brief.setAssignmentId(assignment.getId());
            brief.setStatus(assignment.getStatus() != null ? assignment.getStatus().toString() : "WAITING");
            brief.setCreatedTime(assignment.getCreatedTime());
            brief.setStartTime(assignment.getStartTime());
            brief.setDrawn(false);

            boolean hasNodes = assignment.getNodes() != null && !assignment.getNodes().isEmpty();
            brief.setVrp(hasNodes);

            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle != null) {
                brief.setVehicleId(vehicle.getId());
                brief.setLicensePlate(vehicle.getLicensePlate());
                brief.setVehicleStatus(vehicle.getCurrentStatus() != null
                        ? vehicle.getCurrentStatus().toString()
                        : "IDLE");
                brief.setCurrentLoad(vehicle.getCurrentLoad());
                brief.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());
                brief.setCurrentVolume(vehicle.getCurrentVolumn());
                brief.setMaxVolumeCapacity(vehicle.getCargoVolume());

                if (vehicle.getCurrentPOI() != null) {
                    brief.setVehicleStartLng(vehicle.getCurrentPOI().getLongitude().doubleValue());
                    brief.setVehicleStartLat(vehicle.getCurrentPOI().getLatitude().doubleValue());
                } else if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
                    brief.setVehicleStartLng(vehicle.getCurrentLongitude().doubleValue());
                    brief.setVehicleStartLat(vehicle.getCurrentLatitude().doubleValue());
                }
            }

            POI startPOI = assignment.getOriginPOI();
            POI endPOI = assignment.getDestPOI();

            if (startPOI == null || endPOI == null) {
                List<AssignmentNode> orderedNodes = assignment.getNodes() != null
                        ? new ArrayList<>(assignment.getNodes())
                        : new ArrayList<>();
                orderedNodes.sort(Comparator.comparing(
                        AssignmentNode::getSequenceIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ));

                if (startPOI == null && !orderedNodes.isEmpty()) {
                    startPOI = orderedNodes.get(0).getPoi();
                }
                if (endPOI == null && !orderedNodes.isEmpty()) {
                    endPOI = orderedNodes.get(orderedNodes.size() - 1).getPoi();
                }
            }

            if (startPOI != null) {
                brief.setStartPOIId(startPOI.getId());
                brief.setStartPOIName(startPOI.getName());
                brief.setStartLng(startPOI.getLongitude());
                brief.setStartLat(startPOI.getLatitude());
                brief.setStartPOIType(startPOI.getPoiType() != null ? startPOI.getPoiType().toString() : null);
            }

            if (endPOI != null) {
                brief.setEndPOIId(endPOI.getId());
                brief.setEndPOIName(endPOI.getName());
                brief.setEndLng(endPOI.getLongitude());
                brief.setEndLat(endPOI.getLatitude());
                brief.setEndPOIType(endPOI.getPoiType() != null ? endPOI.getPoiType().toString() : null);
            }

            Set<ShipmentItem> items = assignment.getShipmentItems();
            if (items != null && !items.isEmpty()) {
                ShipmentItem firstItem = items.iterator().next();
                brief.setGoodsName(firstItem.getName());
                brief.setQuantity(firstItem.getQty());

                if (firstItem.getGoods() != null) {
                    Goods goods = firstItem.getGoods();
                    brief.setGoodsWeightPerUnit(goods.getWeightPerUnit());
                    brief.setGoodsVolumePerUnit(goods.getVolumePerUnit());
                }

                if (firstItem.getShipment() != null) {
                    brief.setShipmentRefNo(firstItem.getShipment().getRefNo());
                }
            }

            if (hasNodes) {
                List<AssignmentNode> orderedNodes = new ArrayList<>(assignment.getNodes());
                orderedNodes.sort(Comparator.comparing(
                        AssignmentNode::getSequenceIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ));

                List<AssignmentBriefDTO.NodeDTO> nodeDTOs = new ArrayList<>();
                for (AssignmentNode node : orderedNodes) {
                    if (node == null || node.getPoi() == null || node.getActionType() == null) {
                        continue;
                    }

                    AssignmentBriefDTO.NodeDTO nodeDTO = new AssignmentBriefDTO.NodeDTO();
                    nodeDTO.setSequenceIndex(node.getSequenceIndex());
                    nodeDTO.setPoiId(node.getPoi().getId());
                    nodeDTO.setPoiName(node.getPoi().getName());
                    nodeDTO.setPoiType(node.getPoi().getPoiType() != null ? node.getPoi().getPoiType().name() : null);
                    nodeDTO.setLng(node.getPoi().getLongitude());
                    nodeDTO.setLat(node.getPoi().getLatitude());
                    nodeDTO.setActionType(node.getActionType().name());
                    nodeDTO.setWeightDelta(node.getWeightDelta());
                    nodeDTO.setVolumeDelta(node.getVolumeDelta());
                    nodeDTOs.add(nodeDTO);
                }
                brief.setNodes(nodeDTOs);
            }

            if (startPOI != null && endPOI != null) {
                brief.setPairId(generatePairKey(startPOI, endPOI));
            } else {
                brief.setPairId("ASSIGNMENT_" + assignment.getId());
            }

            assignmentBriefMap.put(assignment.getId(), brief);

            AssignmentStatusDTO status = new AssignmentStatusDTO(
                    assignment.getId(),
                    brief.getPairId(),
                    vehicle != null ? vehicle.getId() : null,
                    null
            );
            assignmentStatusMap.put("ASSIGNMENT_" + assignment.getId(), status);

            System.out.println("[FrontendAssignment] registered assignment " + assignment.getId()
                    + ", vrp=" + hasNodes
                    + ", nodes=" + (brief.getNodes() != null ? brief.getNodes().size() : 0));
        } catch (Exception e) {
            System.err.println("[FrontendAssignment] register failed for assignment "
                    + assignment.getId() + ": " + e.getMessage());
        }
    }

    private void rebuildTransportMetricsSafely(Assignment assignment) {
        try {
            if (assignment != null && assignment.getId() != null) {
                transportMetricsService.rebuildMetricsForAssignment(assignment.getId());
            }
        } catch (Exception e) {
            logger.warn("Transport metrics rebuild failed for assignment {}: {}",
                    assignment != null ? assignment.getId() : null,
                    e.getMessage());
        }
    }

    // 创建 AssignmentBriefDTO
    private boolean rebuildTransportMetricsStrict(Assignment assignment) {
        if (shouldAbortSimulationWork()) {
            return false;
        }
        if (assignment == null || assignment.getId() == null) {
            return false;
        }
        return transportMetricsService.rebuildMetricsForAssignmentStrict(assignment.getId());
    }

    private boolean hasTransportMetrics(Assignment assignment) {
        return assignment != null
                && assignment.getTotalDistanceMeters() != null
                && assignment.getTotalDrivingSeconds() != null;
    }

    private void rollbackAssignmentAllocation(
            Assignment assignment,
            Vehicle vehicle,
            Collection<ShipmentItem> items,
            String reason
    ) {
        Long assignmentId = assignment != null ? assignment.getId() : null;
        try {
            transportLifecycleService.rollbackAssignmentForRetry(
                    assignment,
                    vehicle,
                    items,
                    reason
            );

            if (assignmentId != null) {
                assignmentBriefMap.remove(assignmentId);
                assignmentStatusMap.entrySet().removeIf(entry ->
                        entry.getValue() != null && assignmentId.equals(entry.getValue().getAssignmentId()));
            }

            logger.warn("Rolled back assignment allocation. assignmentId={}, reason={}", assignmentId, reason);
        } catch (Exception e) {
            logger.error("Failed to rollback assignment allocation. assignmentId={}, reason={}",
                    assignmentId, reason, e);
        }
    }

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

            brief.setCurrentVolume(vehicle.getCurrentVolumn());
            brief.setMaxVolumeCapacity(vehicle.getCargoVolume());

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
        brief.setStartPOIType(startPOI.getPoiType() != null ? startPOI.getPoiType().toString() : null);

        // 终点信息
        brief.setEndPOIId(endPOI.getId());
        brief.setEndPOIName(endPOI.getName());
        brief.setEndLng(endPOI.getLongitude());
        brief.setEndLat(endPOI.getLatitude());
        brief.setEndPOIType(endPOI.getPoiType() != null ? endPOI.getPoiType().toString() : null);

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
            dto.setStartPOIType(startPOI.getPoiType() != null ? startPOI.getPoiType().toString() : null);
        }

        // 终点信息
        POI endPOI = route.getEndPOI();
        if (endPOI != null) {
            dto.setEndPOIId(endPOI.getId());
            dto.setEndPOIName(endPOI.getName());
            dto.setEndLng(endPOI.getLongitude());
            dto.setEndLat(endPOI.getLatitude());
            dto.setEndPOIType(endPOI.getPoiType() != null ? endPOI.getPoiType().toString() : null);
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
                pair.setStartPOIType(freshStartPOI.getPoiType() != null ? freshStartPOI.getPoiType().toString() : null);

                pair.setEndPOIId(freshEndPOI.getId());
                pair.setEndPOIName(freshEndPOI.getName());
                pair.setEndLng(freshEndPOI.getLongitude());
                pair.setEndLat(freshEndPOI.getLatitude());
                pair.setEndPOIType(freshEndPOI.getPoiType() != null ? freshEndPOI.getPoiType().toString() : null);

                // 获取货物信息（通过Enrollment）
                Optional<Enrollment> enrollment = enrollmentRepository
                        .findByPoiAndGoods(freshStartPOI, currentGoods);
                enrollment.ifPresent(e -> {
                    pair.setGoodsName(currentGoods.getName());
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
        return getNewAssignments();
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

    private AssignmentBriefDTO refreshAssignmentBriefRuntimeFields(AssignmentBriefDTO dto) {
        if (dto == null || dto.getAssignmentId() == null) {
            return dto;
        }

        assignmentRepository.findById(dto.getAssignmentId()).ifPresent(assignment -> {
            dto.setStatus(assignment.getStatus() != null ? assignment.getStatus().toString() : "UNKNOWN");
            dto.setStartTime(assignment.getStartTime());

            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle != null) {
                dto.setVehicleId(vehicle.getId());
                dto.setLicensePlate(vehicle.getLicensePlate());
                dto.setVehicleStatus(vehicle.getCurrentStatus() != null
                        ? vehicle.getCurrentStatus().toString()
                        : "IDLE");
                dto.setCurrentLoad(vehicle.getCurrentLoad());
                dto.setCurrentVolume(vehicle.getCurrentVolumn());
                dto.setMaxLoadCapacity(vehicle.getMaxLoadCapacity());
                dto.setMaxVolumeCapacity(vehicle.getCargoVolume());
                dto.setVehicleCurrentLon(vehicle.getCurrentLongitude() != null
                        ? vehicle.getCurrentLongitude().doubleValue()
                        : null);
                dto.setVehicleCurrentLat(vehicle.getCurrentLatitude() != null
                        ? vehicle.getCurrentLatitude().doubleValue()
                        : null);
            }
        });

        return dto;
    }

    /**
     * 获取当前活跃的 Assignment 列表
     */
    public List<AssignmentBriefDTO> getActiveAssignments() {
        return assignmentBriefMap.values().stream()
                .map(this::refreshAssignmentBriefRuntimeFields)
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
                .map(this::refreshAssignmentBriefRuntimeFields)
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
