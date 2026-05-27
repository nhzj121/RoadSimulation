package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.VehicleCostDTO;
import org.example.roadsimulation.dto.VehicleCostSummaryDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.CostEntity;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 时间的计量单位为 小时
 * 距离的计量单位为 千米
 * 运能的计量单位为 吨千米
 */
@Service
public class GetCostService {

    private Double VehicleType = 1.0;

    private static final double WEIGHT_A = 0.15;
    private static final double WEIGHT_B = 0.15;
    private static final double WEIGHT_C = 0.18;
    private static final double WEIGHT_D = 0.12;
    private static final double WEIGHT_E = 0.12;
    private static final double WEIGHT_G = 0.10;
    private static final double WEIGHT_H = 0.08;
    private static final double WEIGHT_I = 0.10;

    private static final double GLOBAL_WEIGHT_VEHICLE_COST = 0.75;
    private static final double GLOBAL_WEIGHT_UNASSIGNED_COST = 0.25;

    /**
     * 直接成本
     * A ： costA = 0.5 * <所有车辆等待时间> + 0.5 * <所有车辆空驶里程>
     */
    public Double getCostByAllWaitingTimeAndMileageWithoutGoods(){
        return 0.5 * CostEntity.totalWaitingTime + 0.5 * CostEntity.totalMileageWithoutThings;
    }

    /**
     * 效率 + 关注最差情况
     * B：costB = 0.4 * <总空驶里程/总里程> + 0.5 * <总等待时间/总运输时间> + 0.1 * <(最差情况)>
     */
    public Double getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(){
        double mileageRatio = CostEntity.totalMileage == 0.0 ? 0.0 : (CostEntity.totalMileageWithoutThings / CostEntity.totalMileage);
        double timeRatio = CostEntity.totalTransportTime == 0.0 ? 0.0 : (CostEntity.totalWaitingTime / CostEntity.totalTransportTime);

        return 0.4 * mileageRatio
                + 0.5 * timeRatio
                + 0.1 * CostEntity.WorstWaitingTransportTime;
    }

    /**
     * 运能
     * C: costC = 0.9 * <总理论运能 - 总实际运能> + 0.1 * <(最差情况)>
     */
    public Double getCostByAllEffectiveTransportCapacityWithWorst(){
        return 0.9 * (CostEntity.totalTheoryCapacity - CostEntity.totalRealityCapacity)
                + 0.1 * CostEntity.WorstTheoryRealityCapacity;
    }

    /**
     * 经济收益 （这里需要预设油耗，固定损耗）
     * D： costD = 0.5 * <运输油耗> + 0.3 * <固定损耗> + 0.2 * <最差情况>
     */
    public Double getCostByALlOilAndFixedConsumptionWithWorst(){
        double capacityRatio = CostEntity.totalTheoryCapacity == 0.0 ? 0.0 : (CostEntity.totalRealityCapacity / CostEntity.totalTheoryCapacity);

        return 0.5 * VehicleType * capacityRatio
                + 0.3 * (VehicleType * CostEntity.totalWaitingTime + VehicleType * CostEntity.totalTransportTime)
                + 0.2 * CostEntity.WorstLoss;
    }

    /**
     * =========================================================================
     * 新增：边际成本预估器 (Cost Estimator) —— 专供 VRP 启发式算法使用
     * =========================================================================
     * 作用：在不污染全局 CostEntity 的前提下，模拟“如果增加这笔订单”，总成本会发生什么变化。
     * * @param deltaMileage 新增的总行驶里程 (千米)
     * @param deltaMileageWithoutThings 新增的空驶里程 (千米)
     * @param deltaTransportTime 新增的运输时间 (小时)
     * @param deltaWaitingTime 新增的等待时间 (小时)
     * @param deltaTheoryCapacity 新增的理论运能 (车辆最大载重 * 里程)
     * @param deltaRealityCapacity 新增的实际运能 (货物实际重量 * 里程)
     * @param worstTheoryRealityCapacity 模拟的单次最差运能比 (用于更新最差情况记录，可传当前单次的计算结果)
     * @param worstWaitingTransportTime 模拟的单次最差等待比
     * @param worstLoss 模拟的单次最差损耗
     * @return 模拟后的综合预测成本 (数值越小代表越值得拼车)
     */
    public Double estimateMarginalCost(
            Double deltaMileage,
            Double deltaMileageWithoutThings,
            Double deltaTransportTime,
            Double deltaWaitingTime,
            Double deltaTheoryCapacity,
            Double deltaRealityCapacity,
            Double worstTheoryRealityCapacity,
            Double worstWaitingTransportTime,
            Double worstLoss) {

        // 1. 模拟未来的全局状态 (What-If)
        double simTotalMileage = CostEntity.totalMileage + deltaMileage;
        double simTotalMileageWithoutThings = CostEntity.totalMileageWithoutThings + deltaMileageWithoutThings;
        double simTotalTransportTime = CostEntity.totalTransportTime + deltaTransportTime;
        double simTotalWaitingTime = CostEntity.totalWaitingTime + deltaWaitingTime;
        double simTotalTheoryCapacity = CostEntity.totalTheoryCapacity + deltaTheoryCapacity;
        double simTotalRealityCapacity = CostEntity.totalRealityCapacity + deltaRealityCapacity;

        // 模拟最差情况 (如果新订单引发了更差的情况，则用新的，否则保持原有)
        double simWorstTheoryRealityCapacity = Math.max(CostEntity.WorstTheoryRealityCapacity, worstTheoryRealityCapacity);
        double simWorstWaitingTransportTime = Math.max(CostEntity.WorstWaitingTransportTime, worstWaitingTransportTime);
        double simWorstLoss = Math.max(CostEntity.WorstLoss, worstLoss);

        // 2. 预估 Cost A (直接成本)
        double simCostA = 0.5 * simTotalWaitingTime + 0.5 * simTotalMileage;

        // 3. 预估 Cost B (效率)
        double simMileageRatio = simTotalMileage == 0.0 ? 0.0 : (simTotalMileageWithoutThings / simTotalMileage);
        double simTimeRatio = simTotalTransportTime == 0.0 ? 0.0 : (simTotalWaitingTime / simTotalTransportTime);
        double simCostB = 0.4 * simMileageRatio + 0.5 * simTimeRatio + 0.1 * simWorstWaitingTransportTime;

        // 4. 预估 Cost C (运能)
        double simCostC = 0.9 * (simTotalTheoryCapacity - simTotalRealityCapacity) + 0.1 * simWorstTheoryRealityCapacity;

        // 5. 预估 Cost D (经济)
        // 注意：在你原有的公式中，Cost D 的 capacityRatio 是加项。如果是成本惩罚，这里通常是减去收益。
        // 但为了与你现有的计算逻辑保持绝对一致，这里沿用你的公式结构。
        double simCapacityRatio = simTotalTheoryCapacity == 0.0 ? 0.0 : (simTotalRealityCapacity / simTotalTheoryCapacity);
        double simCostD = 0.5 * VehicleType * simCapacityRatio
                + 0.3 * (VehicleType * simTotalWaitingTime + VehicleType * simTotalTransportTime)
                + 0.2 * simWorstLoss;

        // 6. 计算总预测代价（你可以根据业务侧重点给 A、B、C、D 赋予不同的外层权重）
        // 这里默认它们权重为 1:1:1:1
        return simCostA + simCostB + simCostC + simCostD;
    }

    /**
     * 计算每辆车的 CostA~E 和加权总成本。
     * 归一化采用当前车辆集合的动态 Min-Max，不使用固定范围或“单车成本 / 全车队成本总和”的占比归一化。
     */
    public VehicleCostSummaryDTO calculateVehicleCostSummary(List<Vehicle> vehicles) {
        return calculateVehicleCostSummary(vehicles, List.of(), 0L, 0L);
    }

    public VehicleCostSummaryDTO calculateVehicleCostSummary(List<Vehicle> vehicles,
                                                             List<Assignment> assignments,
                                                             Long totalTaskCount,
                                                             Long unassignedTaskCount) {
        List<Vehicle> safeVehicles = vehicles == null ? List.of() : vehicles;
        Map<Long, List<Assignment>> assignmentsByVehicleId = groupAssignmentsByVehicle(assignments);
        double averageWorkload = calculateAverageWorkload(safeVehicles);

        List<VehicleCostDTO> vehicleCosts = new ArrayList<>();
        CostRange costRange = new CostRange();

        for (Vehicle vehicle : safeVehicles) {
            List<Assignment> vehicleAssignments = assignmentsByVehicleId.getOrDefault(
                    vehicle == null ? null : vehicle.getId(),
                    List.of()
            );
            VehicleCostDTO dto = calculateRawVehicleCost(vehicle, averageWorkload, vehicleAssignments);
            vehicleCosts.add(dto);
            costRange.accept(dto);
        }

        double totalCostSum = 0.0;

        for (VehicleCostDTO dto : vehicleCosts) {
            applyNormalizedCosts(dto, costRange);
            totalCostSum += safe(dto.getTotalCost());
        }

        VehicleCostSummaryDTO summary = new VehicleCostSummaryDTO();
        summary.setWeightA(WEIGHT_A);
        summary.setWeightB(WEIGHT_B);
        summary.setWeightC(WEIGHT_C);
        summary.setWeightD(WEIGHT_D);
        summary.setWeightE(WEIGHT_E);
        summary.setWeightG(WEIGHT_G);
        summary.setWeightH(WEIGHT_H);
        summary.setWeightI(WEIGHT_I);
        summary.setGlobalWeightVehicleCost(GLOBAL_WEIGHT_VEHICLE_COST);
        summary.setGlobalWeightUnassignedCost(GLOBAL_WEIGHT_UNASSIGNED_COST);
        summary.setCostAMin(costRange.minA());
        summary.setCostAMax(costRange.maxA());
        summary.setCostBMin(costRange.minB());
        summary.setCostBMax(costRange.maxB());
        summary.setCostCMin(costRange.minC());
        summary.setCostCMax(costRange.maxC());
        summary.setCostDMin(costRange.minD());
        summary.setCostDMax(costRange.maxD());
        summary.setCostEMin(costRange.minE());
        summary.setCostEMax(costRange.maxE());
        summary.setCostGMin(costRange.minG());
        summary.setCostGMax(costRange.maxG());
        summary.setCostHMin(costRange.minH());
        summary.setCostHMax(costRange.maxH());
        summary.setCostIMin(costRange.minI());
        summary.setCostIMax(costRange.maxI());
        summary.setVehicleCount(vehicleCosts.size());
        summary.setTotalTaskCount(totalTaskCount == null ? 0L : totalTaskCount);
        summary.setUnassignedTaskCount(unassignedTaskCount == null ? 0L : unassignedTaskCount);
        double unassignedTaskCost = safeDivide(summary.getUnassignedTaskCount(), summary.getTotalTaskCount());
        summary.setUnassignedTaskCost(clamp01(unassignedTaskCost));
        double averageTotalCost = vehicleCosts.isEmpty() ? 0.0 : totalCostSum / vehicleCosts.size();
        summary.setAverageTotalCost(averageTotalCost);
        summary.setGlobalCost(GLOBAL_WEIGHT_VEHICLE_COST * averageTotalCost
                + GLOBAL_WEIGHT_UNASSIGNED_COST * summary.getUnassignedTaskCost());
        summary.setVehicleCosts(vehicleCosts);
        return summary;
    }

    public VehicleCostDTO calculateVehicleCost(Vehicle vehicle, double averageWorkload) {
        VehicleCostDTO dto = calculateRawVehicleCost(vehicle, averageWorkload, List.of());
        CostRange singleVehicleRange = new CostRange();
        singleVehicleRange.accept(dto);
        applyNormalizedCosts(dto, singleVehicleRange);
        return dto;
    }

    private VehicleCostDTO calculateRawVehicleCost(Vehicle vehicle,
                                                  double averageWorkload,
                                                  List<Assignment> assignments) {
        double waitingHours = getVehicleWaitingHours(vehicle);
        double transportHours = safe(vehicle == null ? null : vehicle.getTotalDrivingTime()) / 3600.0;
        double emptyDistance = safe(vehicle == null ? null : vehicle.getEmptyDrivingDistance());
        double totalDistance = safe(vehicle == null ? null : vehicle.getTotalDrivingDistance());
        double actualLoad = safe(vehicle == null ? null : vehicle.getCurrentLoad());
        double theoryCapacity = safe(vehicle == null ? null : vehicle.getMaxLoadCapacity()) * Math.max(0.0, totalDistance - emptyDistance);
        double actualCapacity = actualLoad * Math.max(0.0, totalDistance - emptyDistance);
        double workload = getVehicleWorkload(vehicle);

        double costA = 0.5 * waitingHours + 0.5 * emptyDistance;

        double emptyMileageRatio = safeDivide(emptyDistance, totalDistance);
        double waitingTransportRatio = safeDivide(waitingHours, transportHours);
        double worstWaitingProxy = waitingTransportRatio;
        double costB = 0.4 * emptyMileageRatio + 0.5 * waitingTransportRatio + 0.1 * worstWaitingProxy;

        double capacityUtilization = safeDivide(actualCapacity, theoryCapacity);
        double costC = clamp01(1.0 - capacityUtilization);

        double utilizationWasteCost = 1.0 - clamp01(capacityUtilization);
        double worstLossProxy = utilizationWasteCost + waitingTransportRatio;
        double costD = 0.4 * utilizationWasteCost
                + 0.4 * (waitingHours + transportHours)
                + 0.2 * worstLossProxy;

        double costE = averageWorkload <= 0.0
                ? 0.0
                : Math.abs(workload - averageWorkload) / averageWorkload;
        RouteDistance routeDistance = calculateRouteDistance(assignments);
        double costG = routeDistance.baseDistance <= 0.0
                ? 0.0
                : Math.max(0.0, routeDistance.actualDistance - routeDistance.baseDistance) / routeDistance.baseDistance;
        double actualVolume = calculateActualVolume(assignments);
        double cargoVolume = safe(vehicle == null ? null : vehicle.getCargoVolume());
        double costH = cargoVolume <= 0.0 ? 0.0 : clamp01(1.0 - safeDivide(actualVolume, cargoVolume));
        TimeOverrun timeOverrun = calculateTimeOverrun(assignments);
        double costI = timeOverrun.estimatedHours <= 0.0
                ? 0.0
                : Math.max(0.0, timeOverrun.actualHours - timeOverrun.estimatedHours) / timeOverrun.estimatedHours;

        VehicleCostDTO dto = new VehicleCostDTO();
        dto.setVehicleId(vehicle == null ? null : vehicle.getId());
        dto.setLicensePlate(vehicle == null ? null : vehicle.getLicensePlate());
        dto.setCostA(costA);
        dto.setCostB(costB);
        dto.setCostC(costC);
        dto.setCostD(costD);
        dto.setCostE(costE);
        dto.setCostG(costG);
        dto.setCostH(costH);
        dto.setCostI(costI);
        dto.setTotalWaitingHours(waitingHours);
        dto.setTotalTransportHours(transportHours);
        dto.setEmptyDistanceKm(emptyDistance);
        dto.setTotalDistanceKm(totalDistance);
        dto.setTheoryCapacity(theoryCapacity);
        dto.setActualCapacity(actualCapacity);
        dto.setWorkload(workload);
        dto.setAverageWorkload(averageWorkload);
        dto.setActualRouteDistanceKm(routeDistance.actualDistance);
        dto.setBaseRouteDistanceKm(routeDistance.baseDistance);
        dto.setActualVolume(actualVolume);
        dto.setCargoVolume(cargoVolume);
        dto.setActualAssignmentHours(timeOverrun.actualHours);
        dto.setEstimatedAssignmentHours(timeOverrun.estimatedHours);
        return dto;
    }

    private void applyNormalizedCosts(VehicleCostDTO dto, CostRange range) {
        double normalizedCostA = minMaxNormalize(safe(dto.getCostA()), range.minA(), range.maxA());
        double normalizedCostB = minMaxNormalize(safe(dto.getCostB()), range.minB(), range.maxB());
        double normalizedCostC = minMaxNormalize(safe(dto.getCostC()), range.minC(), range.maxC());
        double normalizedCostD = minMaxNormalize(safe(dto.getCostD()), range.minD(), range.maxD());
        double normalizedCostE = minMaxNormalize(safe(dto.getCostE()), range.minE(), range.maxE());
        double normalizedCostG = minMaxNormalize(safe(dto.getCostG()), range.minG(), range.maxG());
        double normalizedCostH = minMaxNormalize(safe(dto.getCostH()), range.minH(), range.maxH());
        double normalizedCostI = minMaxNormalize(safe(dto.getCostI()), range.minI(), range.maxI());

        double totalCost = WEIGHT_A * normalizedCostA
                + WEIGHT_B * normalizedCostB
                + WEIGHT_C * normalizedCostC
                + WEIGHT_D * normalizedCostD
                + WEIGHT_E * normalizedCostE
                + WEIGHT_G * normalizedCostG
                + WEIGHT_H * normalizedCostH
                + WEIGHT_I * normalizedCostI;

        dto.setNormalizedCostA(normalizedCostA);
        dto.setNormalizedCostB(normalizedCostB);
        dto.setNormalizedCostC(normalizedCostC);
        dto.setNormalizedCostD(normalizedCostD);
        dto.setNormalizedCostE(normalizedCostE);
        dto.setNormalizedCostG(normalizedCostG);
        dto.setNormalizedCostH(normalizedCostH);
        dto.setNormalizedCostI(normalizedCostI);
        dto.setTotalCost(totalCost);
    }

    private Map<Long, List<Assignment>> groupAssignmentsByVehicle(List<Assignment> assignments) {
        Map<Long, List<Assignment>> grouped = new HashMap<>();
        if (assignments == null) {
            return grouped;
        }

        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.getAssignedVehicle() == null || assignment.getAssignedVehicle().getId() == null) {
                continue;
            }

            Long vehicleId = assignment.getAssignedVehicle().getId();
            grouped.computeIfAbsent(vehicleId, ignored -> new ArrayList<>()).add(assignment);
        }
        return grouped;
    }

    private RouteDistance calculateRouteDistance(List<Assignment> assignments) {
        double actualDistance = 0.0;
        double baseDistance = 0.0;

        if (assignments == null) {
            return new RouteDistance(actualDistance, baseDistance);
        }

        for (Assignment assignment : assignments) {
            if (assignment == null) {
                continue;
            }

            double assignmentActual = safe(assignment.getTotalDrivingDistance());
            double assignmentBase = assignment.getRoute() == null ? 0.0 : safe(assignment.getRoute().getDistance());

            if (assignmentActual <= 0.0) {
                assignmentActual = assignmentBase + safe(assignment.getEmptyDrivingDistance());
            }
            if (assignmentBase <= 0.0) {
                assignmentBase = Math.max(0.0, assignmentActual - safe(assignment.getEmptyDrivingDistance()));
            }

            actualDistance += assignmentActual;
            baseDistance += assignmentBase;
        }

        return new RouteDistance(actualDistance, baseDistance);
    }

    private double calculateActualVolume(List<Assignment> assignments) {
        double totalVolume = 0.0;
        if (assignments == null) {
            return totalVolume;
        }

        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.getShipmentItems() == null) {
                continue;
            }

            for (ShipmentItem item : assignment.getShipmentItems()) {
                if (item == null) {
                    continue;
                }
                totalVolume += safe(item.getVolume());
            }
        }
        return totalVolume;
    }

    private TimeOverrun calculateTimeOverrun(List<Assignment> assignments) {
        double actualHours = 0.0;
        double estimatedHours = 0.0;

        if (assignments == null) {
            return new TimeOverrun(actualHours, estimatedHours);
        }

        for (Assignment assignment : assignments) {
            if (assignment == null
                    || assignment.getStartTime() == null
                    || assignment.getEndTime() == null
                    || assignment.getRoute() == null
                    || assignment.getRoute().getEstimatedTime() == null
                    || assignment.getRoute().getEstimatedTime() <= 0.0) {
                continue;
            }

            double actual = Duration.between(assignment.getStartTime(), assignment.getEndTime()).toSeconds() / 3600.0;
            if (actual < 0.0) {
                continue;
            }

            actualHours += actual;
            estimatedHours += assignment.getRoute().getEstimatedTime();
        }

        return new TimeOverrun(actualHours, estimatedHours);
    }

    public double minMaxNormalize(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }

        return clamp01((value - min) / (max - min));
    }

    public double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    private double calculateAverageWorkload(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return 0.0;
        }

        double totalWorkload = 0.0;
        for (Vehicle vehicle : vehicles) {
            totalWorkload += getVehicleWorkload(vehicle);
        }

        return totalWorkload / vehicles.size();
    }

    private double getVehicleWorkload(Vehicle vehicle) {
        double drivingHours = safe(vehicle == null ? null : vehicle.getTotalDrivingTime()) / 3600.0;
        return drivingHours + 0.5 * getVehicleWaitingHours(vehicle);
    }

    private double getVehicleWaitingHours(Vehicle vehicle) {
        if (vehicle == null) {
            return 0.0;
        }

        double waitingSeconds = safe(vehicle.getLoadingWaitTime())
                + safe(vehicle.getUnloadingWaitTime())
                + safe(vehicle.getWaitingAssignmentTime());
        return waitingSeconds / 3600.0;
    }

    private double safe(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return value;
    }

    private double safe(Long value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double safeDivide(double numerator, double denominator) {
        if (denominator <= 0.0) {
            return 0.0;
        }
        return numerator / denominator;
    }

    private static class CostRange {
        private double minA = Double.POSITIVE_INFINITY;
        private double maxA = Double.NEGATIVE_INFINITY;
        private double minB = Double.POSITIVE_INFINITY;
        private double maxB = Double.NEGATIVE_INFINITY;
        private double minC = Double.POSITIVE_INFINITY;
        private double maxC = Double.NEGATIVE_INFINITY;
        private double minD = Double.POSITIVE_INFINITY;
        private double maxD = Double.NEGATIVE_INFINITY;
        private double minE = Double.POSITIVE_INFINITY;
        private double maxE = Double.NEGATIVE_INFINITY;
        private double minG = Double.POSITIVE_INFINITY;
        private double maxG = Double.NEGATIVE_INFINITY;
        private double minH = Double.POSITIVE_INFINITY;
        private double maxH = Double.NEGATIVE_INFINITY;
        private double minI = Double.POSITIVE_INFINITY;
        private double maxI = Double.NEGATIVE_INFINITY;

        void accept(VehicleCostDTO dto) {
            minA = Math.min(minA, safeRangeValue(dto.getCostA()));
            maxA = Math.max(maxA, safeRangeValue(dto.getCostA()));
            minB = Math.min(minB, safeRangeValue(dto.getCostB()));
            maxB = Math.max(maxB, safeRangeValue(dto.getCostB()));
            minC = Math.min(minC, safeRangeValue(dto.getCostC()));
            maxC = Math.max(maxC, safeRangeValue(dto.getCostC()));
            minD = Math.min(minD, safeRangeValue(dto.getCostD()));
            maxD = Math.max(maxD, safeRangeValue(dto.getCostD()));
            minE = Math.min(minE, safeRangeValue(dto.getCostE()));
            maxE = Math.max(maxE, safeRangeValue(dto.getCostE()));
            minG = Math.min(minG, safeRangeValue(dto.getCostG()));
            maxG = Math.max(maxG, safeRangeValue(dto.getCostG()));
            minH = Math.min(minH, safeRangeValue(dto.getCostH()));
            maxH = Math.max(maxH, safeRangeValue(dto.getCostH()));
            minI = Math.min(minI, safeRangeValue(dto.getCostI()));
            maxI = Math.max(maxI, safeRangeValue(dto.getCostI()));
        }

        double minA() { return finiteOrZero(minA); }
        double maxA() { return finiteOrZero(maxA); }
        double minB() { return finiteOrZero(minB); }
        double maxB() { return finiteOrZero(maxB); }
        double minC() { return finiteOrZero(minC); }
        double maxC() { return finiteOrZero(maxC); }
        double minD() { return finiteOrZero(minD); }
        double maxD() { return finiteOrZero(maxD); }
        double minE() { return finiteOrZero(minE); }
        double maxE() { return finiteOrZero(maxE); }
        double minG() { return finiteOrZero(minG); }
        double maxG() { return finiteOrZero(maxG); }
        double minH() { return finiteOrZero(minH); }
        double maxH() { return finiteOrZero(maxH); }
        double minI() { return finiteOrZero(minI); }
        double maxI() { return finiteOrZero(maxI); }

        private static double safeRangeValue(Double value) {
            if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
                return 0.0;
            }
            return value;
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0.0;
        }
    }

    private static class RouteDistance {
        private final double actualDistance;
        private final double baseDistance;

        private RouteDistance(double actualDistance, double baseDistance) {
            this.actualDistance = actualDistance;
            this.baseDistance = baseDistance;
        }
    }

    private static class TimeOverrun {
        private final double actualHours;
        private final double estimatedHours;

        private TimeOverrun(double actualHours, double estimatedHours) {
            this.actualHours = actualHours;
            this.estimatedHours = estimatedHours;
        }
    }
}
