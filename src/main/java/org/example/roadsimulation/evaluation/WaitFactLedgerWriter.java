package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentLegRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.CargoWaitEpisodeRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.TaskWaitEpisodeRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.repository.VehicleWaitEpisodeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 9A-1：把一轮结束后的业务只读快照投影为车辆、货物和任务等待事实。
 *
 * <p>该类只调用业务仓库的查询方法和评价仓库的保存方法；禁止调用任务分配、路线规划、
 * 运输推进或状态转换服务，也不修改任何 Vehicle/Assignment/ShipmentItem/AssignmentLeg。</p>
 */
@Component
public class WaitFactLedgerWriter {

    private final VehicleWaitEpisodeRepository vehicleWaitRepository;
    private final CargoWaitEpisodeRepository cargoWaitRepository;
    private final TaskWaitEpisodeRepository taskWaitRepository;
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentLegRepository assignmentLegRepository;
    private final ShipmentItemRepository shipmentItemRepository;

    public WaitFactLedgerWriter(
            VehicleWaitEpisodeRepository vehicleWaitRepository,
            CargoWaitEpisodeRepository cargoWaitRepository,
            TaskWaitEpisodeRepository taskWaitRepository,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            AssignmentLegRepository assignmentLegRepository,
            ShipmentItemRepository shipmentItemRepository
    ) {
        this.vehicleWaitRepository = vehicleWaitRepository;
        this.cargoWaitRepository = cargoWaitRepository;
        this.taskWaitRepository = taskWaitRepository;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentLegRepository = assignmentLegRepository;
        this.shipmentItemRepository = shipmentItemRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void project(String simulationRunId, SimulationTick tick) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId must not be blank");
        }
        Objects.requireNonNull(tick, "simulation tick is required");

        // Phase 9A-1：同一事务读取本轮结束状态，三个账本看到完全相同的业务切片。
        List<Vehicle> vehicles = vehicleRepository.findAll();
        List<Assignment> assignments = assignmentRepository.findAll();
        List<AssignmentLeg> legs = assignmentLegRepository.findAll();
        List<ShipmentItem> items = shipmentItemRepository.findAll();
        LocalDateTime runStart = runStartOf(tick);

        Map<Long, List<ShipmentItem>> itemsByAssignment = indexItemsByAssignment(items);
        Map<Long, LocalDateTime> firstExecutionByAssignment = firstExecutionByAssignment(
                legs, runStart, tick.tickEnd());
        Map<Long, LocalDateTime> firstLoadedExecutionByItem = firstLoadedExecutionByItem(
                legs, itemsByAssignment, runStart, tick.tickEnd());

        Map<Long, CargoWaitEpisode> cargoFacts = projectCargoFacts(
                simulationRunId, tick, runStart, items, firstLoadedExecutionByItem);
        projectTaskFacts(
                simulationRunId, tick, runStart, assignments, itemsByAssignment,
                firstExecutionByAssignment, cargoFacts);
        projectVehicleFacts(simulationRunId, tick, runStart, vehicles, assignments);
    }

    private Map<Long, CargoWaitEpisode> projectCargoFacts(
            String runId,
            SimulationTick tick,
            LocalDateTime runStart,
            List<ShipmentItem> items,
            Map<Long, LocalDateTime> firstLoadedByItem
    ) {
        Map<Long, CargoWaitEpisode> facts = uniqueCargoFacts(runId);
        for (ShipmentItem item : items) {
            if (item == null || item.getId() == null || item.getId() <= 0L) {
                throw new IllegalStateException("shipment item lacks stable positive id");
            }
            Double tonnes = item.getWeightTonnes();
            if (tonnes == null || !Double.isFinite(tonnes) || tonnes < 0.0) {
                throw new IllegalStateException("shipment item " + item.getId() + " has invalid tonnes");
            }

            LocalDateTime loadedAt = firstLoadedByItem.get(item.getId());
            CargoWaitEpisode fact = facts.get(item.getId());
            if (fact == null) {
                CanonicalStart demandStart = canonicalStart(
                        item.getCreatedTime(), runStart, tick.tickEnd(),
                        loadedAt != null ? loadedAt : tick.tickStart());
                fact = CargoWaitEpisode.open(
                        runId, item.getId(), tonnes, demandStart.time(),
                        demandStart.time(),
                        demandStart.source());
                facts.put(item.getId(), fact);
            }

            if (fact.getOutcome() == CargoWaitEpisode.Outcome.OPEN) {
                if (loadedAt != null) {
                    fact.finish(notBefore(loadedAt, fact.getStartedSimTime()),
                            CargoWaitEpisode.Outcome.TRANSPORT_STARTED);
                } else if (item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                    fact.finish(notBefore(tick.tickStart(), fact.getStartedSimTime()),
                            CargoWaitEpisode.Outcome.CANCELLED);
                } else {
                    fact.observeThrough(tick.tickEnd());
                }
                cargoWaitRepository.save(fact);
            }
        }
        return facts;
    }

    private void projectTaskFacts(
            String runId,
            SimulationTick tick,
            LocalDateTime runStart,
            List<Assignment> assignments,
            Map<Long, List<ShipmentItem>> itemsByAssignment,
            Map<Long, LocalDateTime> firstExecutionByAssignment,
            Map<Long, CargoWaitEpisode> cargoFacts
    ) {
        Map<Long, TaskWaitEpisode> facts = uniqueTaskFacts(runId);
        for (Assignment assignment : assignments) {
            if (!isConfirmedTask(assignment)) {
                continue;
            }
            Long assignmentId = assignment.getId();
            List<ShipmentItem> boundItems = itemsByAssignment.getOrDefault(assignmentId, List.of());
            if (boundItems.isEmpty()) {
                // Phase 9A-1：没有需求来源就不能计算响应等待；失败封闭而不是伪造零值。
                throw new IllegalStateException("confirmed assignment " + assignmentId + " has no shipment item");
            }
            LocalDateTime earliestDemand = boundItems.stream()
                    .map(ShipmentItem::getId)
                    .map(cargoFacts::get)
                    .filter(Objects::nonNull)
                    .map(CargoWaitEpisode::getStartedSimTime)
                    .min(Comparator.naturalOrder())
                    .orElseThrow(() -> new IllegalStateException(
                            "confirmed assignment " + assignmentId + " has no cargo wait fact"));
            LocalDateTime firstExecution = firstExecutionByAssignment.get(assignmentId);

            TaskWaitEpisode fact = facts.get(assignmentId);
            if (fact == null) {
                LocalDateTime fallback = firstExecution != null ? firstExecution : tick.tickStart();
                CanonicalStart confirmation = canonicalStart(
                        assignment.getCreatedTime(), runStart, tick.tickEnd(), fallback);
                LocalDateTime confirmedAt = notBefore(confirmation.time(), earliestDemand);
                fact = TaskWaitEpisode.confirm(
                        runId, assignmentId, earliestDemand, confirmedAt,
                        confirmedAt);
                facts.put(assignmentId, fact);
            }

            if (fact.getOutcome() == TaskWaitEpisode.Outcome.WAITING_FOR_EXECUTION) {
                if (firstExecution != null) {
                    fact.markStarted(notBefore(firstExecution, fact.getAssignmentConfirmedSimTime()));
                } else if (assignment.getStatus() == Assignment.AssignmentStatus.CANCELLED) {
                    fact.terminate(notBefore(tick.tickStart(), fact.getAssignmentConfirmedSimTime()),
                            TaskWaitEpisode.Outcome.CANCELLED);
                } else if (assignment.getStatus() == Assignment.AssignmentStatus.FAILED) {
                    fact.terminate(notBefore(tick.tickStart(), fact.getAssignmentConfirmedSimTime()),
                            TaskWaitEpisode.Outcome.FAILED);
                } else if (assignment.getStatus() == Assignment.AssignmentStatus.COMPLETED) {
                    throw new IllegalStateException(
                            "completed assignment " + assignmentId + " has no started assignment leg");
                } else {
                    fact.observeThrough(tick.tickEnd());
                }
                taskWaitRepository.save(fact);
            }
        }
    }

    private void projectVehicleFacts(
            String runId,
            SimulationTick tick,
            LocalDateTime runStart,
            List<Vehicle> vehicles,
            List<Assignment> assignments
    ) {
        List<VehicleWaitEpisode> existing = vehicleWaitRepository.findAllBySimulationRunId(runId);
        Map<Long, VehicleWaitEpisode> openByVehicle = new HashMap<>();
        for (VehicleWaitEpisode fact : existing) {
            if (fact.getStatus() == VehicleWaitEpisode.Status.OPEN
                    && openByVehicle.put(fact.getVehicleId(), fact) != null) {
                throw new IllegalStateException(
                        "vehicle " + fact.getVehicleId() + " has multiple open wait episodes");
            }
        }

        Set<Long> activeVehicleIds = new HashSet<>();
        Map<Long, LocalDateTime> activeVehicleConfirmedAt = new HashMap<>();
        for (Assignment assignment : assignments) {
            if (assignment != null && isRuntimeActive(assignment.getStatus())
                    && assignment.getAssignedVehicle() != null
                    && assignment.getAssignedVehicle().getId() != null) {
                activeVehicleIds.add(assignment.getAssignedVehicle().getId());
                LocalDateTime confirmedAt = canonicalTime(
                        assignment.getCreatedTime(), runStart, tick.tickEnd(), tick.tickStart());
                activeVehicleConfirmedAt.merge(
                        assignment.getAssignedVehicle().getId(), confirmedAt, this::earlier);
            }
        }

        for (Vehicle vehicle : vehicles) {
            if (vehicle == null || vehicle.getId() == null || vehicle.getId() <= 0L) {
                throw new IllegalStateException("vehicle lacks stable positive id");
            }
            boolean active = activeVehicleIds.contains(vehicle.getId());
            boolean availableIdle = vehicle.getCurrentStatus() == Vehicle.VehicleStatus.IDLE && !active;
            VehicleWaitEpisode open = openByVehicle.get(vehicle.getId());
            if (availableIdle) {
                if (open == null) {
                    CanonicalStart start = canonicalStart(
                            vehicle.getStatusStartTime(), runStart, tick.tickEnd(), tick.tickStart());
                    open = VehicleWaitEpisode.open(
                            runId, vehicle.getId(), start.time(),
                            tick.tickEnd().isAfter(start.time()) ? tick.tickEnd() : start.time());
                    openByVehicle.put(vehicle.getId(), open);
                } else {
                    open.observeThrough(tick.tickEnd());
                }
                vehicleWaitRepository.save(open);
            } else if (open != null) {
                // Phase 9A-2 修正：任务占用优先使用分配确认边界；车辆仍短暂 IDLE 时不能回退到旧状态起点。
                LocalDateTime endBoundary = active
                        ? activeVehicleConfirmedAt.getOrDefault(vehicle.getId(), tick.tickStart())
                        : canonicalTime(
                                vehicle.getStatusStartTime(), runStart, tick.tickEnd(), tick.tickStart());
                open.close(notBefore(endBoundary, open.getStartedSimTime()),
                        active
                                ? VehicleWaitEpisode.EndReason.ASSIGNMENT_CONFIRMED
                                : VehicleWaitEpisode.EndReason.LEFT_AVAILABLE_IDLE);
                vehicleWaitRepository.save(open);
                openByVehicle.remove(vehicle.getId());
            }
        }
    }

    private Map<Long, List<ShipmentItem>> indexItemsByAssignment(List<ShipmentItem> items) {
        Map<Long, List<ShipmentItem>> indexed = new HashMap<>();
        for (ShipmentItem item : items) {
            Assignment assignment = item == null ? null : item.getAssignment();
            if (assignment != null && assignment.getId() != null) {
                indexed.computeIfAbsent(assignment.getId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        return indexed;
    }

    private Map<Long, LocalDateTime> firstExecutionByAssignment(
            List<AssignmentLeg> legs,
            LocalDateTime runStart,
            LocalDateTime tickEnd
    ) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (AssignmentLeg leg : legs) {
            if (leg == null || leg.getAssignment() == null || leg.getAssignment().getId() == null
                    || leg.getStartedSimTime() == null) {
                continue;
            }
            LocalDateTime startedAt = requireEventTime(
                    leg.getStartedSimTime(), runStart, tickEnd,
                    "assignment leg " + leg.getId() + " start");
            result.merge(leg.getAssignment().getId(), startedAt, this::earlier);
        }
        return result;
    }

    private Map<Long, LocalDateTime> firstLoadedExecutionByItem(
            List<AssignmentLeg> legs,
            Map<Long, List<ShipmentItem>> itemsByAssignment,
            LocalDateTime runStart,
            LocalDateTime tickEnd
    ) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (AssignmentLeg leg : legs) {
            if (leg == null || leg.getLoadState() != AssignmentLeg.LoadState.LOADED
                    || leg.getStartedSimTime() == null || leg.getAssignment() == null
                    || leg.getAssignment().getId() == null) {
                continue;
            }
            List<Long> carriedIds = new ArrayList<>(leg.getCarriedShipmentItemIdList());
            if (carriedIds.isEmpty()) {
                List<ShipmentItem> bound = itemsByAssignment.getOrDefault(
                        leg.getAssignment().getId(), List.of());
                // Phase 9A-1：普通单货任务的旧路段没有 carried ids 时可无歧义回退；多货任务拒绝猜测。
                if (bound.size() == 1 && bound.get(0).getId() != null) {
                    carriedIds.add(bound.get(0).getId());
                }
            }
            for (Long itemId : carriedIds) {
                if (itemId != null && itemId > 0L) {
                    LocalDateTime startedAt = requireEventTime(
                            leg.getStartedSimTime(), runStart, tickEnd,
                            "loaded assignment leg " + leg.getId() + " start");
                    result.merge(itemId, startedAt, this::earlier);
                }
            }
        }
        return result;
    }

    private Map<Long, CargoWaitEpisode> uniqueCargoFacts(String runId) {
        Map<Long, CargoWaitEpisode> result = new HashMap<>();
        for (CargoWaitEpisode fact : cargoWaitRepository.findAllBySimulationRunId(runId)) {
            if (result.put(fact.getShipmentItemId(), fact) != null) {
                throw new IllegalStateException("duplicate cargo wait fact for item " + fact.getShipmentItemId());
            }
        }
        return result;
    }

    private Map<Long, TaskWaitEpisode> uniqueTaskFacts(String runId) {
        Map<Long, TaskWaitEpisode> result = new HashMap<>();
        for (TaskWaitEpisode fact : taskWaitRepository.findAllBySimulationRunId(runId)) {
            if (result.put(fact.getAssignmentId(), fact) != null) {
                throw new IllegalStateException("duplicate task wait fact for assignment " + fact.getAssignmentId());
            }
        }
        return result;
    }

    private boolean isConfirmedTask(Assignment assignment) {
        return assignment != null && assignment.getId() != null && assignment.getId() > 0L
                && assignment.getAssignedVehicle() != null
                && assignment.getStatus() != null;
    }

    private boolean isRuntimeActive(Assignment.AssignmentStatus status) {
        return status == Assignment.AssignmentStatus.WAITING
                || status == Assignment.AssignmentStatus.ASSIGNED
                || status == Assignment.AssignmentStatus.IN_PROGRESS;
    }

    private CanonicalStart canonicalStart(
            LocalDateTime candidate,
            LocalDateTime runStart,
            LocalDateTime tickEnd,
            LocalDateTime fallback
    ) {
        boolean valid = candidate != null && !candidate.isBefore(runStart) && !candidate.isAfter(tickEnd);
        return new CanonicalStart(
                valid ? candidate : canonicalTime(null, runStart, tickEnd, fallback),
                valid
                        ? CargoWaitEpisode.StartSource.ENTITY_CREATED_TIME
                        : CargoWaitEpisode.StartSource.FIRST_OBSERVED_TICK);
    }

    private LocalDateTime canonicalTime(
            LocalDateTime candidate,
            LocalDateTime runStart,
            LocalDateTime tickEnd,
            LocalDateTime fallback
    ) {
        if (candidate != null && !candidate.isBefore(runStart) && !candidate.isAfter(tickEnd)) {
            return candidate;
        }
        if (fallback == null) {
            return tickEnd;
        }
        if (fallback.isBefore(runStart)) {
            return runStart;
        }
        return fallback.isAfter(tickEnd) ? tickEnd : fallback;
    }

    private LocalDateTime runStartOf(SimulationTick tick) {
        long elapsedSeconds = Math.multiplyExact((long) tick.loopIndex(), tick.availableSeconds());
        return tick.tickStart().minusSeconds(elapsedSeconds);
    }

    private LocalDateTime requireEventTime(
            LocalDateTime eventTime,
            LocalDateTime runStart,
            LocalDateTime tickEnd,
            String field
    ) {
        if (eventTime.isBefore(runStart) || eventTime.isAfter(tickEnd)) {
            throw new IllegalStateException(field + " is outside current simulation run window");
        }
        return eventTime;
    }

    private LocalDateTime notBefore(LocalDateTime value, LocalDateTime lowerBound) {
        return value.isBefore(lowerBound) ? lowerBound : value;
    }

    private LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private record CanonicalStart(LocalDateTime time, CargoWaitEpisode.StartSource source) {
    }
}
