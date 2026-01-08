package org.example.roadsimulation;

import org.example.roadsimulation.service.VehicleInitializationService;
import org.example.roadsimulation.service.impl.StateUpdateService;
import org.example.roadsimulation.service.impl.VehicleInitializationServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * 仿真主循环 - 控制中心
 * 职责：控制所有仿真模块的执行节奏（唯一驱动入口）
 */
@Component
public class SimulationMainLoop {

    private final DataInitializer dataInitializer;
    private final StateUpdateService stateUpdateService;

    @Autowired
    private VehicleInitializationService vehicleInitializationService;

    // 循环计数器
    private int loopCount = 0;

    // 运行状态
    private boolean isRunning = false;

    // 每个循环代表的分钟数
    private final int MINUTES_PER_LOOP = 30;

    // 仿真起始时间（用于把 loopCount 映射为 simNow）
    private static final LocalDateTime SIM_START = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    SimulationMainLoop(DataInitializer dataInitializer,
                       StateUpdateService stateUpdateService) {
        this.dataInitializer = dataInitializer;
        this.stateUpdateService = stateUpdateService;
    }

    /**
     * 主循环方法 - 每 7 秒执行一次循环（现实时间）
     * 每次循环推进 MINUTES_PER_LOOP（仿真时间）
     */
    @Scheduled(fixedRate = 7000)
    public void executeMainLoop() {
        // 前端/API 控制是否运行
        if (!isRunning) {
            return;
        }

        // ✅ 计算仿真当前时间（统一时间框架核心）
        int simMinutes = loopCount * MINUTES_PER_LOOP;
        LocalDateTime simNow = SIM_START.plusMinutes(simMinutes);

        System.out.println("=== 主循环第 " + loopCount + " 次 ===");
        System.out.println("模拟时间: " + (simMinutes / 60.0) + " 小时 | simNow=" + simNow);

        if (loopCount == 0) {
            stateUpdateService.resetWindowsOnce(simNow, MINUTES_PER_LOOP);
            vehicleInitializationService.initializeAllVehicleStatus();
        }
        // ======================
        // 1) 货物生成（每 1 小时）
        if (loopCount % 2 == 0) {
            System.out.println(">>> 执行货物生成逻辑");
            dataInitializer.generateGoods(loopCount);
        }

//        // 2) 货物运出（每 2 小时）
//        if (loopCount % 4 == 0) {
//            System.out.println(">>> 执行货物运出逻辑");
//            dataInitializer.shipOutGoods(loopCount);
//        }

        // 3) 打印仿真状态（每 5 小时）
        if (loopCount % 10 == 0) {
            System.out.println(">>> 打印仿真状态");
            dataInitializer.printSimulationStatus(loopCount);
        }

        // ✅ 4) 车辆状态更新（每循环一次）：统一接入主循环
        // stateUpdateService.tick(simNow, MINUTES_PER_LOOP, loopCount);

        // ======================
        loopCount++;
    }
    public LocalDateTime getCurrentSimTime() {
        long simMinutes = (long) loopCount * MINUTES_PER_LOOP;
        return SIM_START.plusMinutes(simMinutes);
    }

    public int getMinutesPerLoop() {
        return MINUTES_PER_LOOP;
    }
    /**
     * 启动仿真
     */
    public void start() {
        isRunning = true;
        System.out.println("仿真主循环已启动");
    }

    /**
     * 停止仿真
     */
    public void stop() {
        isRunning = false;
        System.out.println("仿真主循环已停止");
    }

    /**
     * 单步执行：执行一次主循环
     */
    public void step() {
        if (isRunning) {
            System.out.println("请先停止仿真再进行单步执行");
            return;
        }
        isRunning = true;
        executeMainLoop();
        isRunning = false;
    }

    /**
     * 重置仿真
     */
    public void reset() {
        loopCount = 0;
        isRunning = false;
        System.out.println("仿真已重置");
    }

    public int getLoopCount() {
        return loopCount;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
