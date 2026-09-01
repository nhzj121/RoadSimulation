package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.service.GaodeRoutePlanningQueueService;
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
    private final GaodeRoutePlanningQueueService gaodeRoutePlanningQueueService;

    @Autowired
    public TimeModuleController(
            SimulationMainLoop simulationMainLoop,
            GaodeRoutePlanningQueueService gaodeRoutePlanningQueueService
    ) {
        this.simulationMainLoop = simulationMainLoop;
        this.gaodeRoutePlanningQueueService = gaodeRoutePlanningQueueService;
    }

    /**
     * 启动模拟（主循环开始运行）
     */
    @PostMapping("/start")
    public Map<String, Object> startSimulation() {
        gaodeRoutePlanningQueueService.resume();
        simulationMainLoop.start();
        return createResponse("模拟已启动");
    }

    /**
     * 暂停模拟（主循环停止运行）
     */
    @PostMapping("/pause")
    public Map<String, Object> pauseSimulation() {
        simulationMainLoop.stop();
        gaodeRoutePlanningQueueService.pauseAndCancelPending();
        return createResponse("模拟已暂停");
    }

    /**
     * 恢复模拟（主循环继续运行）
     * 主循环版没有 paused 概念，resume 等价于 start
     */
    @PostMapping("/resume")
    public Map<String, Object> resumeSimulation() {
        gaodeRoutePlanningQueueService.resume();
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
        // Phase 1：明确速度倍率只属于前端视觉层，后端业务 tick 永远不受该值影响。
        res.put("scope", "VISUAL_ONLY");
        res.put("businessTickChanged", false);
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

        // Phase 1：零值和负值不再隐式推进一轮，保证“请求时间”和“实际业务推进”关系可解释。
        if (milliseconds <= 0L) {
            Map<String, Object> res = createResponse("仿真推进量必须大于 0 毫秒");
            res.put("ok", false);
            res.put("requestedSimulationMilliseconds", milliseconds);
            res.put("advancedLoops", 0);
            res.put("advancedSimulationSeconds", 0L);
            return res;
        }

        // Phase 1：参数明确表示“仿真毫秒”；先换算成固定 tick，再调用现有 step()。
        long secondsPerLoop = simulationMainLoop.getSecondsPerLoop();
        long millisecondsPerLoop = Math.multiplyExact(secondsPerLoop, 1_000L);
        long requestedLoops = milliseconds / millisecondsPerLoop
                + (milliseconds % millisecondsPerLoop == 0L ? 0L : 1L);
        if (requestedLoops > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("请求推进的仿真时间过大: " + milliseconds);
        }
        int loops = (int) requestedLoops;

        int before = simulationMainLoop.getLoopCount();
        for (int i = 0; i < loops; i++) {
            simulationMainLoop.step();
        }
        int after = simulationMainLoop.getLoopCount();

        Map<String, Object> res = createResponse("时间推进完成（使用 step 推进 loop）");
        res.put("ok", true);
        // Phase 1：保留旧响应字段，新增带单位和时间类型的无歧义字段。
        res.put("requestedMilliseconds", milliseconds);
        res.put("requestedSimulationMilliseconds", milliseconds);
        res.put("calculatedMinutes", milliseconds / 60_000.0);
        res.put("minutesPerLoop", simulationMainLoop.getMinutesPerLoop());
        res.put("secondsPerLoop", secondsPerLoop);
        res.put("requestedLoops", loops);
        res.put("advancedLoops", after - before);
        res.put("advancedSimulationSeconds", (long) (after - before) * secondsPerLoop);
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
        // Phase 1：状态接口同时公开规范秒数和本轮边界，避免调用方猜测 simNow 的含义。
        putCanonicalTimeSemantics(status);
        status.put("isRunning", simulationMainLoop.isRunning());
        status.put("routeQueueSize", gaodeRoutePlanningQueueService.getQueueSize());
        status.put("routeQueuePaused", gaodeRoutePlanningQueueService.isPaused());
        status.put("routeQueueGeneration", gaodeRoutePlanningQueueService.getGeneration());
        return status;
    }

    private Map<String, Object> createResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        // Phase 1：旧 timestamp 是系统墙上时间，仅用于响应审计，不能作为业务推进时间。
        response.put("timestamp", LocalDateTime.now());
        response.put("timestampSource", "WALL_CLOCK");

        // 统一输出仿真时间（不再用 SimulationTime）
        response.put("simNow", simulationMainLoop.getCurrentSimTime());
        response.put("loopCount", simulationMainLoop.getLoopCount());
        response.put("minutesPerLoop", simulationMainLoop.getMinutesPerLoop());
        // Phase 1：补充规范仿真秒和 tick 窗口；旧字段继续保留以兼容前端。
        putCanonicalTimeSemantics(response);
        response.put("isRunning", simulationMainLoop.isRunning());
        response.put("routeQueueSize", gaodeRoutePlanningQueueService.getQueueSize());
        response.put("routeQueuePaused", gaodeRoutePlanningQueueService.isPaused());
        response.put("routeQueueGeneration", gaodeRoutePlanningQueueService.getGeneration());
        return response;
    }

    /**
     * Phase 1：把统一时间语义集中写入响应，确保所有时间接口使用同一字段定义。
     */
    private void putCanonicalTimeSemantics(Map<String, Object> target) {
        SimulationTick tick = simulationMainLoop.getCurrentTick();
        target.put("simulationSeconds", simulationMainLoop.getCurrentSimulationSeconds());
        target.put("secondsPerLoop", simulationMainLoop.getSecondsPerLoop());
        target.put("tickStart", tick.tickStart());
        target.put("tickEnd", tick.tickEnd());
        target.put("timeSource", "SIMULATION_CONTEXT");
        target.put("speedFactorScope", "VISUAL_ONLY");
    }
}
