package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.ProcessingChainStatsDTO;
import org.example.roadsimulation.dto.ProcessingItemStatusDTO;
import org.example.roadsimulation.dto.ProcessingOrderStatusDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.ProcessingChainServiceV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 加工链服务实现 V3（简化版：直接使用 Shipment）
 */
@Service
@Transactional
public class ProcessingChainServiceV3Impl implements ProcessingChainServiceV2 {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingChainServiceV3Impl.class);

    @Autowired
    private ProcessingChainRepository processingChainRepository;

    @Autowired
    private ProcessingStageRepository processingStageRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private POIRepository poiRepository;

    @Autowired
    private SimulationContext simulationContext;

    @Autowired
    private RouteServiceImpl routeServiceImpl;

    // ================= 加工链管理 =================

    @Override
    public ProcessingChain createChain(ProcessingChain chain) {
        if (chain.getChainCode() != null && processingChainRepository.existsByChainCode(chain.getChainCode())) {
            throw new IllegalArgumentException("加工链编码已存在：" + chain.getChainCode());
        }

        if (chain.getStages() != null && !chain.getStages().isEmpty()) {
            int totalTime = chain.getStages().stream()
                    .mapToInt(ProcessingStage::getProcessingTimeMinutes)
                    .sum();
            chain.setTotalProcessingTimeMinutes(totalTime);
        }

        ProcessingChain saved = processingChainRepository.save(chain);
        logger.info("创建加工链：chainCode={}, chainName={}, stages={}",
                chain.getChainCode(), chain.getChainName(),
                chain.getStages() != null ? chain.getStages().size() : 0);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessingChain> getChainById(Long id) {
        return processingChainRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessingChain> getAllChains() {
        return processingChainRepository.findAll();
    }

    @Override
    public ProcessingChain updateChainStatus(Long id, ProcessingChain.ChainStatus status) {
        ProcessingChain chain = processingChainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + id));
        chain.setStatus(status);
        return processingChainRepository.save(chain);
    }

    @Override
    public void deleteChain(Long id) {
        ProcessingChain chain = processingChainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + id));

        long count = shipmentRepository.countByProcessingChainId(id);
        if (count > 0) {
            throw new IllegalStateException("加工链下存在 " + count + " 个加工运单，无法删除");
        }

        processingChainRepository.delete(chain);
        logger.info("删除加工链：id={}, chainCode={}", id, chain.getChainCode());
    }

    // ================= Y 形加工链合并管理 =================

    @Override
    public Shipment createMergeShipment(List<Long> upstreamShipmentIds, Long downstreamChainId, String createdBy) {
        // 1. 验证所有上游运单已完成
        List<Shipment> upstreamShipments = shipmentRepository.findAllById(upstreamShipmentIds);
        
        if (upstreamShipments.size() != upstreamShipmentIds.size()) {
            throw new IllegalArgumentException("部分上游运单不存在");
        }
        
        for (Shipment shipment : upstreamShipments) {
            if (shipment.getProcessingStatus() != Shipment.ProcessingStatus.COMPLETED) {
                throw new IllegalStateException(
                    "上游运单 " + shipment.getRefNo() + " 尚未完成，无法创建合并运单，当前状态：" + shipment.getProcessingStatus()
                );
            }
        }
        
        // 2. 获取下游加工链
        ProcessingChain downstreamChain = processingChainRepository.findById(downstreamChainId)
                .orElseThrow(() -> new RuntimeException("下游加工链不存在"));
        
        if (downstreamChain.getStatus() != ProcessingChain.ChainStatus.ACTIVE) {
            throw new IllegalStateException("下游加工链未激活：" + downstreamChain.getChainName());
        }
        
        if (downstreamChain.getStages().isEmpty()) {
            throw new IllegalStateException("下游加工链没有工序");
        }
        
        // 3. 计算合并后的总输入重量（所有上游运单的实际输出重量之和）
        double totalInputWeight = upstreamShipments.stream()
                .mapToDouble(Shipment::getActualOutputWeight)
                .sum();
        
        // 4. 创建合并运单
        Shipment mergeShipment = new Shipment();
        mergeShipment.setRefNo(generateProcRefNo());
        mergeShipment.setCargoType("PROCESSING");
        mergeShipment.setUpdatedBy(createdBy != null ? createdBy : "system");
        
        // 起运地 = 第一道工序 POI
        ProcessingStage firstStage = downstreamChain.getStages().get(0);
        mergeShipment.setOriginPOI(firstStage.getProcessingPOI());
        
        // 目的地 = 最后一道工序 POI
        ProcessingStage lastStage = downstreamChain.getStages().get(downstreamChain.getStages().size() - 1);
        mergeShipment.setDestPOI(lastStage.getProcessingPOI());
        
        mergeShipment.setStatus(Shipment.ShipmentStatus.CREATED);
        mergeShipment.setTotalWeight(totalInputWeight);
        
        // 设置加工特有字段
        mergeShipment.setProcessingShipment(true);
        mergeShipment.setProcessingChain(downstreamChain);
        mergeShipment.setChainCode(downstreamChain.getChainCode());
        mergeShipment.setChainName(downstreamChain.getChainName());
        mergeShipment.setExpectedYieldRate(downstreamChain.getYieldRate());
        mergeShipment.setExpectedOutputWeight(totalInputWeight * downstreamChain.getYieldRate());
        mergeShipment.setProcessingStatus(Shipment.ProcessingStatus.PENDING);
        
        // 设置上游运单关联
        mergeShipment.setUpstreamShipmentIds(new HashSet<>(upstreamShipmentIds));
        
        // 计算预期完成时间
        int totalMinutes = downstreamChain.getTotalProcessingTimeMinutes();
        mergeShipment.setProcessingExpectedFinishTime(
            simulationContext.getCurrentSimTime().plusMinutes(totalMinutes)
        );
        
        Shipment savedShipment = shipmentRepository.save(mergeShipment);
        
        // 5. 为下游链的每个工序创建物料项
        for (ProcessingStage stage : downstreamChain.getStages()) {
            createShipmentItem(savedShipment, stage, totalInputWeight, createdBy);
        }
        
        logger.info("创建合并运单：refNo={}, upstreamCount={}, totalWeight={}t, downstreamChain={}",
                mergeShipment.getRefNo(), upstreamShipments.size(), totalInputWeight, downstreamChain.getChainName());
        
        return savedShipment;
    }

    @Override
    public void checkAndAutoCreateMergeShipment(Long completedShipmentId) {
        Shipment completedShipment = shipmentRepository.findById(completedShipmentId)
                .orElseThrow(() -> new RuntimeException("运单不存在：" + completedShipmentId));
        
        // 查找所有以当前运单所在加工链为前驱的下游加工链
        List<ProcessingChain> downstreamChains = processingChainRepository
                .findByPredecessorChainId(completedShipment.getProcessingChain().getId());
        
        for (ProcessingChain downstreamChain : downstreamChains) {
            // 检查下游链的所有前驱链是否都有完成的运单
            Set<Long> predecessorChainIds = downstreamChain.getPredecessorChainIds();
            
            // 查询所有前驱链的已完成运单
            List<Shipment> allUpstreamShipments = shipmentRepository
                    .findByProcessingChainIdInAndProcessingStatus(
                        new ArrayList<>(predecessorChainIds),
                        Shipment.ProcessingStatus.COMPLETED
                    );
            
            // 按加工链 ID 分组，确保每个前驱链至少有一个完成的运单
            Set<Long> completedChainIds = allUpstreamShipments.stream()
                    .map(s -> s.getProcessingChain().getId())
                    .collect(java.util.stream.Collectors.toSet());
            
            // 如果所有前驱链都有完成的运单，创建合并运单
            if (completedChainIds.containsAll(predecessorChainIds)) {
                // 每个前驱链取一个最新的完成运单
                List<Long> upstreamShipmentIds = allUpstreamShipments.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                            s -> s.getProcessingChain().getId()
                        ))
                        .values()
                        .stream()
                        .map(list -> list.stream()
                            .max(java.util.Comparator.comparing(Shipment::getProcessingActualFinishTime))
                            .map(Shipment::getId)
                            .orElse(null)
                        )
                        .filter(id -> id != null)
                        .toList();
                
                if (!upstreamShipmentIds.isEmpty()) {
                    // 检查是否已经创建过合并运单
                    List<Shipment> existingMergeShipments = shipmentRepository
                            .findByProcessingChainId(downstreamChain.getId());
                    
                    boolean alreadyExists = existingMergeShipments.stream()
                            .anyMatch(s -> s.isMergeShipment() && 
                                   new HashSet<>(s.getUpstreamShipmentIds()).containsAll(upstreamShipmentIds));
                    
                    if (!alreadyExists) {
                        createMergeShipment(upstreamShipmentIds, downstreamChain.getId(), "system");
                    }
                }
            }
        }
    }

    // ================= 加工工序管理 =================

    @Override
    public ProcessingStage createStage(Long chainId, ProcessingStage stage) {
        ProcessingChain chain = processingChainRepository.findById(chainId)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + chainId));

        boolean exists = chain.getStages().stream()
                .anyMatch(s -> s.getStageOrder().equals(stage.getStageOrder()));
        if (exists) {
            throw new IllegalArgumentException("工序顺序 " + stage.getStageOrder() + " 已存在");
        }

        stage.setProcessingChain(chain);
        ProcessingStage saved = processingStageRepository.save(stage);

        int totalTime = chain.getStages().stream()
                .mapToInt(ProcessingStage::getProcessingTimeMinutes)
                .sum();
        chain.setTotalProcessingTimeMinutes(totalTime);
        processingChainRepository.save(chain);

        logger.info("创建加工工序：chainId={}, stageName={}, order={}",
                chainId, stage.getStageName(), stage.getStageOrder());

        return saved;
    }

    @Override
    public ProcessingStage updateStage(Long stageId, ProcessingStage stageDetails) {
        ProcessingStage stage = processingStageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("工序不存在：" + stageId));

        if (stageDetails.getStageName() != null) {
            stage.setStageName(stageDetails.getStageName());
        }
        if (stageDetails.getProcessingTimeMinutes() != null) {
            stage.setProcessingTimeMinutes(stageDetails.getProcessingTimeMinutes());
        }

        return processingStageRepository.save(stage);
    }

    @Override
    public void deleteStage(Long stageId) {
        long count = shipmentItemRepository.countByStageId(stageId);
        if (count > 0) {
            throw new IllegalStateException("工序下存在 " + count + " 个加工物料项，无法删除");
        }
        processingStageRepository.deleteById(stageId);
    }

    // ================= 加工运单管理 =================

    @Override
    public Shipment createProcessingShipment(Long chainId, Double inputWeight, String createdBy) {
        // 1. 获取加工链
        ProcessingChain chain = processingChainRepository.findById(chainId)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + chainId));

        if (chain.getStatus() != ProcessingChain.ChainStatus.ACTIVE) {
            throw new IllegalStateException("加工链未激活：" + chain.getChainName());
        }

        if (chain.getStages().isEmpty()) {
            throw new IllegalStateException("加工链没有工序");
        }

        // 2. 创建 Shipment (直接在 Shipment 表中存储加工信息)
        Shipment shipment = new Shipment();
        shipment.setRefNo(generateProcRefNo());
        shipment.setCargoType("PROCESSING");
        shipment.setUpdatedBy(createdBy != null ? createdBy : "system");

        // 起运地 = 第一道工序 POI
        ProcessingStage firstStage = chain.getStages().get(0);
        shipment.setOriginPOI(firstStage.getProcessingPOI());

        // 目的地 = 最后一道工序 POI
        ProcessingStage lastStage = chain.getStages().get(chain.getStages().size() - 1);
        shipment.setDestPOI(lastStage.getProcessingPOI());

        shipment.setStatus(Shipment.ShipmentStatus.CREATED);
        shipment.setTotalWeight(inputWeight);

        // 设置加工特有字段
        shipment.setProcessingShipment(true);
        shipment.setProcessingChain(chain);
        shipment.setChainCode(chain.getChainCode());
        shipment.setChainName(chain.getChainName());
        shipment.setExpectedYieldRate(chain.getYieldRate());
        shipment.setExpectedOutputWeight(inputWeight * chain.getYieldRate());
        shipment.setProcessingStatus(Shipment.ProcessingStatus.PENDING);

        // 计算预期完成时间
        int totalMinutes = chain.getTotalProcessingTimeMinutes();
        shipment.setProcessingExpectedFinishTime(
            simulationContext.getCurrentSimTime().plusMinutes(totalMinutes)
        );

        Shipment savedShipment = shipmentRepository.save(shipment);

        // 3. 为每道工序创建 ShipmentItem
        for (ProcessingStage stage : chain.getStages()) {
            createShipmentItem(savedShipment, stage, inputWeight, createdBy);
        }

        logger.info("创建加工运单：refNo={}, chain={}, inputWeight={}t, stages={}",
                shipment.getRefNo(), chain.getChainName(), inputWeight, chain.getStages().size());

        return savedShipment;
    }

    /**
     * 创建加工物料项
     */
    private void createShipmentItem(Shipment shipment, ProcessingStage stage, 
                                     Double inputWeight, String createdBy) {
        ShipmentItem item = new ShipmentItem();
        item.setShipment(shipment);
        item.setName(stage.getStageName() + "_物料");
        item.setSku(stage.getOutputGoodsSku());
        item.setWeight(inputWeight);
        item.setVolume(0.0);
        item.setQty(1);
        item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
        item.setUpdatedBy(createdBy != null ? createdBy : "system");

        // 设置加工特有字段
        item.setStage(stage);
        item.setStageOrder(stage.getStageOrder());
        item.setStageName(stage.getStageName());
        item.setProcessingPOI(stage.getProcessingPOI());
        item.setProcessedWeight(inputWeight);
        item.setProcessingStatus(ShipmentItem.ProcessingItemStatus.WAITING);
        item.setProgressPercent(0);

        shipmentItemRepository.save(item);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> getProcessingShipmentById(Long shipmentId) {
        return shipmentRepository.findById(shipmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByChainId(Long chainId) {
        return shipmentRepository.findByProcessingChainId(chainId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> getShipments(Pageable pageable) {
        return shipmentRepository.findAll(pageable);
    }

    // ================= 加工执行控制 =================

    @Override
    public void startProcessing(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("加工运单不存在：" + shipmentId));

        if (shipment.getProcessingStatus() != Shipment.ProcessingStatus.PENDING) {
            throw new IllegalStateException("运单状态不允许开始加工：" + shipment.getProcessingStatus());
        }

        LocalDateTime simNow = simulationContext.getCurrentSimTime();

        // 更新运单状态
        shipment.setProcessingStatus(Shipment.ProcessingStatus.IN_PROCESS);
        shipment.setProcessingStartTime(simNow);
        shipment.setStatus(Shipment.ShipmentStatus.IN_TRANSIT);

        // 启动第一道工序
        List<ShipmentItem> items = shipmentItemRepository.findByShipmentIdOrderByStageOrder(shipmentId);
        if (!items.isEmpty()) {
            ShipmentItem firstItem = items.get(0);
            firstItem.setProcessingStatus(ShipmentItem.ProcessingItemStatus.PROCESSING);
            firstItem.setProcessingStartTime(simNow);

            // 创建 Assignment (运入任务)
            Assignment assignment = createAssignmentForStage(
                    null,
                    firstItem.getProcessingPOI(),
                    firstItem,
                    simNow
            );

            firstItem.setInboundAssignment(assignment);
            firstItem.setStatus(ShipmentItem.ShipmentItemStatus.ASSIGNED);
            shipmentItemRepository.save(firstItem);
        }

        shipmentRepository.save(shipment);
        logger.info("开始加工运单：refNo={}, firstStage={}, poi={}",
                shipment.getRefNo(),
                items.isEmpty() ? "N/A" : items.get(0).getStageName(),
                items.isEmpty() ? null : items.get(0).getProcessingPOI().getName());
    }

    @Override
    public void cancelProcessing(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("加工运单不存在：" + shipmentId));

        if (shipment.getProcessingStatus() == Shipment.ProcessingStatus.COMPLETED ||
            shipment.getProcessingStatus() == Shipment.ProcessingStatus.CANCELLED) {
            throw new IllegalStateException("运单已完成或已取消，无法再次取消");
        }

        LocalDateTime simNow = simulationContext.getCurrentSimTime();

        shipment.setProcessingStatus(Shipment.ProcessingStatus.CANCELLED);
        shipment.setProcessingActualFinishTime(simNow);
        shipment.setStatus(Shipment.ShipmentStatus.CANCELLED);

        // 取消所有未完成的加工物料项
        List<ShipmentItem> items = shipmentItemRepository.findByShipmentIdOrderByStageOrder(shipmentId);
        for (ShipmentItem item : items) {
            if (item.getProcessingStatus() != ShipmentItem.ProcessingItemStatus.COMPLETED) {
                item.setProcessingStatus(ShipmentItem.ProcessingItemStatus.CANCELLED);
            }
        }
        shipmentItemRepository.saveAll(items);

        logger.info("取消加工运单：refNo={}", shipment.getRefNo());
    }

    @Override
    public void updateProcessingProgress(LocalDateTime simNow, int minutesPerLoop) {
        List<Shipment> shipments = shipmentRepository.findByProcessingStatus(Shipment.ProcessingStatus.IN_PROCESS);

        for (Shipment shipment : shipments) {
            processShipment(shipment, simNow, minutesPerLoop);
        }
    }

    /**
     * 处理单个运单的加工进度
     */
    private void processShipment(Shipment shipment, LocalDateTime simNow, int minutesPerLoop) {
        List<ShipmentItem> items = shipmentItemRepository.findByShipmentIdOrderByStageOrder(shipment.getId());

        for (ShipmentItem item : items) {
            if (item.getProcessingStatus() == ShipmentItem.ProcessingItemStatus.PROCESSING) {
                if (item.getProcessingStartTime() != null) {
                    Duration elapsed = Duration.between(item.getProcessingStartTime(), simNow);
                    int totalMinutes = item.getStage().getProcessingTimeMinutes();

                    if (totalMinutes > 0) {
                        int progress = Math.min(100, (int) (elapsed.toMinutes() * 100 / totalMinutes));
                        item.setProgressPercent(progress);

                        if (progress >= 100) {
                            completeStage(item, simNow);
                        }
                    } else {
                        item.setProgressPercent(100);
                        completeStage(item, simNow);
                    }
                }
                break;
            }
        }

        checkAndCompleteShipment(shipment, simNow);
    }

    /**
     * 检查并完成运单
     */
    private void checkAndCompleteShipment(Shipment shipment, LocalDateTime simNow) {
        List<ShipmentItem> items = shipmentItemRepository.findByShipmentIdOrderByStageOrder(shipment.getId());

        boolean allCompleted = items.stream()
                .allMatch(i -> i.getProcessingStatus() == ShipmentItem.ProcessingItemStatus.COMPLETED);

        if (allCompleted) {
            shipment.setProcessingStatus(Shipment.ProcessingStatus.COMPLETED);
            shipment.setProcessingActualFinishTime(simNow);
            shipment.setActualOutputWeight(shipment.getExpectedOutputWeight());
            shipment.setStatus(Shipment.ShipmentStatus.DELIVERED);
            shipmentRepository.save(shipment);

            logger.info("加工运单完成：refNo={}, outputWeight={}t",
                    shipment.getRefNo(), shipment.getActualOutputWeight());
            
            // 检查并自动创建合并运单（Y 形加工链）
            checkAndAutoCreateMergeShipment(shipment.getId());
        }
    }

    @Override
    public void completeStage(ShipmentItem currentStageItem, LocalDateTime simNow) {
        currentStageItem.setProcessingStatus(ShipmentItem.ProcessingItemStatus.COMPLETED);
        currentStageItem.setProcessingEndTime(simNow);
        currentStageItem.setProgressPercent(100);
        currentStageItem.setStatus(ShipmentItem.ShipmentItemStatus.DELIVERED);
        shipmentItemRepository.save(currentStageItem);

        logger.info("工序完成：shipmentId={}, stageName={}, poi={}",
                currentStageItem.getShipment().getId(),
                currentStageItem.getStageName(),
                currentStageItem.getProcessingPOI().getName());

        // 查找下一道工序
        ShipmentItem nextStageItem = shipmentItemRepository
                .findByShipmentIdAndStageOrder(
                    currentStageItem.getShipment().getId(),
                    currentStageItem.getStageOrder() + 1
                )
                .orElse(null);

        if (nextStageItem != null) {
            Assignment transportAssignment = createAssignmentForStage(
                    currentStageItem.getProcessingPOI(),
                    nextStageItem.getProcessingPOI(),
                    nextStageItem,
                    simNow
            );

            currentStageItem.setOutboundAssignment(transportAssignment);
            nextStageItem.setInboundAssignment(transportAssignment);
            shipmentItemRepository.save(currentStageItem);
            shipmentItemRepository.save(nextStageItem);

            nextStageItem.setProcessingStatus(ShipmentItem.ProcessingItemStatus.PROCESSING);
            nextStageItem.setProcessingStartTime(simNow);
            nextStageItem.setStatus(ShipmentItem.ShipmentItemStatus.IN_TRANSIT);
            nextStageItem.setAssignment(transportAssignment);
            shipmentItemRepository.save(nextStageItem);

            logger.info("启动下道工序：shipmentId={}, nextStage={}, nextPOI={}",
                    currentStageItem.getShipment().getId(),
                    nextStageItem.getStageName(),
                    nextStageItem.getProcessingPOI().getName());
        } else {
            checkAndCompleteShipment(currentStageItem.getShipment(), simNow);
        }
    }

    /**
     * 创建工序间的运输任务
     */
    private Assignment createAssignmentForStage(POI fromPOI, POI toPOI, 
                                                  ShipmentItem item, LocalDateTime simNow) {
        Long startPoiId = fromPOI != null ? fromPOI.getId() : 
            item.getShipment().getOriginPOI().getId();
        Long endPoiId = toPOI.getId();

        Route route;
        List<Route> existingRoutes = routeServiceImpl.getRoutesByStartPoi(startPoiId)
                .stream()
                .filter(dto -> dto.getEndPoiId().equals(endPoiId))
                .map(this::convertToEntity)
                .toList();

        if (!existingRoutes.isEmpty()) {
            route = existingRoutes.get(0);
        } else {
            route = new Route();
            route.setRouteCode("ROUTE_PROC_" + System.currentTimeMillis());
            route.setName("加工运输路线");
            route.setStartPOI(poiRepository.findById(startPoiId).orElse(toPOI));
            route.setEndPOI(toPOI);
            route.setDistance(10.0);
            route.setEstimatedTime(30.0);
            route.setStatus(Route.RouteStatus.ACTIVE);
            route = routeRepository.save(route);
        }

        Assignment assignment = new Assignment(item, route);
        assignment.setStatus(Assignment.AssignmentStatus.WAITING);
        assignment.setCurrentActionIndex(0);
        assignment.setCreatedTime(simNow);

        List<Long> actionIds = new ArrayList<>();
        if (fromPOI != null) {
            actionIds.add(fromPOI.getId());
        }
        actionIds.add(toPOI.getId());
        assignment.setActionLine(actionIds);

        return assignmentRepository.save(assignment);
    }

    private Route convertToEntity(org.example.roadsimulation.dto.RouteResponseDTO dto) {
        Route route = new Route();
        route.setId(dto.getId());
        route.setRouteCode(dto.getRouteCode());
        route.setName(dto.getName());
        route.setDistance(dto.getDistance());
        route.setEstimatedTime(dto.getEstimatedTime());
        return route;
    }

    // ================= 加工物料项管理 =================

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentItem> getProcessingItemsByShipmentId(Long shipmentId) {
        return shipmentItemRepository.findByShipmentIdOrderByStageOrder(shipmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentItem> getProcessingItemById(Long itemId) {
        return shipmentItemRepository.findById(itemId);
    }

    @Override
    public ShipmentItem updateItemProgress(Long itemId, Integer progressPercent) {
        ShipmentItem item = shipmentItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("加工物料项不存在：" + itemId));
        item.setProgressPercent(progressPercent);
        return shipmentItemRepository.save(item);
    }

    // ================= 查询与统计 =================

    @Override
    @Transactional(readOnly = true)
    public ProcessingOrderStatusDTO getShipmentStatus(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("加工运单不存在：" + shipmentId));

        ProcessingOrderStatusDTO dto = new ProcessingOrderStatusDTO();
        dto.setOrderId(shipmentId);
        dto.setOrderNo(shipment.getRefNo());
        dto.setStatus(shipment.getProcessingStatus().name());
        dto.setStartTime(shipment.getProcessingStartTime());
        dto.setExpectedFinishTime(shipment.getProcessingExpectedFinishTime());
        dto.setActualFinishTime(shipment.getProcessingActualFinishTime());

        List<ShipmentItem> items = shipmentItemRepository.findByShipmentIdOrderByStageOrder(shipmentId);
        ShipmentItem currentItem = items.stream()
                .filter(i -> i.getProcessingStatus() == ShipmentItem.ProcessingItemStatus.PROCESSING)
                .findFirst()
                .orElse(null);

        if (currentItem != null) {
            dto.setCurrentStageName(currentItem.getStageName());
            dto.setCurrentStageIndex(currentItem.getStageOrder() - 1);
            dto.setOverallProgress(currentItem.getProgressPercent());
        } else if (shipment.getProcessingStatus() == Shipment.ProcessingStatus.COMPLETED) {
            dto.setOverallProgress(100);
        } else {
            dto.setOverallProgress(0);
        }

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingChainStatsDTO getChainStats(Long chainId) {
        ProcessingChain chain = processingChainRepository.findById(chainId)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + chainId));

        ProcessingChainStatsDTO stats = new ProcessingChainStatsDTO();
        stats.setChainId(chainId);
        stats.setChainName(chain.getChainName());

        List<Shipment> shipments = shipmentRepository.findByProcessingChainId(chainId);

        stats.setTotalOrders(shipments.size());
        stats.setPendingOrders((int) shipments.stream()
                .filter(s -> s.getProcessingStatus() == Shipment.ProcessingStatus.PENDING).count());
        stats.setInProcessOrders((int) shipments.stream()
                .filter(s -> s.getProcessingStatus() == Shipment.ProcessingStatus.IN_PROCESS).count());
        stats.setCompletedOrders((int) shipments.stream()
                .filter(s -> s.getProcessingStatus() == Shipment.ProcessingStatus.COMPLETED).count());
        stats.setCancelledOrders((int) shipments.stream()
                .filter(s -> s.getProcessingStatus() == Shipment.ProcessingStatus.CANCELLED).count());

        stats.setTotalInputWeight(shipments.stream()
                .filter(s -> s.getExpectedOutputWeight() != null)
                .mapToDouble(s -> s.getExpectedOutputWeight() / (s.getExpectedYieldRate() != null ? s.getExpectedYieldRate() : 1.0))
                .sum());

        stats.setTotalOutputWeight(shipments.stream()
                .filter(s -> s.getActualOutputWeight() != null)
                .mapToDouble(Shipment::getActualOutputWeight)
                .sum());

        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingItemStatusDTO getItemStatus(Long itemId) {
        ShipmentItem item = shipmentItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("加工物料项不存在：" + itemId));

        ProcessingItemStatusDTO dto = new ProcessingItemStatusDTO();
        dto.setId(item.getId());
        dto.setItemId(itemId);
        dto.setStageName(item.getStageName());
        dto.setStageOrder(item.getStageOrder());
        dto.setProcessingStatus(item.getProcessingStatus().name());
        dto.setProgressPercent(item.getProgressPercent());
        dto.setProcessedWeight(item.getProcessedWeight());
        dto.setProcessingStartTime(item.getProcessingStartTime());
        dto.setProcessingEndTime(item.getProcessingEndTime());

        if (item.getInboundAssignment() != null) {
            dto.setInboundAssignmentId(item.getInboundAssignment().getId());
        }
        if (item.getOutboundAssignment() != null) {
            dto.setOutboundAssignmentId(item.getOutboundAssignment().getId());
        }

        return dto;
    }

    // ================= 辅助方法 =================

    private String generateProcRefNo() {
        long nanoTime = System.nanoTime();
        return "PROC-" + LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-")) + 
                String.format("%06d", nanoTime % 1000000);
    }
}
