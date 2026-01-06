package org.example.roadsimulation.config;

import org.example.roadsimulation.service.impl.StateUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SimulationTimer {

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