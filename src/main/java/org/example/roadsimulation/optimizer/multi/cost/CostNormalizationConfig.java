package org.example.roadsimulation.optimizer.multi.cost;

/**
 * 多运单启发式评价函数配置。
 *
 * 说明：
 * 1. hard penalty 用于强约束，数值应明显大于软目标。
 * 2. soft cost 使用归一化后的无量纲值。
 * 3. 未分配惩罚允许保留尾单，但会随着等待时间/优先级增加而变大。
 */
public class CostNormalizationConfig {

    // ================= 硬约束惩罚 =================

    private double hardConstraintPenalty = 1_000_000.0;

    private double duplicateAssignmentPenalty = 500_000.0;

    private double missingLoadUnloadPenalty = 500_000.0;

    private double lifoViolationPenalty = 500_000.0;

    private double overloadPenalty = 500_000.0;

    private double overVolumePenalty = 500_000.0;

    // ================= 软目标权重 =================

    private double distanceWeight = 0.35;

    private double emptyDistanceWeight = 0.15;

    private double capacityWasteWeight = 0.25;

    private double vehicleCountWeight = 0.15;

    private double lowUtilizationWeight = 0.10;

    // ================= 未分配惩罚 =================

    /**
     * 单个未分配运单基础惩罚。
     * 不是极大值，允许尾单保留。
     */
    private double unassignedBasePenalty = 20.0;

    /**
     * 每等待 1 小时增加的惩罚。
     */
    private double unassignedWaitingHourPenalty = 5.0;

    /**
     * 优先级惩罚权重。
     * 如果当前 ShipmentItem 暂无 priority 字段，可以先保持为 0 或后续接入。
     */
    private double unassignedPriorityPenalty = 10.0;

    // ================= 归一化尺度 =================

    /**
     * 距离归一化尺度，单位 km。
     */
    private double distanceNormKm = 100.0;

    /**
     * 空驶距离归一化尺度，单位 km。
     */
    private double emptyDistanceNormKm = 50.0;

    /**
     * 运能浪费归一化尺度，单位 吨*km。
     */
    private double capacityWasteNormTonKm = 500.0;

    /**
     * 车辆数归一化尺度。
     */
    private double vehicleCountNorm = 10.0;

    /**
     * 低利用率归一化尺度。
     */
    private double lowUtilNorm = 10.0;

    /**
     * 理想最低利用率。
     */
    private double idealUtilization = 0.50;

    public double getHardConstraintPenalty() {
        return hardConstraintPenalty;
    }

    public void setHardConstraintPenalty(double hardConstraintPenalty) {
        this.hardConstraintPenalty = hardConstraintPenalty;
    }

    public double getDuplicateAssignmentPenalty() {
        return duplicateAssignmentPenalty;
    }

    public void setDuplicateAssignmentPenalty(double duplicateAssignmentPenalty) {
        this.duplicateAssignmentPenalty = duplicateAssignmentPenalty;
    }

    public double getMissingLoadUnloadPenalty() {
        return missingLoadUnloadPenalty;
    }

    public void setMissingLoadUnloadPenalty(double missingLoadUnloadPenalty) {
        this.missingLoadUnloadPenalty = missingLoadUnloadPenalty;
    }

    public double getLifoViolationPenalty() {
        return lifoViolationPenalty;
    }

    public void setLifoViolationPenalty(double lifoViolationPenalty) {
        this.lifoViolationPenalty = lifoViolationPenalty;
    }

    public double getOverloadPenalty() {
        return overloadPenalty;
    }

    public void setOverloadPenalty(double overloadPenalty) {
        this.overloadPenalty = overloadPenalty;
    }

    public double getOverVolumePenalty() {
        return overVolumePenalty;
    }

    public void setOverVolumePenalty(double overVolumePenalty) {
        this.overVolumePenalty = overVolumePenalty;
    }

    public double getDistanceWeight() {
        return distanceWeight;
    }

    public void setDistanceWeight(double distanceWeight) {
        this.distanceWeight = distanceWeight;
    }

    public double getEmptyDistanceWeight() {
        return emptyDistanceWeight;
    }

    public void setEmptyDistanceWeight(double emptyDistanceWeight) {
        this.emptyDistanceWeight = emptyDistanceWeight;
    }

    public double getCapacityWasteWeight() {
        return capacityWasteWeight;
    }

    public void setCapacityWasteWeight(double capacityWasteWeight) {
        this.capacityWasteWeight = capacityWasteWeight;
    }

    public double getVehicleCountWeight() {
        return vehicleCountWeight;
    }

    public void setVehicleCountWeight(double vehicleCountWeight) {
        this.vehicleCountWeight = vehicleCountWeight;
    }

    public double getLowUtilizationWeight() {
        return lowUtilizationWeight;
    }

    public void setLowUtilizationWeight(double lowUtilizationWeight) {
        this.lowUtilizationWeight = lowUtilizationWeight;
    }

    public double getUnassignedBasePenalty() {
        return unassignedBasePenalty;
    }

    public void setUnassignedBasePenalty(double unassignedBasePenalty) {
        this.unassignedBasePenalty = unassignedBasePenalty;
    }

    public double getUnassignedWaitingHourPenalty() {
        return unassignedWaitingHourPenalty;
    }

    public void setUnassignedWaitingHourPenalty(double unassignedWaitingHourPenalty) {
        this.unassignedWaitingHourPenalty = unassignedWaitingHourPenalty;
    }

    public double getUnassignedPriorityPenalty() {
        return unassignedPriorityPenalty;
    }

    public void setUnassignedPriorityPenalty(double unassignedPriorityPenalty) {
        this.unassignedPriorityPenalty = unassignedPriorityPenalty;
    }

    public double getDistanceNormKm() {
        return distanceNormKm;
    }

    public void setDistanceNormKm(double distanceNormKm) {
        this.distanceNormKm = distanceNormKm;
    }

    public double getEmptyDistanceNormKm() {
        return emptyDistanceNormKm;
    }

    public void setEmptyDistanceNormKm(double emptyDistanceNormKm) {
        this.emptyDistanceNormKm = emptyDistanceNormKm;
    }

    public double getCapacityWasteNormTonKm() {
        return capacityWasteNormTonKm;
    }

    public void setCapacityWasteNormTonKm(double capacityWasteNormTonKm) {
        this.capacityWasteNormTonKm = capacityWasteNormTonKm;
    }

    public double getVehicleCountNorm() {
        return vehicleCountNorm;
    }

    public void setVehicleCountNorm(double vehicleCountNorm) {
        this.vehicleCountNorm = vehicleCountNorm;
    }

    public double getLowUtilNorm() {
        return lowUtilNorm;
    }

    public void setLowUtilNorm(double lowUtilNorm) {
        this.lowUtilNorm = lowUtilNorm;
    }

    public double getIdealUtilization() {
        return idealUtilization;
    }

    public void setIdealUtilization(double idealUtilization) {
        this.idealUtilization = idealUtilization;
    }
}