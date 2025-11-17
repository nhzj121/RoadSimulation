// TimeModuleController.java - 时间模块REST控制器
package org.example.roadsimulation.controller;

import org.example.roadsimulation.service.impl.StateUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation/time")
public class TimeModuleController {

    @Autowired
    private StateUpdateService stateUpdateService;

    @PostMapping("/start")
    public Map<String, Object> startSimulation() {
        stateUpdateService.startSimulation();
        return createResponse("模拟已启动");
    }

    @PostMapping("/pause")
    public Map<String, Object> pauseSimulation() {
        stateUpdateService.pauseSimulation();
        return createResponse("模拟已暂停");
    }

    @PostMapping("/advance")
    public Map<String, Object> advanceTime(@RequestParam long minutes) {
        stateUpdateService.advanceTime(minutes * 60 * 1000); // 转换为毫秒
        return createResponse("时间已推进 " + minutes + " 分钟");
    }

    @PostMapping("/scale")
    public Map<String, Object> setTimeScale(@RequestParam double scale) {
        stateUpdateService.setTimeScale(scale);
        return createResponse("时间缩放设置为: " + scale + "x");
    }

    @GetMapping("/status")
    public Map<String, Object> getTimeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentTime", stateUpdateService.getSimulationTime().getCurrentTime());
        status.put("elapsedHours", stateUpdateService.getSimulationTime().getElapsedHours());
        status.put("elapsedMinutes", stateUpdateService.getSimulationTime().getElapsedMinutes());
        status.put("isPaused", stateUpdateService.getSimulationTime().isPaused());
        status.put("pendingEvents", stateUpdateService.getEventScheduler().getPendingEventCount());

        return status;
    }

    private Map<String, Object> createResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        response.put("simulationTime", stateUpdateService.getSimulationTime().getCurrentTime());
        return response;
    }
}