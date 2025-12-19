// SimulationTime.java - 模拟时间核心类（最终优化版）
package org.example.roadsimulation.core;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 模拟时间管理类
 * 负责管理模拟时钟、时间推进和时间相关计算
 * 支持暂停/继续、时间缩放、步长推进
 */
public class SimulationTime {
    private LocalDateTime currentTime;
    private final LocalDateTime startTime;
    private double timeScale = 1.0; // 时间缩放比例 (1.0 = 实时)
    private boolean isPaused = false;
    private long simulationStep = 1000; // 默认步长：1秒（1000毫秒）

    /**
     * 带参构造函数（推荐用于自定义起始时间）
     */
    public SimulationTime(LocalDateTime startTime, double timeScale) {
        this.startTime = startTime;
        this.currentTime = startTime;
        this.timeScale = Math.max(0.1, Math.min(10.0, timeScale)); // 限制合理范围
    }

    /**
     * 无参构造函数（默认从现在开始）
     */
    public SimulationTime() {
        this(LocalDateTime.now(), 1.0);
    }

    /**
     * 推进一个步长的模拟时间（无参版本，定时器常用）
     */
    public void advanceTime() {
        if (!isPaused) {
            long advanceMs = (long) (simulationStep * timeScale);
            currentTime = currentTime.plus(advanceMs, ChronoUnit.MILLIS);
        }
    }

    /**
     * 推进指定真实毫秒的模拟时间（带参版本，外部手动推进用）
     */
    public void advanceTime(long realMilliseconds) {
        if (!isPaused) {
            long advanceMs = (long) (realMilliseconds * timeScale);
            currentTime = currentTime.plus(advanceMs, ChronoUnit.MILLIS);
        }
    }

    /**
     * 获取当前模拟时间
     */
    public LocalDateTime getCurrentTime() {
        return currentTime;
    }

    /**
     * 获取已流逝的模拟分钟数
     */
    public long getElapsedMinutes() {
        return ChronoUnit.MINUTES.between(startTime, currentTime);
    }

    /**
     * 获取已流逝的模拟小时数
     */
    public long getElapsedHours() {
        return ChronoUnit.HOURS.between(startTime, currentTime);
    }

    /**
     * 设置时间缩放倍率（0.1x ~ 10x）
     */
    public void setTimeScale(double scale) {
        this.timeScale = Math.max(0.1, Math.min(10.0, scale));
    }

    /**
     * 获取当前时间缩放倍率
     */
    public double getTimeScale() {
        return timeScale;
    }

    /**
     * 暂停模拟时间
     */
    public void pause() {
        this.isPaused = true;
    }

    /**
     * 恢复模拟时间
     */
    public void resume() {
        this.isPaused = false;
    }

    /**
     * 设置模拟步长（毫秒，默认1000）
     */
    public void setSimulationStep(long stepMs) {
        this.simulationStep = stepMs > 0 ? stepMs : 1000;
    }

    /**
     * 获取模拟步长
     */
    public long getSimulationStep() {
        return simulationStep;
    }

    /**
     * 是否已暂停
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * 重置模拟时间到起始时间（可选功能）
     */
    public void reset() {
        this.currentTime = startTime;
    }

    @Override
    public String toString() {
        return "SimulationTime{" +
                "currentTime=" + currentTime +
                ", timeScale=" + timeScale +
                ", isPaused=" + isPaused +
                '}';
    }
}