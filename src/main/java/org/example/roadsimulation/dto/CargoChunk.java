package org.example.roadsimulation.dto;

/**
 * 货物拆分块 —— 将一个大订单按车辆容量预先拆分为标准化的小块。
 * 每块天然匹配某类车辆的载重能力，保证高装载率（80-100%）。
 *
 * <p>与旧 CargoSplitResult 的区别：
 * <ul>
 *   <li>CargoSplitResult: 分配后才知分给哪辆车，装载率不可控</li>
 *   <li>CargoChunk: 分配前就按车型容量预先算好每块大小，装载率天然保证</li>
 * </ul>
 */
public class CargoChunk {

    /** 该块包含的货物单位数 */
    private final int quantity;

    /** 推荐承运的车型名称（如"解放J6G仓栅车"），为null表示无适配车型 */
    private final String recommendedVehicleType;

    /** 该车型的最大载重（吨） */
    private final Double vehicleMaxLoad;

    /** 该车型的最大容积（立方米） */
    private final Double vehicleMaxVolume;

    /** 预期装载率（0.0~1.0），基于重量和体积取高者 */
    private final double expectedLoadFactor;

    /** 是否所有车型都无法承运此货物（单件超重/超大） */
    private final boolean exceptional;

    public CargoChunk(int quantity, String recommendedVehicleType,
                      Double vehicleMaxLoad, Double vehicleMaxVolume,
                      double expectedLoadFactor, boolean exceptional) {
        this.quantity = quantity;
        this.recommendedVehicleType = recommendedVehicleType;
        this.vehicleMaxLoad = vehicleMaxLoad;
        this.vehicleMaxVolume = vehicleMaxVolume;
        this.expectedLoadFactor = expectedLoadFactor;
        this.exceptional = exceptional;
    }

    public int getQuantity() { return quantity; }
    public String getRecommendedVehicleType() { return recommendedVehicleType; }
    public Double getVehicleMaxLoad() { return vehicleMaxLoad; }
    public Double getVehicleMaxVolume() { return vehicleMaxVolume; }
    public double getExpectedLoadFactor() { return expectedLoadFactor; }
    public boolean isExceptional() { return exceptional; }

    @Override
    public String toString() {
        if (exceptional) {
            return String.format("Chunk[异常:%d单位, 无适配车辆]", quantity);
        }
        return String.format("Chunk[%d单位 → %s, 装载率%.0f%%]",
                quantity, recommendedVehicleType, expectedLoadFactor * 100);
    }
}
