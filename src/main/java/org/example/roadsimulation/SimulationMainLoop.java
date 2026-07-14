package org.example.roadsimulation;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.core.SimulationModeGuard;
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

    private static final long LOOP_INTERVAL_MS = 4_000L;

    private final ReentrantLock lifecycleLock = new ReentrantLock(true);

    private final DataInitializer dataInitializer;
    private final StateUpdateService stateUpdateService;
    private final VehicleInitializationService vehicleInitializationService;
    private final SimulationContext simulationContext;
    private final SimulationModeGuard simulationModeGuard;
    private final POIShipmentManager poiShipmentManager;
    private final SimulationDispatchRouter simulationDispatchRouter;
    private final GetCostService getCostService;
    private final CostBaselineNormalizationService costBaselineNormalizationService;
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;

    @Autowired(required = false)
    private ProcessingChainServiceV2 processingChainServiceV2;

    @Autowired
    SimulationMainLoop(DataInitializer dataInitializer,
                       StateUpdateService stateUpdateService,
                       VehicleInitializationService vehicleInitializationService,
                       SimulationContext simulationContext,
                       SimulationModeGuard simulationModeGuard,
                       POIShipmentManager poiShipmentManager,
                       SimulationDispatchRouter simulationDispatchRouter,
                       GetCostService getCostService,
                       CostBaselineNormalizationService costBaselineNormalizationService,
                       VehicleRepository vehicleRepository,
                       AssignmentRepository assignmentRepository,
                       ShipmentItemRepository shipmentItemRepository) {
        this.dataInitializer = dataInitializer;
        this.stateUpdateService = stateUpdateService;
        this.vehicleInitializationService = vehicleInitializationService;
        this.simulationContext = simulationContext;
        this.simulationModeGuard = simulationModeGuard;
        this.poiShipmentManager = poiShipmentManager;
        this.simulationDispatchRouter = simulationDispatchRouter;
        this.getCostService = getCostService;
        this.costBaselineNormalizationService = costBaselineNormalizationService;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
    }

    /**
     * 主循环方法 - 每 4 秒尝试执行一次循环（现实时间）。
     * 每次成功循环推进 {@link SimulationContext#getMinutesPerLoop()} 分钟（仿真时间）。
     */
    @Scheduled(fixedRate = LOOP_INTERVAL_MS)
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

            LocalDateTime simNow = simulationContext.getCurrentSimTime();
            int minutesPerLoop = simulationContext.getMinutesPerLoop();
            int simMinutes = (int) java.time.Duration.between(
                    simulationContext.getSimStart(), simNow).toMinutes();

            System.out.println("=== 主循环第 " + simulationContext.getLoopCount() + " 次 ===");
            System.out.println("模拟时间：" + (simMinutes / 60.0) + " 小时 | simNow=" + simNow);

            if (simulationContext.getLoopCount() == 0) {
                vehicleInitializationService.initializeAllVehicleStatus();
                if (shouldAbortLoop()) {
                    return;
                }
                stateUpdateService.resetWindowsOnce(simNow, minutesPerLoop);
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
                processingChainServiceV2.updateProcessingProgress(simNow, minutesPerLoop);
                if (shouldAbortLoop()) {
                    return;
                }
            }

            stateUpdateService.tick(simNow, minutesPerLoop, simulationContext.getLoopCount());
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
        return simulationContext.getMinutesPerLoop();
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
