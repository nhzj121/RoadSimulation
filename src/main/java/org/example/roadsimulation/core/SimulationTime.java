// SimulationTime.java - 模拟时间核心类
package org.example.roadsimulation.core;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 模拟时间管理类
 * 负责管理模拟时钟、时间推进和时间相关计算
 */
public class SimulationTime {
    private LocalDateTime currentTime;
    private LocalDateTime startTime;
    private double timeScale; // 时间缩放比例 (1.0 = 实时)
    private boolean isPaused;
    private long simulationStep; // 模拟步长(毫秒)

    public SimulationTime(LocalDateTime startTime, double timeScale) {
        this.startTime = startTime;
        this.currentTime = startTime;
        this.timeScale = timeScale;
        this.isPaused = false;
        this.simulationStep = 1000; // 默认1秒
    }

    public void advanceTime() {
        if (!isPaused) {
            currentTime = currentTime.plus((long)(simulationStep * timeScale), ChronoUnit.MILLIS);
        }
    }

    public void advanceTime(long milliseconds) {
        if (!isPaused) {
            currentTime = currentTime.plus((long)(milliseconds * timeScale), ChronoUnit.MILLIS);
        }
    }

    public LocalDateTime getCurrentTime() {
        return currentTime;
    }

    public long getElapsedMinutes() {
        return ChronoUnit.MINUTES.between(startTime, currentTime);
    }

    public long getElapsedHours() {
        return ChronoUnit.HOURS.between(startTime, currentTime);
    }

    public void setTimeScale(double scale) {
        this.timeScale = Math.max(0.1, Math.min(10.0, scale)); // 限制在0.1-10倍
    }

    public void pause() {
        this.isPaused = true;
    }

    public void resume() {
        this.isPaused = false;
    }

    public void setSimulationStep(long stepMs) {
        this.simulationStep = stepMs;
    }

    public boolean isPaused() {
        return isPaused;
    }
}