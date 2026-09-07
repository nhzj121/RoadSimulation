package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentLegRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 4：后端权威运输路段进度服务。
 *
 * <p>本服务仍只计算“走到哪里”，但在路段新完成时会把唯一事件交给
 * {@link TransportLifecycleService}。货物、任务和车辆动作仍由生命周期服务执行，
 * 避免进度服务演变为新的巨型业务类。</p>
 */
@Service
public class TransportProgressService {

    private static final Logger log = LoggerFactory.getLogger(TransportProgressService.class);
    // Phase 4：COMPLETED 是严格终态；Phase 3 为对比而保留的追赶语义在切权后删除。
    private static final List<Assignment.AssignmentStatus> PROGRESS_ELIGIBLE_STATUSES = List.of(
            Assignment.AssignmentStatus.IN_PROGRESS
    );

    private final AssignmentRepository assignmentRepository;
    private final AssignmentLegRepository assignmentLegRepository;
    // Phase 4：路段完成后只通知生命周期服务，不在本类散写车辆/货物状态。
    private final TransportLifecycleService transportLifecycleService;
    private final TransactionTemplate assignmentProgressTransaction;

    public TransportProgressService(
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            TransportLifecycleService transportLifecycleService,
            PlatformTransactionManager transactionManager
    ) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentLegRepository = assignmentLegRepository;
        this.transportLifecycleService = Objects.requireNonNull(
                transportLifecycleService,
                "transportLifecycleService must not be null"
        );
        // Phase 4：每个任务使用独立事务；路段完成和对应车辆动作在同一事务中提交。
        this.assignmentProgressTransaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
        this.assignmentProgressTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Phase 4：推进当前数据库中所有 IN_PROGRESS 且仍有未完成路段的任务。
     * 外部环境在 Phase 7 才引入；本阶段固定使用规划距离/规划秒数，不使用随机倍率。
     */
    public List<TransportProgressResult> advanceAllActiveAssignments(SimulationTick tick) {
        requireTick(tick);
        List<Long> assignmentIds = assignmentLegRepository.findProgressCandidateAssignmentIds(
                AssignmentLeg.ProgressStatus.COMPLETED,
                PROGRESS_ELIGIBLE_STATUSES
        );
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return List.of();
        }

        List<TransportProgressResult> results = new ArrayList<>();
        for (Long assignmentId : assignmentIds) {
            if (assignmentId != null) {
                try {
                    // Phase 4：TransactionTemplate 保证每个 Assignment 的路段+动作事件独立提交或回滚。
                    TransportProgressResult result = assignmentProgressTransaction.execute(
                            status -> advanceAssignment(assignmentId, tick)
                    );
                    if (result == null) {
                        throw new IllegalStateException("Authoritative progress transaction returned no result");
                    }
                    results.add(result);
                } catch (RuntimeException ex) {
                    // Phase 4：单任务失败作为数据返回，其它车辆仍可在本轮继续推进。
                    log.error(
                            "[Phase4 Progress] assignment failed but remaining assignments continue. assignmentId={}, loop={}, reason={}",
                            assignmentId,
                            tick.loopIndex(),
                            ex.getMessage(),
                            ex
                    );
                    results.add(new TransportProgressResult(
                            assignmentId,
                            null,
                            null,
                            tick.loopIndex(),
                            TransportProgressResult.Outcome.FAILED,
                            0L,
                            tick.availableSeconds(),
                            false,
                            false,
                            ex.getMessage()
                    ));
                }
            }
        }
        return List.copyOf(results);
    }

    /**
     * Phase 4：以方案 A 推进一个任务——本轮最多完成当前一个路段，剩余时间不传给下一状态。
     * 该公开入口也用于确定性数学测试和以后按任务重放诊断。
     */
    @Transactional
    public TransportProgressResult advanceAssignment(Long assignmentId, SimulationTick tick) {
        if (assignmentId == null) {
            throw new IllegalArgumentException("assignmentId must not be null");
        }
        requireTick(tick);

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        // Phase 4：只有 IN_PROGRESS 任务能消费路段时间；任何终态都不得追赶进度。
        if (!PROGRESS_ELIGIBLE_STATUSES.contains(assignment.getStatus())) {
            return result(
                    assignment,
                    null,
                    tick,
                    TransportProgressResult.Outcome.ASSIGNMENT_NOT_ELIGIBLE,
                    0L,
                    tick.availableSeconds(),
                    false,
                    false
            );
        }

        // Phase 3 二次修复：只有具备推进资格的任务才要求存在规划路段；取消/失败/未开始任务应直接返回不推进结果。
        List<AssignmentLeg> legs = orderedAndValidatedLegs(assignmentId);
        int currentLegIndex = assignment.getCurrentLegIndex();
        // Phase 3 修复：先校验游标与整条路段状态链，再做重复 tick 判断；否则脏游标可能借由幂等分支被静默掩盖。
        validateLegCursorState(assignmentId, legs, currentLegIndex);

        Optional<AssignmentLeg> alreadyProcessed = legs.stream()
                .filter(leg -> Objects.equals(leg.getLastProcessedLoopIndex(), tick.loopIndex()))
                .findFirst();
        if (alreadyProcessed.isPresent()) {
            AssignmentLeg duplicateLeg = alreadyProcessed.get();
            // Phase 3：扫描整项任务而非只看 currentLegIndex，防止完成路段后重复 tick 错推下一路段。
            return result(
                    assignment,
                    duplicateLeg,
                    tick,
                    TransportProgressResult.Outcome.DUPLICATE_TICK_SKIPPED,
                    0L,
                    tick.availableSeconds(),
                    // Phase 4：重复 tick 不能重放历史路段完成和生命周期事件。
                    false,
                    false
            );
        }

        int latestProcessedLoop = legs.stream()
                .map(AssignmentLeg::getLastProcessedLoopIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        // Phase 3：相同 tick 是幂等跳过；更早的 tick 表示调用顺序错误，不能倒退覆盖新进度。
        if (tick.loopIndex() < latestProcessedLoop) {
            throw new IllegalStateException(
                    "Out-of-order simulation tick for assignment " + assignmentId
                            + ": latest=" + latestProcessedLoop + ", incoming=" + tick.loopIndex()
            );
        }

        if (currentLegIndex == legs.size()) {
            return result(
                    assignment,
                    null,
                    tick,
                    TransportProgressResult.Outcome.ALL_LEGS_COMPLETED,
                    0L,
                    tick.availableSeconds(),
                    false,
                    // Phase 4：ALL_LEGS_COMPLETED 是只读快照，不再次发出路段完成事件。
                    false
            );
        }
        AssignmentLeg leg = legs.get(currentLegIndex);

        Vehicle vehicle = assignment.getAssignedVehicle();
        if (vehicle == null) {
            throw new IllegalStateException("IN_PROGRESS assignment has no vehicle: " + assignmentId);
        }
        Vehicle.VehicleStatus expectedDrivingStatus = expectedDrivingStatus(leg);
        Vehicle.VehicleStatus currentVehicleStatus = vehicle.getCurrentStatus();
        if (currentVehicleStatus != Vehicle.VehicleStatus.ORDER_DRIVING
                && currentVehicleStatus != Vehicle.VehicleStatus.TRANSPORT_DRIVING) {
            // Phase 4：装卸/等待/故障是合法暂停；不写 lastProcessedLoopIndex，同轮内仍可安全重试。
            return result(
                    assignment,
                    leg,
                    tick,
                    TransportProgressResult.Outcome.WAITING_FOR_DRIVING_STATE,
                    0L,
                    tick.availableSeconds(),
                    false,
                    false
            );
        }
        if (currentVehicleStatus != expectedDrivingStatus) {
            // Phase 4：行驶状态与冻结载货快照不一致是数据错位，必须失败封闭，不能静默算错指标。
            throw new IllegalStateException(
                    "Vehicle driving state does not match active leg load state: assignmentId=" + assignmentId
                            + ", legIndex=" + leg.getSequenceIndex()
                            + ", expected=" + expectedDrivingStatus
                            + ", actual=" + currentVehicleStatus
            );
        }

        if (leg.getProgressStatus() == AssignmentLeg.ProgressStatus.PENDING
                && leg.getLoadState() == AssignmentLeg.LoadState.LOADED) {
            // Phase 4：载货路段第一次真正行驶时，由后端把货物从 LOADED 推进到 IN_TRANSIT。
            transportLifecycleService.markTransportStarted(
                    assignment,
                    tick.tickStart(),
                    "Phase4 TransportProgressService"
            );
        }

        TransportProgressResult result = advanceCurrentLeg(assignment, legs, leg, tick);
        // Phase 4：显式 save 保留清晰写入边界；@Version 拒绝重复请求的静默覆盖。
        assignmentLegRepository.save(leg);
        if (result.legCompleted()) {
            assignmentRepository.save(assignment);
            // Phase 4：方案 A 丢弃本 tick 剩余时间，因此下一动作从 tickEnd 开始，不偷用剩余秒数。
            transportLifecycleService.handleLegCompleted(
                    assignment,
                    leg,
                    vehicle,
                    tick.tickEnd(),
                    result.allLegsCompleted(),
                    "Phase4 TransportProgressService"
            );
        }
        return result;
    }

    private TransportProgressResult advanceCurrentLeg(
            Assignment assignment,
            List<AssignmentLeg> legs,
            AssignmentLeg leg,
            SimulationTick tick
    ) {
        double plannedDistance = requirePlannedDistance(assignment.getId(), leg);
        long plannedSeconds = requirePlannedSeconds(assignment.getId(), leg);
        double executedDistance = leg.getExecutedDistanceMeters();
        long executedSeconds = leg.getExecutedDrivingSeconds();

        // Phase 3：零秒只能对应零距离；拒绝“零耗时瞬移”，避免产生无限速度和失真的评价指标。
        // Phase 3 二次修复：零耗时只允许数学上的零距离；不能用完成判定容差放行任何正距离“瞬移”。
        if (plannedSeconds == 0L && plannedDistance > 0.0) {
            throw new IllegalStateException(
                    "Positive-distance leg cannot have zero planned seconds: assignmentId="
                            + assignment.getId() + ", legIndex=" + leg.getSequenceIndex()
            );
        }
        if (executedSeconds > plannedSeconds) {
            throw new IllegalStateException("Executed seconds exceed plan before authoritative advancement");
        }

        long remainingLegSeconds = plannedSeconds - executedSeconds;
        long consumedSeconds = Math.min(tick.availableSeconds(), remainingLegSeconds);
        long newExecutedSeconds = Math.addExact(executedSeconds, consumedSeconds);
        boolean completed = newExecutedSeconds == plannedSeconds;

        double newExecutedDistance;
        if (completed) {
            // Phase 3：完成时直接落在计划终点，消除浮点除法造成的尾差和越界。
            newExecutedDistance = plannedDistance;
        } else {
            // Phase 3：时间是唯一累计资源，距离每次由累计秒数反算，避免两个累计量各自截断后漂移。
            newExecutedDistance = plannedDistance * newExecutedSeconds / (double) plannedSeconds;
        }
        // Phase 3 修复：累计距离的单调性不使用“允许回退”的浮点容差；哪怕极小回退也必须拒绝写库。
        if (newExecutedDistance < executedDistance) {
            throw new IllegalStateException("Authoritative advancement would move executed distance backwards");
        }

        LocalDateTime startedAt = leg.getStartedSimTime();
        if (startedAt == null) {
            // Phase 3：开始时间使用本轮仿真窗口起点，绝不读取系统墙上时间。
            startedAt = tick.tickStart();
            leg.setStartedSimTime(startedAt);
        }
        leg.setExecutedDrivingSeconds(newExecutedSeconds);
        leg.setExecutedDistanceMeters(newExecutedDistance);
        leg.setLastProcessedLoopIndex(tick.loopIndex());

        if (completed) {
            // Phase 3：完成时间只增加本路段实际消费秒数；方案 A 的剩余秒数不计入下一状态。
            leg.setCompletedSimTime(tick.tickStart().plusSeconds(consumedSeconds));
            leg.setProgressStatus(AssignmentLeg.ProgressStatus.COMPLETED);
            assignment.setCurrentLegIndex(assignment.getCurrentLegIndex() + 1);
        } else {
            leg.setProgressStatus(AssignmentLeg.ProgressStatus.RUNNING);
        }
        leg.validateExecutionState();

        long remainingTickSeconds = tick.availableSeconds() - consumedSeconds;
        boolean allLegsCompleted = completed && assignment.getCurrentLegIndex() == legs.size();
        if (allLegsCompleted) {
            // Phase 4：所有行驶路段完成只代表“已到达”；任务仍需经过后端 UNLOADING 才变为 COMPLETED。
            log.info(
                    "[Phase4 Progress] assignmentId={} finished all legs at loop={}, status={}, arrivalTime={}",
                    assignment.getId(),
                    tick.loopIndex(),
                    assignment.getStatus(),
                    leg.getCompletedSimTime()
            );
        }

        return result(
                assignment,
                leg,
                tick,
                completed
                        ? TransportProgressResult.Outcome.LEG_COMPLETED
                        : TransportProgressResult.Outcome.ADVANCED,
                consumedSeconds,
                remainingTickSeconds,
                completed,
                allLegsCompleted
        );
    }

    private List<AssignmentLeg> orderedAndValidatedLegs(Long assignmentId) {
        List<AssignmentLeg> legs = assignmentLegRepository.findByAssignmentIdOrderBySequenceIndexAsc(assignmentId);
        if (legs == null || legs.isEmpty()) {
            throw new IllegalStateException("Assignment has no planned legs: " + assignmentId);
        }
        List<AssignmentLeg> ordered = new ArrayList<>(legs);
        ordered.sort(Comparator.comparing(AssignmentLeg::getSequenceIndex, Comparator.nullsLast(Integer::compareTo)));
        for (int index = 0; index < ordered.size(); index++) {
            AssignmentLeg leg = ordered.get(index);
            // Phase 3：方案 A 使用 currentLegIndex 直接寻址，因此序号必须从零连续且实体归属正确。
            if (leg == null || !Objects.equals(leg.getSequenceIndex(), index)) {
                throw new IllegalStateException(
                        "Assignment legs must use contiguous zero-based sequence indexes: assignmentId=" + assignmentId
                );
            }
            if (leg.getAssignment() != null && leg.getAssignment().getId() != null
                    && !assignmentId.equals(leg.getAssignment().getId())) {
                throw new IllegalStateException("Assignment leg belongs to another assignment: legId=" + leg.getId());
            }
        }
        return ordered;
    }

    /**
     * Phase 3 修复：校验 currentLegIndex 是“已完成前缀”与“未完成后缀”的唯一分界。
     * 这可阻止游标越过未完成路段、指向已完成路段，或在全完成游标下遗留未完成路段。
     */
    private void validateLegCursorState(Long assignmentId, List<AssignmentLeg> legs, int currentLegIndex) {
        if (currentLegIndex < 0 || currentLegIndex > legs.size()) {
            throw new IllegalStateException(
                    "currentLegIndex is outside assignment legs: assignmentId=" + assignmentId
                            + ", currentLegIndex=" + currentLegIndex + ", legCount=" + legs.size()
            );
        }

        for (int index = 0; index < legs.size(); index++) {
            AssignmentLeg.ProgressStatus status = legs.get(index).getProgressStatus();
            if (index < currentLegIndex && status != AssignmentLeg.ProgressStatus.COMPLETED) {
                throw new IllegalStateException(
                        "currentLegIndex skips an unfinished leg: assignmentId=" + assignmentId
                                + ", currentLegIndex=" + currentLegIndex + ", legIndex=" + index
                );
            }
            if (index == currentLegIndex && status == AssignmentLeg.ProgressStatus.COMPLETED) {
                throw new IllegalStateException(
                        "currentLegIndex points to an already completed leg: assignmentId=" + assignmentId
                                + ", currentLegIndex=" + currentLegIndex
                );
            }
            if (index > currentLegIndex && status != AssignmentLeg.ProgressStatus.PENDING) {
                throw new IllegalStateException(
                        "A future leg contains premature progress: assignmentId=" + assignmentId
                                + ", currentLegIndex=" + currentLegIndex + ", legIndex=" + index
                );
            }
        }
    }

    private double requirePlannedDistance(Long assignmentId, AssignmentLeg leg) {
        Double value = leg.getPlannedDistanceMeters();
        if (value == null || !Double.isFinite(value) || value < 0.0) {
            throw new IllegalStateException(
                    "Invalid planned distance: assignmentId=" + assignmentId + ", value=" + value
            );
        }
        return value;
    }

    private long requirePlannedSeconds(Long assignmentId, AssignmentLeg leg) {
        Long value = leg.getPlannedDrivingSeconds();
        if (value == null || value < 0L) {
            throw new IllegalStateException(
                    "Invalid planned seconds: assignmentId=" + assignmentId + ", value=" + value
            );
        }
        return value;
    }

    private TransportProgressResult result(
            Assignment assignment,
            AssignmentLeg leg,
            SimulationTick tick,
            TransportProgressResult.Outcome outcome,
            long consumedSeconds,
            long remainingSeconds,
            boolean legCompleted,
            boolean allLegsCompleted
    ) {
        return new TransportProgressResult(
                assignment.getId(),
                leg == null ? null : leg.getId(),
                leg == null ? null : leg.getSequenceIndex(),
                tick.loopIndex(),
                outcome,
                consumedSeconds,
                remainingSeconds,
                legCompleted,
                allLegsCompleted,
                null
        );
    }

    /**
     * Phase 4：行驶状态由当前路段的冻结载货快照唯一派生，不使用可变的实时载重猜测历史语义。
     */
    private Vehicle.VehicleStatus expectedDrivingStatus(AssignmentLeg leg) {
        if (leg.getLoadState() == AssignmentLeg.LoadState.EMPTY) {
            return Vehicle.VehicleStatus.ORDER_DRIVING;
        }
        if (leg.getLoadState() == AssignmentLeg.LoadState.LOADED) {
            return Vehicle.VehicleStatus.TRANSPORT_DRIVING;
        }
        throw new IllegalStateException("Active assignment leg has no load state: legId=" + leg.getId());
    }

    private void requireTick(SimulationTick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("simulation tick must not be null");
        }
    }
}
