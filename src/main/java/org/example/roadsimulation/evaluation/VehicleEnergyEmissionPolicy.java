package org.example.roadsimulation.evaluation;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 8：版本化的柴油当量能耗与直接运行碳排代理模型配置。
 *
 * <p>参数属于评价模型而不是车辆主数据，因此不写入 Vehicle 表。吨位边界和修正系数
 * 由配置统一管理，代码只负责校验、分类和计算，避免为每辆车复制同一套代理参数。</p>
 */
@Component
@ConfigurationProperties(prefix = "simulation.evaluation.emission")
public class VehicleEnergyEmissionPolicy {

    // Phase 8：模型标识必须在参数口径变化时同步升级，用于识别跨重启混用的累计事实。
    private String modelId = "DIESEL_FLEET_PAYLOAD_PROXY_V1";

    // Phase 8：K_vehicle=1、空载、正常环境下的柴油当量基准油耗，单位 L/km。
    private double baseFuelLitersPerKm = 0.25;

    // Phase 8：只计算车辆直接运行排放，不包含制造和燃料上游生命周期排放。
    private double directEmissionKgPerLiter = 2.68;

    // Phase 8：载重修正 K_load=1+beta*loadRatio。
    private double loadFactorBeta = 0.20;

    // Phase 8：环境修正 K_env=1+gamma*(travelTimeFactor-1)，不直接冒用旅行时间因子。
    private double environmentFactorGamma = 0.30;

    // Phase 8：四档吨位边界基于当前仿真车辆的额定载重，单位 t。
    private double l1MaxCapacityTonnes = 2.0;
    private double l2MaxCapacityTonnes = 5.0;
    private double mediumMaxCapacityTonnes = 8.0;

    // Phase 8：四档车辆修正系数属于代理模型参数，不是某辆车的固有数据库属性。
    private double l1VehicleFactor = 0.50;
    private double l2VehicleFactor = 0.85;
    private double mediumVehicleFactor = 1.00;
    private double heavyVehicleFactor = 1.40;

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public double getBaseFuelLitersPerKm() { return baseFuelLitersPerKm; }
    public void setBaseFuelLitersPerKm(double value) { this.baseFuelLitersPerKm = value; }
    public double getDirectEmissionKgPerLiter() { return directEmissionKgPerLiter; }
    public void setDirectEmissionKgPerLiter(double value) { this.directEmissionKgPerLiter = value; }
    public double getLoadFactorBeta() { return loadFactorBeta; }
    public void setLoadFactorBeta(double value) { this.loadFactorBeta = value; }
    public double getEnvironmentFactorGamma() { return environmentFactorGamma; }
    public void setEnvironmentFactorGamma(double value) { this.environmentFactorGamma = value; }
    public double getL1MaxCapacityTonnes() { return l1MaxCapacityTonnes; }
    public void setL1MaxCapacityTonnes(double value) { this.l1MaxCapacityTonnes = value; }
    public double getL2MaxCapacityTonnes() { return l2MaxCapacityTonnes; }
    public void setL2MaxCapacityTonnes(double value) { this.l2MaxCapacityTonnes = value; }
    public double getMediumMaxCapacityTonnes() { return mediumMaxCapacityTonnes; }
    public void setMediumMaxCapacityTonnes(double value) { this.mediumMaxCapacityTonnes = value; }
    public double getL1VehicleFactor() { return l1VehicleFactor; }
    public void setL1VehicleFactor(double value) { this.l1VehicleFactor = value; }
    public double getL2VehicleFactor() { return l2VehicleFactor; }
    public void setL2VehicleFactor(double value) { this.l2VehicleFactor = value; }
    public double getMediumVehicleFactor() { return mediumVehicleFactor; }
    public void setMediumVehicleFactor(double value) { this.mediumVehicleFactor = value; }
    public double getHeavyVehicleFactor() { return heavyVehicleFactor; }
    public void setHeavyVehicleFactor(double value) { this.heavyVehicleFactor = value; }

    /** Phase 8：属性绑定完成后集中拒绝缺口、重叠、负数和非有限配置。 */
    @PostConstruct
    public void validatePolicy() {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("emission modelId must not be blank");
        }
        requirePositiveFinite(baseFuelLitersPerKm, "baseFuelLitersPerKm");
        requirePositiveFinite(directEmissionKgPerLiter, "directEmissionKgPerLiter");
        requireNonNegativeFinite(loadFactorBeta, "loadFactorBeta");
        requireNonNegativeFinite(environmentFactorGamma, "environmentFactorGamma");
        requirePositiveFinite(l1MaxCapacityTonnes, "l1MaxCapacityTonnes");
        requirePositiveFinite(l2MaxCapacityTonnes, "l2MaxCapacityTonnes");
        requirePositiveFinite(mediumMaxCapacityTonnes, "mediumMaxCapacityTonnes");
        if (!(l1MaxCapacityTonnes < l2MaxCapacityTonnes
                && l2MaxCapacityTonnes < mediumMaxCapacityTonnes)) {
            throw new IllegalArgumentException("emission capacity boundaries must be strictly increasing");
        }
        requirePositiveFinite(l1VehicleFactor, "l1VehicleFactor");
        requirePositiveFinite(l2VehicleFactor, "l2VehicleFactor");
        requirePositiveFinite(mediumVehicleFactor, "mediumVehicleFactor");
        requirePositiveFinite(heavyVehicleFactor, "heavyVehicleFactor");
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
}
