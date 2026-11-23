package org.example.roadsimulation.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.core.SimulationModeGuard;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.VehicleReplacementAttempt;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.TransportRandomEventRepository;
import org.example.roadsimulation.repository.VehicleReplacementAttemptRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.example.roadsimulation.service.ManualReplacementStore.Phase.*;

@Service
@Profile("!sandbox-runtime")
@Transactional
public class ManualReplacementService {
    static final String RULE = "manual-map-handoff/v1";
    private static final List<Assignment.AssignmentStatus> OPEN = List.of(
            Assignment.AssignmentStatus.WAITING, Assignment.AssignmentStatus.ASSIGNED,
            Assignment.AssignmentStatus.IN_PROGRESS);

    public record Start(String runId, String requestId, long vehicleId, long assignmentId,
                        Double longitude, Double latitude, Integer nextStopIndex) {}
    public record Operation(String runId, Long attemptId) {}
    public record Approach(double startLongitude, double startLatitude, double faultLongitude, double faultLatitude) {}
    public record Snapshot(long eventId, String runId, String requestId, long originalVehicleId, String originalPlate,
                           long assignmentId, double longitude, double latitude, int nextStopIndex,
                           ManualReplacementStore.Phase phase, Long attemptId, Long spareVehicleId, String sparePlate,
                           Double spareLongitude, Double spareLatitude, boolean restored, String reason) {}
    public record Monitor(String runId, boolean available, List<Snapshot> replacements) {}
    private record Position(double longitude, double latitude) {}
    private record Candidate(Vehicle vehicle, Position position, double distance) {}

    private final ManualReplacementStore store;
    private final SimulationContext clock;
    private final SimulationModeGuard mode;
    private final VehicleRepository vehicles;
    private final AssignmentRepository assignments;
    private final TransportRandomEventRepository events;
    private final VehicleReplacementAttemptRepository attempts;
    private final DriverResourceService drivers;
    private final WeatherEnvironmentService weather;
    private final DrivingProgressService driving;
    private final RandomEventProperties properties;
    private final EntityManager entityManager;

    public ManualReplacementService(ManualReplacementStore store, SimulationContext clock, SimulationModeGuard mode,
            VehicleRepository vehicles, AssignmentRepository assignments, TransportRandomEventRepository events,
            VehicleReplacementAttemptRepository attempts, DriverResourceService drivers, WeatherEnvironmentService weather,
            DrivingProgressService driving, RandomEventProperties properties, EntityManager entityManager) {
        this.store = store;
        this.clock = clock;
        this.mode = mode;
        this.vehicles = vehicles;
        this.assignments = assignments;
        this.events = events;
        this.attempts = attempts;
        this.drivers = drivers;
        this.weather = weather;
        this.driving = driving;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Monitor monitor() {
        String run = clock.getSimulationRunId().orElse(null);
        boolean available = ordinary() && eventsEnabled() && store.installed();
        var replacements = ordinary() && run != null && store.installed()
                ? store.open(run).stream().map(state -> snapshot(state, event(state.eventId()))).toList()
                : List.<Snapshot>of();
        return new Monitor(ordinary() ? run : null, available, replacements);
    }

    @Transactional(readOnly = true)
    public Approach approach(long id, Operation input) {
        var state = store.find(requireRun(input.runId()), id).orElseThrow(() -> notFound("接替记录不存在"));
        if (input.attemptId() == null || state.phase() != EN_ROUTE || !input.attemptId().equals(state.attemptId())
                || state.spareLongitude() == null || state.spareLatitude() == null) {
            conflict("接替车辆已变化，请刷新接替状态");
        }
        return new Approach(state.spareLongitude(), state.spareLatitude(), state.longitude(), state.latitude());
    }

    public Snapshot start(Start input) {
        String run = requireRun(input.runId());
        validate(input);
        var existing = store.request(run, input.requestId());
        if (existing.isPresent()) {
            var state = existing.get();
            if (state.originalVehicleId() != input.vehicleId() || state.assignmentId() != input.assignmentId()
                    || state.nextStopIndex() != input.nextStopIndex()
                    || state.longitude() != input.longitude() || state.latitude() != input.latitude()) {
                conflict("接替请求与已保存的数据不一致");
            }
            return snapshot(state, event(state.eventId()));
        }
        if (!clock.isRunning() || !eventsEnabled()) conflict("请在启用运输事件的普通仿真中触发换车");
        Vehicle original = vehicles.findByIdForUpdate(input.vehicleId()).orElseThrow(() -> notFound("原车不存在"));
        if (store.heldVehicleIds().contains(input.vehicleId())) conflict("该车已在故障车辆列表中");
        if (original.getCurrentStatus() == Vehicle.VehicleStatus.RESERVED_REPLACEMENT
                || original.getCurrentStatus() == Vehicle.VehicleStatus.SCRAPPED
                || events.findFirstByVehicleIdAndStatus(original.getId(), TransportRandomEvent.EventStatus.ACTIVE).isPresent()) {
            conflict("该车已经预留、报废或存在活动事件");
        }
        Assignment task = lockedAssignment(input.assignmentId());
        if (!ownedBy(task, original.getId()) || (task.getStatus() != Assignment.AssignmentStatus.COMPLETED
                && !executing(task))) {
            conflict("原任务已换车或终止");
        }
        if (assignments.findByAssignedVehicleAndStatusIn(original, OPEN).stream()
                .anyMatch(other -> !Objects.equals(other.getId(), task.getId()))) conflict("原车已领取其他任务");
        int stops = task.getNodes().isEmpty() ? 2 : task.getNodes().size();
        if (input.nextStopIndex() >= stops) conflict("画面装卸进度已过期");

        LocalDateTime now = clock.getCurrentSimTime();
        TransportRandomEvent event = new TransportRandomEvent();
        event.setRunId(weather.runId());
        event.setVehicleId(original.getId());
        event.setLicensePlate(original.getLicensePlate());
        event.setAssignmentId(task.getId());
        event.setEventType(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN);
        event.setTriggerSource(TransportRandomEvent.TriggerSource.MANUAL);
        event.setStatus(TransportRandomEvent.EventStatus.ACTIVE);
        event.setBreakdownLevel(TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED);
        event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.WAITING_REPLACEMENT);
        event.setBreakdownRuleVersion(RULE);
        event.setStartTime(now);
        event.setPlannedEndTime(now);
        event.setSpeedFactor(1.0);
        event.setDelaySeconds(0L);
        event.setDescription("手动故障换车，等待接替车辆");
        events.saveAndFlush(event);
        var state = new ManualReplacementStore.State(event.getId(), run, input.requestId(), original.getId(),
                task.getId(), input.longitude(), input.latitude(), input.nextStopIndex(), WAITING,
                null, null, null, false, null);
        store.insert(state);
        weather.manualIntervention();
        return snapshot(reserve(state, event, original, task, now), event);
    }

    public void retryWaiting() {
        String run = clock.getSimulationRunId().orElse(null);
        if (!ordinary() || run == null || !store.installed()) return;
        LocalDateTime now = clock.getCurrentSimTime();
        for (var waiting : store.waiting(run)) {
            var original = vehicles.findByIdForUpdate(waiting.originalVehicleId()).orElse(null);
            var state = store.lock(run, waiting.eventId()).orElse(null);
            if (state == null || state.phase() != WAITING) continue;
            var event = event(state.eventId());
            var task = lockedAssignment(state.assignmentId());
            if (original == null || !ownedBy(task, state.originalVehicleId())
                    || (!executing(task) && task.getStatus() != Assignment.AssignmentStatus.COMPLETED)) {
                terminate(state, event, "ASSIGNMENT_CHANGED", now);
            } else {
                reserve(state, event, original, task, now);
            }
        }
    }

    public Snapshot arrived(long id, Operation input) {
        var state = state(id, input);
        var event = event(id);
        if (state.phase() != EN_ROUTE || !Objects.equals(state.attemptId(), input.attemptId())) return snapshot(state, event);
        LocalDateTime now = clock.getCurrentSimTime();
        var original = vehicles.findByIdForUpdate(state.originalVehicleId()).orElse(null);
        var spare = vehicles.findByIdForUpdate(event.getReplacementVehicleId()).orElse(null);
        var task = lockedAssignment(state.assignmentId());
        if (original == null || !ownedBy(task, state.originalVehicleId())) {
            return snapshot(terminate(state, event, "ASSIGNMENT_CHANGED", now), event);
        }
        if (!executing(task) && task.getStatus() != Assignment.AssignmentStatus.COMPLETED) {
            return snapshot(terminate(state, event, "ASSIGNMENT_TERMINATED", now), event);
        }
        if (!reservationValid(event, spare) || !fits(spare, required(state, original, task))) {
            release(state, event, spare, "SPARE_UNAVAILABLE", now);
            event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.WAITING_REPLACEMENT);
            event.setDescription("接替车不可用，等待重新选车");
            event.setReplacementVehicleId(null);
            event.setReplacementDriverId(null);
            event.setReplacementLicensePlate(null);
            event.setReplacementSelectedTime(null);
            event.setReplacementReadyTime(null);
            return snapshot(store.update(state.with(WAITING, null, null, null, "SPARE_UNAVAILABLE")), event);
        }
        if (task.getStatus() == Assignment.AssignmentStatus.COMPLETED) {
            resolve(event, "VISUAL_ONLY", now);
            return snapshot(store.update(state.with(VISUAL_ONLY, state.attemptId(),
                    state.spareLongitude(), state.spareLatitude(), null)), event);
        }

        drivers.requireExecutionDriver(task);
        Long oldDriver = task.getAssignedDriver().getId();
        var status = original.getCurrentStatus();
        LocalDateTime statusEnd = original.getStatusEndTime();
        Duration remaining = statusEnd == null || statusEnd.isBefore(now) ? Duration.ZERO : Duration.between(now, statusEnd);
        var progress = weather.eventConfiguration() == null && driving.enabled() ? driving.settle(original, now) : null;
        drivers.handoff(task, original, spare, oldDriver, event.getReplacementDriverId(), id, now);
        original.removeAssignment(task);
        spare.addAssignment(task);
        spare.setCurrentPOI(original.getCurrentPOI());
        spare.setCurrentLatitude(original.getCurrentLatitude());
        spare.setCurrentLongitude(original.getCurrentLongitude());
        spare.setCurrentLoadTonnes(original.getCurrentLoad());
        spare.setCurrentVolumn(original.getCurrentVolumn());
        original.setCurrentLoadTonnes(0.0);
        original.setCurrentVolumn(0.0);
        original.resetToIdle(now);
        spare.activateReplacement(status, now, remaining);
        assignments.save(task);
        vehicles.save(original);
        vehicles.save(spare);
        if (progress != null) driving.transferAndSettle(original.getId(), spare, task, progress.getPhaseKey(), now, now);
        event.setOriginalDriverId(oldDriver);
        event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.REPLACED);
        resolve(event, "REPLACED", now);
        finishAttempt(state, VehicleReplacementAttempt.AttemptStatus.COMPLETED, "REPLACED", now);
        return snapshot(store.update(state.with(HANDED_OFF, state.attemptId(),
                state.spareLongitude(), state.spareLatitude(), null)), event);
    }

    public Snapshot finished(long id, Operation input) {
        var state = state(id, input);
        var event = event(id);
        if (!Objects.equals(state.attemptId(), input.attemptId())) return snapshot(state, event);
        if (state.phase() == VISUAL_ONLY) {
            LocalDateTime now = clock.getCurrentSimTime();
            release(state, event, vehicles.findByIdForUpdate(event.getReplacementVehicleId()).orElse(null), "VISUAL_ONLY", now);
        } else if (state.phase() != HANDED_OFF) {
            return snapshot(state, event);
        }
        return snapshot(store.update(state.with(FINISHED, state.attemptId(), state.spareLongitude(), state.spareLatitude(), null)), event);
    }

    public Snapshot cancel(long id, Operation input) {
        var state = state(id, input);
        var event = event(id);
        if (state.phase() == FINISHED || state.phase() == TERMINATED) return snapshot(state, event);
        if (!Objects.equals(state.attemptId(), input.attemptId())) return snapshot(state, event);
        if (state.phase() == HANDED_OFF) return finished(id, input);
        return snapshot(terminate(state, event, "USER_CANCELLED", clock.getCurrentSimTime()), event);
    }

    public Snapshot restore(long id, Operation input) {
        var state = state(id, input);
        var event = event(id);
        if (state.phase() != HANDED_OFF && state.phase() != FINISHED && state.phase() != TERMINATED)
            conflict("请先完成或终止本次接替");
        // Serialize recovery with the final dispatch claim on the same vehicle.
        vehicles.findByIdForUpdate(state.originalVehicleId());
        store.restore(id);
        return snapshot(store.lock(state.runId(), id).orElseThrow(), event);
    }

    private ManualReplacementStore.State reserve(ManualReplacementStore.State state, TransportRandomEvent event,
                                                   Vehicle original, Assignment task, LocalDateTime now) {
        var requirement = required(state, original, task);
        var held = store.heldVehicleIds();
        var candidates = vehicles.findByCurrentStatus(Vehicle.VehicleStatus.IDLE).stream()
                .filter(vehicle -> !held.contains(vehicle.getId()) && fits(vehicle, requirement))
                .map(vehicle -> {
                    var position = position(vehicle);
                    return position == null ? null : new Candidate(vehicle, position, distance(state, position));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Candidate::distance)
                        .thenComparingDouble(candidate -> capacityDifference(candidate.vehicle().getMaxLoadCapacity(), original.getMaxLoadCapacity()))
                        .thenComparingDouble(candidate -> capacityDifference(candidate.vehicle().getCargoVolume(), original.getCargoVolume()))
                        .thenComparing(candidate -> candidate.vehicle().getId())).toList();
        for (var candidate : candidates) {
            long id = candidate.vehicle().getId();
            if (vehicles.reserveReplacementIfIdle(id, event.getId(), now) != 1) continue;
            Vehicle spare = vehicles.findByIdForUpdate(id).orElseThrow();
            entityManager.refresh(spare, LockModeType.PESSIMISTIC_WRITE);
            if (!fits(spare, requirement) || position(spare) == null
                    || !assignments.findByAssignedVehicleAndStatusIn(spare, OPEN).isEmpty()
                    || events.findFirstByVehicleIdAndStatus(id, TransportRandomEvent.EventStatus.ACTIVE).isPresent()) {
                vehicles.releaseReplacementIfOwned(id, event.getId(), now);
                continue;
            }
            var driver = drivers.reserveReplacement(spare, task, event.getId(), now);
            if (driver.isEmpty()) {
                vehicles.releaseReplacementIfOwned(id, event.getId(), now);
                continue;
            }

            event.setReplacementVehicleId(id);
            event.setReplacementDriverId(driver.get().getId());
            event.setReplacementLicensePlate(spare.getLicensePlate());
            event.setReplacementSelectedTime(now);
            event.setRequiredLoad(requirement.load());
            event.setRequiredVolume(requirement.volume());
            event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.REPLACEMENT_PREPARING);
            event.setDescription("手动故障换车，接替车辆赶赴中");
            var attempt = new VehicleReplacementAttempt();
            attempt.setRunId(event.getRunId());
            attempt.setEventId(event.getId());
            attempt.setAssignmentId(task.getId());
            attempt.setOriginalVehicleId(original.getId());
            attempt.setOriginalLicensePlate(original.getLicensePlate());
            attempt.setOriginalDriverId(task.getAssignedDriver() == null ? null : task.getAssignedDriver().getId());
            attempt.setReplacementVehicleId(id);
            attempt.setReplacementDriverId(driver.get().getId());
            attempt.setReplacementLicensePlate(spare.getLicensePlate());
            attempt.setSelectedTime(now);
            attempt.setReadyTime(now);
            attempt.setWaitMinutes(0);
            attempt.setRequiredLoad(requirement.load());
            attempt.setRequiredVolume(requirement.volume());
            attempt.setStatus(VehicleReplacementAttempt.AttemptStatus.RESERVED);
            attempt.setRuleVersion(RULE);
            attempts.saveAndFlush(attempt);
            var registeredPosition = position(spare);
            return store.update(state.with(EN_ROUTE, attempt.getId(), registeredPosition.longitude(), registeredPosition.latitude(), null));
        }
        return state;
    }

    static TransportRandomEventService.RequiredCapacity required(ManualReplacementStore.State state, Vehicle vehicle, Assignment task) {
        double load = 0, volume = 0, peakLoad = 0, peakVolume = 0;
        var nodes = task.getNodes().stream().sorted(Comparator.comparing(AssignmentNode::getSequenceIndex)).toList();
        if (nodes.isEmpty()) {
            load = task.getShipmentItems().stream().mapToDouble(item -> amount(item.getWeight())).sum();
            volume = task.getShipmentItems().stream().mapToDouble(item -> amount(item.getVolume())).sum();
            peakLoad = load;
            peakVolume = volume;
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                if (i >= state.nextStopIndex()) {
                    peakLoad = Math.max(peakLoad, load);
                    peakVolume = Math.max(peakVolume, volume);
                }
                load = Math.max(0, load + amount(nodes.get(i).getWeightDelta()));
                volume = Math.max(0, volume + amount(nodes.get(i).getVolumeDelta()));
                if (i >= state.nextStopIndex()) {
                    peakLoad = Math.max(peakLoad, load);
                    peakVolume = Math.max(peakVolume, volume);
                }
            }
        }
        if (executing(task)) {
            var actual = TransportRandomEventService.requiredCapacity(vehicle, task);
            peakLoad = Math.max(peakLoad, actual.load());
            peakVolume = Math.max(peakVolume, actual.volume());
        }
        return new TransportRandomEventService.RequiredCapacity(peakLoad, peakVolume);
    }

    private static double amount(Double value) {
        return value == null ? 0 : value;
    }

    private boolean reservationValid(TransportRandomEvent event, Vehicle spare) {
        return spare != null && spare.getCurrentStatus() == Vehicle.VehicleStatus.RESERVED_REPLACEMENT
                && Objects.equals(spare.getReplacementReservationEventId(), event.getId())
                && assignments.findByAssignedVehicleAndStatusIn(spare, OPEN).isEmpty()
                && drivers.reservationValid(event.getReplacementDriverId(), event.getId(), spare);
    }

    private static boolean fits(Vehicle vehicle, TransportRandomEventService.RequiredCapacity required) {
        return vehicle != null && vehicle.getMaxLoadCapacity() != null && vehicle.getCargoVolume() != null
                && vehicle.getMaxLoadCapacity() >= required.load() && vehicle.getCargoVolume() >= required.volume();
    }

    private static double capacityDifference(Double capacity, Double original) {
        return original == null ? 0 : Math.abs(capacity - original);
    }

    private static Position position(Vehicle vehicle) {
        if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
            double longitude = vehicle.getCurrentLongitude().doubleValue(), latitude = vehicle.getCurrentLatitude().doubleValue();
            if (validPosition(longitude, latitude)) return new Position(longitude, latitude);
        }
        var poi = vehicle.getCurrentPOI();
        if (poi == null || poi.getLongitude() == null || poi.getLatitude() == null) return null;
        double longitude = poi.getLongitude().doubleValue(), latitude = poi.getLatitude().doubleValue();
        return validPosition(longitude, latitude) ? new Position(longitude, latitude) : null;
    }

    private static double distance(ManualReplacementStore.State state, Position position) {
        double lat1 = Math.toRadians(state.latitude()), lat2 = Math.toRadians(position.latitude());
        double lat = Math.sin((lat2 - lat1) / 2);
        double lon = Math.sin(Math.toRadians(position.longitude() - state.longitude()) / 2);
        return lat * lat + Math.cos(lat1) * Math.cos(lat2) * lon * lon;
    }

    private ManualReplacementStore.State terminate(ManualReplacementStore.State state, TransportRandomEvent event,
                                                     String reason, LocalDateTime now) {
        if (event.getReplacementVehicleId() != null) {
            release(state, event, vehicles.findByIdForUpdate(event.getReplacementVehicleId()).orElse(null), reason, now);
        }
        resolve(event, reason, now);
        return store.update(state.with(TERMINATED, state.attemptId(), state.spareLongitude(), state.spareLatitude(), reason));
    }

    private void release(ManualReplacementStore.State state, TransportRandomEvent event, Vehicle spare,
                         String reason, LocalDateTime now) {
        drivers.cancelReservation(event.getReplacementDriverId(), event.getId(), now);
        if (spare != null) {
            int released = vehicles.releaseReplacementIfOwned(spare.getId(), event.getId(), now);
            if (released == 1) entityManager.refresh(spare, LockModeType.PESSIMISTIC_WRITE);
        }
        finishAttempt(state, VehicleReplacementAttempt.AttemptStatus.CANCELLED, reason, null);
    }

    private void finishAttempt(ManualReplacementStore.State state, VehicleReplacementAttempt.AttemptStatus status,
                               String outcome, LocalDateTime at) {
        if (state.attemptId() == null) return;
        attempts.findById(state.attemptId()).ifPresent(attempt -> {
            attempt.setStatus(status);
            attempt.setOutcome(outcome);
            if (at != null) attempt.setReadyTime(at);
            attempt.setHandoffTime(at);
        });
    }

    private void resolve(TransportRandomEvent event, String outcome, LocalDateTime now) {
        event.setStatus(TransportRandomEvent.EventStatus.RESOLVED);
        event.setReplacementOutcome(outcome);
        event.setResolvedTime(now);
        event.setReplacementProcessedTime(now);
        event.setDescription(switch (outcome) {
            case "REPLACED" -> "手动换车续运";
            case "VISUAL_ONLY" -> "原任务已完成，继续播放接替动画";
            default -> "手动换车已终止";
        });
    }

    private ManualReplacementStore.State state(long id, Operation input) {
        String run = requireRun(input.runId());
        var observed = store.find(run, id).orElseThrow(() -> notFound("接替记录不存在"));
        vehicles.findByIdForUpdate(observed.originalVehicleId());
        return store.lock(run, id).orElseThrow(() -> notFound("接替记录不存在"));
    }

    private String requireRun(String expected) {
        if (!ordinary()) conflict("手动接替仅支持普通仿真");
        String run = clock.getSimulationRunId().orElse(null);
        if (run == null || !run.equals(expected)) conflict("仿真已重置或运行已变化");
        if (!store.installed()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "普通库尚未执行手动换车迁移");
        return run;
    }

    private boolean ordinary() {
        return !clock.isDeterministicSandboxRun() && !mode.isDispatchComparisonExperimentActive();
    }

    private boolean eventsEnabled() {
        var configuration = weather.eventConfiguration();
        return configuration == null ? properties.isEnabled() : configuration.enabled();
    }

    private static void validate(Start input) {
        try {
            if (!UUID.fromString(input.requestId()).toString().equals(input.requestId()))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求 ID 必须为 UUID");
        }
        if (input.vehicleId() <= 0 || input.assignmentId() <= 0 || input.nextStopIndex() == null || input.nextStopIndex() < 0
                || input.longitude() == null || input.latitude() == null
                || !validPosition(input.longitude(), input.latitude())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "车辆、任务、装卸进度或坐标无效");
        }
    }

    private static boolean validPosition(double longitude, double latitude) {
        return longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90;
    }

    private Assignment lockedAssignment(long id) {
        return entityManager.find(Assignment.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    private TransportRandomEvent event(long id) {
        return events.findById(id).orElseThrow(() -> notFound("运输事件不存在"));
    }

    private static boolean ownedBy(Assignment task, long vehicle) {
        return task != null && task.getAssignedVehicle() != null && Objects.equals(task.getAssignedVehicle().getId(), vehicle);
    }

    private static boolean executing(Assignment task) {
        return task.getStatus() == Assignment.AssignmentStatus.ASSIGNED || task.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS;
    }

    private static Snapshot snapshot(ManualReplacementStore.State state, TransportRandomEvent event) {
        return new Snapshot(state.eventId(), state.runId(), state.requestId(), state.originalVehicleId(), event.getLicensePlate(),
                state.assignmentId(), state.longitude(), state.latitude(), state.nextStopIndex(), state.phase(), state.attemptId(),
                event.getReplacementVehicleId(), event.getReplacementLicensePlate(), state.spareLongitude(),
                state.spareLatitude(), state.restored(), state.reason());
    }

    private static void conflict(String reason) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
