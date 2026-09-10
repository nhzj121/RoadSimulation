package org.example.roadsimulation.evaluation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.example.roadsimulation.evaluation.EvaluationMetricId.*;
import static org.example.roadsimulation.evaluation.EvaluationMetricTimeScope.*;

/**
 * Phase 6A：评价指标的唯一代码级目录。
 *
 * <p>目录只声明契约，不查询数据库、不计算指标，也不参与每轮状态推进。Phase 6B 的
 * 快照计算器必须按这里的标识和事实来源实现。</p>
 */
@Component
public final class EvaluationMetricCatalog {

    /** Phase 6A：快照和前端用于识别指标语义版本的稳定编号。 */
    // Phase 7E-R：1.4 明确区分“事实待接入”与“当前版本不支持”。
    public static final String CONTRACT_VERSION = "1.4";

    private static final String EVERY_TICK = "每个 simulation tick 的业务推进完成后更新";
    private static final String ZERO_DENOMINATOR = "分母大于 0；否则返回 NOT_AVAILABLE";
    private static final String DIRECT_VALUE = "事实存在且数值有限；无事实时返回 NOT_AVAILABLE";

    private final Map<EvaluationMetricId, EvaluationMetricDefinition> definitions;
    private final Map<String, EvaluationMetricDefinition> definitionsByExternalId;

    public EvaluationMetricCatalog() {
        // Phase 6A：按评价文档的六个分组建立完整目录，并在构造时验证无遗漏、无重复。
        List<EvaluationMetricDefinition> ordered = new ArrayList<>();
        ordered.addAll(globalDefinitions());
        ordered.addAll(vehicleDefinitions());
        ordered.addAll(cargoDefinitions());
        ordered.addAll(taskDefinitions());
        ordered.addAll(environmentDefinitions());

        Map<EvaluationMetricId, EvaluationMetricDefinition> byEnum = new EnumMap<>(EvaluationMetricId.class);
        Map<String, EvaluationMetricDefinition> byExternalId = new LinkedHashMap<>();
        for (EvaluationMetricDefinition definition : ordered) {
            if (byEnum.put(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate evaluation metric enum id: " + definition.id());
            }
            if (byExternalId.put(definition.id().getMetricId(), definition) != null) {
                throw new IllegalStateException("Duplicate evaluation metric external id: "
                        + definition.id().getMetricId());
            }
        }
        if (byEnum.size() != EvaluationMetricId.values().length) {
            List<EvaluationMetricId> missing = Arrays.stream(EvaluationMetricId.values())
                    .filter(id -> !byEnum.containsKey(id))
                    .toList();
            throw new IllegalStateException("Missing evaluation metric definitions: " + missing);
        }
        this.definitions = Map.copyOf(byEnum);
        this.definitionsByExternalId = Map.copyOf(byExternalId);
    }

    public List<EvaluationMetricDefinition> all() {
        // Phase 6A：按枚举声明顺序返回，保证接口和测试展示稳定。
        return Arrays.stream(EvaluationMetricId.values()).map(definitions::get).toList();
    }

    public List<EvaluationMetricDefinition> byCategory(EvaluationMetricCategory category) {
        return all().stream().filter(definition -> definition.category() == category).toList();
    }

    public EvaluationMetricDefinition require(EvaluationMetricId id) {
        EvaluationMetricDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown evaluation metric: " + id);
        }
        return definition;
    }

    public Optional<EvaluationMetricDefinition> findByMetricId(String metricId) {
        return Optional.ofNullable(definitionsByExternalId.get(metricId));
    }

    private List<EvaluationMetricDefinition> globalDefinitions() {
        return List.of(
                ready(GLOBAL_EMPTY_MILEAGE_RATIO, "全局车辆空载里程率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "emptyExecutedDistance / totalExecutedDistance",
                        "Σ AssignmentLeg.executedDistanceMeters where loadState=EMPTY",
                        "Σ AssignmentLeg.executedDistanceMeters",
                        ZERO_DENOMINATOR,
                        "AssignmentLeg.executedDistanceMeters", "AssignmentLeg.loadState"),
                ready(GLOBAL_CAPACITY_WASTE_RATIO, "全局运力浪费率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "1 - Σ(loadTonnes × executedDistanceKm) / Σ(capacityTonnes × executedDistanceKm), loaded legs only",
                        "Σ AssignmentLeg.currentLoadTonnes × executedDistanceKm for currentLoadTonnes>0",
                        "Σ Vehicle.maxLoadCapacity × executedDistanceKm for currentLoadTonnes>0",
                        ZERO_DENOMINATOR,
                        "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters",
                        "AssignmentLeg.vehicle.maxLoadCapacity"),
                ready(GLOBAL_UNMET_DEMAND_RATIO, "全局运输需求未达成率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "1 - deliveredTonnes / requiredTonnes",
                        "requiredTonnes - deliveredTonnes",
                        "Σ ShipmentItem.weightTonnes created in current run, including CANCELLED",
                        ZERO_DENOMINATOR,
                        "ShipmentItem.weightTonnes", "ShipmentItem.status", "ShipmentItem.createdTime"),
                energy(GLOBAL_CARBON_INTENSITY, "全局单位有效吨公里碳排放", "kgCO2e/(t·km)",
                        CURRENT_RUN_CUMULATIVE,
                        "totalEmissionKg / executedTonneKm", "Σ vehicle emissionKg",
                        "Σ currentLoadTonnes × executedDistanceKm", ZERO_DENOMINATOR,
                        "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters",
                        "Phase 8 emission fact"),
                fact(GLOBAL_WAITING_SERVICE_COMPLIANT, "P95 等待时间服务约束", "boolean",
                        CURRENT_RUN_COMPLETED_EPISODES,
                        "max(vehicleP95, cargoP95, taskP95) <= maxServiceWaitSeconds",
                        "车辆、货物和任务等待事件的 P95", "simulation.evaluation.max-service-wait-seconds",
                        "三类等待样本均可用；任一类不可用时返回 NOT_AVAILABLE",
                        "仿真等待事件账本", "EvaluationMetricPolicy.maxServiceWaitSeconds")
        );
    }

    private List<EvaluationMetricDefinition> vehicleDefinitions() {
        return List.of(
                ready(VEHICLE_TOTAL_COUNT, "车辆总数", "vehicle", TICK_END_INSTANT,
                        "count(all Vehicle)", "Vehicle 记录数", "不适用", DIRECT_VALUE, "Vehicle.id"),
                ready(VEHICLE_AVAILABLE_COUNT, "可用车辆数", "vehicle", TICK_END_INSTANT,
                        "count(Vehicle.currentStatus=IDLE)", "IDLE 车辆数", "不适用", DIRECT_VALUE,
                        "Vehicle.currentStatus"),
                ready(VEHICLE_IN_TRANSPORT_COUNT, "运输中车辆数", "vehicle", TICK_END_INSTANT,
                        "count(distinct vehicle of active Assignment)", "绑定 IN_PROGRESS Assignment 的车辆数",
                        "不适用", DIRECT_VALUE, "Assignment.status", "Assignment.assignedVehicle"),
                ready(VEHICLE_EMPTY_DRIVING_COUNT, "空载行驶车辆数", "vehicle", TICK_END_INSTANT,
                        "count(current leg loadState=EMPTY and vehicle status=ORDER_DRIVING)",
                        "当前处于空载执行路段的车辆数", "不适用", DIRECT_VALUE,
                        "Vehicle.currentStatus", "Assignment.currentLegIndex", "AssignmentLeg.loadState"),
                ready(VEHICLE_LOADED_DRIVING_COUNT, "有载行驶车辆数", "vehicle", TICK_END_INSTANT,
                        "count(current leg loadState=LOADED and vehicle status=TRANSPORT_DRIVING)",
                        "当前处于有载执行路段的车辆数", "不适用", DIRECT_VALUE,
                        "Vehicle.currentStatus", "Assignment.currentLegIndex", "AssignmentLeg.loadState"),
                ready(VEHICLE_TOTAL_EXECUTED_DISTANCE_KM, "总实际行驶里程", "km", CURRENT_RUN_CUMULATIVE,
                        "Σ executedDistanceMeters / 1000", "所有路段实际执行米数", "1000", DIRECT_VALUE,
                        "AssignmentLeg.executedDistanceMeters"),
                ready(VEHICLE_EMPTY_EXECUTED_DISTANCE_KM, "空载实际行驶里程", "km", CURRENT_RUN_CUMULATIVE,
                        "Σ empty-leg executedDistanceMeters / 1000", "空载路段实际执行米数", "1000",
                        DIRECT_VALUE, "AssignmentLeg.executedDistanceMeters", "AssignmentLeg.loadState"),
                ready(VEHICLE_EMPTY_MILEAGE_RATIO, "车辆空载里程率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "emptyExecutedDistance / totalExecutedDistance", "空载实际里程", "总实际里程",
                        ZERO_DENOMINATOR, "AssignmentLeg.executedDistanceMeters", "AssignmentLeg.loadState"),
                ready(VEHICLE_CURRENT_LOAD_TONNES, "当前总载重", "t", TICK_END_INSTANT,
                        "Σ max(Vehicle.currentLoadTonnes, 0)", "当前车辆实际载重总和", "不适用",
                        DIRECT_VALUE, "Vehicle.currentLoadTonnes"),
                ready(VEHICLE_RATED_CAPACITY_TONNES, "总额定载重", "t", TICK_END_INSTANT,
                        "Σ max(Vehicle.maxLoadCapacity, 0)", "车辆额定载重总和", "不适用",
                        DIRECT_VALUE, "Vehicle.maxLoadCapacity"),
                ready(VEHICLE_DISTANCE_WEIGHTED_LOAD_RATIO, "里程加权载重率", "ratio",
                        CURRENT_RUN_CUMULATIVE,
                        "Σ(loadTonnes × distanceKm) / Σ(capacityTonnes × distanceKm), loaded legs only",
                        "有载实际吨公里", "有载路段理论最大吨公里", ZERO_DENOMINATOR,
                        "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters",
                        "AssignmentLeg.vehicle.maxLoadCapacity"),
                ready(VEHICLE_REMAINING_CAPACITY_TONNES, "当前剩余运力", "t", TICK_END_INSTANT,
                        "Σ max(maxLoadCapacity-currentLoadTonnes, 0)", "各车辆未使用吨位总和", "不适用",
                        DIRECT_VALUE, "Vehicle.maxLoadCapacity", "Vehicle.currentLoadTonnes"),
                ready(VEHICLE_LOW_LOAD_RATIO, "低载车辆比例", "ratio", TICK_END_INSTANT,
                        "count(0<load/capacity<lowLoadThreshold) / count(load>0 and capacity>0)",
                        "低于配置阈值的当前有载车辆数", "当前载重>0 且额定载重>0 的车辆数",
                        ZERO_DENOMINATOR, "Vehicle.currentLoadTonnes", "Vehicle.maxLoadCapacity",
                        "EvaluationMetricPolicy.lowLoadRatioThreshold"),
                ready(VEHICLE_FULL_LOAD_RATIO, "满载车辆比例", "ratio", TICK_END_INSTANT,
                        "count(load/capacity>=fullLoadThreshold) / count(load>0 and capacity>0)",
                        "达到配置阈值的当前有载车辆数", "当前载重>0 且额定载重>0 的车辆数",
                        ZERO_DENOMINATOR, "Vehicle.currentLoadTonnes", "Vehicle.maxLoadCapacity",
                        "EvaluationMetricPolicy.fullLoadRatioThreshold"),
                fact(VEHICLE_CUMULATIVE_WAIT_SECONDS, "车辆累计等待时间", "s", CURRENT_RUN_CUMULATIVE,
                        "Σ closed vehicle waiting episode seconds", "全部已闭合车辆等待事件时长", "不适用",
                        "存在只使用仿真时间记录的车辆等待事件",
                        "待新增 VehicleWaitEpisode.startedSimTime/completedSimTime/type"),
                fact(VEHICLE_P95_WAIT_SECONDS, "车辆 P95 等待时间", "s", CURRENT_RUN_COMPLETED_EPISODES,
                        "P95(vehicle waiting episode seconds)", "车辆等待时长样本第 95 分位", "不适用",
                        "至少存在一个已闭合等待事件",
                        "待新增 VehicleWaitEpisode.startedSimTime/completedSimTime/type"),
                energy(VEHICLE_TOTAL_ENERGY, "车辆总能耗", "model-specific", CURRENT_RUN_CUMULATIVE,
                        "Σ vehicle energy consumption", "车辆能耗总和", "不适用", DIRECT_VALUE,
                        "Phase 8 vehicle energy fact"),
                energy(VEHICLE_TOTAL_EMISSION_KG, "车辆总碳排放", "kgCO2e", CURRENT_RUN_CUMULATIVE,
                        "Σ vehicle emissionKg", "车辆碳排总和", "不适用", DIRECT_VALUE,
                        "Phase 8 vehicle emission fact"),
                energy(VEHICLE_EMISSION_INTENSITY, "车辆单位吨公里排放", "kgCO2e/(t·km)",
                        CURRENT_RUN_CUMULATIVE, "totalEmissionKg / executedTonneKm", "车辆总碳排",
                        "实际有效吨公里", ZERO_DENOMINATOR,
                        "Phase 8 vehicle emission fact", "AssignmentLeg.currentLoadTonnes",
                        "AssignmentLeg.executedDistanceMeters")
        );
    }

    private List<EvaluationMetricDefinition> cargoDefinitions() {
        return List.of(
                ready(CARGO_REQUIRED_TONNES, "总运输需求吨位", "t", CURRENT_RUN_CUMULATIVE,
                        "Σ ShipmentItem.weightTonnes created in current run, including CANCELLED",
                        "当前运行产生的全部有效需求吨位", "不适用", DIRECT_VALUE,
                        "ShipmentItem.weightTonnes", "ShipmentItem.createdTime", "ShipmentItem.status"),
                ready(CARGO_UNASSIGNED_TONNES, "待分配货物吨位", "t", TICK_END_INSTANT,
                        "Σ weightTonnes where status=NOT_ASSIGNED", "待分配货物吨位", "不适用",
                        DIRECT_VALUE, "ShipmentItem.weightTonnes", "ShipmentItem.status"),
                ready(CARGO_ASSIGNED_NOT_LOADED_TONNES, "已分配待装货吨位", "t", TICK_END_INSTANT,
                        "Σ weightTonnes where status=ASSIGNED", "已分配未装车货物吨位", "不适用",
                        DIRECT_VALUE, "ShipmentItem.weightTonnes", "ShipmentItem.status"),
                ready(CARGO_IN_TRANSIT_TONNES, "运输中货物吨位", "t", TICK_END_INSTANT,
                        "Σ weightTonnes where status in (LOADED, IN_TRANSIT)", "已经装车且尚未交付的货物吨位",
                        "不适用", DIRECT_VALUE, "ShipmentItem.weightTonnes", "ShipmentItem.status"),
                ready(CARGO_DELIVERED_TONNES, "已完成运输吨位", "t", CURRENT_RUN_CUMULATIVE,
                        "Σ weightTonnes where status=DELIVERED", "已交付货物吨位", "不适用",
                        DIRECT_VALUE, "ShipmentItem.weightTonnes", "ShipmentItem.status"),
                ready(CARGO_DELIVERY_ACHIEVEMENT_RATIO, "运输需求达成率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "deliveredTonnes / requiredTonnes", "已交付吨位",
                        "当前运行全部需求吨位（包含后续 CANCELLED）", ZERO_DENOMINATOR,
                        "ShipmentItem.weightTonnes", "ShipmentItem.status", "ShipmentItem.createdTime"),
                ready(CARGO_UNMET_TONNES, "未达成吨位", "t", CURRENT_RUN_CUMULATIVE,
                        "max(requiredTonnes-deliveredTonnes, 0)", "需求吨位减已交付吨位", "不适用",
                        DIRECT_VALUE, "ShipmentItem.weightTonnes", "ShipmentItem.status"),
                fact(CARGO_OVERDUE_UNTRANSPORTED_TONNES, "超时未运输吨位", "t", TICK_END_INSTANT,
                        "Σ undelivered weightTonnes where waitSeconds>maxServiceWaitSeconds",
                        "超过服务阈值且未交付的货物吨位", "不适用",
                        "每件货物具有可信创建和首次运输仿真时间",
                        "待新增 ShipmentItem.firstTransportStartedSimTime"),
                fact(CARGO_AVERAGE_WAIT_SECONDS, "平均货物等待时间", "s", CURRENT_RUN_COMPLETED_EPISODES,
                        "average(firstTransportStartedSimTime-createdSimTime)", "货物等待时长总和",
                        "已开始运输的货物数", "至少存在一个完整等待样本",
                        "待新增 ShipmentItem.createdSimTime/firstTransportStartedSimTime"),
                fact(CARGO_P95_WAIT_SECONDS, "P95 货物等待时间", "s", CURRENT_RUN_COMPLETED_EPISODES,
                        "P95(firstTransportStartedSimTime-createdSimTime)", "货物等待时长样本第 95 分位",
                        "不适用", "至少存在一个完整等待样本",
                        "待新增 ShipmentItem.createdSimTime/firstTransportStartedSimTime"),
                fact(CARGO_HIGH_PRIORITY_COMPLETION_RATIO, "高优先级货物完成率", "ratio",
                        CURRENT_RUN_CUMULATIVE,
                        "delivered high-priority tonnes / required high-priority tonnes",
                        "高优先级已交付吨位", "高优先级需求吨位",
                        "存在显式业务优先级且高优先级需求吨位大于 0",
                        "待新增 ShipmentItem.priority"),
                fact(CARGO_PRIORITY_WEIGHTED_COMPLETION_RATIO, "优先级加权达成率", "ratio",
                        CURRENT_RUN_CUMULATIVE,
                        "Σ(priority×deliveredTonnes) / Σ(priority×requiredTonnes)",
                        "优先级加权已交付吨位", "优先级加权需求吨位",
                        "存在显式业务优先级且加权需求大于 0", "待新增 ShipmentItem.priority"),
                ready(CARGO_EXECUTED_TONNE_KM, "实际有效吨公里", "t·km", CURRENT_RUN_CUMULATIVE,
                        "Σ currentLoadTonnes × executedDistanceKm for loaded legs",
                        "所有已执行有载路段的实际运输工作量", "不适用", DIRECT_VALUE,
                        "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters",
                        "AssignmentLeg.loadState")
        );
    }

    private List<EvaluationMetricDefinition> taskDefinitions() {
        return List.of(
                ready(TASK_TOTAL_COUNT, "当前任务总数", "task", CURRENT_RUN_CUMULATIVE,
                        "count(Assignment created in current run)", "当前运行生成的 Assignment 数量", "不适用",
                        DIRECT_VALUE, "Assignment.id", "Assignment.createdTime"),
                notApplicable(TASK_UNASSIGNED_COUNT, "未分配任务数", "task", TICK_END_INSTANT,
                        "不适用：车辆分配完成后才创建 Assignment",
                        "未分配需求必须使用 ShipmentItem.status=NOT_ASSIGNED 表达",
                        "不存在可统计的未分配 Assignment",
                        "ShipmentItem.status=NOT_ASSIGNED（替代指标事实来源）"),
                ready(TASK_ASSIGNED_PENDING_COUNT, "已分配待执行任务数", "task", TICK_END_INSTANT,
                        "count(Assignment.status=ASSIGNED)", "ASSIGNED 任务数", "不适用", DIRECT_VALUE,
                        "Assignment.status"),
                ready(TASK_IN_PROGRESS_COUNT, "执行中任务数", "task", TICK_END_INSTANT,
                        "count(Assignment.status=IN_PROGRESS)", "IN_PROGRESS 任务数", "不适用", DIRECT_VALUE,
                        "Assignment.status"),
                ready(TASK_COMPLETED_COUNT, "已完成任务数", "task", CURRENT_RUN_CUMULATIVE,
                        "count(Assignment.status=COMPLETED)", "COMPLETED 任务数", "不适用", DIRECT_VALUE,
                        "Assignment.status"),
                ready(TASK_COMPLETION_RATIO, "任务完成率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "completed Assignment count / all current-run Assignment count", "已完成任务数",
                        "当前运行创建的全部任务数（包含 FAILED/CANCELLED）", ZERO_DENOMINATOR,
                        "Assignment.status", "Assignment.createdTime"),
                fact(TASK_AVERAGE_RESPONSE_SECONDS, "平均任务响应时间", "s",
                        CURRENT_RUN_COMPLETED_EPISODES,
                        "average(assignmentConfirmedSimTime-taskCreatedSimTime)", "任务响应时长总和",
                        "具有完整时间事实的任务数", "存在可信的任务创建和确认仿真时间",
                        "待新增 taskCreatedSimTime/assignmentConfirmedSimTime"),
                fact(TASK_AVERAGE_START_WAIT_SECONDS, "平均启动等待时间", "s",
                        CURRENT_RUN_COMPLETED_EPISODES,
                        "average(firstExecutionSimTime-assignmentConfirmedSimTime)", "任务启动等待时长总和",
                        "具有完整时间事实的任务数", "存在可信的确认和首次执行仿真时间",
                        "待新增 assignmentConfirmedSimTime/firstExecutionSimTime"),
                fact(TASK_OVERDUE_COUNT, "超时任务数", "task", TICK_END_INSTANT,
                        "count(task past wait or completion deadline)", "超过服务或交付时限的任务数", "不适用",
                        "任务具有可信时限、创建、确认、首次执行和完成仿真时间",
                        "待新增任务观测时间；现有 appointment 字段不是所有任务必填"),
                fact(TASK_ON_TIME_COMPLETION_RATIO, "准时完成率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "on-time completed task count / completed task count", "按时完成任务数", "已完成任务数",
                        "已完成任务具有可信完成仿真时间和交付时限",
                        "待统一 deliveryDeadlineSimTime/completedSimTime"),
                ready(TASK_AVERAGE_LOAD_RATIO, "任务平均载重率", "ratio", CURRENT_RUN_CUMULATIVE,
                        "average(per-task loaded-distance-weighted load ratio)", "各任务有效载重率总和",
                        "具有正有载实际里程和正额定载重的任务数", ZERO_DENOMINATOR,
                        "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters",
                        "AssignmentLeg.vehicle.maxLoadCapacity"),
                ready(TASK_LOW_LOAD_COUNT, "低载任务数", "task", CURRENT_RUN_CUMULATIVE,
                        "count(task load ratio<lowLoadThreshold)", "低于配置阈值的有载任务数", "不适用",
                        DIRECT_VALUE, "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters",
                        "AssignmentLeg.vehicle.maxLoadCapacity", "EvaluationMetricPolicy.lowLoadRatioThreshold"),
                ready(TASK_EMPTY_PICKUP_DISTANCE_KM, "任务空驶接驳距离", "km", CURRENT_RUN_CUMULATIVE,
                        "Σ leading EMPTY-leg executedDistanceMeters / 1000",
                        "每个任务首次有载路段之前的空载实际里程", "1000", DIRECT_VALUE,
                        "AssignmentLeg.sequenceIndex", "AssignmentLeg.loadState",
                        "AssignmentLeg.executedDistanceMeters"),
                ready(TASK_AVERAGE_EMPTY_PICKUP_DISTANCE_KM, "平均空驶接驳距离", "km",
                        CURRENT_RUN_CUMULATIVE,
                        "total empty pickup distance / task count with an executed pickup leg",
                        "任务空驶接驳总里程", "具有实际接驳路段的任务数", ZERO_DENOMINATOR,
                        "AssignmentLeg.sequenceIndex", "AssignmentLeg.loadState",
                        "AssignmentLeg.executedDistanceMeters"),
                fact(TASK_ROUTE_EFFICIENCY_RATIO, "实际里程与理论最短里程比", "ratio",
                        CURRENT_RUN_CUMULATIVE,
                        "completed-task actualDistance / frozenShortestDistance", "任务完成实际总里程",
                        "任务创建时冻结的理论最短里程", ZERO_DENOMINATOR,
                        "待新增 Assignment.shortestDistanceMeters 快照；Route 计划值不保证是理论最短值"),
                ready(TASK_TONNE_KM, "任务吨公里", "t·km", CURRENT_RUN_CUMULATIVE,
                        "per Assignment Σ currentLoadTonnes × executedDistanceKm",
                        "任务全部有载路段的实际运输工作量", "不适用", DIRECT_VALUE,
                        "AssignmentLeg.assignment", "AssignmentLeg.currentLoadTonnes",
                        "AssignmentLeg.executedDistanceMeters"),
                energy(TASK_EMISSION_KG, "任务碳排放", "kgCO2e", CURRENT_RUN_CUMULATIVE,
                        "Σ emission allocated to Assignment legs", "任务路段碳排总和", "不适用", DIRECT_VALUE,
                        "Phase 8 assignment-leg emission fact"),
                energy(TASK_EMISSION_INTENSITY, "任务单位吨公里排放", "kgCO2e/(t·km)",
                        CURRENT_RUN_CUMULATIVE,
                        "taskEmissionKg / taskTonneKm", "任务碳排放", "任务实际吨公里",
                        ZERO_DENOMINATOR, "Phase 8 assignment-leg emission fact",
                        "AssignmentLeg.currentLoadTonnes", "AssignmentLeg.executedDistanceMeters"),
                fact(TASK_REASSIGNMENT_COUNT, "重分配次数", "count", CURRENT_RUN_CUMULATIVE,
                        "Σ Assignment.reassignmentCount", "任务重新匹配车辆事件数", "不适用",
                        "每次重新分配均由生命周期入口持久化计数", "待新增 Assignment.reassignmentCount")
        );
    }

    private List<EvaluationMetricDefinition> environmentDefinitions() {
        return List.of(
                readyEnvironment(ENV_NETWORK_AVERAGE_SPEED_KPH, "路网平均通行速度", "km/h",
                        "scenario normal network speed / travel time factor", "场景正常路网速度", "本轮旅行时间因子",
                        "Phase 7C EnvironmentScenarioSnapshot.normalNetworkSpeedKph",
                        "Phase 7C EnvironmentScenarioSnapshot.travelTimeFactor"),
                environment(ENV_ROAD_REALTIME_SPEED_KPH, "各道路实时速度", "km/h",
                        "current speed per road segment", "单路段当前速度", "不适用",
                        "已按确认口径暂缓：缺少稳定道路段身份与逐路段速度集合"),
                readyEnvironment(ENV_CONGESTION_INDEX, "拥堵指数", "index",
                        "scenario-defined congestion index", "路网拥堵程度", "不适用",
                        "Phase 7C EnvironmentScenarioSnapshot.congestionIndex"),
                readyEnvironment(ENV_ROAD_PASSABILITY_RATIO, "道路可通行率", "ratio",
                        "passable logical execution segments / modeled logical execution segments",
                        "可通行逻辑执行路段数", "场景建模的逻辑执行路段数",
                        "Phase 7C EnvironmentScenarioSnapshot.modeledRoadCount",
                        "Phase 7C EnvironmentScenarioSnapshot.closedRoadCount"),
                readyEnvironment(ENV_CLOSED_ROAD_COUNT, "封闭路段数量", "road",
                        "scenario-defined closed logical execution segment count", "封闭逻辑执行路段数", "不适用",
                        "Phase 7C EnvironmentScenarioSnapshot.closedRoadCount"),
                readyEnvironment(ENV_ABNORMAL_EVENT_COUNT, "异常事件数", "event",
                        "count(active environment events)", "本轮生效的事故、施工等事件数", "不适用",
                        "Phase 7C EnvironmentScenarioSnapshot.abnormalEventCount"),
                readyEnvironment(ENV_WEATHER_RISK_LEVEL, "天气风险等级", "level",
                        "scenario-defined weather risk level", "本轮天气风险等级", "不适用",
                        "Phase 7C EnvironmentScenarioSnapshot.weatherRiskLevel"),
                readyEnvironment(ENV_TRAVEL_TIME_FACTOR, "旅行时间放大系数", "ratio",
                        "current travel time / normal travel time", "环境影响后的旅行时间", "正常旅行时间",
                        "Phase 7C EnvironmentScenarioSnapshot.travelTimeFactor"),
                unsupported(ENV_DISTANCE_FACTOR, "距离修正系数", "ratio", TICK_END_INSTANT,
                        "current feasible distance / frozen shortest distance", "当前可行路径距离",
                        "无环境扰动时理论最短距离",
                        "当前版本不支持动态绕行；禁止改写同步路线规划、路线几何和前端路线展示",
                        "动态执行路径", "冻结最短距离基线"),
                environment(ENV_ENERGY_FACTOR, "环境能耗修正系数", "ratio",
                        "current energy per km / normal energy per km", "环境下单位里程能耗",
                        "正常单位里程能耗", "Phase 7 environment factor and Phase 8 energy baseline"),
                // Phase 7E-R：节点排队被明确排除；平均服务时间和吞吐量仍由现有服务账本支持。
                unsupported(ENV_NODE_QUEUE_LENGTH, "节点装卸排队长度", "vehicle", TICK_END_INSTANT,
                        "count(node service episodes with status=QUEUED)", "全系统当前排队车辆数", "不适用",
                        "当前版本不支持节点容量竞争与排队；禁止用 Vehicle.WAITING 伪造队列",
                        "节点服务容量模型", "节点排队事件账本"),
                // Phase 7B：平均值覆盖当前运行内所有已经闭合的 LOAD/UNLOAD 服务事件。
                ready(ENV_NODE_AVERAGE_SERVICE_SECONDS, "节点平均服务时间", "s",
                        CURRENT_RUN_COMPLETED_EPISODES,
                        "average(serviceCompletedAt - serviceStartedAt)", "节点服务时长总和",
                        "节点已完成服务事件数", ZERO_DENOMINATOR,
                        "NodeServiceEpisode.serviceStartedAt", "NodeServiceEpisode.serviceCompletedAt"),
                // Phase 7B：按已确认口径统计 LOAD 与 UNLOAD 的绝对处理吨数，表示节点装卸工作量。
                ready(ENV_NODE_THROUGHPUT_TONNES, "节点装卸处理量", "t/tick", TICK_END_INSTANT,
                        "Σ abs(processedTonnes) for LOAD and UNLOAD completed in [tickStart,tickEnd)",
                        "本轮节点完成装卸处理吨位", "一个 simulation tick", DIRECT_VALUE,
                        "NodeServiceEpisode.actionType", "NodeServiceEpisode.processedTonnes",
                        "NodeServiceEpisode.serviceCompletedAt")
        );
    }

    private EvaluationMetricDefinition ready(
            EvaluationMetricId id,
            String name,
            String unit,
            EvaluationMetricTimeScope scope,
            String formula,
            String numerator,
            String denominator,
            String availability,
            String... sources
    ) {
        return definition(id, name, unit, scope, formula, numerator, denominator, availability,
                EvaluationMetricReadiness.READY, EvaluationMetricValueStatus.NOT_AVAILABLE,
                Set.of(), "当前后端已具有可信仿真事实", sources);
    }

    private EvaluationMetricDefinition fact(
            EvaluationMetricId id,
            String name,
            String unit,
            EvaluationMetricTimeScope scope,
            String formula,
            String numerator,
            String denominator,
            String availability,
            String... sources
    ) {
        return definition(id, name, unit, scope, formula, numerator, denominator, availability,
                EvaluationMetricReadiness.REQUIRES_FACT_CAPTURE, EvaluationMetricValueStatus.NOT_AVAILABLE,
                Set.of(EvaluationMetricDependency.FACT_CAPTURE), "当前字段不足，禁止以墙上时间或默认零替代", sources);
    }

    private EvaluationMetricDefinition energy(
            EvaluationMetricId id,
            String name,
            String unit,
            EvaluationMetricTimeScope scope,
            String formula,
            String numerator,
            String denominator,
            String availability,
            String... sources
    ) {
        return definition(id, name, unit, scope, formula, numerator, denominator, availability,
                EvaluationMetricReadiness.REQUIRES_ENERGY_MODEL, EvaluationMetricValueStatus.NOT_AVAILABLE,
                Set.of(EvaluationMetricDependency.ENERGY_AND_CARBON_MODEL),
                "等待 Phase 8 的能耗和碳排事实模型", sources);
    }

    private EvaluationMetricDefinition environment(
            EvaluationMetricId id,
            String name,
            String unit,
            String formula,
            String numerator,
            String denominator,
            String... sources
    ) {
        Set<EvaluationMetricDependency> dependencies = id == ENV_ENERGY_FACTOR
                ? Set.of(EvaluationMetricDependency.ENVIRONMENT_SCENARIO,
                EvaluationMetricDependency.ENERGY_AND_CARBON_MODEL)
                : Set.of(EvaluationMetricDependency.ENVIRONMENT_SCENARIO);
        // Phase 7C：剩余三项的缺失原因分别落到真实依赖，不能继续笼统声称“尚无环境模型”。
        String availability = switch (id) {
            case ENV_ROAD_REALTIME_SPEED_KPH -> "稳定道路段身份与逐路段速度集合存在时计算";
            case ENV_ENERGY_FACTOR -> "环境能耗因子与正常单位里程能耗基线同时存在时计算";
            default -> "相应的环境事实存在时计算";
        };
        String readinessReason = switch (id) {
            case ENV_ROAD_REALTIME_SPEED_KPH -> "已确认暂缓逐路段速度集合，网络级场景不能冒充道路级事实";
            case ENV_ENERGY_FACTOR -> "等待 Phase 8 能耗基线；Phase 7C 不以旅行时间因子冒充能耗因子";
            default -> "当前环境事实粒度不足";
        };
        return definition(id, name, unit, TICK_END_INSTANT, formula, numerator, denominator,
                availability,
                EvaluationMetricReadiness.REQUIRES_ENVIRONMENT,
                EvaluationMetricValueStatus.NOT_AVAILABLE,
                dependencies, readinessReason, sources);
    }

    private EvaluationMetricDefinition readyEnvironment(
            EvaluationMetricId id,
            String name,
            String unit,
            String formula,
            String numerator,
            String denominator,
            String... sources
    ) {
        // Phase 7C：只把已有确定性 tick 快照直接支撑的标量指标提升为 READY。
        return definition(id, name, unit, TICK_END_INSTANT, formula, numerator, denominator,
                DIRECT_VALUE, EvaluationMetricReadiness.READY, EvaluationMetricValueStatus.NOT_AVAILABLE,
                Set.of(EvaluationMetricDependency.ENVIRONMENT_SCENARIO),
                "Phase 7D 已具有可复现且与实际推进同源的逐 tick 环境事实", sources);
    }

    private EvaluationMetricDefinition unsupported(
            EvaluationMetricId id,
            String name,
            String unit,
            EvaluationMetricTimeScope scope,
            String formula,
            String numerator,
            String denominator,
            String reason,
            String... sources
    ) {
        // Phase 7E-R：不支持是稳定契约结论，不声明尚待满足的事实或环境依赖。
        return definition(id, name, unit, scope, formula, numerator, denominator,
                "当前契约版本明确不计算该指标",
                EvaluationMetricReadiness.NOT_SUPPORTED, EvaluationMetricValueStatus.NOT_SUPPORTED,
                Set.of(), reason, sources);
    }

    private EvaluationMetricDefinition notApplicable(
            EvaluationMetricId id,
            String name,
            String unit,
            EvaluationMetricTimeScope scope,
            String formula,
            String numerator,
            String denominator,
            String... sources
    ) {
        return definition(id, name, unit, scope, formula, numerator, denominator,
                "当前领域模型不存在该对象状态",
                EvaluationMetricReadiness.NOT_APPLICABLE, EvaluationMetricValueStatus.NOT_APPLICABLE,
                Set.of(), "未分配需求属于 ShipmentItem，不属于 Assignment", sources);
    }

    private EvaluationMetricDefinition definition(
            EvaluationMetricId id,
            String name,
            String unit,
            EvaluationMetricTimeScope scope,
            String formula,
            String numerator,
            String denominator,
            String availability,
            EvaluationMetricReadiness readiness,
            EvaluationMetricValueStatus unavailableStatus,
            Set<EvaluationMetricDependency> dependencies,
            String readinessReason,
            String... sources
    ) {
        return new EvaluationMetricDefinition(
                id,
                name,
                id.getCategory(),
                unit,
                scope,
                formula,
                numerator,
                denominator,
                List.of(sources),
                availability,
                EVERY_TICK,
                readiness,
                unavailableStatus,
                dependencies,
                readinessReason
        );
    }
}
