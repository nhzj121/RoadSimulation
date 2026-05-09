package org.example.roadsimulation;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.service.ProcessingChainServiceV2;
import org.example.roadsimulation.service.VehicleInitializationService;
import org.example.roadsimulation.service.impl.StateUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 仿真主循环 - 控制中心
 */
@Component
public class SimulationMainLoop {


    private final DataInitializer dataInitializer;
    private final StateUpdateService stateUpdateService;

    @Autowired
    private VehicleInitializationService vehicleInitializationService;

    @Autowired(required = false)
    private ProcessingChainServiceV2 processingChainServiceV2;

    @Autowired
    private SimulationContext simulationContext;

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
        if (!simulationContext.isRunning()) {
            return;
        }

        LocalDateTime simNow = simulationContext.getCurrentSimTime();
        int simMinutes = (int) java.time.Duration.between(
                simulationContext.getSimStart(), simNow).toMinutes();

        System.out.println("=== 主循环第 " + simulationContext.getLoopCount() + " 次 ===");
        System.out.println("模拟时间：" + (simMinutes / 60.0) + " 小时 | simNow=" + simNow);

        if (simulationContext.getLoopCount() == 0) {
            stateUpdateService.resetWindowsOnce(simNow, 30);
            vehicleInitializationService.initializeAllVehicleStatus();
        }

        if (simulationContext.getLoopCount() % 2 == 0) {
            dataInitializer.generateGoods(simulationContext.getLoopCount());
        }

        if (simulationContext.getLoopCount() % 10 == 0) {
            dataInitializer.printSimulationStatus(simulationContext.getLoopCount());
        }

        if (simulationContext.getLoopCount() != 0 && simulationContext.getLoopCount() % 3 == 0){
            dataInitializer.vrpDispatchingCycle();
        }

        // 加工链进度更新
        if (processingChainServiceV2 != null) {
            processingChainServiceV2.updateProcessingProgress(simNow, 30);
        }

        simulationContext.incrementLoop();
    }

    public LocalDateTime getCurrentSimTime() {
        return simulationContext.getCurrentSimTime();
    }

    public int getMinutesPerLoop() {
        return 30;
    }

    public void start() {
        simulationContext.setRunning(true);
        System.out.println("仿真主循环已启动");
    }

    public void stop() {
        simulationContext.setRunning(false);
        System.out.println("仿真主循环已停止");
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
        simulationContext.reset();
        System.out.println("仿真已重置");
    }

    public int getLoopCount() {
        return simulationContext.getLoopCount();
    }

    public boolean isRunning() {
        return simulationContext.isRunning();
    }
}
