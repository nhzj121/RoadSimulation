package org.example.roadsimulation.evaluation;

import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Phase 8：把单轮实际距离增量转换为柴油当量能耗与直接运行碳排增量。
 *
 * <p>本组件不读取数据库、不改变车辆或任务状态，也不参与路径规划。调用方必须传入
 * AssignmentLeg 本轮真实新增距离和同轮环境因子，保证能耗事实与权威推进完全同源。</p>
 */
@Component
public final class VehicleEnergyEmissionModel {

    public static final String ENERGY_UNIT = "L(diesel-eq)";
    private final VehicleEnergyEmissionPolicy policy;

    public VehicleEnergyEmissionModel(VehicleEnergyEmissionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "energy emission policy must not be null");
        // Phase 8：直接构造测试与 Spring 配置绑定共用同一套启动校验。
        this.policy.validatePolicy();
    }

    /** Phase 8：为旧阶段单元测试提供与生产默认配置一致的显式基线。 */
    public static VehicleEnergyEmissionModel defaultModel() {
        return new VehicleEnergyEmissionModel(new VehicleEnergyEmissionPolicy());
    }

    /** Phase 8：按额定载重解析代理车辆档位，不使用车型名称猜测排放。 */
    public VehicleClassProfile resolveVehicleClass(double capacityTonnes) {
        requirePositiveFinite(capacityTonnes, "capacityTonnes");
        if (capacityTonnes <= policy.getL1MaxCapacityTonnes()) {
            return new VehicleClassProfile("L1", policy.getL1VehicleFactor());
        }
        if (capacityTonnes <= policy.getL2MaxCapacityTonnes()) {
            return new VehicleClassProfile("L2", policy.getL2VehicleFactor());
        }
        if (capacityTonnes <= policy.getMediumMaxCapacityTonnes()) {
            return new VehicleClassProfile("M", policy.getMediumVehicleFactor());
        }
        return new VehicleClassProfile("H", policy.getHeavyVehicleFactor());
    }

    /** Phase 8：环境能耗因子采用已确认的线性代理关系，始终独立于旅行时间指标命名。 */
    public double environmentEnergyFactor(double travelTimeFactor) {
        requirePositiveFinite(travelTimeFactor, "travelTimeFactor");
        double factor = 1.0 + policy.getEnvironmentFactorGamma() * (travelTimeFactor - 1.0);
        requirePositiveFinite(factor, "environmentEnergyFactor");
        return factor;
    }

    /** Phase 8：只接受单轮距离增量，禁止把累计距离重复送入造成双重计量。 */
    public EnergyEmissionDelta calculateDelta(
            double distanceDeltaMeters,
            double capacityTonnes,
            double currentLoadTonnes,
            double travelTimeFactor
    ) {
        requireNonNegativeFinite(distanceDeltaMeters, "distanceDeltaMeters");
        requirePositiveFinite(capacityTonnes, "capacityTonnes");
        requireNonNegativeFinite(currentLoadTonnes, "currentLoadTonnes");
        if (currentLoadTonnes > capacityTonnes + 1.0e-9) {
            throw new IllegalArgumentException("currentLoadTonnes cannot exceed capacityTonnes");
        }

        VehicleClassProfile vehicleClass = resolveVehicleClass(capacityTonnes);
        double loadRatio = currentLoadTonnes / capacityTonnes;
        double loadFactor = 1.0 + policy.getLoadFactorBeta() * loadRatio;
        double environmentFactor = environmentEnergyFactor(travelTimeFactor);
        double energyLiters = distanceDeltaMeters / 1000.0
                * policy.getBaseFuelLitersPerKm()
                * vehicleClass.vehicleFactor()
                * loadFactor
                * environmentFactor;
        double emissionKg = energyLiters * policy.getDirectEmissionKgPerLiter();
        requireNonNegativeFinite(energyLiters, "energyLiters");
        requireNonNegativeFinite(emissionKg, "emissionKg");
        return new EnergyEmissionDelta(
                policy.getModelId(),
                vehicleClass.code(),
                vehicleClass.vehicleFactor(),
                loadFactor,
                environmentFactor,
                energyLiters,
                emissionKg
        );
    }

    /** Phase 8：快照回传完整模型参数，避免只看到结果却无法复现实验。 */
    public EnergyEmissionModelSnapshot snapshot() {
        return new EnergyEmissionModelSnapshot(
                policy.getModelId(),
                ENERGY_UNIT,
                policy.getBaseFuelLitersPerKm(),
                policy.getDirectEmissionKgPerLiter(),
                policy.getLoadFactorBeta(),
                policy.getEnvironmentFactorGamma(),
                policy.getL1MaxCapacityTonnes(),
                policy.getL2MaxCapacityTonnes(),
                policy.getMediumMaxCapacityTonnes(),
                policy.getL1VehicleFactor(),
                policy.getL2VehicleFactor(),
                policy.getMediumVehicleFactor(),
                policy.getHeavyVehicleFactor()
        );
    }

    private void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    /** Phase 8：车辆档位代码与修正系数作为不可分解析结果返回。 */
    public record VehicleClassProfile(String code, double vehicleFactor) {
    }

    /** Phase 8：单轮增量携带所有实际使用的修正因子，便于单元测试和诊断。 */
    public record EnergyEmissionDelta(
            String modelId,
            String vehicleClassCode,
            double vehicleFactor,
            double loadFactor,
            double environmentFactor,
            double energyLiters,
            double emissionKg
    ) {
    }
}
