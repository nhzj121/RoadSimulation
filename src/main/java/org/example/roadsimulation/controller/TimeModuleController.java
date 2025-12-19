package org.example.roadsimulation.controller;

import org.example.roadsimulation.service.impl.StateUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 时间模块REST控制器（修复版：注入正确，方法调用正确）
 */
@RestController
@RequestMapping("/api/simulation/time")
public class TimeModuleController {

    private final StateUpdateService stateUpdateService;

    @Autowired
    public TimeModuleController(StateUpdateService stateUpdateService) {
        this.stateUpdateService = stateUpdateService;
    }

    /**
     * 启动模拟
     */
    @PostMapping("/start")
    public Map<String, Object> startSimulation() {
        stateUpdateService.startSimulation();
        return createResponse("模拟已启动");
    }

    /**
     * 暂停模拟
     */
    @PostMapping("/pause")
    public Map<String, Object> pauseSimulation() {
        stateUpdateService.pauseSimulation();
        return createResponse("模拟已暂停");
    }

    /**
     * 恢复模拟（如果有resume方法）
     */
    @PostMapping("/resume")
    public Map<String, Object> resumeSimulation() {
        stateUpdateService.getSimulationTime().resume();
        return createResponse("模拟已恢复");
    }

    /**
     * 设置时间缩放（如 2.0 表示2倍速）
     */
    @PostMapping("/scale")
    public Map<String, Object> setTimeScale(@RequestParam double scale) {
        stateUpdateService.setTimeScale(scale);
        return createResponse("时间缩放设置为 " + scale + "x");
    }

    /**
     * 手动推进时间（毫秒）
     */
    @PostMapping("/advance")
    public Map<String, Object> advanceTime(@RequestParam long milliseconds) {
        stateUpdateService.advanceTime(milliseconds);
        return createResponse("时间推进 " + milliseconds + " 毫秒");
    }

    /**
     * 获取当前模拟状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentTime", stateUpdateService.getSimulationTime().getCurrentTime());
        status.put("isPaused", stateUpdateService.getSimulationTime().isPaused());
        status.put("timeScale", stateUpdateService.getSimulationTime().getTimeScale());
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