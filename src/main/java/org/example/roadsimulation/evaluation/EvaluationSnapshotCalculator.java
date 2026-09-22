package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentLegRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.repository.NodeServiceEpisodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.roadsimulation.evaluation.EvaluationMetricId.*;

/**
 * 在单个只读、一致性事务中采集事实并计算已就绪指标。
 *
 * <p>该组件不保存快照、不写运输实体，也不调用调度或生命周期服务。数据库读取与纯计算
 * 放在同一 REPEATABLE_READ 边界中，防止一张快照混合两个业务时刻。</p>
 */
@Component
public class EvaluationSnapshotCalculator {

    public static final String INVALID_FACT_ERROR = "EVALUATION_INVALID_FACT";
    public static final String READY_METRIC_UNAVAILABLE_ERROR = "EVALUATION_READY_METRIC_UNAVAILABLE";

    private final EvaluationMetricCatalog catalog;
    private final EvaluationMetricPolicy policy;
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentLegRepository assignmentLegRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    // 服务时长来自只读节点账本，不从车辆当前状态反推历史记录。
    private final NodeServiceEpisodeRepository nodeServiceEpisodeRepository;
    // 节点观察写入失败后，将相关指标标为 INVALID，避免用缺失事件计算偏低的数值。
    private final NodeServiceLedgerHealth nodeServiceLedgerHealth;
    // 环境场景仅作为只读评价事实，不参与运输推进或路线规划。
    private final ReproducibleEnvironmentScenarioService environmentScenarioService;
    // 能耗评价读取已持久化的累计事实，并使用统一模型校验排放换算与环境因子。
    private final VehicleEnergyEmissionModel energyEmissionModel;
    // 等待指标只读取评价账本，不从当前业务状态推断历史等待。
    private final WaitMetricFactReader waitMetricFactReader;
    // 交付指标读取按运行隔离的截止时间与后端卸货完成事实。
    private final DeliverySlaMetricFactReader deliverySlaMetricFactReader;

    /** 兼容既有测试的简化构造器；生产环境使用完整依赖构造器。 */
    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository
    ) {
        this(
                catalog,
                policy,
                vehicleRepository,
                assignmentRepository,
                assignmentLegRepository,
                shipmentItemRepository,
                null,
                null,
                null
        );
    }

    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository
    ) {
        this(
                catalog,
                policy,
                vehicleRepository,
                assignmentRepository,
                assignmentLegRepository,
                shipmentItemRepository,
                nodeServiceEpisodeRepository,
                null,
                null
        );
    }

    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository,
            NodeServiceLedgerHealth nodeServiceLedgerHealth
    ) {
        this(
                catalog,
                policy,
                vehicleRepository,
                assignmentRepository,
                assignmentLegRepository,
                shipmentItemRepository,
                nodeServiceEpisodeRepository,
                nodeServiceLedgerHealth,
                null
        );
    }

    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository,
            NodeServiceLedgerHealth nodeServiceLedgerHealth,
            ReproducibleEnvironmentScenarioService environmentScenarioService
    ) {
        this(
                catalog,
                policy,
                vehicleRepository,
                assignmentRepository,
                assignmentLegRepository,
                shipmentItemRepository,
                nodeServiceEpisodeRepository,
                nodeServiceLedgerHealth,
                environmentScenarioService,
                VehicleEnergyEmissionModel.defaultModel(),
                null
        );
    }

    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository,
            NodeServiceLedgerHealth nodeServiceLedgerHealth,
            ReproducibleEnvironmentScenarioService environmentScenarioService,
            VehicleEnergyEmissionModel energyEmissionModel
    ) {
        this(
                catalog, policy, vehicleRepository, assignmentRepository,
                assignmentLegRepository, shipmentItemRepository,
                nodeServiceEpisodeRepository, nodeServiceLedgerHealth,
                environmentScenarioService, energyEmissionModel, null, null
        );
    }

    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository,
            NodeServiceLedgerHealth nodeServiceLedgerHealth,
            ReproducibleEnvironmentScenarioService environmentScenarioService,
            VehicleEnergyEmissionModel energyEmissionModel,
            WaitMetricFactReader waitMetricFactReader
    ) {
        this(
                catalog, policy, vehicleRepository, assignmentRepository,
                assignmentLegRepository, shipmentItemRepository,
                nodeServiceEpisodeRepository, nodeServiceLedgerHealth,
                environmentScenarioService, energyEmissionModel, waitMetricFactReader, null
        );
    }

    @Autowired
    public EvaluationSnapshotCalculator(
            EvaluationMetricCatalog catalog,
            EvaluationMetricPolicy policy,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository,
            NodeServiceLedgerHealth nodeServiceLedgerHealth,
            ReproducibleEnvironmentScenarioService environmentScenarioService,
            VehicleEnergyEmissionModel energyEmissionModel,
            WaitMetricFactReader waitMetricFactReader,
            DeliverySlaMetricFactReader deliverySlaMetricFactReader
    ) {
        // 生产环境必须显式提供场景服务；缺省依赖只用于兼容既有测试。
        this.catalog = catalog;
        this.policy = policy;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentLegRepository = assignmentLegRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.nodeServiceEpisodeRepository = nodeServiceEpisodeRepository;
        this.nodeServiceLedgerHealth = nodeServiceLedgerHealth;
        this.environmentScenarioService = environmentScenarioService;
        // 生产环境注入已校验的能耗模型；简化构造器使用相同参数的默认模型。
        this.energyEmissionModel = energyEmissionModel;
        this.waitMetricFactReader = waitMetricFactReader;
        this.deliverySlaMetricFactReader = deliverySlaMetricFactReader;
    }

    /** 无明确 tick 的兼容入口；本轮节点吞吐量因此保持不可用。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calculation calculate() {
        return calculateInternal(null, null);
    }

    /** 每次调用只扫描一次各类根事实，并按传入 tick 计算本轮节点吞吐量。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calculation calculate(SimulationTick tick) {
        return calculateInternal(tick, null);
    }

    /** 生产快照需要运行标识，防止等待事实跨运行混算。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calculation calculate(SimulationTick tick, String simulationRunId) {
        return calculateInternal(tick, simulationRunId);
    }

    private Calculation calculateInternal(SimulationTick tick, String simulationRunId) {
        MetricAccumulator metrics = new MetricAccumulator(catalog);

        // 各类事实查询位于同一事务，接口层直接使用计算结果，不再重复读取。
        List<Vehicle> vehicles = List.copyOf(vehicleRepository.findAll());
        List<Assignment> assignments = List.copyOf(assignmentRepository.findAll());
        List<AssignmentLeg> legs = List.copyOf(assignmentLegRepository.findAll());
        List<ShipmentItem> shipmentItems = List.copyOf(shipmentItemRepository.findAll());
        List<NodeServiceEpisode> nodeServiceEpisodes = nodeServiceEpisodeRepository == null
                ? List.of()
                : List.copyOf(nodeServiceEpisodeRepository.findAll());

        VehicleFacts vehicleFacts = calculateVehicleFacts(metrics, vehicles, assignments, legs);
        LegFacts legFacts = calculateLegFacts(metrics, legs);
        calculateCargoFacts(metrics, shipmentItems, legFacts);
        calculateTaskFacts(metrics, assignments, legs, legFacts);
        calculateGlobalFacts(metrics, legFacts);
        // 场景只在评价侧读取，不向运输实体或路径服务回写结果。
        calculateEnvironmentFacts(metrics, tick);
        calculateNodeServiceFacts(metrics, nodeServiceEpisodes, tick);
        // 等待事实已在采集前投影完成，开放样本统一统计到本轮 tickEnd。
        calculateWaitingFacts(metrics, tick, simulationRunId);
        // 交付事实已在本轮计算前投影完成，这里不反向修改任务。
        calculateDeliverySlaFacts(metrics, tick, simulationRunId, assignments, shipmentItems);

        // 车辆里程取自同一份路段事实，在校验后统一写入计算结果。
        if (legFacts.distanceFactsValid()) {
            metrics.available(VEHICLE_TOTAL_EXECUTED_DISTANCE_KM, legFacts.totalDistanceKm());
            metrics.available(VEHICLE_EMPTY_EXECUTED_DISTANCE_KM, legFacts.emptyDistanceKm());
            metrics.ratio(VEHICLE_EMPTY_MILEAGE_RATIO,
                    legFacts.emptyDistanceKm(), legFacts.totalDistanceKm(), "总实际里程为 0");
        }
        if (legFacts.capacityFactsValid()) {
            metrics.ratio(VEHICLE_DISTANCE_WEIGHTED_LOAD_RATIO,
                    legFacts.executedTonneKm(), legFacts.capacityTonneKm(),
                    "不存在正载重且正额定载重的已执行有载里程");
        }
        // 能耗、排放和强度使用同一份路段累计事实，保持车辆与任务口径一致。
        calculateEnergyEmissionFacts(metrics, legFacts);

        // 保留车辆事实与路段事实各自的校验结果，不在此处混合校验边界。
        if (!vehicleFacts.statusFactsValid()) {
            metrics.error(INVALID_FACT_ERROR);
        }
        return metrics.finish();
    }

    /** 计算等待指标以及货物、任务的 P95 服务约束。 */
    private void calculateWaitingFacts(
            MetricAccumulator metrics,
            SimulationTick tick,
            String simulationRunId
    ) {
        List<EvaluationMetricId> dependent = List.of(
                GLOBAL_WAITING_SERVICE_COMPLIANT,
                VEHICLE_CUMULATIVE_WAIT_SECONDS,
                VEHICLE_P95_WAIT_SECONDS,
                CARGO_OVERDUE_UNTRANSPORTED_TONNES,
                CARGO_AVERAGE_WAIT_SECONDS,
                CARGO_P95_WAIT_SECONDS,
                TASK_AVERAGE_RESPONSE_SECONDS,
                TASK_AVERAGE_START_WAIT_SECONDS,
                TASK_P95_SERVICE_WAIT_SECONDS
        );
        if (tick == null || simulationRunId == null || simulationRunId.isBlank()
                || waitMetricFactReader == null) {
            // 缺少运行标识时无法定位等待账本，仅将等待指标标为不适用。
            dependent.forEach(id -> metrics.zeroDenominator(
                    id, "缺少评价运行标识或等待账本读取器，兼容入口不计算等待指标"));
            return;
        }

        final WaitMetricFactReader.WaitFacts facts;
        try {
            facts = waitMetricFactReader.read(simulationRunId);
        } catch (RuntimeException ex) {
            // 等待账本读取或结构异常只影响等待指标，不修改业务事实或伪造零值。
            dependent.forEach(id -> metrics.invalid(id, "等待事实账本读取失败"));
            return;
        }
        if (facts.projectionFailed()) {
            String reason = "当前运行存在等待事实投影失败；" + facts.failureReason();
            dependent.forEach(id -> metrics.invalid(id, reason));
            return;
        }

        try {
            WaitingMetricResult result = aggregateWaitingFacts(facts, tick.tickEnd());
            publishVehicleWaiting(metrics, result);
            publishCargoWaiting(metrics, result);
            publishTaskWaiting(metrics, result);
            if (result.cargoP95Seconds() == null || result.taskP95Seconds() == null) {
                metrics.zeroDenominator(GLOBAL_WAITING_SERVICE_COMPLIANT,
                        "货物和任务两类等待观察样本尚未同时形成");
            } else {
                boolean compliant = Math.max(result.cargoP95Seconds(), result.taskP95Seconds())
                        <= policy.getMaxServiceWaitSeconds();
                // 布尔指标沿用数值契约：1 表示满足，0 表示违反。
                metrics.available(GLOBAL_WAITING_SERVICE_COMPLIANT, compliant ? 1.0 : 0.0);
            }
        } catch (RuntimeException ex) {
            dependent.forEach(id -> metrics.invalid(id, "等待事实时间、重量或状态不一致"));
        }
    }

    /** 从当前运行的交付账本计算逾期任务数和准时完成率。 */
    private void calculateDeliverySlaFacts(
            MetricAccumulator metrics,
            SimulationTick tick,
            String simulationRunId,
            List<Assignment> assignments,
            List<ShipmentItem> shipmentItems
    ) {
        List<EvaluationMetricId> dependent = List.of(
                TASK_OVERDUE_COUNT,
                TASK_ON_TIME_COMPLETION_RATIO
        );
        if (tick == null || simulationRunId == null || simulationRunId.isBlank()
                || deliverySlaMetricFactReader == null) {
            // 缺少运行边界时标为不适用，不用墙钟或其他运行的事实补算。
            dependent.forEach(id -> metrics.zeroDenominator(
                    id, "缺少评价运行标识、仿真 tick 或交付 SLA 账本读取器"));
            return;
        }

        final DeliverySlaMetricFactReader.DeliveryFacts facts;
        try {
            facts = deliverySlaMetricFactReader.read(simulationRunId);
        } catch (RuntimeException ex) {
            dependent.forEach(id -> metrics.invalid(id, "交付 SLA 事实账本读取或模型校验失败"));
            return;
        }
        if (facts.projectionFailed()) {
            String reason = "当前运行存在交付 SLA 事实投影失败；" + facts.failureReason();
            dependent.forEach(id -> metrics.invalid(id, reason));
            return;
        }

        try {
            DeliveryMetricResult result = aggregateDeliveryFacts(
                    facts.facts(), assignments, shipmentItems, tick.tickEnd());
            metrics.available(TASK_OVERDUE_COUNT, result.overdueTaskCount());
            if (result.completedTaskCount() == 0L) {
                // 没有已完成样本时，准时率既不是 0% 也不是 100%。
                metrics.zeroDenominator(TASK_ON_TIME_COMPLETION_RATIO, "当前运行尚无已完成任务样本");
            } else {
                metrics.ratio(
                        TASK_ON_TIME_COMPLETION_RATIO,
                        result.onTimeCompletedTaskCount(),
                        result.completedTaskCount(),
                        "当前运行尚无已完成任务样本");
            }
        } catch (RuntimeException ex) {
            dependent.forEach(id -> metrics.invalid(id, "交付截止、卸货完成时间或任务关联事实不一致"));
        }
    }

    private DeliveryMetricResult aggregateDeliveryFacts(
            List<DeliverySlaFact> facts,
            List<Assignment> assignments,
            List<ShipmentItem> shipmentItems,
            LocalDateTime tickEnd
    ) {
        Map<Long, ShipmentItem> itemsById = new HashMap<>();
        Map<Long, List<ShipmentItem>> itemsByAssignment = new HashMap<>();
        for (ShipmentItem item : shipmentItems) {
            requireRunFact(item != null && item.getId() != null && item.getId() > 0L
                    && item.getStatus() != null, "shipment item delivery state");
            if (itemsById.put(item.getId(), item) != null) {
                throw new IllegalStateException("duplicate shipment item id");
            }
            if (item.getAssignment() != null && item.getAssignment().getId() != null) {
                itemsByAssignment.computeIfAbsent(item.getAssignment().getId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        Map<Long, DeliverySlaFact> factsByItem = new HashMap<>();
        for (DeliverySlaFact fact : facts) {
            requireRunFact(fact != null && fact.getShipmentItemId() != null
                    && fact.getShipmentItemId() > 0L, "delivery SLA fact identity");
            if (factsByItem.put(fact.getShipmentItemId(), fact) != null) {
                throw new IllegalStateException("duplicate delivery SLA fact for shipment item");
            }
        }
        // 当前运行的业务项必须与账本一一对应，不能静默忽略多记或漏记。
        requireRunFact(itemsById.keySet().equals(factsByItem.keySet()), "delivery SLA fact coverage");

        for (Map.Entry<Long, ShipmentItem> entry : itemsById.entrySet()) {
            ShipmentItem item = entry.getValue();
            DeliverySlaFact fact = factsByItem.get(entry.getKey());
            validateDeliveryState(item, fact, tickEnd);
        }

        Map<Long, Assignment> assignmentsById = new HashMap<>();
        for (Assignment assignment : assignments) {
            requireRunFact(assignment != null && assignment.getId() != null && assignment.getId() > 0L
                    && assignment.getStatus() != null, "assignment delivery state");
            if (assignmentsById.put(assignment.getId(), assignment) != null) {
                throw new IllegalStateException("duplicate assignment id");
            }
        }

        long overdueTaskCount = 0L;
        long completedTaskCount = 0L;
        long onTimeCompletedTaskCount = 0L;
        for (Assignment assignment : assignmentsById.values()) {
            List<ShipmentItem> effectiveItems = itemsByAssignment
                    .getOrDefault(assignment.getId(), List.of())
                    .stream()
                    .filter(item -> item.getStatus() != ShipmentItem.ShipmentItemStatus.CANCELLED)
                    .toList();

            if (isDeliveryActive(assignment.getStatus())) {
                requireRunFact(!effectiveItems.isEmpty(), "active assignment shipment items");
                boolean overdue = effectiveItems.stream()
                        .map(item -> factsByItem.get(item.getId()))
                        .anyMatch(fact -> fact.getStatus() == DeliverySlaFact.Status.OPEN
                                && tickEnd.isAfter(fact.getDeliveryDeadlineSimTime()));
                if (overdue) overdueTaskCount++;
            } else if (assignment.getStatus() == Assignment.AssignmentStatus.COMPLETED) {
                requireRunFact(!effectiveItems.isEmpty(), "completed assignment shipment items");
                boolean allDelivered = effectiveItems.stream()
                        .map(item -> factsByItem.get(item.getId()))
                        .allMatch(fact -> fact.getStatus() == DeliverySlaFact.Status.DELIVERED);
                requireRunFact(allDelivered, "completed assignment delivery facts");
                completedTaskCount++;
                boolean allOnTime = effectiveItems.stream()
                        .map(item -> factsByItem.get(item.getId()))
                        .allMatch(fact -> !fact.getDeliveredSimTime()
                                .isAfter(fact.getDeliveryDeadlineSimTime()));
                if (allOnTime) onTimeCompletedTaskCount++;
            }
            // 失败和取消的任务不进入分母，也不计作仍在活动的逾期任务。
        }
        return new DeliveryMetricResult(
                overdueTaskCount, completedTaskCount, onTimeCompletedTaskCount);
    }

    private void validateDeliveryState(
            ShipmentItem item,
            DeliverySlaFact fact,
            LocalDateTime tickEnd
    ) {
        requireRunFact(fact.getDemandCreatedSimTime() != null
                && fact.getDeliveryDeadlineSimTime() != null
                && fact.getDeliveryDeadlineSimTime().isAfter(fact.getDemandCreatedSimTime()),
                "delivery SLA time boundary");
        if (item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
            requireRunFact(fact.getStatus() == DeliverySlaFact.Status.CANCELLED
                    && fact.getDeliveredSimTime() == null
                    && fact.getDeliveryAssignmentId() == null, "cancelled delivery fact");
            return;
        }
        if (item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
            requireRunFact(fact.getStatus() == DeliverySlaFact.Status.DELIVERED
                    && fact.getDeliveredSimTime() != null
                    && !fact.getDeliveredSimTime().isBefore(fact.getDemandCreatedSimTime())
                    && !fact.getDeliveredSimTime().isAfter(tickEnd)
                    && fact.getDeliveryAssignmentId() != null
                    && fact.getDeliveryAssignmentId() > 0L,
                    "completed delivery fact");
            Long currentAssignmentId = item.getAssignment() == null ? null : item.getAssignment().getId();
            requireRunFact(fact.getDeliveryAssignmentId().equals(currentAssignmentId),
                    "delivery assignment identity");
            return;
        }
        requireRunFact(fact.getStatus() == DeliverySlaFact.Status.OPEN
                && fact.getDeliveredSimTime() == null
                && fact.getDeliveryAssignmentId() == null, "open delivery fact");
    }

    private boolean isDeliveryActive(Assignment.AssignmentStatus status) {
        return status == Assignment.AssignmentStatus.WAITING
                || status == Assignment.AssignmentStatus.ASSIGNED
                || status == Assignment.AssignmentStatus.IN_PROGRESS
                || status == Assignment.AssignmentStatus.DELAYED;
    }

    /** 任务级交付汇总只保留计算指标所需的三个计数。 */
    private record DeliveryMetricResult(
            long overdueTaskCount,
            long completedTaskCount,
            long onTimeCompletedTaskCount
    ) { }

    /** 开放、成功和终止等待样本按各自的统计口径归集。 */
    private WaitingMetricResult aggregateWaitingFacts(
            WaitMetricFactReader.WaitFacts facts,
            java.time.LocalDateTime tickEnd
    ) {
        List<Long> vehicleObserved = new ArrayList<>();
        double vehicleCumulative = 0.0;
        for (VehicleWaitEpisode episode : facts.vehicleEpisodes()) {
            requireRunFact(episode != null && episode.getStatus() != null, "vehicle wait fact");
            long seconds = episode.observedSeconds(tickEnd);
            requireNonNegative(seconds, "vehicle wait seconds");
            vehicleObserved.add(seconds);
            vehicleCumulative += seconds;
        }

        List<Long> cargoObserved = new ArrayList<>();
        double cargoCompletedSum = 0.0;
        long cargoCompletedCount = 0L;
        double overdueTonnes = 0.0;
        for (CargoWaitEpisode episode : facts.cargoEpisodes()) {
            requireRunFact(episode != null && episode.getOutcome() != null, "cargo wait fact");
            long seconds = episode.observedSeconds(tickEnd);
            requireNonNegative(seconds, "cargo wait seconds");
            Double tonnes = episode.getWeightTonnes();
            requireRunFact(tonnes != null && Double.isFinite(tonnes) && tonnes >= 0.0,
                    "cargo wait tonnes");
            cargoObserved.add(seconds);
            if (episode.getOutcome() == CargoWaitEpisode.Outcome.TRANSPORT_STARTED) {
                cargoCompletedSum += seconds;
                cargoCompletedCount++;
            } else if (seconds > policy.getMaxServiceWaitSeconds()) {
                // 只有尚未首次有载运输的开放或取消样本计入超时吨位。
                overdueTonnes += tonnes;
            }
        }

        List<Long> taskObserved = new ArrayList<>();
        double responseSum = 0.0;
        long responseCount = 0L;
        double startWaitSum = 0.0;
        long startWaitCount = 0L;
        for (TaskWaitEpisode episode : facts.taskEpisodes()) {
            requireRunFact(episode != null && episode.getOutcome() != null, "task wait fact");
            long response = episode.responseSeconds();
            long service = episode.observedServiceWaitSeconds(tickEnd);
            requireNonNegative(response, "task response seconds");
            requireNonNegative(service, "task service wait seconds");
            responseSum += response;
            responseCount++;
            taskObserved.add(service);
            if (episode.getOutcome() == TaskWaitEpisode.Outcome.STARTED) {
                long startWait = episode.startWaitSeconds();
                requireNonNegative(startWait, "task start wait seconds");
                startWaitSum += startWait;
                startWaitCount++;
            }
        }

        return new WaitingMetricResult(
                vehicleCumulative,
                nearestRankP95(vehicleObserved),
                overdueTonnes,
                cargoCompletedCount == 0L ? null : cargoCompletedSum / cargoCompletedCount,
                nearestRankP95(cargoObserved),
                responseCount == 0L ? null : responseSum / responseCount,
                startWaitCount == 0L ? null : startWaitSum / startWaitCount,
                nearestRankP95(taskObserved)
        );
    }

    private void publishVehicleWaiting(MetricAccumulator metrics, WaitingMetricResult result) {
        if (result.vehicleP95Seconds() == null) {
            metrics.zeroDenominator(VEHICLE_CUMULATIVE_WAIT_SECONDS, "当前运行尚无车辆可用空闲等待样本");
            metrics.zeroDenominator(VEHICLE_P95_WAIT_SECONDS, "当前运行尚无车辆可用空闲等待样本");
        } else {
            metrics.available(VEHICLE_CUMULATIVE_WAIT_SECONDS, result.vehicleCumulativeSeconds());
            metrics.available(VEHICLE_P95_WAIT_SECONDS, result.vehicleP95Seconds());
        }
    }

    private void publishCargoWaiting(MetricAccumulator metrics, WaitingMetricResult result) {
        metrics.available(CARGO_OVERDUE_UNTRANSPORTED_TONNES, result.overdueCargoTonnes());
        if (result.cargoAverageSeconds() == null) {
            metrics.zeroDenominator(CARGO_AVERAGE_WAIT_SECONDS, "当前运行尚无成功开始有载运输的货物样本");
        } else {
            metrics.available(CARGO_AVERAGE_WAIT_SECONDS, result.cargoAverageSeconds());
        }
        if (result.cargoP95Seconds() == null) {
            metrics.zeroDenominator(CARGO_P95_WAIT_SECONDS, "当前运行尚无货物等待观察样本");
        } else {
            metrics.available(CARGO_P95_WAIT_SECONDS, result.cargoP95Seconds());
        }
    }

    private void publishTaskWaiting(MetricAccumulator metrics, WaitingMetricResult result) {
        if (result.taskAverageResponseSeconds() == null) {
            metrics.zeroDenominator(TASK_AVERAGE_RESPONSE_SECONDS, "当前运行尚无已确认任务等待样本");
        } else {
            metrics.available(TASK_AVERAGE_RESPONSE_SECONDS, result.taskAverageResponseSeconds());
        }
        if (result.taskAverageStartSeconds() == null) {
            metrics.zeroDenominator(TASK_AVERAGE_START_WAIT_SECONDS, "当前运行尚无首次实际执行的任务样本");
        } else {
            metrics.available(TASK_AVERAGE_START_WAIT_SECONDS, result.taskAverageStartSeconds());
        }
        if (result.taskP95Seconds() == null) {
            metrics.zeroDenominator(TASK_P95_SERVICE_WAIT_SECONDS, "当前运行尚无任务整体等待观察样本");
        } else {
            metrics.available(TASK_P95_SERVICE_WAIT_SECONDS, result.taskP95Seconds());
        }
    }

    /** P95 使用 nearest-rank：索引为 ceil(0.95*n)-1，不做线性插值。 */
    private Double nearestRankP95(List<Long> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        List<Long> ordered = new ArrayList<>(samples);
        ordered.sort(Long::compareTo);
        int index = (int) Math.ceil(0.95 * ordered.size()) - 1;
        return ordered.get(index).doubleValue();
    }

    private void requireRunFact(boolean condition, String field) {
        if (!condition) {
            throw new IllegalStateException(field + " is invalid");
        }
    }

    private void requireNonNegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalStateException(field + " must be non-negative");
        }
    }

    /** 内部汇总结果只保留统计标量，不暴露账本实体。 */
    private record WaitingMetricResult(
            double vehicleCumulativeSeconds,
            Double vehicleP95Seconds,
            double overdueCargoTonnes,
            Double cargoAverageSeconds,
            Double cargoP95Seconds,
            Double taskAverageResponseSeconds,
            Double taskAverageStartSeconds,
            Double taskP95Seconds
    ) {
    }

    private void calculateEnvironmentFacts(MetricAccumulator metrics, SimulationTick tick) {
        List<EvaluationMetricId> dependent = List.of(
                ENV_NETWORK_AVERAGE_SPEED_KPH,
                ENV_CONGESTION_INDEX,
                ENV_ROAD_PASSABILITY_RATIO,
                ENV_CLOSED_ROAD_COUNT,
                ENV_ABNORMAL_EVENT_COUNT,
                ENV_WEATHER_RISK_LEVEL,
                ENV_TRAVEL_TIME_FACTOR,
                ENV_ENERGY_FACTOR
        );
        if (tick == null) {
            // 环境是逐 tick 事实；无 tick 时不能用快照版本或墙钟时间补造。
            dependent.forEach(id -> metrics.missing(id, "缺少当前 simulation tick，无法生成环境快照"));
            return;
        }
        if (environmentScenarioService == null) {
            // 简化测试构造器可能缺少场景服务，生产构造器不会进入此分支。
            dependent.forEach(id -> metrics.missing(id, "可复现环境场景提供器不可用"));
            return;
        }

        final EnvironmentScenarioSnapshot snapshot;
        try {
            snapshot = environmentScenarioService.snapshotFor(tick);
            // 不论场景是否影响运输进度，环境快照都必须与当前完整 tick 对齐。
            if (snapshot.loopIndex() != tick.loopIndex()
                    || !snapshot.validFrom().equals(tick.tickStart())
                    || !snapshot.validTo().equals(tick.tickEnd())) {
                throw new IllegalStateException("environment snapshot does not match current tick");
            }
        } catch (RuntimeException ex) {
            // 环境事实读取失败仅令相关环境指标无效，其余事实仍可形成部分完成快照。
            dependent.forEach(id -> metrics.invalid(id, "本轮环境快照生成或校验失败"));
            return;
        }

        metrics.available(ENV_NETWORK_AVERAGE_SPEED_KPH, snapshot.networkAverageSpeedKph());
        metrics.available(ENV_CONGESTION_INDEX, snapshot.congestionIndex());
        metrics.available(ENV_ROAD_PASSABILITY_RATIO, snapshot.roadPassabilityRatio());
        metrics.available(ENV_CLOSED_ROAD_COUNT, snapshot.closedRoadCount());
        metrics.available(ENV_ABNORMAL_EVENT_COUNT, snapshot.abnormalEventCount());
        metrics.available(ENV_WEATHER_RISK_LEVEL, snapshot.weatherRiskLevel());
        metrics.available(ENV_TRAVEL_TIME_FACTOR, snapshot.travelTimeFactor());
        try {
            // 环境能耗因子由能耗模型转换，不直接复用旅行时间放大系数。
            metrics.available(ENV_ENERGY_FACTOR,
                    energyEmissionModel.environmentEnergyFactor(snapshot.travelTimeFactor()));
        } catch (RuntimeException ex) {
            metrics.invalid(ENV_ENERGY_FACTOR, "环境能耗修正系数计算失败");
        }
    }

    /** 将可信的路段累计事实映射为车辆、任务及全局碳排指标。 */
    private void calculateEnergyEmissionFacts(MetricAccumulator metrics, LegFacts legFacts) {
        List<EvaluationMetricId> dependent = List.of(
                VEHICLE_TOTAL_ENERGY,
                VEHICLE_TOTAL_EMISSION_KG,
                VEHICLE_EMISSION_INTENSITY,
                TASK_EMISSION_KG,
                TASK_EMISSION_INTENSITY,
                GLOBAL_CARBON_INTENSITY
        );
        if (!legFacts.energyFactsValid()) {
            dependent.forEach(id -> metrics.invalid(id,
                    "路段能耗事实缺失、跨模型混用或与累计距离不一致"));
            return;
        }

        metrics.available(VEHICLE_TOTAL_ENERGY, legFacts.totalEnergyLiters());
        metrics.available(VEHICLE_TOTAL_EMISSION_KG, legFacts.totalEmissionKg());
        metrics.available(TASK_EMISSION_KG, legFacts.totalEmissionKg());
        if (legFacts.loadFactsValid()) {
            metrics.ratio(VEHICLE_EMISSION_INTENSITY,
                    legFacts.totalEmissionKg(), legFacts.executedTonneKm(), "实际有效吨公里为 0");
            metrics.ratio(TASK_EMISSION_INTENSITY,
                    legFacts.totalEmissionKg(), legFacts.executedTonneKm(), "任务实际吨公里为 0");
            metrics.ratio(GLOBAL_CARBON_INTENSITY,
                    legFacts.totalEmissionKg(), legFacts.executedTonneKm(), "实际有效吨公里为 0");
        } else {
            metrics.invalid(VEHICLE_EMISSION_INTENSITY, "有载路段吨公里事实无效");
            metrics.invalid(TASK_EMISSION_INTENSITY, "有载路段吨公里事实无效");
            metrics.invalid(GLOBAL_CARBON_INTENSITY, "有载路段吨公里事实无效");
        }
    }

    private void calculateNodeServiceFacts(
            MetricAccumulator metrics,
            List<NodeServiceEpisode> episodes,
            SimulationTick tick
    ) {
        if (nodeServiceEpisodeRepository == null) {
            // 简化测试构造器可能缺少仓库；生产环境不能用零值掩盖依赖缺失。
            metrics.missing(ENV_NODE_AVERAGE_SERVICE_SECONDS, "节点服务账本仓库不可用");
            metrics.missing(ENV_NODE_THROUGHPUT_TONNES, "节点服务账本仓库不可用");
            return;
        }
        if (nodeServiceLedgerHealth != null && nodeServiceLedgerHealth.hasProjectionFailures()) {
            // 存在漏记风险时使本轮节点指标失效，避免用残缺账本计算偏低的值。
            // 首个错误的上下文放在指标原因中，顶层错误码保持稳定。
            String reason = "当前运行存在节点服务账本投影失败；"
                    + nodeServiceLedgerHealth.describeFirstFailure();
            metrics.invalid(ENV_NODE_AVERAGE_SERVICE_SECONDS, reason);
            metrics.invalid(ENV_NODE_THROUGHPUT_TONNES, reason);
            return;
        }

        double totalServiceSeconds = 0.0;
        long completedCount = 0L;
        double tickThroughputTonnes = 0.0;
        boolean factsValid = true;

        for (NodeServiceEpisode episode : episodes) {
            if (episode == null || episode.getStatus() == null || episode.getActionType() == null
                    || episode.getServiceStartedAt() == null || episode.getArrivedAt() == null
                    || episode.getArrivedAt().isAfter(episode.getServiceStartedAt())) {
                factsValid = false;
                continue;
            }
            if (episode.getStatus() != NodeServiceEpisode.Status.COMPLETED) {
                // 进行中的服务属于有效事实，但不计入完成样本和本轮吞吐量。
                continue;
            }
            if (episode.getServiceCompletedAt() == null || episode.getProcessedTonnes() == null
                    || episode.getServiceCompletedAt().isBefore(episode.getServiceStartedAt())
                    || !isNonNegativeFinite(episode.getProcessedTonnes())) {
                factsValid = false;
                continue;
            }

            long serviceSeconds = episode.getServiceSeconds();
            if (serviceSeconds < 0L) {
                factsValid = false;
                continue;
            }
            totalServiceSeconds += serviceSeconds;
            completedCount++;

            // 主循环在 tickStart 处理到期动作；使用左闭右开区间避免边界重复计数。
            if (tick != null
                    && !episode.getServiceCompletedAt().isBefore(tick.tickStart())
                    && episode.getServiceCompletedAt().isBefore(tick.tickEnd())) {
                tickThroughputTonnes += episode.getProcessedTonnes();
            }
        }

        if (!factsValid) {
            metrics.invalid(ENV_NODE_AVERAGE_SERVICE_SECONDS, "节点服务事件缺少时间、动作或有效吨数事实");
            metrics.invalid(ENV_NODE_THROUGHPUT_TONNES, "节点服务事件缺少时间、动作或有效吨数事实");
            return;
        }
        metrics.ratio(
                ENV_NODE_AVERAGE_SERVICE_SECONDS,
                totalServiceSeconds,
                completedCount,
                "当前运行尚无已完成的节点装卸服务事件"
        );
        if (tick == null) {
            metrics.missing(ENV_NODE_THROUGHPUT_TONNES, "缺少当前 simulation tick，无法确定本轮处理量");
        } else {
            // 账本有效但本轮没有完成事件时，吞吐量为事实零值。
            metrics.available(ENV_NODE_THROUGHPUT_TONNES, tickThroughputTonnes);
        }
    }

    /** 即使整体采集失败，也返回字段齐全的指标结构。 */
    public Map<String, EvaluationMetricValue> failedMetricValues(String reason) {
        MetricAccumulator metrics = new MetricAccumulator(catalog);
        for (EvaluationMetricDefinition definition : catalog.all()) {
            EvaluationMetricValueStatus status = unresolvedStatus(definition);
            // 整体采集失败不覆盖“不适用”或“不支持”的既定原因。
            String metricReason = status == EvaluationMetricValueStatus.NOT_AVAILABLE
                    ? reason
                    : definition.readinessReason();
            metrics.replace(definition.id(), EvaluationMetricValue.unavailable(definition, status, metricReason));
        }
        return metrics.finish().metrics();
    }

    private static EvaluationMetricValueStatus unresolvedStatus(EvaluationMetricDefinition definition) {
        // 初始化与失败快照使用同一状态映射，保持指标语义一致。
        return switch (definition.readiness()) {
            case NOT_APPLICABLE -> EvaluationMetricValueStatus.NOT_APPLICABLE;
            case NOT_SUPPORTED -> EvaluationMetricValueStatus.NOT_SUPPORTED;
            default -> EvaluationMetricValueStatus.NOT_AVAILABLE;
        };
    }

    private VehicleFacts calculateVehicleFacts(
            MetricAccumulator metrics,
            List<Vehicle> vehicles,
            List<Assignment> assignments,
            List<AssignmentLeg> legs
    ) {
        metrics.available(VEHICLE_TOTAL_COUNT, vehicles.size());

        boolean statusValid = vehicles.stream().allMatch(vehicle -> vehicle != null && vehicle.getCurrentStatus() != null);
        if (statusValid) {
            metrics.available(VEHICLE_AVAILABLE_COUNT, vehicles.stream()
                    .filter(vehicle -> vehicle.getCurrentStatus() == Vehicle.VehicleStatus.IDLE).count());
        } else {
            metrics.invalid(VEHICLE_AVAILABLE_COUNT, "存在 currentStatus 为空的车辆");
        }

        Set<Long> inTransportVehicleIds = new HashSet<>();
        boolean inTransportValid = true;
        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.getStatus() == null) {
                inTransportValid = false;
                continue;
            }
            if (assignment.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS) {
                Vehicle vehicle = assignment.getAssignedVehicle();
                if (vehicle == null || vehicle.getId() == null) {
                    inTransportValid = false;
                } else {
                    inTransportVehicleIds.add(vehicle.getId());
                }
            }
        }
        if (inTransportValid) {
            metrics.available(VEHICLE_IN_TRANSPORT_COUNT, inTransportVehicleIds.size());
        } else {
            metrics.invalid(VEHICLE_IN_TRANSPORT_COUNT, "存在状态缺失或未绑定车辆的 IN_PROGRESS 任务");
        }

        calculateCurrentDrivingCounts(metrics, assignments, legs);
        calculateVehicleCapacityFacts(metrics, vehicles);
        return new VehicleFacts(statusValid);
    }

    private void calculateCurrentDrivingCounts(
            MetricAccumulator metrics,
            List<Assignment> assignments,
            List<AssignmentLeg> legs
    ) {
        Map<Long, Map<Integer, AssignmentLeg>> legsByAssignment = new HashMap<>();
        boolean legIndexValid = true;
        for (AssignmentLeg leg : legs) {
            if (leg == null || leg.getAssignment() == null || leg.getAssignment().getId() == null
                    || leg.getSequenceIndex() == null || leg.getSequenceIndex() < 0) {
                legIndexValid = false;
                continue;
            }
            AssignmentLeg duplicate = legsByAssignment
                    .computeIfAbsent(leg.getAssignment().getId(), ignored -> new HashMap<>())
                    .put(leg.getSequenceIndex(), leg);
            if (duplicate != null) {
                legIndexValid = false;
            }
        }

        Set<Long> emptyDrivingVehicleIds = new HashSet<>();
        Set<Long> loadedDrivingVehicleIds = new HashSet<>();
        boolean drivingFactsValid = legIndexValid;
        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.getStatus() == null) {
                drivingFactsValid = false;
                continue;
            }
            if (assignment.getStatus() != Assignment.AssignmentStatus.IN_PROGRESS) {
                continue;
            }
            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle == null || vehicle.getId() == null || vehicle.getCurrentStatus() == null) {
                drivingFactsValid = false;
                continue;
            }
            if (vehicle.getCurrentStatus() != Vehicle.VehicleStatus.ORDER_DRIVING
                    && vehicle.getCurrentStatus() != Vehicle.VehicleStatus.TRANSPORT_DRIVING) {
                continue;
            }
            if (assignment.getId() == null) {
                drivingFactsValid = false;
                continue;
            }
            AssignmentLeg currentLeg = legsByAssignment
                    .getOrDefault(assignment.getId(), Map.of())
                    .get(assignment.getCurrentLegIndex());
            if (currentLeg == null || currentLeg.getLoadState() == null) {
                drivingFactsValid = false;
                continue;
            }
            if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.ORDER_DRIVING
                    && currentLeg.getLoadState() == AssignmentLeg.LoadState.EMPTY) {
                emptyDrivingVehicleIds.add(vehicle.getId());
            } else if (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.TRANSPORT_DRIVING
                    && currentLeg.getLoadState() == AssignmentLeg.LoadState.LOADED) {
                loadedDrivingVehicleIds.add(vehicle.getId());
            } else {
                // 车辆状态与冻结载货事实冲突时，不把车辆计入错误类别。
                drivingFactsValid = false;
            }
        }
        if (drivingFactsValid) {
            // 车辆数量按车辆去重，不因多个活动任务重复计数。
            metrics.available(VEHICLE_EMPTY_DRIVING_COUNT, emptyDrivingVehicleIds.size());
            metrics.available(VEHICLE_LOADED_DRIVING_COUNT, loadedDrivingVehicleIds.size());
        } else {
            metrics.invalid(VEHICLE_EMPTY_DRIVING_COUNT, "当前行驶状态与任务路段事实不完整或冲突");
            metrics.invalid(VEHICLE_LOADED_DRIVING_COUNT, "当前行驶状态与任务路段事实不完整或冲突");
        }
    }

    private void calculateVehicleCapacityFacts(MetricAccumulator metrics, List<Vehicle> vehicles) {
        double totalLoad = 0.0;
        double totalCapacity = 0.0;
        double remainingCapacity = 0.0;
        long currentlyLoadedVehicles = 0L;
        long lowLoadedVehicles = 0L;
        long fullLoadedVehicles = 0L;
        boolean factsAvailable = true;
        boolean factsValid = true;

        for (Vehicle vehicle : vehicles) {
            if (vehicle == null || vehicle.getCurrentLoadTonnes() == null
                    || vehicle.getMaxLoadCapacityTonnes() == null) {
                factsAvailable = false;
                continue;
            }
            double load = vehicle.getCurrentLoadTonnes();
            double capacity = vehicle.getMaxLoadCapacityTonnes();
            if (!isNonNegativeFinite(load) || !isNonNegativeFinite(capacity) || load > capacity + 1.0e-9) {
                factsValid = false;
                continue;
            }
            totalLoad += load;
            totalCapacity += capacity;
            remainingCapacity += Math.max(capacity - load, 0.0);
            if (load > 0.0 && capacity > 0.0) {
                currentlyLoadedVehicles++;
                double ratio = load / capacity;
                if (ratio < policy.getLowLoadRatioThreshold()) {
                    lowLoadedVehicles++;
                }
                if (ratio >= policy.getFullLoadRatioThreshold()) {
                    fullLoadedVehicles++;
                }
            }
        }

        List<EvaluationMetricId> dependent = List.of(
                VEHICLE_CURRENT_LOAD_TONNES,
                VEHICLE_RATED_CAPACITY_TONNES,
                VEHICLE_REMAINING_CAPACITY_TONNES,
                VEHICLE_LOW_LOAD_RATIO,
                VEHICLE_FULL_LOAD_RATIO
        );
        if (!factsValid) {
            dependent.forEach(id -> metrics.invalid(id, "车辆载重或额定载重为负数、非有限值或载重超过额定值"));
            return;
        }
        if (!factsAvailable) {
            dependent.forEach(id -> metrics.missing(id, "存在缺失当前载重或额定载重的车辆"));
            return;
        }
        metrics.available(VEHICLE_CURRENT_LOAD_TONNES, totalLoad);
        metrics.available(VEHICLE_RATED_CAPACITY_TONNES, totalCapacity);
        metrics.available(VEHICLE_REMAINING_CAPACITY_TONNES, remainingCapacity);
        metrics.ratio(VEHICLE_LOW_LOAD_RATIO, lowLoadedVehicles, currentlyLoadedVehicles,
                "当前不存在正载重且正额定载重的车辆");
        metrics.ratio(VEHICLE_FULL_LOAD_RATIO, fullLoadedVehicles, currentlyLoadedVehicles,
                "当前不存在正载重且正额定载重的车辆");
    }

    private LegFacts calculateLegFacts(MetricAccumulator metrics, List<AssignmentLeg> legs) {
        double totalDistanceKm = 0.0;
        double emptyDistanceKm = 0.0;
        double executedTonneKm = 0.0;
        double capacityTonneKm = 0.0;
        // 能耗与排放来自当前运行的路段累计事实，不读取计划路线或车辆旧汇总字段。
        double totalEnergyLiters = 0.0;
        double totalEmissionKg = 0.0;
        boolean distanceFactsValid = true;
        boolean loadFactsValid = true;
        boolean capacityFactsValid = true;
        boolean energyFactsValid = true;
        // 每次扫描只冻结一次模型参数，避免同轮计算使用不同口径。
        EnergyEmissionModelSnapshot emissionModelSnapshot = energyEmissionModel.snapshot();

        for (AssignmentLeg leg : legs) {
            if (leg == null || leg.getLoadState() == null) {
                distanceFactsValid = false;
                loadFactsValid = false;
                capacityFactsValid = false;
                energyFactsValid = false;
                continue;
            }
            double distanceMeters = leg.getExecutedDistanceMeters();
            if (!isNonNegativeFinite(distanceMeters)) {
                distanceFactsValid = false;
                loadFactsValid = false;
                capacityFactsValid = false;
                energyFactsValid = false;
                continue;
            }
            double distanceKm = distanceMeters / 1000.0;
            totalDistanceKm += distanceKm;

            // 有执行距离却缺少对应能耗账本时标为无效，不用当前环境补算历史。
            double energyLiters = leg.getExecutedEnergyLiters();
            double emissionKg = leg.getExecutedEmissionKg();
            AssignmentLeg.EnergyFactStatus energyStatus = leg.getEnergyFactStatus();
            if (!isNonNegativeFinite(energyLiters) || !isNonNegativeFinite(emissionKg)
                    || energyStatus == AssignmentLeg.EnergyFactStatus.INVALID) {
                energyFactsValid = false;
            } else if (energyStatus == AssignmentLeg.EnergyFactStatus.PENDING) {
                if (distanceMeters > 0.0 || energyLiters > 0.0 || emissionKg > 0.0) {
                    energyFactsValid = false;
                }
            } else {
                boolean metadataValid = leg.getEmissionModelId() != null
                        && leg.getEmissionModelId().equals(emissionModelSnapshot.modelId())
                        && leg.getVehicleEmissionClassCode() != null;
                try {
                    Vehicle vehicle = leg.getVehicle();
                    Double capacity = vehicle == null ? null : vehicle.getMaxLoadCapacityTonnes();
                    metadataValid = metadataValid
                            && capacity != null
                            && leg.getVehicleEmissionClassCode().equals(
                                    energyEmissionModel.resolveVehicleClass(capacity).code()
                            );
                } catch (RuntimeException ex) {
                    metadataValid = false;
                }
                if (!metadataValid
                        || (distanceMeters > 0.0 && energyLiters <= 0.0)
                        || !approximatelyEqual(emissionKg,
                        energyLiters * emissionModelSnapshot.directEmissionKgPerLiter())) {
                    energyFactsValid = false;
                }
            }
            totalEnergyLiters += energyLiters;
            totalEmissionKg += emissionKg;

            if (leg.getLoadState() == AssignmentLeg.LoadState.EMPTY) {
                emptyDistanceKm += distanceKm;
                continue;
            }

            double loadTonnes = leg.getCurrentLoadTonnes();
            if (!isNonNegativeFinite(loadTonnes)) {
                loadFactsValid = false;
                capacityFactsValid = false;
                continue;
            }
            executedTonneKm += loadTonnes * distanceKm;
            if (loadTonnes > 0.0 && distanceKm > 0.0) {
                Vehicle vehicle = leg.getVehicle();
                Double capacityValue = vehicle == null ? null : vehicle.getMaxLoadCapacityTonnes();
                if (capacityValue == null || !Double.isFinite(capacityValue)
                        || capacityValue <= 0.0 || loadTonnes > capacityValue + 1.0e-9) {
                    capacityFactsValid = false;
                } else {
                    capacityTonneKm += capacityValue * distanceKm;
                }
            }
        }

        if (!distanceFactsValid) {
            List.of(VEHICLE_TOTAL_EXECUTED_DISTANCE_KM, VEHICLE_EMPTY_EXECUTED_DISTANCE_KM,
                            VEHICLE_EMPTY_MILEAGE_RATIO)
                    .forEach(id -> metrics.invalid(id, "路段实际距离或载货状态无效"));
        }
        if (!capacityFactsValid) {
            metrics.invalid(VEHICLE_DISTANCE_WEIGHTED_LOAD_RATIO, "有载路段载重或车辆额定载重无效");
        }
        return new LegFacts(totalDistanceKm, emptyDistanceKm, executedTonneKm,
                capacityTonneKm, totalEnergyLiters, totalEmissionKg,
                distanceFactsValid, loadFactsValid, capacityFactsValid, energyFactsValid);
    }

    private void calculateCargoFacts(
            MetricAccumulator metrics,
            List<ShipmentItem> shipmentItems,
            LegFacts legFacts
    ) {
        double required = 0.0;
        double unassigned = 0.0;
        double assigned = 0.0;
        double inTransit = 0.0;
        double delivered = 0.0;
        boolean weightsAvailable = true;
        boolean factsValid = true;

        for (ShipmentItem item : shipmentItems) {
            if (item == null || item.getStatus() == null) {
                factsValid = false;
                continue;
            }
            Double weightValue = item.getWeightTonnes();
            if (weightValue == null) {
                weightsAvailable = false;
                continue;
            }
            if (!isNonNegativeFinite(weightValue)) {
                factsValid = false;
                continue;
            }
            double weight = weightValue;
            required += weight;
            switch (item.getStatus()) {
                case NOT_ASSIGNED -> unassigned += weight;
                case ASSIGNED -> assigned += weight;
                case LOADED, IN_TRANSIT -> inTransit += weight;
                case DELIVERED -> delivered += weight;
                case CANCELLED -> {
                    // 取消货物保留在需求分母中，但不计入已完成吨位。
                }
            }
        }

        List<EvaluationMetricId> dependent = List.of(
                CARGO_REQUIRED_TONNES, CARGO_UNASSIGNED_TONNES, CARGO_ASSIGNED_NOT_LOADED_TONNES,
                CARGO_IN_TRANSIT_TONNES, CARGO_DELIVERED_TONNES,
                CARGO_DELIVERY_ACHIEVEMENT_RATIO, CARGO_UNMET_TONNES, GLOBAL_UNMET_DEMAND_RATIO
        );
        if (!factsValid) {
            dependent.forEach(id -> metrics.invalid(id, "货物状态、重量或记录无效"));
        } else if (!weightsAvailable) {
            dependent.forEach(id -> metrics.missing(id, "存在缺失吨制重量的货物项"));
        } else {
            metrics.available(CARGO_REQUIRED_TONNES, required);
            metrics.available(CARGO_UNASSIGNED_TONNES, unassigned);
            metrics.available(CARGO_ASSIGNED_NOT_LOADED_TONNES, assigned);
            metrics.available(CARGO_IN_TRANSIT_TONNES, inTransit);
            metrics.available(CARGO_DELIVERED_TONNES, delivered);
            metrics.available(CARGO_UNMET_TONNES, Math.max(required - delivered, 0.0));
            metrics.ratio(CARGO_DELIVERY_ACHIEVEMENT_RATIO, delivered, required, "总需求吨位为 0");
            metrics.ratio(GLOBAL_UNMET_DEMAND_RATIO, Math.max(required - delivered, 0.0), required,
                    "总需求吨位为 0");
        }

        if (legFacts.loadFactsValid()) {
            metrics.available(CARGO_EXECUTED_TONNE_KM, legFacts.executedTonneKm());
        } else {
            metrics.invalid(CARGO_EXECUTED_TONNE_KM, "有载路段载重事实无效");
        }
    }

    private void calculateTaskFacts(
            MetricAccumulator metrics,
            List<Assignment> assignments,
            List<AssignmentLeg> legs,
            LegFacts legFacts
    ) {
        metrics.available(TASK_TOTAL_COUNT, assignments.size());
        long assigned = 0L;
        long inProgress = 0L;
        long completed = 0L;
        boolean statusesValid = true;
        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.getStatus() == null) {
                statusesValid = false;
                continue;
            }
            switch (assignment.getStatus()) {
                case ASSIGNED -> assigned++;
                case IN_PROGRESS -> inProgress++;
                case COMPLETED -> completed++;
                default -> {
                    // 待执行、失败、取消和延迟任务只进入任务总数及完成率分母。
                }
            }
        }
        if (statusesValid) {
            metrics.available(TASK_ASSIGNED_PENDING_COUNT, assigned);
            metrics.available(TASK_IN_PROGRESS_COUNT, inProgress);
            metrics.available(TASK_COMPLETED_COUNT, completed);
            metrics.ratio(TASK_COMPLETION_RATIO, completed, assignments.size(), "当前运行任务总数为 0");
        } else {
            List.of(TASK_ASSIGNED_PENDING_COUNT, TASK_IN_PROGRESS_COUNT, TASK_COMPLETED_COUNT,
                            TASK_COMPLETION_RATIO)
                    .forEach(id -> metrics.invalid(id, "存在状态为空的任务"));
        }

        if (legFacts.loadFactsValid()) {
            metrics.available(TASK_TONNE_KM, legFacts.executedTonneKm());
        } else {
            metrics.invalid(TASK_TONNE_KM, "有载路段载重事实无效");
        }
        calculatePerTaskLegMetrics(metrics, assignments, legs);
    }

    private void calculatePerTaskLegMetrics(
            MetricAccumulator metrics,
            List<Assignment> assignments,
            List<AssignmentLeg> legs
    ) {
        Map<Long, List<AssignmentLeg>> byAssignment = new HashMap<>();
        boolean groupingValid = true;
        for (AssignmentLeg leg : legs) {
            if (leg == null || leg.getAssignment() == null || leg.getAssignment().getId() == null) {
                groupingValid = false;
                continue;
            }
            byAssignment.computeIfAbsent(leg.getAssignment().getId(), ignored -> new ArrayList<>()).add(leg);
        }

        double loadRatioSum = 0.0;
        long loadRatioTaskCount = 0L;
        long lowLoadTaskCount = 0L;
        double emptyPickupDistanceKm = 0.0;
        long executedPickupTaskCount = 0L;
        boolean loadFactsValid = groupingValid;
        boolean pickupFactsValid = groupingValid;

        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.getId() == null) {
                loadFactsValid = false;
                pickupFactsValid = false;
                continue;
            }
            List<AssignmentLeg> taskLegs = new ArrayList<>(byAssignment.getOrDefault(assignment.getId(), List.of()));
            taskLegs.sort(Comparator.comparing(AssignmentLeg::getSequenceIndex,
                    Comparator.nullsLast(Integer::compareTo)));
            if (!hasContiguousSequence(taskLegs)) {
                loadFactsValid = false;
                pickupFactsValid = false;
                continue;
            }

            double tonneKm = 0.0;
            double capacityTonneKm = 0.0;
            double taskPickupKm = 0.0;
            boolean beforeFirstLoadedLeg = true;
            for (AssignmentLeg leg : taskLegs) {
                if (leg.getLoadState() == null || !isNonNegativeFinite(leg.getExecutedDistanceMeters())
                        || !isNonNegativeFinite(leg.getCurrentLoadTonnes())) {
                    loadFactsValid = false;
                    pickupFactsValid = false;
                    continue;
                }
                double distanceKm = leg.getExecutedDistanceMeters() / 1000.0;
                if (leg.getLoadState() == AssignmentLeg.LoadState.LOADED) {
                    beforeFirstLoadedLeg = false;
                    double load = leg.getCurrentLoadTonnes();
                    tonneKm += load * distanceKm;
                    if (load > 0.0 && distanceKm > 0.0) {
                        Vehicle vehicle = leg.getVehicle();
                        Double capacity = vehicle == null ? null : vehicle.getMaxLoadCapacityTonnes();
                        if (capacity == null || !Double.isFinite(capacity) || capacity <= 0.0
                                || load > capacity + 1.0e-9) {
                            loadFactsValid = false;
                        } else {
                            capacityTonneKm += capacity * distanceKm;
                        }
                    }
                } else if (beforeFirstLoadedLeg) {
                    taskPickupKm += distanceKm;
                }
            }
            if (capacityTonneKm > 0.0) {
                double ratio = tonneKm / capacityTonneKm;
                loadRatioSum += ratio;
                loadRatioTaskCount++;
                if (ratio < policy.getLowLoadRatioThreshold()) {
                    lowLoadTaskCount++;
                }
            }
            emptyPickupDistanceKm += taskPickupKm;
            if (taskPickupKm > 0.0) {
                executedPickupTaskCount++;
            }
        }

        if (loadFactsValid) {
            metrics.ratio(TASK_AVERAGE_LOAD_RATIO, loadRatioSum, loadRatioTaskCount,
                    "不存在具有正有载实际里程和正额定载重的任务");
            metrics.available(TASK_LOW_LOAD_COUNT, lowLoadTaskCount);
        } else {
            metrics.invalid(TASK_AVERAGE_LOAD_RATIO, "任务有载路段序列、载重或额定载重事实无效");
            metrics.invalid(TASK_LOW_LOAD_COUNT, "任务有载路段序列、载重或额定载重事实无效");
        }
        if (pickupFactsValid) {
            metrics.available(TASK_EMPTY_PICKUP_DISTANCE_KM, emptyPickupDistanceKm);
            metrics.ratio(TASK_AVERAGE_EMPTY_PICKUP_DISTANCE_KM,
                    emptyPickupDistanceKm, executedPickupTaskCount, "不存在已执行的前置空载接驳路段");
        } else {
            metrics.invalid(TASK_EMPTY_PICKUP_DISTANCE_KM, "任务路段序列或空载接驳事实无效");
            metrics.invalid(TASK_AVERAGE_EMPTY_PICKUP_DISTANCE_KM, "任务路段序列或空载接驳事实无效");
        }
    }

    private void calculateGlobalFacts(MetricAccumulator metrics, LegFacts legFacts) {
        if (legFacts.distanceFactsValid()) {
            metrics.ratio(GLOBAL_EMPTY_MILEAGE_RATIO,
                    legFacts.emptyDistanceKm(), legFacts.totalDistanceKm(), "总实际里程为 0");
        } else {
            metrics.invalid(GLOBAL_EMPTY_MILEAGE_RATIO, "路段实际距离或载货状态无效");
        }
        if (legFacts.capacityFactsValid()) {
            if (legFacts.capacityTonneKm() > 0.0) {
                metrics.available(GLOBAL_CAPACITY_WASTE_RATIO,
                        1.0 - legFacts.executedTonneKm() / legFacts.capacityTonneKm());
            } else {
                metrics.zeroDenominator(GLOBAL_CAPACITY_WASTE_RATIO,
                        "不存在正载重且正额定载重的已执行有载里程");
            }
        } else {
            metrics.invalid(GLOBAL_CAPACITY_WASTE_RATIO, "有载路段载重或额定载重事实无效");
        }
    }

    private boolean hasContiguousSequence(List<AssignmentLeg> legs) {
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < legs.size(); index++) {
            Integer sequence = legs.get(index).getSequenceIndex();
            if (sequence == null || sequence != index || !seen.add(sequence)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    /** 容忍浮点累计带来的微小舍入差，但拒绝排放口径不一致造成的实质偏差。 */
    private boolean approximatelyEqual(double left, double right) {
        double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= 1.0e-9 * scale;
    }

    /** 计算结果携带降级状态和机器错误码，供快照层确定整体状态。 */
    public record Calculation(
            Map<String, EvaluationMetricValue> metrics,
            boolean degraded,
            List<String> errorCodes
    ) {
        public Calculation {
            // 按指标目录顺序输出，便于接口展示和快照比对。
            metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
            errorCodes = List.copyOf(errorCodes);
        }
    }

    private record VehicleFacts(boolean statusFactsValid) {
    }

    private record LegFacts(
            double totalDistanceKm,
            double emptyDistanceKm,
            double executedTonneKm,
            double capacityTonneKm,
            double totalEnergyLiters,
            double totalEmissionKg,
            boolean distanceFactsValid,
            boolean loadFactsValid,
            boolean capacityFactsValid,
            boolean energyFactsValid
    ) {
    }

    /** 集中校验指标状态和值，避免各指标采用不同的缺失值策略。 */
    private static final class MetricAccumulator {
        private final EvaluationMetricCatalog catalog;
        private final Map<EvaluationMetricId, EvaluationMetricValue> values = new EnumMap<>(EvaluationMetricId.class);
        private final Set<String> errorCodes = new LinkedHashSet<>();
        private boolean degraded;

        private MetricAccumulator(EvaluationMetricCatalog catalog) {
            this.catalog = catalog;
            for (EvaluationMetricDefinition definition : catalog.all()) {
                EvaluationMetricValueStatus status = unresolvedStatus(definition);
                String reason = definition.readiness() == EvaluationMetricReadiness.READY
                        ? "本轮尚未生成该 READY 指标"
                        : definition.readinessReason();
                values.put(definition.id(), EvaluationMetricValue.unavailable(definition, status, reason));
            }
        }

        private void available(EvaluationMetricId id, double value) {
            if (!Double.isFinite(value)) {
                invalid(id, "计算结果不是有限数值");
                return;
            }
            values.put(id, EvaluationMetricValue.available(catalog.require(id), value));
        }

        private void ratio(EvaluationMetricId id, double numerator, double denominator, String zeroReason) {
            if (!Double.isFinite(numerator) || numerator < 0.0
                    || !Double.isFinite(denominator) || denominator < 0.0) {
                invalid(id, "比例的分子或分母无效");
            } else if (denominator == 0.0) {
                zeroDenominator(id, zeroReason);
            } else {
                available(id, numerator / denominator);
            }
        }

        private void zeroDenominator(EvaluationMetricId id, String reason) {
            // 契约允许的零分母只影响对应指标，不降低整张快照的状态。
            replace(id, EvaluationMetricValue.unavailable(
                    catalog.require(id), EvaluationMetricValueStatus.NOT_AVAILABLE, reason));
        }

        private void missing(EvaluationMetricId id, String reason) {
            replace(id, EvaluationMetricValue.unavailable(
                    catalog.require(id), EvaluationMetricValueStatus.NOT_AVAILABLE, reason));
            degraded = true;
            error(READY_METRIC_UNAVAILABLE_ERROR);
        }

        private void invalid(EvaluationMetricId id, String reason) {
            replace(id, EvaluationMetricValue.unavailable(
                    catalog.require(id), EvaluationMetricValueStatus.INVALID, reason));
            degraded = true;
            error(INVALID_FACT_ERROR);
        }

        private void replace(EvaluationMetricId id, EvaluationMetricValue value) {
            values.put(id, value);
        }

        private void error(String code) {
            errorCodes.add(code);
        }

        private Calculation finish() {
            LinkedHashMap<String, EvaluationMetricValue> ordered = new LinkedHashMap<>();
            for (EvaluationMetricDefinition definition : catalog.all()) {
                EvaluationMetricValue value = values.get(definition.id());
                // 已就绪指标若仍处于初始占位状态，属于计算遗漏而非合法零分母。
                if (definition.readiness() == EvaluationMetricReadiness.READY
                        && value.status() == EvaluationMetricValueStatus.NOT_AVAILABLE
                        && "本轮尚未生成该 READY 指标".equals(value.reason())) {
                    degraded = true;
                    error(READY_METRIC_UNAVAILABLE_ERROR);
                }
                ordered.put(definition.id().getMetricId(), value);
            }
            return new Calculation(ordered, degraded, List.copyOf(errorCodes));
        }
    }
}
