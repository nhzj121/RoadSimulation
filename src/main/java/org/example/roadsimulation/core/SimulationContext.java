package org.example.roadsimulation.core;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 仿真上下文 - 统一时间框架
 * 
 * 所有仿真相关的时间操作都应该通过此类获取仿真时间，
 * 而不是直接使用 LocalDateTime.now()
 * 
 * 使用示例:
 * ```java
 * @Autowired
 * private SimulationContext simulationContext;
 * 
 * public void processOrder() {
 *     LocalDateTime simNow = simulationContext.getCurrentSimTime();
 *     order.setStartTime(simNow);  // 使用仿真时间
 * }
 * ```
 */
@Component
public class SimulationContext {

    /**
     * 仿真起始时间（固定值）
     * 所有仿真时间都基于此起始时间计算
     */
    private static final LocalDateTime SIM_START = LocalDateTime.of(2026, 1, 1, 0, 0);

    /**
     * Phase 1：后端业务时间固定每轮推进 30 个仿真分钟。
     * 前端 speedFactor 只能改变动画耗时，不能改变这个 Duration。
     */
    public static final Duration TICK_DURATION = Duration.ofMinutes(30);

    /**
     * 仿真循环计数器
     */
    private volatile int loopCount = 0;

    /**
     * 仿真运行状态
     */
    private volatile boolean isRunning = false;

    /**
     * Reset lifecycle flag. When true, already-entered simulation work should
     * stop creating new runtime data as soon as it reaches a safe checkpoint.
     */
    private volatile boolean resetting = false;

    /**
     * 获取当前仿真时间
     * 
     * @return 当前仿真时间
     */
    public LocalDateTime getCurrentSimTime() {
        // Phase 1：仿真时间只由“循环数 × 固定 tick 秒数”推进，不读取系统时间或前端速度。
        return SIM_START.plusSeconds(Math.multiplyExact((long) loopCount, getSecondsPerLoop()));
    }

    /**
     * Phase 1：返回当前循环对应的不可变仿真时间窗口。
     * 当前 loopCount 指向待执行轮次，因此 getCurrentSimTime() 等于 tickStart。
     */
    public SimulationTick getCurrentTick() {
        return SimulationTick.of(loopCount, getCurrentSimTime(), TICK_DURATION);
    }

    /**
     * Phase 1：从仿真起点累计的秒数；这是跨模块交换时推荐的无歧义时间量。
     */
    public long getCurrentSimulationSeconds() {
        return Math.multiplyExact((long) loopCount, getSecondsPerLoop());
    }

    /**
     * 获取仿真起始时间
     * 
     * @return 仿真起始时间
     */
    public LocalDateTime getSimStart() {
        return SIM_START;
    }

    /**
     * 增加循环计数（每次主循环执行后调用）
     */
    public void incrementLoop() {
        loopCount++;
    }

    /**
     * 重置仿真
     */
    public void reset() {
        loopCount = 0;
        isRunning = false;
    }

    public void beginReset() {
        isRunning = false;
        resetting = true;
    }

    public void finishReset() {
        resetting = false;
    }

    public boolean isResetting() {
        return resetting;
    }

    public boolean shouldAbortSimulationWork() {
        return resetting || !isRunning;
    }

    /**
     * 获取当前循环次数
     * 
     * @return 循环次数
     */
    public int getLoopCount() {
        return loopCount;
    }

    /**
     * 获取每个循环的分钟数
     * 
     * @return 分钟数
     */
    public int getMinutesPerLoop() {
        // Phase 1：兼容旧接口；分钟值从唯一的 TICK_DURATION 派生，不再保存第二份配置。
        return Math.toIntExact(TICK_DURATION.toMinutes());
    }

    /**
     * Phase 1：运输推进的规范 tick 预算，单位固定为仿真秒。
     */
    public long getSecondsPerLoop() {
        return TICK_DURATION.getSeconds();
    }

    /**
     * Phase 1：提供固定 Duration，供需要 Java 时间类型的调用方使用。
     */
    public Duration getTickDuration() {
        return TICK_DURATION;
    }

    /**
     * 获取仿真运行状态
     * 
     * @return 是否运行中
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 设置仿真运行状态
     * 
     * @param running 运行状态
     */
    public void setRunning(boolean running) {
        if (running) {
            resetting = false;
        }
        isRunning = running;
    }

    /**
     * 快进仿真时间
     * 
     * @param minutes 快进的分钟数
     */
    public void fastForward(int minutes) {
        // Phase 1：保留分钟参数用于兼容，但明确只消费完整 tick，余数不会生成隐式半轮状态。
        int loopsToAdd = minutes / getMinutesPerLoop();
        if (loopsToAdd > 0) {
            loopCount += loopsToAdd;
        }
    }

    /**
     * 计算从指定时间到当前仿真时间的 elapsed 分钟数
     * 
     * @param startTime 开始时间
     * @return 经过的分钟数
     */
    public long getElapsedMinutes(LocalDateTime startTime) {
        if (startTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, getCurrentSimTime()).toMinutes();
    }

    /**
     * 计算从指定时间到当前仿真时间的 elapsed 分钟数
     * 
     * @param startTime 开始时间
     * @return 经过的分钟数
     */
    public long getElapsedMinutes(java.time.Instant startTime) {
        if (startTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, getCurrentSimTime().atZone(java.time.ZoneId.systemDefault()).toInstant()).toMinutes();
    }

    /**
     * 根据开始时间和总时长计算进度百分比
     * 
     * @param startTime 开始时间
     * @param totalMinutes 总时长（分钟）
     * @return 进度百分比（0-100）
     */
    public int calculateProgress(LocalDateTime startTime, int totalMinutes) {
        if (startTime == null || totalMinutes <= 0) {
            return 0;
        }
        long elapsed = getElapsedMinutes(startTime);
        int progress = (int) (elapsed * 100 / totalMinutes);
        return Math.min(100, progress);
    }

    @Override
    public String toString() {
        return String.format("SimulationContext{loop=%d, simTime=%s, isRunning=%b}", 
            loopCount, getCurrentSimTime(), isRunning);
    }
}
