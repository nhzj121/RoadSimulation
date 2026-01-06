package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 时间模块REST控制器（主循环版）
 *
 * ✅ 统一框架：由 SimulationMainLoop 控制仿真运行与时间推进
 * ✅ 不再依赖 SimulationTime / TimeEventScheduler
 */
@RestController
@RequestMapping("/api/simulation/time")
public class TimeModuleController {

    private final SimulationMainLoop simulationMainLoop;

    @Autowired
    public TimeModuleController(SimulationMainLoop simulationMainLoop) {
        this.simulationMainLoop = simulationMainLoop;
    }

    /**
     * 启动模拟（主循环开始运行）
     */
    @PostMapping("/start")
    public Map<String, Object> startSimulation() {
        simulationMainLoop.start();
        return createResponse("模拟已启动");
    }

    /**
     * 暂停模拟（主循环停止运行）
     */
    @PostMapping("/pause")
    public Map<String, Object> pauseSimulation() {
        simulationMainLoop.stop();
        return createResponse("模拟已暂停");
    }

    /**
     * 恢复模拟（主循环继续运行）
     * 主循环版没有 paused 概念，resume 等价于 start
     */
    @PostMapping("/resume")
    public Map<String, Object> resumeSimulation() {
        simulationMainLoop.start();
        return createResponse("模拟已恢复");
    }

    /**
     * 设置时间缩放
     * 主循环版目前不支持 timeScale（因为仿真推进由 MINUTES_PER_LOOP 固定控制）
     */
    @PostMapping("/scale")
    public Map<String, Object> setTimeScale(@RequestParam double scale) {
        Map<String, Object> res = createResponse("主循环版暂不支持 timeScale（已统一到 SimulationMainLoop）");
        res.put("requestedScale", scale);
        res.put("supported", false);
        return res;
    }

    /**
     * 手动推进时间（毫秒）
     * 主循环版用 step() 推进：把毫秒换算成分钟，再换算成需要执行多少个 loop
     *
     * 注意：step() 要求当前不是 running，否则它会拒绝（你 MainLoop 的 step() 逻辑如此）
     */
    @PostMapping("/advance")
    public Map<String, Object> advanceTime(@RequestParam long milliseconds) {
        if (simulationMainLoop.isRunning()) {
            Map<String, Object> res = createResponse("请先暂停模拟再推进时间（主循环 running 时不允许 step）");
            res.put("ok", false);
            return res;
        }

        // 约定：这里把 milliseconds 当作“仿真时间”推进量
        // 1 分钟 = 60_000 毫秒
        double minutes = milliseconds / 60000.0;
        int minutesPerLoop = simulationMainLoop.getMinutesPerLoop();

        // 至少推进 1 个 loop
        int loops = (int) Math.ceil(minutes / minutesPerLoop);
        loops = Math.max(1, loops);

        int before = simulationMainLoop.getLoopCount();
        for (int i = 0; i < loops; i++) {
            simulationMainLoop.step();
        }
        int after = simulationMainLoop.getLoopCount();

        Map<String, Object> res = createResponse("时间推进完成（使用 step 推进 loop）");
        res.put("ok", true);
        res.put("requestedMilliseconds", milliseconds);
        res.put("calculatedMinutes", minutes);
        res.put("minutesPerLoop", minutesPerLoop);
        res.put("advancedLoops", loops);
        res.put("loopCountBefore", before);
        res.put("loopCountAfter", after);
        return res;
    }

    /**
     * 获取当前模拟状态（主循环版）
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("simNow", simulationMainLoop.getCurrentSimTime());
        status.put("loopCount", simulationMainLoop.getLoopCount());
        status.put("minutesPerLoop", simulationMainLoop.getMinutesPerLoop());
        status.put("isRunning", simulationMainLoop.isRunning());
        return status;
    }

    private Map<String, Object> createResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());

        // 统一输出仿真时间（不再用 SimulationTime）
        response.put("simNow", simulationMainLoop.getCurrentSimTime());
        response.put("loopCount", simulationMainLoop.getLoopCount());
        response.put("minutesPerLoop", simulationMainLoop.getMinutesPerLoop());
        response.put("isRunning", simulationMainLoop.isRunning());
        return response;
    }
}
