package org.example.roadsimulation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 旧时间框架定时器（已停用）
 *
 * 原先用于每 1 秒推进 SimulationTime / 调度 TimeEventScheduler。
 * 现在项目统一改为 SimulationMainLoop 驱动仿真时间与状态更新，
 * 为避免“双驱动”导致状态乱跳，默认禁用。
 *
 * 如确实需要启用旧框架（不建议），可在 application.properties 设置：
 * legacy.timer.enabled=true
 */
@Component
@ConditionalOnProperty(name = "legacy.timer.enabled", havingValue = "true")
public class SimulationTimer {
<<<<<<< Updated upstream

    @Autowired
    private StateUpdateService stateUpdateService;

    /**
     * 定时推进模拟时间（每真实1秒执行一次）
     */
//    @Scheduled(fixedRate = 1000) // 1秒
//    public void advanceSimulationTime() {
//        if (!stateUpdateService.getSimulationTime().isPaused()) {
//            // 推进1秒模拟时间（实际推进量取决于时间缩放）
//            stateUpdateService.advanceTime(1000);
//        }
//    }
}
=======
    // ✅ 主循环模式下不需要任何实现；保留该类仅用于兼容/占位。
}
>>>>>>> Stashed changes
