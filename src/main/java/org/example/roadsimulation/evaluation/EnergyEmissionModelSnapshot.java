package org.example.roadsimulation.evaluation;

/** Phase 8：随评价快照冻结的完整能耗与排放代理模型参数。 */
public record EnergyEmissionModelSnapshot(
        String modelId,
        String energyUnit,
        double baseFuelLitersPerKm,
        double directEmissionKgPerLiter,
        double loadFactorBeta,
        double environmentFactorGamma,
        double l1MaxCapacityTonnes,
        double l2MaxCapacityTonnes,
        double mediumMaxCapacityTonnes,
        double l1VehicleFactor,
        double l2VehicleFactor,
        double mediumVehicleFactor,
        double heavyVehicleFactor
) {
}
