package org.example.roadsimulation.service.impl;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.ProcessingChainService;
import org.example.roadsimulation.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessingChainServiceImpl implements ProcessingChainService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessingChainServiceImpl.class);
    
    private final ProcessingChainRepository processingChainRepository;
    private final ProcessingStageRepository processingStageRepository;
    private final ProcessingOrderRepository processingOrderRepository;
    private final ProcessingTaskRepository processingTaskRepository;
    private final AssignmentRepository assignmentRepository;
    private final POIRepository poiRepository;
    private final GoodsRepository goodsRepository;
    
    // ================= 加工链管理 =================
    
    @Override
    @Transactional
    public ProcessingChain createChain(ProcessingChain chain) {
        // 检查编码是否已存在
        if (chain.getChainCode() != null && processingChainRepository.existsByChainCode(chain.getChainCode())) {
            throw new IllegalArgumentException("加工链编码已存在：" + chain.getChainCode());
        }
        
        // 计算总加工时间
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
    @Transactional
    public ProcessingChain updateChainStatus(Long id, ProcessingChain.ChainStatus status) {
        ProcessingChain chain = processingChainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + id));
        
        chain.setStatus(status);
        return processingChainRepository.save(chain);
    }
    
    @Override
    @Transactional
    public void deleteChain(Long id) {
        ProcessingChain chain = processingChainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + id));
        
        processingChainRepository.delete(chain);
        logger.info("删除加工链：id={}, chainCode={}", id, chain.getChainCode());
    }
    
    // ================= 加工工序管理 =================
    
    @Override
    @Transactional
    public ProcessingStage createStage(Long chainId, ProcessingStage stage) {
        ProcessingChain chain = processingChainRepository.findById(chainId)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + chainId));
        
        stage.setProcessingChain(chain);
        ProcessingStage saved = processingStageRepository.save(stage);
        
        // 更新加工链的总时间
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
    @Transactional
    public ProcessingStage updateStage(Long stageId, ProcessingStage stageDetails) {
        ProcessingStage stage = processingStageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("工序不存在：" + stageId));
        
        if (stageDetails.getStageName() != null) {
            stage.setStageName(stageDetails.getStageName());
        }
        if (stageDetails.getDescription() != null) {
            stage.setDescription(stageDetails.getDescription());
        }
        if (stageDetails.getProcessingTimeMinutes() != null) {
            stage.setProcessingTimeMinutes(stageDetails.getProcessingTimeMinutes());
        }
        if (stageDetails.getMaxCapacityPerCycle() != null) {
            stage.setMaxCapacityPerCycle(stageDetails.getMaxCapacityPerCycle());
        }
        if (stageDetails.getInputGoods() != null) {
            stage.setInputGoods(stageDetails.getInputGoods());
        }
        if (stageDetails.getOutputGoods() != null) {
            stage.setOutputGoods(stageDetails.getOutputGoods());
        }
        
        return processingStageRepository.save(stage);
    }
    
    @Override
    @Transactional
    public void deleteStage(Long stageId) {
        processingStageRepository.deleteById(stageId);
    }
    
    // ================= 加工订单管理 =================
    
    @Override
    @Transactional
    public ProcessingOrder createProcessingOrder(Long chainId, Double inputWeight, String createdBy) {
        ProcessingChain chain = processingChainRepository.findById(chainId)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + chainId));
        
        if (chain.getStatus() != ProcessingChain.ChainStatus.ACTIVE) {
            throw new IllegalStateException("加工链未激活：" + chain.getChainName());
        }
        
        // 1. 创建订单
        ProcessingOrder order = new ProcessingOrder();
        order.setOrderNo(generateOrderNo());
        order.setProcessingChain(chain);
        order.setStatus(ProcessingOrder.OrderStatus.PENDING);
        
        // 设置输入货物（第一道工序的输入）
        if (!chain.getStages().isEmpty()) {
            ProcessingStage firstStage = chain.getStages().get(0);
            order.setInputGoods(firstStage.getInputGoods());
            order.setInputGoodsSku(firstStage.getInputGoodsSku());
        }
        
        order.setInputWeight(inputWeight);
        order.setExpectedOutputWeight(inputWeight * chain.getYieldRate());
        
        // 2. 为每个工序创建加工任务
        for (ProcessingStage stage : chain.getStages()) {
            ProcessingTask task = new ProcessingTask();
            task.setProcessingOrder(order);
            task.setStage(stage);
            task.setStatus(ProcessingTask.TaskStatus.WAITING);
            task.setProcessedWeight(inputWeight);
            task.setProgressPercent(0);
            order.addTask(task);
        }
        
        // 3. 计算预期完成时间
        int totalMinutes = chain.getTotalProcessingTimeMinutes() != null ? 
                chain.getTotalProcessingTimeMinutes() : 
                chain.getStages().stream().mapToInt(ProcessingStage::getProcessingTimeMinutes).sum();
        order.setExpectedFinishTime(LocalDateTime.now().plusMinutes(totalMinutes));
        
        ProcessingOrder saved = processingOrderRepository.save(order);
        logger.info("创建加工订单：orderNo={}, chain={}, inputWeight={}t, stages={}", 
                order.getOrderNo(), chain.getChainName(), inputWeight, order.getTasks().size());
        
        return saved;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessingOrder> getOrderById(Long id) {
        return processingOrderRepository.findById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ProcessingOrder> getOrdersByChainId(Long chainId) {
        return processingOrderRepository.findByProcessingChainId(chainId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ProcessingOrder> getOrdersByStatus(ProcessingOrder.OrderStatus status) {
        return processingOrderRepository.findByStatus(status);
    }
    
    // ================= 加工执行控制 =================
    
    @Override
    @Transactional
    public void startProcessing(Long orderId) {
        ProcessingOrder order = processingOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("加工订单不存在：" + orderId));
        
        if (order.getStatus() != ProcessingOrder.OrderStatus.PENDING) {
            throw new IllegalStateException("订单状态不允许开始加工：" + order.getStatus());
        }
        
        order.setStatus(ProcessingOrder.OrderStatus.IN_PROCESS);
        order.setStartTime(LocalDateTime.now());
        
        // 启动第一道工序
        ProcessingTask firstTask = order.getTasks().stream()
                .filter(t -> t.getStage().getStageOrder() == 1)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到第一道工序"));
        
        firstTask.setStatus(ProcessingTask.TaskStatus.PROCESSING);
        firstTask.setStartTime(LocalDateTime.now());
        
        processingOrderRepository.save(order);
        logger.info("开始加工：orderNo={}, firstStage={}, poi={}", 
                order.getOrderNo(), 
                firstTask.getStage().getStageName(),
                firstTask.getStage().getProcessingPOI().getName());
    }
    
    @Override
    @Transactional
    public void cancelProcessing(Long orderId) {
        ProcessingOrder order = processingOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("加工订单不存在：" + orderId));
        
        if (order.getStatus() == ProcessingOrder.OrderStatus.COMPLETED || 
            order.getStatus() == ProcessingOrder.OrderStatus.CANCELLED) {
            throw new IllegalStateException("订单已完成或已取消，无法再次取消");
        }
        
        order.setStatus(ProcessingOrder.OrderStatus.CANCELLED);
        order.setActualFinishTime(LocalDateTime.now());
        
        // 取消所有未完成的任务
        for (ProcessingTask task : order.getTasks()) {
            if (task.getStatus() != ProcessingTask.TaskStatus.COMPLETED) {
                task.setStatus(ProcessingTask.TaskStatus.CANCELLED);
            }
        }
        
        processingOrderRepository.save(order);
        logger.info("取消加工订单：orderNo={}", order.getOrderNo());
    }
    
    @Override
    @Transactional
    public void updateProcessingProgress(LocalDateTime simNow, int minutesPerLoop) {
        // 查询所有进行中的加工订单
        List<ProcessingOrder> orders = processingOrderRepository.findByStatus(ProcessingOrder.OrderStatus.IN_PROCESS);
        
        for (ProcessingOrder order : orders) {
            processOrder(order, simNow, minutesPerLoop);
        }
    }
    
    /**
     * 处理单个订单的加工进度
     */
    private void processOrder(ProcessingOrder order, LocalDateTime simNow, int minutesPerLoop) {
        List<ProcessingTask> tasks = order.getTasks();
        
        for (ProcessingTask task : tasks) {
            if (task.getStatus() == ProcessingTask.TaskStatus.PROCESSING) {
                // 计算加工进度
                if (task.getStartTime() != null) {
                    Duration elapsed = Duration.between(task.getStartTime(), simNow);
                    int totalMinutes = task.getStage().getProcessingTimeMinutes();
                    int progress = Math.min(100, (int)(elapsed.toMinutes() * 100 / totalMinutes));
                    task.setProgressPercent(progress);
                    
                    logger.debug("加工进度：orderNo={}, stage={}, progress={}%", 
                            order.getOrderNo(), task.getStage().getStageName(), progress);
                    
                    // 工序完成
                    if (progress >= 100) {
                        completeTask(task, simNow);
                        startNextTask(order, task, simNow);
                    }
                }
                
                break; // 只处理一个进行中的任务
            }
        }
        
        // 检查订单是否完成
        checkAndCompleteOrder(order, simNow);
    }
    
    /**
     * 完成单个工序
     */
    private void completeTask(ProcessingTask task, LocalDateTime simNow) {
        task.setStatus(ProcessingTask.TaskStatus.COMPLETED);
        task.setEndTime(simNow);
        task.setProgressPercent(100);
        processingTaskRepository.save(task);
        
        logger.info("工序完成：orderId={}, stageName={}, poi={}", 
                task.getProcessingOrder().getId(),
                task.getStage().getStageName(),
                task.getStage().getProcessingPOI().getName());
    }
    
    /**
     * 启动下一道工序
     */
    private void startNextTask(ProcessingOrder order, ProcessingTask currentTask, LocalDateTime simNow) {
        int currentOrder = currentTask.getStage().getStageOrder();
        
        // 查找下一道工序
        ProcessingTask nextTask = order.getTasks().stream()
                .filter(t -> t.getStage().getStageOrder() == currentOrder + 1)
                .findFirst()
                .orElse(null);
        
        if (nextTask != null) {
            nextTask.setStatus(ProcessingTask.TaskStatus.PROCESSING);
            nextTask.setStartTime(simNow);
            
            // 创建从当前 POI 到下一 POI 的运输任务
            createTransportAssignment(currentTask, nextTask, simNow);
            
            logger.info("启动下道工序：orderId={}, nextStage={}, nextPOI={}", 
                    order.getId(), 
                    nextTask.getStage().getStageName(),
                    nextTask.getStage().getProcessingPOI().getName());
        }
    }
    
    /**
     * 创建工序间的运输任务
     */
    private void createTransportAssignment(ProcessingTask fromTask, ProcessingTask toTask, LocalDateTime simNow) {
        POI fromPOI = fromTask.getStage().getProcessingPOI();
        POI toPOI = toTask.getStage().getProcessingPOI();
        
        // TODO: 调用 AssignmentService 创建运输任务
        // 这里需要根据实际需求创建 Route 和 Assignment
        // Assignment assignment = assignmentService.createAssignment(fromPOI, toPOI, ...);
        // toTask.setInboundAssignment(assignment);
        
        logger.info("创建工序间运输：from={}({}) to={}({})", 
                fromPOI.getName(), fromPOI.getId(),
                toPOI.getName(), toPOI.getId());
    }
    
    /**
     * 检查并完成订单
     */
    private void checkAndCompleteOrder(ProcessingOrder order, LocalDateTime simNow) {
        boolean allCompleted = order.getTasks().stream()
                .allMatch(t -> t.getStatus() == ProcessingTask.TaskStatus.COMPLETED);
        
        if (allCompleted) {
            order.setStatus(ProcessingOrder.OrderStatus.COMPLETED);
            order.setActualFinishTime(simNow);
            order.setActualOutputWeight(order.getExpectedOutputWeight());
            
            processingOrderRepository.save(order);
            logger.info("加工订单完成：orderNo={}, outputWeight={}t", 
                    order.getOrderNo(), order.getActualOutputWeight());
        }
    }
    
    // ================= 查询与统计 =================
    
    @Override
    @Transactional(readOnly = true)
    public ProcessingOrderStatusDTO getOrderStatus(Long orderId) {
        ProcessingOrder order = processingOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("加工订单不存在：" + orderId));
        
        ProcessingOrderStatusDTO dto = new ProcessingOrderStatusDTO();
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setStatus(order.getStatus().name());
        dto.setStartTime(order.getStartTime());
        dto.setExpectedFinishTime(order.getExpectedFinishTime());
        dto.setActualFinishTime(order.getActualFinishTime());
        
        // 计算当前阶段
        List<ProcessingTask> tasks = order.getTasks();
        ProcessingTask currentTask = tasks.stream()
                .filter(t -> t.getStatus() == ProcessingTask.TaskStatus.PROCESSING)
                .findFirst()
                .orElse(null);
        
        if (currentTask != null) {
            dto.setCurrentStageName(currentTask.getStage().getStageName());
            dto.setCurrentStageIndex(currentTask.getStage().getStageOrder() - 1);
            dto.setOverallProgress(currentTask.getProgressPercent());
        } else if (order.getStatus() == ProcessingOrder.OrderStatus.COMPLETED) {
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
        
        List<ProcessingOrder> orders = processingOrderRepository.findByProcessingChainId(chainId);
        
        stats.setTotalOrders(orders.size());
        stats.setPendingOrders((int) orders.stream().filter(o -> o.getStatus() == ProcessingOrder.OrderStatus.PENDING).count());
        stats.setInProcessOrders((int) orders.stream().filter(o -> o.getStatus() == ProcessingOrder.OrderStatus.IN_PROCESS).count());
        stats.setCompletedOrders((int) orders.stream().filter(o -> o.getStatus() == ProcessingOrder.OrderStatus.COMPLETED).count());
        stats.setCancelledOrders((int) orders.stream().filter(o -> o.getStatus() == ProcessingOrder.OrderStatus.CANCELLED).count());
        
        stats.setTotalInputWeight(orders.stream()
                .filter(o -> o.getInputWeight() != null)
                .mapToDouble(ProcessingOrder::getInputWeight)
                .sum());
        
        stats.setTotalOutputWeight(orders.stream()
                .filter(o -> o.getActualOutputWeight() != null)
                .mapToDouble(ProcessingOrder::getActualOutputWeight)
                .sum());
        
        return stats;
    }
    
    // ================= DTO 查询 =================
    
    @Override
    @Transactional(readOnly = true)
    public ProcessingChainDTO getChainDTO(Long id) {
        ProcessingChain chain = processingChainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("加工链不存在：" + id));
        
        return convertToDTO(chain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ProcessingChainDTO> getAllChainDTOs() {
        return processingChainRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public ProcessingOrderDTO getOrderDTO(Long id) {
        ProcessingOrder order = processingOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("加工订单不存在：" + id));
        
        return convertToDTO(order);
    }
    
    // ================= 辅助方法 =================
    
    private ProcessingChainDTO convertToDTO(ProcessingChain chain) {
        ProcessingChainDTO dto = new ProcessingChainDTO();
        dto.setId(chain.getId());
        dto.setChainCode(chain.getChainCode());
        dto.setChainName(chain.getChainName());
        dto.setStatus(chain.getStatus().name());
        dto.setDescription(chain.getDescription());
        dto.setTotalProcessingTimeMinutes(chain.getTotalProcessingTimeMinutes());
        dto.setInputWeightPerCycle(chain.getInputWeightPerCycle());
        dto.setOutputWeightPerCycle(chain.getOutputWeightPerCycle());
        dto.setYieldRate(chain.getYieldRate());
        dto.setCreatedAt(chain.getCreatedAt());
        dto.setUpdatedAt(chain.getUpdatedAt());
        
        for (ProcessingStage stage : chain.getStages()) {
            ProcessingStageDTO stageDTO = convertStageToDTO(stage);
            dto.addStage(stageDTO);
        }
        
        return dto;
    }
    
    private ProcessingStageDTO convertStageToDTO(ProcessingStage stage) {
        ProcessingStageDTO dto = new ProcessingStageDTO();
        dto.setId(stage.getId());
        dto.setStageOrder(stage.getStageOrder());
        dto.setStageName(stage.getStageName());
        dto.setDescription(stage.getDescription());
        
        if (stage.getProcessingPOI() != null) {
            dto.setPoiId(stage.getProcessingPOI().getId());
            dto.setPoiName(stage.getProcessingPOI().getName());
            dto.setPoiType(stage.getProcessingPOI().getPoiType().name());
        }
        
        if (stage.getInputGoods() != null) {
            dto.setInputGoodsId(stage.getInputGoods().getId());
            dto.setInputGoodsSku(stage.getInputGoods().getSku());
            dto.setInputGoodsName(stage.getInputGoods().getName());
        } else {
            dto.setInputGoodsSku(stage.getInputGoodsSku());
        }
        
        dto.setInputWeightRatio(stage.getInputWeightRatio());
        
        if (stage.getOutputGoods() != null) {
            dto.setOutputGoodsId(stage.getOutputGoods().getId());
            dto.setOutputGoodsSku(stage.getOutputGoods().getSku());
            dto.setOutputGoodsName(stage.getOutputGoods().getName());
        } else {
            dto.setOutputGoodsSku(stage.getOutputGoodsSku());
        }
        
        dto.setOutputWeightRatio(stage.getOutputWeightRatio());
        dto.setProcessingTimeMinutes(stage.getProcessingTimeMinutes());
        dto.setMaxCapacityPerCycle(stage.getMaxCapacityPerCycle());
        dto.setMinBatchSize(stage.getMinBatchSize());
        
        return dto;
    }
    
    private ProcessingOrderDTO convertToDTO(ProcessingOrder order) {
        ProcessingOrderDTO dto = new ProcessingOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setStatus(order.getStatus().name());
        
        if (order.getProcessingChain() != null) {
            dto.setChainId(order.getProcessingChain().getId());
            dto.setChainName(order.getProcessingChain().getChainName());
        }
        
        if (order.getInputGoods() != null) {
            dto.setInputGoodsId(order.getInputGoods().getId());
            dto.setInputGoodsName(order.getInputGoods().getName());
        }
        
        dto.setInputWeight(order.getInputWeight());
        dto.setInputVolume(order.getInputVolume());
        
        if (order.getOutputGoods() != null) {
            dto.setOutputGoodsId(order.getOutputGoods().getId());
            dto.setOutputGoodsName(order.getOutputGoods().getName());
        }
        
        dto.setExpectedOutputWeight(order.getExpectedOutputWeight());
        dto.setActualOutputWeight(order.getActualOutputWeight());
        dto.setOrderTime(order.getOrderTime());
        dto.setStartTime(order.getStartTime());
        dto.setExpectedFinishTime(order.getExpectedFinishTime());
        dto.setActualFinishTime(order.getActualFinishTime());
        
        for (ProcessingTask task : order.getTasks()) {
            ProcessingTaskDTO taskDTO = convertTaskToDTO(task);
            dto.addTask(taskDTO);
        }
        
        return dto;
    }
    
    private ProcessingTaskDTO convertTaskToDTO(ProcessingTask task) {
        ProcessingTaskDTO dto = new ProcessingTaskDTO();
        dto.setId(task.getId());
        
        if (task.getProcessingOrder() != null) {
            dto.setOrderId(task.getProcessingOrder().getId());
        }
        
        if (task.getStage() != null) {
            dto.setStageId(task.getStage().getId());
            dto.setStageName(task.getStage().getStageName());
            dto.setStageOrder(task.getStage().getStageOrder());
            
            if (task.getStage().getProcessingPOI() != null) {
                dto.setPoiId(task.getStage().getProcessingPOI().getId());
                dto.setPoiName(task.getStage().getProcessingPOI().getName());
            }
        }
        
        dto.setStatus(task.getStatus().name());
        dto.setProgressPercent(task.getProgressPercent());
        dto.setProcessedWeight(task.getProcessedWeight());
        dto.setStartTime(task.getStartTime());
        dto.setEndTime(task.getEndTime());
        
        if (task.getInboundAssignment() != null) {
            dto.setInboundAssignmentId(task.getInboundAssignment().getId());
        }
        
        if (task.getOutboundAssignment() != null) {
            dto.setOutboundAssignmentId(task.getOutboundAssignment().getId());
        }
        
        return dto;
    }
    
    private String generateOrderNo() {
        return "PO-" + LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-nnnnnn"));
    }
}
