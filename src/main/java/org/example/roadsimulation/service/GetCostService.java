package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.RuntimeCostDetailDTO;
import org.example.roadsimulation.dto.RuntimeCostDTO;
import org.example.roadsimulation.dto.VehicleCostDTO;
import org.example.roadsimulation.dto.VehicleCostSummaryDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.CostEntity;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 时间的计量单位为 小时
 * 距离的计量单位为 千米
 * 运能的计量单位为 吨千米
 */
@Service
public class GetCostService {

    private Double VehicleType = 1.0;

    @Autowired
    private SimulationContext simulationContext;

    private static final double WEIGHT_A = 0.15;
    private static final double WEIGHT_B = 0.15;
    private static final double WEIGHT_C = 0.18;
    private static final double WEIGHT_D = 0.12;
    private static final double WEIGHT_E = 0.12;
    private static final double WEIGHT_G = 0.10;
    private static final double WEIGHT_H = 0.08;
    private static final double WEIGHT_I = 0.10;
    private static final double ALL_COST_WEIGHT_SUM = WEIGHT_A + WEIGHT_B + WEIGHT_C + WEIGHT_D + WEIGHT_E;

    // /api/simulation/costs 的实时 AllCost 使用 A-E 等权；车辆汇总口径继续使用上方 A-I 权重。
    private static final double RUNTIME_WEIGHT_A = 0.20;
    private static final double RUNTIME_WEIGHT_B = 0.20;
    private static final double RUNTIME_WEIGHT_C = 0.20;
    private static final double RUNTIME_WEIGHT_D = 0.20;
    private static final double RUNTIME_WEIGHT_E = 0.20;
    private static final double RUNTIME_ALL_COST_WEIGHT_SUM = RUNTIME_WEIGHT_A
            + RUNTIME_WEIGHT_B
            + RUNTIME_WEIGHT_C
            + RUNTIME_WEIGHT_D
            + RUNTIME_WEIGHT_E;

    private static final double GLOBAL_WEIGHT_VEHICLE_COST = 0.75;
    private static final double GLOBAL_WEIGHT_UNASSIGNED_COST = 0.25;
    private static final double IDLE_WAIT_TARGET_HOURS = 2.0;
    private static final double IDLE_WAIT_WORST_HOURS = 4.0;
    private static final double IDLE_ECON_TARGET_HOURS = 2.0;

    /**
     * 直接成本
     * A ： costA = 0.5 * <所有车辆等待时间> + 0.5 * <所有车辆空驶里程>
     */
    public Double getCostByAllWaitingTimeAndMileageWithoutGoods(){
        return 0.5 * CostEntity.totalWaitingTime + 0.5 * CostEntity.totalMileageWithoutThings;
    }

    /**
     * 效率 + 关注最差情况
     * B：costB = 0.40 * <总空驶里程/总里程>
     *          + 0.35 * <已派单等待时间/总运输时间>
     *          + 0.10 * <最差等待运输比>
     *          + 0.15 * <未分配车辆空等惩罚>
     */
    public Double getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(){
        return getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(new IdleVehicleCostStats());
    }

    private Double getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(IdleVehicleCostStats idleStats){
        IdleVehicleCostStats safeIdleStats = idleStats == null ? new IdleVehicleCostStats() : idleStats;
        double mileageRatio = CostEntity.totalMileage == 0.0 ? 0.0 : (CostEntity.totalMileageWithoutThings / CostEntity.totalMileage);
        double assignedWaitingTransportRatio = CostEntity.totalTransportTime == 0.0 ? 0.0 : (CostEntity.totalWaitingTime / CostEntity.totalTransportTime);

        return 0.4 * mileageRatio
                + 0.35 * assignedWaitingTransportRatio
                + 0.1 * CostEntity.WorstWaitingTransportTime
                + 0.15 * safeIdleStats.idleWaitPenalty;
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
     * D： costD = 0.50 * <运输油耗代理>
     *           + 0.20 * <已派单时间经济损耗>
     *           + 0.10 * <未分配车辆空间经济损耗>
     *           + 0.20 * <最差情况>
     */
    public Double getCostByALlOilAndFixedConsumptionWithWorst(){
        return getCostByALlOilAndFixedConsumptionWithWorst(new IdleVehicleCostStats());
    }

    private Double getCostByALlOilAndFixedConsumptionWithWorst(IdleVehicleCostStats idleStats){
        IdleVehicleCostStats safeIdleStats = idleStats == null ? new IdleVehicleCostStats() : idleStats;
        double capacityRatio = CostEntity.totalTheoryCapacity == 0.0 ? 0.0 : (CostEntity.totalRealityCapacity / CostEntity.totalTheoryCapacity);
        double assignedTimeEconomicLoss = VehicleType * CostEntity.totalWaitingTime + VehicleType * CostEntity.totalTransportTime;

        return 0.5 * VehicleType * capacityRatio
                + 0.2 * assignedTimeEconomicLoss
                + 0.1 * safeIdleStats.idleSpaceEconomicLoss
                + 0.2 * CostEntity.WorstLoss;
    }

    public RuntimeCostDTO calculateRuntimeCosts(List<Vehicle> vehicles) {
        return calculateRuntimeCosts(vehicles, List.of());
    }

    public RuntimeCostDTO calculateRuntimeCosts(List<Vehicle> vehicles, List<Assignment> activeAssignments) {
        IdleVehicleCostStats idleStats = calculateIdleVehicleCostStats(vehicles, activeAssignments);
        double costA = getCostByAllWaitingTimeAndMileageWithoutGoods();
        double costB = getCostByAllEffectiveTimeAndEffectiveMileageWithWorst(idleStats);
        double costC = getCostByAllEffectiveTransportCapacityWithWorst();
        double costD = getCostByALlOilAndFixedConsumptionWithWorst(idleStats);
        double costE = getCostByVehicleWorkloadBalance(vehicles);
        double allCost = calculateAllCost(costA, costB, costC, costD, costE);

        return new RuntimeCostDTO(costA, costB, costC, costD, costE, allCost);
    }

    public RuntimeCostDetailDTO calculateRuntimeCostDetail(List<Vehicle> vehicles, List<Assignment> activeAssignments) {
        IdleVehicleCostStats idleStats = calculateIdleVehicleCostStats(vehicles, activeAssignments);
        WorkloadStats workloadStats = calculateRuntimeWorkloadStats(vehicles);

        double totalWaitingHours = safe(CostEntity.totalWaitingTime);
        double totalEmptyDistanceKm = safe(CostEntity.totalMileageWithoutThings);
        double emptyMileageRatio = safeDivide(totalEmptyDistanceKm, safe(CostEntity.totalMileage));
        double assignedWaitingTransportRatio = safeDivide(totalWaitingHours, safe(CostEntity.totalTransportTime));
        double theoryActualCapacityGap = safe(CostEntity.totalTheoryCapacity) - safe(CostEntity.totalRealityCapacity);
        double capacityRatio = safeDivide(safe(CostEntity.totalRealityCapacity), safe(CostEntity.totalTheoryCapacity));
        double assignedTimeEconomicLoss = VehicleType * totalWaitingHours + VehicleType * safe(CostEntity.totalTransportTime);

        RuntimeCostDetailDTO.CostADetail costA = new RuntimeCostDetailDTO.CostADetail();
        costA.setTotalWaitingHours(totalWaitingHours);
        costA.setTotalEmptyDistanceKm(totalEmptyDistanceKm);
        costA.setWaitingCostContribution(0.5 * totalWaitingHours);
        costA.setEmptyDistanceCostContribution(0.5 * totalEmptyDistanceKm);

        RuntimeCostDetailDTO.CostBDetail costB = new RuntimeCostDetailDTO.CostBDetail();
        costB.setEmptyMileageRatio(emptyMileageRatio);
        costB.setAssignedWaitingTransportRatio(assignedWaitingTransportRatio);
        costB.setWorstWaitingTransportRatio(safe(CostEntity.WorstWaitingTransportTime));
        costB.setIdleWaitPenalty(idleStats.idleWaitPenalty);
        costB.setEmptyMileageContribution(0.4 * emptyMileageRatio);
        costB.setWaitingTransportContribution(0.35 * assignedWaitingTransportRatio);
        costB.setWorstWaitingContribution(0.1 * safe(CostEntity.WorstWaitingTransportTime));
        costB.setIdleWaitContribution(0.15 * idleStats.idleWaitPenalty);

        RuntimeCostDetailDTO.CostCDetail costC = new RuntimeCostDetailDTO.CostCDetail();
        costC.setTotalTheoryCapacity(safe(CostEntity.totalTheoryCapacity));
        costC.setTotalActualCapacity(safe(CostEntity.totalRealityCapacity));
        costC.setTheoryActualCapacityGap(theoryActualCapacityGap);
        costC.setWorstTheoryRealityCapacity(safe(CostEntity.WorstTheoryRealityCapacity));
        costC.setCapacityGapContribution(0.9 * theoryActualCapacityGap);
        costC.setWorstCapacityContribution(0.1 * safe(CostEntity.WorstTheoryRealityCapacity));

        RuntimeCostDetailDTO.CostDDetail costD = new RuntimeCostDetailDTO.CostDDetail();
        costD.setCapacityRatio(capacityRatio);
        costD.setUtilizationWasteCost(VehicleType * capacityRatio);
        costD.setAssignedTimeEconomicLoss(assignedTimeEconomicLoss);
        costD.setIdleSpaceEconomicLoss(idleStats.idleSpaceEconomicLoss);
        costD.setWorstEconomicLoss(safe(CostEntity.WorstLoss));
        costD.setUtilizationWasteContribution(0.5 * VehicleType * capacityRatio);
        costD.setAssignedTimeContribution(0.2 * assignedTimeEconomicLoss);
        costD.setIdleSpaceContribution(0.1 * idleStats.idleSpaceEconomicLoss);
        costD.setWorstEconomicContribution(0.2 * safe(CostEntity.WorstLoss));

        RuntimeCostDetailDTO.CostEDetail costE = new RuntimeCostDetailDTO.CostEDetail();
        costE.setAverageWorkload(workloadStats.averageWorkload);
        costE.setWorkloadStandardDeviation(workloadStats.standardDeviation);
        costE.setWorkloadVariationCoefficient(workloadStats.variationCoefficient);

        RuntimeCostDTO summary = new RuntimeCostDTO(
                costA.getWaitingCostContribution() + costA.getEmptyDistanceCostContribution(),
                costB.getEmptyMileageContribution()
                        + costB.getWaitingTransportContribution()
                        + costB.getWorstWaitingContribution()
                        + costB.getIdleWaitContribution(),
                costC.getCapacityGapContribution() + costC.getWorstCapacityContribution(),
                costD.getUtilizationWasteContribution()
                        + costD.getAssignedTimeContribution()
                        + costD.getIdleSpaceContribution()
                        + costD.getWorstEconomicContribution(),
                costE.getWorkloadVariationCoefficient(),
                0.0
        );
        summary.setAllCost(calculateAllCost(
                summary.getCostA(),
                summary.getCostB(),
                summary.getCostC(),
                summary.getCostD(),
                summary.getCostE()
        ));

        RuntimeCostDetailDTO detail = new RuntimeCostDetailDTO();
        detail.setGeneratedAt(LocalDateTime.now());
        detail.setSummary(summary);
        detail.setCostA(costA);
        detail.setCostB(costB);
        detail.setCostC(costC);
        detail.setCostD(costD);
        detail.setCostE(costE);
        return detail;
    }

    /**
     * 车队负载均衡成本。
     * E = 工作量标准差 / 平均工作量，工作量 = 总行驶时间 + 0.5 * 总等待时间。
     */
    public Double getCostByVehicleWorkloadBalance(List<Vehicle> vehicles) {
        List<Vehicle> safeVehicles = vehicles == null ? List.of() : vehicles;
        if (safeVehicles.isEmpty()) {
            return 0.0;
        }
        if (simulationContext != null
                && !simulationContext.isRunning()
                && simulationContext.getLoopCount() == 0) {
            return 0.0;
        }

        List<Double> workloads = new ArrayList<>();
        LocalDateTime simNow = simulationContext == null ? null : simulationContext.getCurrentSimTime();
        for (Vehicle vehicle : safeVehicles) {
            workloads.add(getVehicleRuntimeWorkload(vehicle, simNow));
        }
        return calculateVariationCoefficient(workloads);
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
        double simCostA = 0.5 * simTotalWaitingTime + 0.5 * simTotalMileageWithoutThings;

        // 3. 预估 Cost B (效率)
        double simMileageRatio = simTotalMileage == 0.0 ? 0.0 : (simTotalMileageWithoutThings / simTotalMileage);
        double simTimeRatio = simTotalTransportTime == 0.0 ? 0.0 : (simTotalWaitingTime / simTotalTransportTime);
        double simCostB = 0.4 * simMileageRatio + 0.35 * simTimeRatio + 0.1 * simWorstWaitingTransportTime;

        // 4. 预估 Cost C (运能)
        double simCostC = 0.9 * (simTotalTheoryCapacity - simTotalRealityCapacity) + 0.1 * simWorstTheoryRealityCapacity;

        // 5. 预估 Cost D (经济)
        // 注意：在你原有的公式中，Cost D 的 capacityRatio 是加项。如果是成本惩罚，这里通常是减去收益。
        // 但为了与你现有的计算逻辑保持绝对一致，这里沿用你的公式结构。
        double simCapacityRatio = simTotalTheoryCapacity == 0.0 ? 0.0 : (simTotalRealityCapacity / simTotalTheoryCapacity);
        double simCostD = 0.5 * VehicleType * simCapacityRatio
                + 0.2 * (VehicleType * simTotalWaitingTime + VehicleType * simTotalTransportTime)
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

        for (VehicleCostDTO dto : vehicleCosts) {
            applyRawVehicleCost(dto);
        }

        SchemeCostSnapshot schemeCost = calculateSchemeCost(vehicleCosts);

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
        double totalCostSum = 0.0;
        for (VehicleCostDTO dto : vehicleCosts) {
            totalCostSum += safe(dto.getTotalCost());
        }
        double averageTotalCost = vehicleCosts.isEmpty() ? 0.0 : totalCostSum / vehicleCosts.size();
        summary.setAverageTotalCost(averageTotalCost);
        applySchemeCosts(summary, schemeCost);
        summary.setVehicleCosts(vehicleCosts);
        normalizeSchemeCosts(List.of(summary));
        return summary;
    }

    public VehicleCostDTO calculateVehicleCost(Vehicle vehicle, double averageWorkload) {
        VehicleCostDTO dto = calculateRawVehicleCost(vehicle, averageWorkload, List.of());
        applyRawVehicleCost(dto);
        return dto;
    }

    public List<VehicleCostSummaryDTO> normalizeSchemeCosts(List<VehicleCostSummaryDTO> summaries) {
        List<VehicleCostSummaryDTO> safeSummaries = summaries == null ? List.of() : summaries;
        SchemeCostRange range = new SchemeCostRange();
        for (VehicleCostSummaryDTO summary : safeSummaries) {
            range.accept(summary);
        }

        String scope = safeSummaries.size() <= 1
                ? "SINGLE_SCHEME_NO_CROSS_NORMALIZATION_BASELINE"
                : "CROSS_SCHEME_MIN_MAX";

        for (VehicleCostSummaryDTO summary : safeSummaries) {
            double normalizedA = minMaxNormalize(safe(summary.getSchemeCostA()), range.minA(), range.maxA());
            double normalizedB = minMaxNormalize(safe(summary.getSchemeCostB()), range.minB(), range.maxB());
            double normalizedC = minMaxNormalize(safe(summary.getSchemeCostC()), range.minC(), range.maxC());
            double normalizedD = minMaxNormalize(safe(summary.getSchemeCostD()), range.minD(), range.maxD());
            double normalizedE = minMaxNormalize(safe(summary.getSchemeCostE()), range.minE(), range.maxE());
            double normalizedG = minMaxNormalize(safe(summary.getSchemeCostG()), range.minG(), range.maxG());
            double normalizedH = minMaxNormalize(safe(summary.getSchemeCostH()), range.minH(), range.maxH());
            double normalizedI = minMaxNormalize(safe(summary.getSchemeCostI()), range.minI(), range.maxI());

            double allCost = (WEIGHT_A * normalizedA
                    + WEIGHT_B * normalizedB
                    + WEIGHT_C * normalizedC
                    + WEIGHT_D * normalizedD
                    + WEIGHT_E * normalizedE) / ALL_COST_WEIGHT_SUM;

            summary.setNormalizedSchemeCostA(normalizedA);
            summary.setNormalizedSchemeCostB(normalizedB);
            summary.setNormalizedSchemeCostC(normalizedC);
            summary.setNormalizedSchemeCostD(normalizedD);
            summary.setNormalizedSchemeCostE(normalizedE);
            summary.setNormalizedSchemeCostG(normalizedG);
            summary.setNormalizedSchemeCostH(normalizedH);
            summary.setNormalizedSchemeCostI(normalizedI);
            summary.setAllCost(allCost);
            summary.setGlobalCost(GLOBAL_WEIGHT_VEHICLE_COST * allCost
                    + GLOBAL_WEIGHT_UNASSIGNED_COST * safe(summary.getUnassignedTaskCost()));
            summary.setNormalizationScope(scope);
        }

        return safeSummaries;
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

    private void applyRawVehicleCost(VehicleCostDTO dto) {
        double rawTotalCost = (WEIGHT_A * safe(dto.getCostA())
                + WEIGHT_B * safe(dto.getCostB())
                + WEIGHT_C * safe(dto.getCostC())
                + WEIGHT_D * safe(dto.getCostD())
                + WEIGHT_E * safe(dto.getCostE())) / ALL_COST_WEIGHT_SUM;
        dto.setTotalCost(rawTotalCost);
    }

    private SchemeCostSnapshot calculateSchemeCost(List<VehicleCostDTO> vehicleCosts) {
        SchemeCostSnapshot snapshot = new SchemeCostSnapshot();
        if (vehicleCosts == null || vehicleCosts.isEmpty()) {
            return snapshot;
        }

        List<Double> workloads = new ArrayList<>();

        for (VehicleCostDTO dto : vehicleCosts) {
            double waitingHours = safe(dto.getTotalWaitingHours());
            double transportHours = safe(dto.getTotalTransportHours());
            double emptyDistance = safe(dto.getEmptyDistanceKm());
            double totalDistance = safe(dto.getTotalDistanceKm());
            double theoryCapacity = safe(dto.getTheoryCapacity());
            double actualCapacity = safe(dto.getActualCapacity());
            double capacityWaste = Math.max(0.0, theoryCapacity - actualCapacity);

            snapshot.totalWaitingHours += waitingHours;
            snapshot.totalTransportHours += transportHours;
            snapshot.totalEmptyDistanceKm += emptyDistance;
            snapshot.totalDistanceKm += totalDistance;
            snapshot.totalTheoryCapacity += theoryCapacity;
            snapshot.totalActualCapacity += actualCapacity;
            snapshot.worstWaitingTransportRatio = Math.max(
                    snapshot.worstWaitingTransportRatio,
                    safeDivide(waitingHours, transportHours)
            );
            snapshot.worstCapacityWaste = Math.max(snapshot.worstCapacityWaste, capacityWaste);

            double oilLoss = calculateOilLoss(actualCapacity, theoryCapacity);
            double fixedLoss = waitingHours + transportHours;
            double loss = 0.5 * oilLoss + 0.3 * fixedLoss;
            snapshot.worstLoss = Math.max(snapshot.worstLoss, loss);

            snapshot.totalActualRouteDistanceKm += safe(dto.getActualRouteDistanceKm());
            snapshot.totalBaseRouteDistanceKm += safe(dto.getBaseRouteDistanceKm());
            snapshot.totalActualVolume += safe(dto.getActualVolume());
            snapshot.totalCargoVolume += safe(dto.getCargoVolume());
            snapshot.totalActualAssignmentHours += safe(dto.getActualAssignmentHours());
            snapshot.totalEstimatedAssignmentHours += safe(dto.getEstimatedAssignmentHours());
            workloads.add(safe(dto.getWorkload()));
        }

        double capacityRatio = safeDivide(snapshot.totalActualCapacity, snapshot.totalTheoryCapacity);

        snapshot.costA = 0.5 * snapshot.totalWaitingHours + 0.5 * snapshot.totalEmptyDistanceKm;
        snapshot.costB = 0.4 * safeDivide(snapshot.totalEmptyDistanceKm, snapshot.totalDistanceKm)
                + 0.5 * safeDivide(snapshot.totalWaitingHours, snapshot.totalTransportHours)
                + 0.1 * snapshot.worstWaitingTransportRatio;
        snapshot.costC = 0.9 * Math.max(0.0, snapshot.totalTheoryCapacity - snapshot.totalActualCapacity)
                + 0.1 * snapshot.worstCapacityWaste;
        snapshot.costD = 0.5 * VehicleType * capacityRatio
                + 0.3 * (VehicleType * snapshot.totalWaitingHours + VehicleType * snapshot.totalTransportHours)
                + 0.2 * snapshot.worstLoss;
        snapshot.costE = calculateVariationCoefficient(workloads);
        snapshot.costG = snapshot.totalBaseRouteDistanceKm <= 0.0
                ? 0.0
                : Math.max(0.0, snapshot.totalActualRouteDistanceKm - snapshot.totalBaseRouteDistanceKm)
                / snapshot.totalBaseRouteDistanceKm;
        snapshot.costH = snapshot.totalCargoVolume <= 0.0
                ? 0.0
                : clamp01(1.0 - safeDivide(snapshot.totalActualVolume, snapshot.totalCargoVolume));
        snapshot.costI = snapshot.totalEstimatedAssignmentHours <= 0.0
                ? 0.0
                : Math.max(0.0, snapshot.totalActualAssignmentHours - snapshot.totalEstimatedAssignmentHours)
                / snapshot.totalEstimatedAssignmentHours;

        return snapshot;
    }

    private void applySchemeCosts(VehicleCostSummaryDTO summary, SchemeCostSnapshot schemeCost) {
        summary.setSchemeCostA(schemeCost.costA);
        summary.setSchemeCostB(schemeCost.costB);
        summary.setSchemeCostC(schemeCost.costC);
        summary.setSchemeCostD(schemeCost.costD);
        summary.setSchemeCostE(schemeCost.costE);
        summary.setSchemeCostG(schemeCost.costG);
        summary.setSchemeCostH(schemeCost.costH);
        summary.setSchemeCostI(schemeCost.costI);
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

    private double calculateVariationCoefficient(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (Double value : values) {
            sum += safe(value);
        }

        double average = sum / values.size();
        if (average <= 0.0) {
            return 0.0;
        }

        double variance = 0.0;
        for (Double value : values) {
            double diff = safe(value) - average;
            variance += diff * diff;
        }

        double standardDeviation = Math.sqrt(variance / values.size());
        return standardDeviation / average;
    }

    private WorkloadStats calculateRuntimeWorkloadStats(List<Vehicle> vehicles) {
        List<Vehicle> safeVehicles = vehicles == null ? List.of() : vehicles;
        if (safeVehicles.isEmpty()) {
            return new WorkloadStats(0.0, 0.0, 0.0);
        }
        if (simulationContext != null
                && !simulationContext.isRunning()
                && simulationContext.getLoopCount() == 0) {
            return new WorkloadStats(0.0, 0.0, 0.0);
        }

        List<Double> workloads = new ArrayList<>();
        LocalDateTime simNow = simulationContext == null ? null : simulationContext.getCurrentSimTime();
        for (Vehicle vehicle : safeVehicles) {
            workloads.add(getVehicleRuntimeWorkload(vehicle, simNow));
        }

        double sum = 0.0;
        for (Double workload : workloads) {
            sum += safe(workload);
        }
        double average = sum / workloads.size();
        if (average <= 0.0) {
            return new WorkloadStats(average, 0.0, 0.0);
        }

        double variance = 0.0;
        for (Double workload : workloads) {
            double diff = safe(workload) - average;
            variance += diff * diff;
        }
        double standardDeviation = Math.sqrt(variance / workloads.size());
        return new WorkloadStats(average, standardDeviation, standardDeviation / average);
    }

    private double calculateAllCost(double costA, double costB, double costC, double costD, double costE) {
        return (RUNTIME_WEIGHT_A * safe(costA)
                + RUNTIME_WEIGHT_B * safe(costB)
                + RUNTIME_WEIGHT_C * safe(costC)
                + RUNTIME_WEIGHT_D * safe(costD)
                + RUNTIME_WEIGHT_E * safe(costE)) / RUNTIME_ALL_COST_WEIGHT_SUM;
    }

    private double getVehicleRuntimeWorkload(Vehicle vehicle, LocalDateTime simNow) {
        double baseWorkload = getVehicleWorkload(vehicle);
        VehicleStatus status = vehicle == null ? null : vehicle.getCurrentStatus();
        double statusWeight = getStatusWorkloadWeight(status);
        if (statusWeight <= 0.0) {
            return baseWorkload;
        }

        double statusHours = getCurrentStatusHours(vehicle, simNow);
        if (statusHours <= 0.0) {
            statusHours = simulationContext == null
                    ? 0.5
                    : simulationContext.getMinutesPerLoop() / 60.0;
        }

        return baseWorkload + statusWeight * statusHours;
    }

    private double getStatusWorkloadWeight(VehicleStatus status) {
        if (status == null) {
            return 0.0;
        }

        return switch (status) {
            case ORDER_DRIVING, TRANSPORT_DRIVING -> 1.0;
            case LOADING, UNLOADING, WAITING, BREAKDOWN -> 0.5;
            case IDLE -> 0.0;
        };
    }

    private double getCurrentStatusHours(Vehicle vehicle, LocalDateTime simNow) {
        if (vehicle == null) {
            return 0.0;
        }

        LocalDateTime statusStartTime = vehicle.getStatusStartTime();
        if (simNow != null && statusStartTime != null && !statusStartTime.isAfter(simNow)) {
            long seconds = Duration.between(statusStartTime, simNow).toSeconds();
            return Math.max(0.0, seconds / 3600.0);
        }

        return safe(vehicle.getStatusDurationSeconds()) / 3600.0;
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

    private IdleVehicleCostStats calculateIdleVehicleCostStats(List<Vehicle> vehicles, List<Assignment> activeAssignments) {
        IdleVehicleCostStats stats = new IdleVehicleCostStats();
        List<Vehicle> safeVehicles = vehicles == null ? List.of() : vehicles;
        if (safeVehicles.isEmpty()) {
            return stats;
        }

        Set<Long> activeVehicleIds = collectActiveVehicleIds(activeAssignments);
        LocalDateTime simNow = simulationContext == null ? null : simulationContext.getCurrentSimTime();

        for (Vehicle vehicle : safeVehicles) {
            if (!isAvailableForIdlePenalty(vehicle)) {
                continue;
            }

            stats.availableVehicleCount++;
            stats.fleetLoadCapacity += safe(vehicle.getMaxLoadCapacity());
            stats.fleetVolumeCapacity += safe(vehicle.getCargoVolume());

            if (!isIdleUnassignedVehicle(vehicle, activeVehicleIds)) {
                continue;
            }

            double idleHours = getCurrentStatusHours(vehicle, simNow);
            if (idleHours <= 0.0) {
                continue;
            }

            stats.idleVehicleCount++;
            stats.totalIdleHours += idleHours;
            stats.maxIdleHours = Math.max(stats.maxIdleHours, idleHours);
            stats.totalIdleLoadCapacityHours += safe(vehicle.getMaxLoadCapacity()) * idleHours;
            stats.totalIdleVolumeCapacityHours += safe(vehicle.getCargoVolume()) * idleHours;
        }

        stats.idleWaitPenalty = calculateIdleWaitPenalty(stats);
        stats.idleSpaceEconomicLoss = calculateIdleSpaceEconomicLoss(stats);
        return stats;
    }

    private Set<Long> collectActiveVehicleIds(List<Assignment> activeAssignments) {
        Set<Long> activeVehicleIds = new HashSet<>();
        if (activeAssignments == null) {
            return activeVehicleIds;
        }

        for (Assignment assignment : activeAssignments) {
            if (assignment == null
                    || assignment.getAssignedVehicle() == null
                    || assignment.getAssignedVehicle().getId() == null) {
                continue;
            }
            activeVehicleIds.add(assignment.getAssignedVehicle().getId());
        }
        return activeVehicleIds;
    }

    private boolean isAvailableForIdlePenalty(Vehicle vehicle) {
        if (vehicle == null) {
            return false;
        }
        return vehicle.getCurrentStatus() != VehicleStatus.BREAKDOWN;
    }

    private boolean isIdleUnassignedVehicle(Vehicle vehicle, Set<Long> activeVehicleIds) {
        if (vehicle == null || vehicle.getId() == null || activeVehicleIds.contains(vehicle.getId())) {
            return false;
        }
        VehicleStatus status = vehicle.getCurrentStatus();
        return status == VehicleStatus.IDLE || status == VehicleStatus.WAITING;
    }

    private double calculateIdleWaitPenalty(IdleVehicleCostStats stats) {
        if (stats == null || stats.availableVehicleCount <= 0 || stats.idleVehicleCount <= 0) {
            return 0.0;
        }

        double idleVehicleRatio = safeDivide(stats.idleVehicleCount, stats.availableVehicleCount);
        double averageIdleHours = safeDivide(stats.totalIdleHours, stats.idleVehicleCount);
        double avgIdleSeverity = safeDivide(averageIdleHours, IDLE_WAIT_TARGET_HOURS);
        double maxIdleSeverity = safeDivide(stats.maxIdleHours, IDLE_WAIT_WORST_HOURS);

        return 0.40 * clamp01(idleVehicleRatio)
                + 0.40 * clamp01(avgIdleSeverity)
                + 0.20 * clamp01(maxIdleSeverity);
    }

    private double calculateIdleSpaceEconomicLoss(IdleVehicleCostStats stats) {
        if (stats == null || stats.idleVehicleCount <= 0) {
            return 0.0;
        }

        double loadIdleSeverity = safeDivide(
                stats.totalIdleLoadCapacityHours,
                stats.fleetLoadCapacity * IDLE_ECON_TARGET_HOURS
        );
        double volumeIdleSeverity = safeDivide(
                stats.totalIdleVolumeCapacityHours,
                stats.fleetVolumeCapacity * IDLE_ECON_TARGET_HOURS
        );

        return 0.60 * clamp01(loadIdleSeverity)
                + 0.40 * clamp01(volumeIdleSeverity);
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

    private double calculateOilLoss(double actualCapacity, double theoryCapacity) {
        if (theoryCapacity <= 0.0) {
            return 0.0;
        }

        double oilLoss = actualCapacity / theoryCapacity;
        if (!Double.isFinite(oilLoss)) {
            return 0.0;
        }
        return Math.max(0.0, oilLoss);
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

    private static class IdleVehicleCostStats {
        private int idleVehicleCount;
        private int availableVehicleCount;
        private double totalIdleHours;
        private double maxIdleHours;
        private double totalIdleLoadCapacityHours;
        private double totalIdleVolumeCapacityHours;
        private double fleetLoadCapacity;
        private double fleetVolumeCapacity;
        private double idleWaitPenalty;
        private double idleSpaceEconomicLoss;
    }

    private static class WorkloadStats {
        private final double averageWorkload;
        private final double standardDeviation;
        private final double variationCoefficient;

        private WorkloadStats(double averageWorkload, double standardDeviation, double variationCoefficient) {
            this.averageWorkload = averageWorkload;
            this.standardDeviation = standardDeviation;
            this.variationCoefficient = variationCoefficient;
        }
    }

    private static class SchemeCostSnapshot {
        private double totalWaitingHours;
        private double totalTransportHours;
        private double totalEmptyDistanceKm;
        private double totalDistanceKm;
        private double totalTheoryCapacity;
        private double totalActualCapacity;
        private double worstWaitingTransportRatio;
        private double worstCapacityWaste;
        private double worstLoss;
        private double totalActualRouteDistanceKm;
        private double totalBaseRouteDistanceKm;
        private double totalActualVolume;
        private double totalCargoVolume;
        private double totalActualAssignmentHours;
        private double totalEstimatedAssignmentHours;

        private double costA;
        private double costB;
        private double costC;
        private double costD;
        private double costE;
        private double costG;
        private double costH;
        private double costI;
    }

    private static class SchemeCostRange {
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

        void accept(VehicleCostSummaryDTO summary) {
            if (summary == null) {
                return;
            }
            minA = Math.min(minA, safeRangeValue(summary.getSchemeCostA()));
            maxA = Math.max(maxA, safeRangeValue(summary.getSchemeCostA()));
            minB = Math.min(minB, safeRangeValue(summary.getSchemeCostB()));
            maxB = Math.max(maxB, safeRangeValue(summary.getSchemeCostB()));
            minC = Math.min(minC, safeRangeValue(summary.getSchemeCostC()));
            maxC = Math.max(maxC, safeRangeValue(summary.getSchemeCostC()));
            minD = Math.min(minD, safeRangeValue(summary.getSchemeCostD()));
            maxD = Math.max(maxD, safeRangeValue(summary.getSchemeCostD()));
            minE = Math.min(minE, safeRangeValue(summary.getSchemeCostE()));
            maxE = Math.max(maxE, safeRangeValue(summary.getSchemeCostE()));
            minG = Math.min(minG, safeRangeValue(summary.getSchemeCostG()));
            maxG = Math.max(maxG, safeRangeValue(summary.getSchemeCostG()));
            minH = Math.min(minH, safeRangeValue(summary.getSchemeCostH()));
            maxH = Math.max(maxH, safeRangeValue(summary.getSchemeCostH()));
            minI = Math.min(minI, safeRangeValue(summary.getSchemeCostI()));
            maxI = Math.max(maxI, safeRangeValue(summary.getSchemeCostI()));
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
}
