package org.example.roadsimulation.core;

/**
 * Phase 1：运输纵向链路的统一单位转换入口。
 *
 * <p>方案 A 保留旧数据库和旧接口中的公里/小时字段，但任何进入运输执行链路的数值
 * 必须先通过本类转换为米/秒。载重在现有数据中本来就是吨，因此只做非负有限值校验，
 * 不进行比例换算。</p>
 */
public final class TransportUnits {

    // Phase 1：集中声明换算常量，禁止业务代码散落 1000、3600、60 等“魔法数字”。
    public static final double METERS_PER_KILOMETER = 1_000.0;
    public static final long SECONDS_PER_HOUR = 3_600L;
    public static final long SECONDS_PER_MINUTE = 60L;

    private TransportUnits() {
        // Phase 1：纯静态单位工具类不允许实例化，避免被误认为有可变单位配置。
    }

    /**
     * Phase 1：把兼容边界的公里值转换为运输内部使用的米。
     */
    public static double kilometersToMeters(double kilometers) {
        return requireNonNegativeFinite(kilometers, "kilometers") * METERS_PER_KILOMETER;
    }

    /**
     * Phase 1：把运输内部的米转换为兼容边界仍使用的公里。
     */
    public static double metersToKilometers(double meters) {
        return requireNonNegativeFinite(meters, "meters") / METERS_PER_KILOMETER;
    }

    /**
     * Phase 1：把兼容边界的小时值转换为运输内部使用的整数秒。
     * 小数秒采用四舍五入，确保同一输入在不同调用点得到相同结果。
     */
    public static long hoursToSeconds(double hours) {
        double seconds = requireNonNegativeFinite(hours, "hours") * SECONDS_PER_HOUR;
        if (seconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("hours is too large to represent as seconds: " + hours);
        }
        return Math.round(seconds);
    }

    /**
     * Phase 1：把运输内部的整数秒转换为兼容边界仍使用的小时。
     */
    public static double secondsToHours(long seconds) {
        requireNonNegative(seconds, "seconds");
        return seconds / (double) SECONDS_PER_HOUR;
    }

    /**
     * Phase 1：把明确标注为“仿真分钟”的值转换为仿真秒。
     */
    public static long minutesToSeconds(long minutes) {
        requireNonNegative(minutes, "minutes");
        try {
            return Math.multiplyExact(minutes, SECONDS_PER_MINUTE);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("minutes is too large to represent as seconds: " + minutes, ex);
        }
    }

    /**
     * Phase 1：载重规范单位为吨；本方法只验证语义，不改变数值。
     */
    public static double tonnes(double tonnes) {
        return requireNonNegativeFinite(tonnes, "tonnes");
    }

    private static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be a non-negative finite value: " + value);
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }
}
