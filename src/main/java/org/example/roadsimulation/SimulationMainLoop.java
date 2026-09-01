package org.example.roadsimulation;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.core.SimulationModeGuard;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.core.TransportUnits;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.example.roadsimulation.entity.CostEntity;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.CostBaselineNormalizationService;
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.POIShipmentManager;
import org.example.roadsimulation.service.ProcessingChainServiceV2;
import org.example.roadsimulation.service.VehicleInitializationService;
import org.example.roadsimulation.service.impl.SimulationDispatchRouter;
import org.example.roadsimulation.service.impl.StateUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 仿真主循环 - 控制中心
 */
@Component
public class SimulationMainLoop {

    private final ReentrantLock lifecycleLock = new ReentrantLock(true);

    private final DataInitializer dataInitializer;
    private final StateUpdateService stateUpdateService;

    @Autowired
    private VehicleInitializationService vehicleInitializationService;

    @Autowired(required = false)
    private ProcessingChainServiceV2 processingChainServiceV2;

    @Autowired
    private SimulationContext simulationContext;

    @Autowired
    private SimulationModeGuard simulationModeGuard;

    @Autowired
    private POIShipmentManager poiShipmentManager;

    @Autowired
    private SimulationDispatchRouter simulationDispatchRouter;

    @Autowired
    private GetCostService getCostService;

    @Autowired
    private CostBaselineNormalizationService costBaselineNormalizationService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    SimulationMainLoop(DataInitializer dataInitializer,
                       StateUpdateService stateUpdateService,
                       SimulationContext simulationContext) {
        this.dataInitializer = dataInitializer;
        this.stateUpdateService = stateUpdateService;
        this.simulationContext = simulationContext;
    }

    /**
     * 主循环方法 - 每 7 秒执行一次循环（现实时间）
     * 每次循环推进 MINUTES_PER_LOOP（仿真时间）
     */
    @Scheduled(fixedRate = 4000)
    public void executeMainLoop() {
        if (shouldAbortLoop()) {
            return;
        }

        if (!lifecycleLock.tryLock()) {
            System.out.println("仿真主循环上一轮尚未结束或正在重置，本轮调度跳过");
            return;
        }

        try {
            if (shouldAbortLoop()) {
                return;
            }

            // Phase 1：主循环只从 SimulationContext 获取本轮窗口；前端动画速度不参与业务时间计算。
            SimulationTick currentTick = simulationContext.getCurrentTick();
            LocalDateTime simNow = currentTick.tickStart();
            long simSeconds = simulationContext.getCurrentSimulationSeconds();

            System.out.println("=== 主循环第 " + simulationContext.getLoopCount() + " 次 ===");
            // Phase 1：日志同时输出明确的仿真秒和窗口边界，避免把现实调度间隔误作业务耗时。
            System.out.println("模拟时间：" + (simSeconds / (double) TransportUnits.SECONDS_PER_HOUR)
                    + " 小时 | tick=" + currentTick);

            if (simulationContext.getLoopCount() == 0) {
                vehicleInitializationService.initializeAllVehicleStatus();
                if (shouldAbortLoop()) {
                    return;
                }
                // Phase 1：窗口重置接收同一个规范 tick，不再由调用点重建分钟参数。
                stateUpdateService.resetWindowsOnce(currentTick);
                if (shouldAbortLoop()) {
                    return;
                }
            }

            if (simulationContext.getLoopCount() % 2 == 0) {
                dataInitializer.generateGoods(simulationContext.getLoopCount());
                if (shouldAbortLoop()) {
                    return;
                }
            }

            if (simulationContext.getLoopCount() % 10 == 0) {
                dataInitializer.printSimulationStatus(simulationContext.getLoopCount());
                // 周期性超时检测：清理长时间未完成的运单，防止POI永久阻塞
                try {
                    if (shouldAbortLoop()) {
                        return;
                    }
                    int expiredCount = poiShipmentManager.sweepExpiredShipments(120).size();
                    if (expiredCount > 0) {
                        System.out.println("周期性超时清理: 释放了 " + expiredCount + " 个卡住的POI");
                    }
                } catch (Exception e) {
                    System.err.println("超时清理执行异常: " + e.getMessage());
                }
            }

            if (simulationContext.getLoopCount() != 0 && simulationContext.getLoopCount() % 3 == 0){
                if (shouldAbortLoop()) {
                    return;
                }
                simulationDispatchRouter.dispatch();
                recordCostNormalizationDispatchSnapshot();
                if (shouldAbortLoop()) {
                    return;
                }
            }

            // 加工链进度更新
            if (processingChainServiceV2 != null) {
                if (shouldAbortLoop()) {
                    return;
                }
                // Phase 1：加工链与运输链使用同一个后端仿真 tick，不再各自硬编码 30。
                processingChainServiceV2.updateProcessingProgress(simNow, simulationContext.getMinutesPerLoop());
                if (shouldAbortLoop()) {
                    return;
                }
            }

            // Phase 1：状态更新接收本轮唯一 SimulationTick；秒预算将在后续进度阶段真正消费。
            stateUpdateService.tick(currentTick);
            if (shouldAbortLoop()) {
                return;
            }

            simulationContext.incrementLoop();
        } finally {
            lifecycleLock.unlock();
        }
    }

    public LocalDateTime getCurrentSimTime() {
        return simulationContext.getCurrentSimTime();
    }

    public int getMinutesPerLoop() {
        // Phase 1：兼容现有 Controller；分钟数来自唯一的 SimulationContext 配置。
        return simulationContext.getMinutesPerLoop();
    }

    /**
     * Phase 1：对外提供规范秒数，供进度服务和状态接口避免再次换算。
     */
    public long getSecondsPerLoop() {
        return simulationContext.getSecondsPerLoop();
    }

    /**
     * Phase 1：暴露当前仿真窗口的只读投影，不允许调用方自行构造业务时间。
     */
    public SimulationTick getCurrentTick() {
        return simulationContext.getCurrentTick();
    }

    /**
     * Phase 1：从仿真起点累计的规范秒数，与现实运行时长和前端 speedFactor 无关。
     */
    public long getCurrentSimulationSeconds() {
        return simulationContext.getCurrentSimulationSeconds();
    }

    public void start() {
        simulationContext.finishReset();
        simulationContext.setRunning(true);
        System.out.println("仿真主循环已启动");
    }

    public void stop() {
        simulationContext.setRunning(false);
        System.out.println("仿真主循环已停止");
    }

    public void stopForReset() {
        simulationContext.beginReset();
        System.out.println("仿真主循环正在为重置停止");
    }

    public void completeResetLifecycle() {
        simulationContext.finishReset();
    }

    public void step() {
        if (simulationContext.isRunning()) {
            System.out.println("请先停止仿真再进行单步执行");
            return;
        }
        simulationContext.setRunning(true);
        executeMainLoop();
        simulationContext.setRunning(false);
    }

    public void reset() {
        stopForReset();
        awaitLoopIdleAndResetContext();
    }

    public void awaitLoopIdleAndResetContext() {
        lifecycleLock.lock();
        try {
            simulationContext.reset();
            CostEntity.reset();
            costBaselineNormalizationService.reset();
            System.out.println("仿真已重置");
        } finally {
            lifecycleLock.unlock();
        }
    }

    public int getLoopCount() {
        return simulationContext.getLoopCount();
    }

    public boolean isRunning() {
        return simulationContext.isRunning();
    }

    private boolean shouldAbortLoop() {
        if (simulationModeGuard != null && simulationModeGuard.isDispatchComparisonExperimentActive()) {
            return true;
        }
        return simulationContext.shouldAbortSimulationWork();
    }

    private void recordCostNormalizationDispatchSnapshot() {
        try {
            RuntimeCostDTO costs = getCostService.calculateRuntimeCosts(
                    vehicleRepository.findAll(),
                    assignmentRepository.findRuntimeActiveAssignments()
            );
            long totalShipmentItems = shipmentItemRepository.count();
            long notAssignedItems = shipmentItemRepository
                    .findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED)
                    .size();
            costBaselineNormalizationService.recordDispatchSnapshot(
                    costs,
                    totalShipmentItems,
                    notAssignedItems
            );
        } catch (Exception ex) {
            System.err.println("Cost baseline normalization snapshot failed: " + ex.getMessage());
        }
    }
}
