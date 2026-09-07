package org.example.roadsimulation.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase 1：一次后端仿真循环的不可变时间窗口。
 *
 * <p>{@code tickStart}/{@code tickEnd} 都是仿真时间，不是系统墙上时间；
 * {@code availableSeconds} 是后续逐路段推进可以消费的唯一时间预算。
 * 当前阶段只建立语义，不改变既有运输生命周期。</p>
 */
public record SimulationTick(
        int loopIndex,
        LocalDateTime tickStart,
        LocalDateTime tickEnd,
        long availableSeconds
) {

    public SimulationTick {
        // Phase 1：禁止非法窗口进入运输链路，避免后续进度出现倒退或负时间。
        if (loopIndex < 0) {
            throw new IllegalArgumentException("loopIndex must be non-negative: " + loopIndex);
        }
        Objects.requireNonNull(tickStart, "tickStart must not be null");
        Objects.requireNonNull(tickEnd, "tickEnd must not be null");
        if (!tickEnd.isAfter(tickStart)) {
            throw new IllegalArgumentException("tickEnd must be after tickStart");
        }

        Duration actualDuration = Duration.between(tickStart, tickEnd);
        // Phase 1：进度预算只允许完整仿真秒，禁止把毫秒/纳秒静默截断后丢失时间。
        if (availableSeconds <= 0L || !actualDuration.equals(Duration.ofSeconds(availableSeconds))) {
            throw new IllegalArgumentException(
                    "availableSeconds must equal the positive tick window duration: " + availableSeconds
            );
        }
    }

    /**
     * Phase 1：由循环序号、起点和固定时长创建窗口，避免调用方分别计算结束时间和秒数。
     */
    public static SimulationTick of(int loopIndex, LocalDateTime tickStart, Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive: " + duration);
        }
        // Phase 1：统一预算的最小粒度为一秒，后续逐路段计算不会接收隐含小数秒。
        if (duration.getNano() != 0) {
            throw new IllegalArgumentException("duration must use whole simulation seconds: " + duration);
        }
        return new SimulationTick(
                loopIndex,
                tickStart,
                tickStart.plus(duration),
                duration.getSeconds()
        );
    }
}
