package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 状态更新服务（主循环版）
 *
 * ✅ 统一时间框架：由 SimulationMainLoop 传入 simNow（仿真时间）
 * ✅ 每循环一次：MainLoop 每次循环调用 tick()
 * ✅ 调试增强：第一次 tick 自动 reset 一次车辆状态窗口（解决 endTime 太远导致长期不转移）
 */
@Service
public class StateUpdateService {

    // ✅ 改成注入具体实现：这样可以稳定调用 resetVehicleStateWindows
    private final StateTransitionServiceImpl stateTransitionService;
    private final VehicleRepository vehicleRepository;

    /**
     * 统计打印频率：每隔多少个 loop 打印一次
     * MINUTES_PER_LOOP=30：
     * - loopsPerStats=2 -> 每 1 小时打印一次
     */
    private final int loopsPerStats = 2;

    // ✅ 确保 reset 只执行一次
    private boolean windowsResetDone = false;

    @Autowired
    public StateUpdateService(StateTransitionServiceImpl stateTransitionService,
                              VehicleRepository vehicleRepository) {
        this.stateTransitionService = stateTransitionService;
        this.vehicleRepository = vehicleRepository;
    }

    /**
     * 主循环每次调用一次（每循环一次）
     *
     * @param simNow         当前仿真时间（由 SimulationMainLoop 计算并传入）
     * @param minutesPerLoop 每个 loop 对应的仿真分钟数（你们是 30）
     * @param loopCount      当前第几个 loop（用于控制统计打印频率）
     */
    public void tick(LocalDateTime simNow, int minutesPerLoop, int loopCount) {
        try {
            // ✅ 第一次 tick：先 reset 一次，把所有车的窗口挪到 simNow
            // 这样最晚 1~2 个循环就能看到“状态更新”
            if (!windowsResetDone) {
                System.out.println(">>> [StateUpdateService] 首次tick：重置车辆状态窗口（调试用）");
                resetWindowsOnce(simNow, minutesPerLoop);
                windowsResetDone = true;
            }

            // ✅ 唯一状态更新入口：到点才转移等逻辑都在 StateTransitionServiceImpl 内部
            stateTransitionService.batchUpdateAllVehicleStates(simNow, minutesPerLoop);

            // ✅ 统计打印：按 loop 节奏输出
            if (loopsPerStats > 0 && loopCount % loopsPerStats == 0) {
                printStateStatistics(simNow);
            }

        } catch (Exception e) {
            System.err.println("[StateUpdateService] tick 执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ 手动/首次调用都可用：重置所有车辆的 statusStartTime/statusDuration 窗口
     */
    public void resetWindowsOnce(LocalDateTime simNow, int minutesPerLoop) {
        stateTransitionService.resetVehicleStateWindows(simNow, minutesPerLoop);
    }

    /**
     * 打印所有车辆当前状态分布统计（由主循环控制频率）
     */
    private void printStateStatistics(LocalDateTime simNow) {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        Map<VehicleStatus, Long> stats = vehicles.stream()
                .collect(Collectors.groupingBy(Vehicle::getCurrentStatus, Collectors.counting()));

        System.out.printf("[状态统计] 仿真时间: %s | 车辆总数: %d | 分布: %s%n",
                simNow,
                vehicles.size(),
                stats);
    }

    // ==========================
    // 旧框架控制方法已废弃
    // ==========================

    @Deprecated
    public void startSimulation() {
        throw new UnsupportedOperationException("已改为 SimulationMainLoop 驱动：请调用 SimulationMainLoop.start()");
    }

    @Deprecated
    public void pauseSimulation() {
        throw new UnsupportedOperationException("已改为 SimulationMainLoop 驱动：请调用 SimulationMainLoop.stop()");
    }

    @Deprecated
    public void setTimeScale(double scale) {
        throw new UnsupportedOperationException("已改为 SimulationMainLoop 驱动：不再使用旧的 timeScale");
    }

}
