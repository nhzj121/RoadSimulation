package org.example.roadsimulation.core;

import org.springframework.stereotype.Component;

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
     * 仿真循环计数器
     */
    private int loopCount = 0;

    /**
     * 每个循环代表的仿真分钟数
     */
    private final int minutesPerLoop = 30;

    /**
     * 仿真运行状态
     */
    private boolean isRunning = false;

    /**
     * 获取当前仿真时间
     * 
     * @return 当前仿真时间
     */
    public LocalDateTime getCurrentSimTime() {
        return SIM_START.plusMinutes((long) loopCount * minutesPerLoop);
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
        return minutesPerLoop;
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
        isRunning = running;
    }

    /**
     * 快进仿真时间
     * 
     * @param minutes 快进的分钟数
     */
    public void fastForward(int minutes) {
        int loopsToAdd = minutes / minutesPerLoop;
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
