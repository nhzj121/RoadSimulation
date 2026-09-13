package org.example.roadsimulation.service;

import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.TransportRandomEventRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class TransportRandomEventService {
    @org.springframework.beans.factory.annotation.Autowired
    private DrivingProgressService drivingProgressService;
    @org.springframework.beans.factory.annotation.Autowired
    private WeatherEnvironmentService weatherEnvironmentService;
    @org.springframework.beans.factory.annotation.Autowired
    private BreakdownDecisionPolicy breakdownDecisionPolicy;
    private static final int MIN_MANUAL_DURATION_MINUTES = 30;
    private static final int MAX_MANUAL_DURATION_MINUTES = 240;

    private final TransportRandomEventRepository eventRepository;
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;
    private final RandomEventDecisionPolicy decisionPolicy;
    private final RandomEventProperties properties;

    public TransportRandomEventService(
            TransportRandomEventRepository eventRepository,
            VehicleRepository vehicleRepository,
            AssignmentRepository assignmentRepository,
            RandomEventDecisionPolicy decisionPolicy,
            RandomEventProperties properties
    ) {
        this.eventRepository = eventRepository;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
        this.decisionPolicy = decisionPolicy;
        this.properties = properties;
    }

    @Transactional
    public void tick(LocalDateTime simNow, int minutesPerLoop, int loopCount) {
        Set<Long> resolvedVehicleIds = resolveDueEvents(simNow);
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isAutoEnabled()) {
            return;
        }

        assignmentRepository.findActiveAssignments().stream()
                .filter(this::isEligibleAssignment)
                .sorted(Comparator.comparing(a -> a.getAssignedVehicle().getId()))
                .filter(assignment -> !resolvedVehicleIds.contains(assignment.getAssignedVehicle().getId()))
                .forEach(assignment -> tryAutoTrigger(assignment, simNow, minutesPerLoop, loopCount));
    }

    @Transactional
    public TransportRandomEvent triggerManually(
            TransportRandomEvent.EventType eventType,
            Long vehicleId,
            Integer durationMinutes,
            LocalDateTime simNow
    ) {
        return triggerManually(eventType, vehicleId, durationMinutes, null, null, null, simNow);
    }

    public TransportRandomEvent triggerManually(TransportRandomEvent.EventType eventType, Long vehicleId,
            TransportRandomEvent.BreakdownLevel level, Integer rescueWaitMinutes, Integer repairMinutes, LocalDateTime simNow) {
        return triggerManually(eventType, vehicleId, null, level, rescueWaitMinutes, repairMinutes, simNow);
    }

    @Transactional
    public TransportRandomEvent triggerManually(TransportRandomEvent.EventType eventType, Long vehicleId,
            Integer durationMinutes, TransportRandomEvent.BreakdownLevel level, Integer rescueWaitMinutes,
            Integer repairMinutes, LocalDateTime simNow) {
        if (eventType == null || vehicleId == null || simNow == null) {
            throw new IllegalArgumentException("eventType, vehicleId and simNow are required");
        }
        boolean v2=level!=null||rescueWaitMinutes!=null||repairMinutes!=null;
        if(eventType==TransportRandomEvent.EventType.TRAFFIC_CONGESTION && v2)
            throw new IllegalArgumentException("congestion cannot use breakdown fields");
        if(v2 && eventType!=TransportRandomEvent.EventType.VEHICLE_BREAKDOWN)
            throw new IllegalArgumentException("breakdown fields require VEHICLE_BREAKDOWN");
        if(v2 && durationMinutes!=null) throw new IllegalArgumentException("durationMinutes cannot be combined with breakdown fields");
        if(v2 && level==null) throw new IllegalArgumentException("breakdownLevel is required for breakdown-v2");
        int wait=v2?(rescueWaitMinutes==null?(level==TransportRandomEvent.BreakdownLevel.MINOR?0:30):rescueWaitMinutes):0;
        int repair=v2?(repairMinutes==null?(level==TransportRandomEvent.BreakdownLevel.MINOR?60:90):repairMinutes):0;
        if(v2) validateBreakdown(level,wait,repair);
        int effectiveDuration = v2?wait+repair:(durationMinutes == null ? defaultDuration(eventType) : durationMinutes);
        validateManualDuration(effectiveDuration);
        if (!properties.isEnabled()) {
            throw new IllegalStateException("random events are disabled");
        }
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("vehicle not found: " + vehicleId));
        Assignment assignment = assignmentRepository.findActiveAssignmentByVehicle(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("vehicle has no active assignment: " + vehicleId));
        if (!isDriving(vehicle)) {
            throw new IllegalStateException("vehicle is not in a driving state: " + vehicle.getCurrentStatus());
        }
        if (eventRepository.findFirstByVehicleIdAndStatus(vehicleId, TransportRandomEvent.EventStatus.ACTIVE).isPresent()) {
            throw new IllegalStateException("vehicle already has an active random event: " + vehicleId);
        }
        double speedFactor = eventType == TransportRandomEvent.EventType.TRAFFIC_CONGESTION
                ? properties.getCongestion().getSpeedFactor()
                : 0.0;
        TransportRandomEvent event=createEvent(eventType, vehicle, assignment, effectiveDuration, speedFactor,
                TransportRandomEvent.TriggerSource.MANUAL, simNow, properties.getSeed());
        if (v2) {
            applyBreakdownMetadata(event, level, wait, repair, simNow);
            eventRepository.save(event);
        }
        return event;
    }

    @Transactional(readOnly = true)
    public boolean isTransitionBlocked(Long vehicleId, LocalDateTime simNow) {
        if (vehicleId == null || simNow == null) {
            return false;
        }
        return eventRepository.findFirstByVehicleIdAndStatus(vehicleId, TransportRandomEvent.EventStatus.ACTIVE)
                .filter(event -> drivingProgressService == null || !drivingProgressService.enabled()
                        || event.getEventType() == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN)
                .filter(event -> event.getEventType() == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN
                        || event.getPlannedEndTime().isAfter(simNow))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<TransportRandomEvent> getActiveEvents() {
        return eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<TransportRandomEvent> getHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        return eventRepository.findAllByOrderByStartTimeDesc(PageRequest.of(0, safeLimit));
    }

    @Transactional
    public void deleteAllEvents() {
        eventRepository.deleteAllInBatch();
    }

    private Set<Long> resolveDueEvents(LocalDateTime simNow) {
        Set<Long> resolvedVehicleIds = new HashSet<>();
        for (TransportRandomEvent event : eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)) {
            if (event.getStatus() != TransportRandomEvent.EventStatus.ACTIVE) {
                continue;
            }
            if(event.getBreakdownRuleVersion()!=null && event.getEventType()==TransportRandomEvent.EventType.VEHICLE_BREAKDOWN
                    && !simNow.isBefore(event.getRepairStartTime()) && simNow.isBefore(event.getPlannedEndTime())) {
                event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.REPAIRING);
            }
            if (event.getPlannedEndTime().isAfter(simNow)) {
                event.setDelaySeconds(elapsedSeconds(event.getStartTime(), simNow));
                eventRepository.save(event);
                continue;
            }
            resolveEvent(event, simNow);
            resolvedVehicleIds.add(event.getVehicleId());
        }
        return resolvedVehicleIds;
    }

    private void resolveEvent(TransportRandomEvent event, LocalDateTime simNow) {
        event.setStatus(TransportRandomEvent.EventStatus.RESOLVED);
        event.setResolvedTime(event.getPlannedEndTime());
        event.setDelaySeconds(elapsedSeconds(event.getStartTime(), event.getPlannedEndTime()));
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(event.getVehicleId()).orElse(null);
        if (event.getEventType() == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN && vehicle == null) {
            event.setRecoveryOutcome("VEHICLE_MISSING");
        }
        if (event.getEventType() == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN
                && event.getPreviousVehicleStatus() != null
                && vehicle != null) {
            String guardFailure=recoveryGuardFailure(event,vehicle);
            if(guardFailure==null) vehicle.transitionToStatus(
                    event.getPreviousVehicleStatus(),
                    simNow,
                    Duration.ofSeconds(Math.max(0L, Optional.ofNullable(event.getRemainingStatusSeconds()).orElse(0L)))
            );
            if(guardFailure==null && drivingProgressService != null && drivingProgressService.enabled()) {
                var progress = drivingProgressService.latest(vehicle.getId());
                if (progress != null && java.util.Objects.equals(progress.getAssignmentId(), event.getAssignmentId())) {
                    vehicle.setStatusStartTime(progress.getPhaseStart());
                    vehicle.setStatusDuration(Duration.ofSeconds((long) progress.getInitialWorkSeconds()));
                }
            }
            if(guardFailure==null) vehicleRepository.save(vehicle);
            event.setRecoveryOutcome(guardFailure==null?"RESTORED":guardFailure);
        }
        if(event.getBreakdownRuleVersion()!=null){event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.RECOVERED);event.setRecoveryProcessedTime(simNow);}
        eventRepository.save(event);
    }

    private long elapsedSeconds(LocalDateTime startTime, LocalDateTime simNow) {
        if (startTime == null || simNow == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startTime, simNow).getSeconds());
    }

    private void tryAutoTrigger(Assignment assignment, LocalDateTime simNow, int minutesPerLoop, int loopCount) {
        Long vehicleId = assignment.getAssignedVehicle().getId();
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId).orElse(null);
        if (!isDriving(vehicle)) {
            return;
        }
        if (eventRepository.findFirstByVehicleIdAndStatus(vehicle.getId(), TransportRandomEvent.EventStatus.ACTIVE).isPresent()) {
            return;
        }
        RandomEventProperties.EventSettings congestion = properties.getCongestion();
        RandomEventProperties.EventSettings breakdown = properties.getBreakdown();
        decisionPolicy.decide(
                properties.getSeed(), loopCount, vehicle.getId(), minutesPerLoop,
                congestion.getHourlyProbability(), breakdown.getHourlyProbability(),
                congestion.getMinDurationMinutes(), congestion.getMaxDurationMinutes(),
                breakdown.getMinDurationMinutes(), breakdown.getMaxDurationMinutes(),
                congestion.getSpeedFactor()
        ).ifPresent(decision -> {
            if (decision.eventType() == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN
                    && weatherEnvironmentService != null && weatherEnvironmentService.breakdownPolicy() != null) {
                BreakdownDecisionPolicy.Decision breakdownDecision = breakdownDecisionPolicy.decide(
                        properties.getSeed(), loopCount, vehicle.getId(), weatherEnvironmentService.breakdownPolicy());
                TransportRandomEvent event = createEvent(decision.eventType(), vehicle, assignment,
                        breakdownDecision.rescueWaitMinutes() + breakdownDecision.repairMinutes(), 0,
                        TransportRandomEvent.TriggerSource.AUTO, simNow, properties.getSeed());
                applyBreakdownMetadata(event, breakdownDecision.level(), breakdownDecision.rescueWaitMinutes(),
                        breakdownDecision.repairMinutes(), simNow);
                eventRepository.save(event);
            } else {
                createEvent(decision.eventType(), vehicle, assignment, decision.durationMinutes(), decision.speedFactor(),
                        TransportRandomEvent.TriggerSource.AUTO, simNow, properties.getSeed());
            }
        });
    }

    private TransportRandomEvent createEvent(
            TransportRandomEvent.EventType eventType,
            Vehicle vehicle,
            Assignment assignment,
            int durationMinutes,
            double speedFactor,
            TransportRandomEvent.TriggerSource source,
            LocalDateTime simNow,
            long seed
    ) {
        org.example.roadsimulation.entity.DrivingProgress settledProgress = drivingProgressService == null
                ? null : drivingProgressService.settle(vehicle, simNow);
        if (source == TransportRandomEvent.TriggerSource.MANUAL && weatherEnvironmentService != null)
            weatherEnvironmentService.manualIntervention();
        TransportRandomEvent event = new TransportRandomEvent();
        event.setRunId(weatherEnvironmentService == null ? null : weatherEnvironmentService.runId());
        event.setEventType(eventType);
        event.setStatus(TransportRandomEvent.EventStatus.ACTIVE);
        event.setTriggerSource(source);
        event.setVehicleId(vehicle.getId());
        event.setLicensePlate(vehicle.getLicensePlate());
        event.setAssignmentId(assignment.getId());
        event.setStartTime(simNow);
        event.setPlannedEndTime(simNow.plusMinutes(durationMinutes));
        event.setSpeedFactor(speedFactor);
        event.setRandomSeed(seed);
        event.setDelaySeconds(0L);
        event.setDescription(eventType == TransportRandomEvent.EventType.TRAFFIC_CONGESTION
                ? "交通拥堵，车辆减速行驶"
                : "车辆故障，等待维修恢复");

        if (eventType == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN) {
            event.setPreviousVehicleStatus(vehicle.getCurrentStatus());
            LocalDateTime statusEndTime = vehicle.getStatusEndTime();
            long remainingSeconds = statusEndTime == null
                    ? 0L
                    : Math.max(0L, Duration.between(simNow, statusEndTime).getSeconds());
            event.setRemainingStatusSeconds(remainingSeconds);
            event.setOriginalAssignmentStatus(assignment.getStatus());
            event.setOriginalLegIndex(settledProgress == null ? assignment.getCurrentActionIndex() : settledProgress.getLegIndex());
            event.setOriginalDrivingPhaseKey(settledProgress == null ? null : settledProgress.getPhaseKey());
            vehicle.transitionToStatus(Vehicle.VehicleStatus.BREAKDOWN, simNow, Duration.ofMinutes(durationMinutes));
            vehicleRepository.save(vehicle);
        }
        return eventRepository.save(event);
    }

    private boolean isEligibleAssignment(Assignment assignment) {
        return assignment != null
                && assignment.getAssignedVehicle() != null
                && assignment.getAssignedVehicle().getId() != null
                && isDriving(assignment.getAssignedVehicle());
    }

    private boolean isDriving(Vehicle vehicle) {
        return vehicle != null && (vehicle.getCurrentStatus() == Vehicle.VehicleStatus.ORDER_DRIVING
                || vehicle.getCurrentStatus() == Vehicle.VehicleStatus.TRANSPORT_DRIVING);
    }

    private int defaultDuration(TransportRandomEvent.EventType eventType) {
        RandomEventProperties.EventSettings settings = eventType == TransportRandomEvent.EventType.TRAFFIC_CONGESTION
                ? properties.getCongestion()
                : properties.getBreakdown();
        return settings.getMinDurationMinutes();
    }

    private void validateManualDuration(int durationMinutes) {
        if (durationMinutes < MIN_MANUAL_DURATION_MINUTES || durationMinutes > MAX_MANUAL_DURATION_MINUTES) {
            throw new IllegalArgumentException("durationMinutes must be between 30 and 240");
        }
    }

    private void validateBreakdown(TransportRandomEvent.BreakdownLevel level,int wait,int repair){
        if(repair<30||repair>180||wait<0||wait>180||wait+repair>240||
                (level==TransportRandomEvent.BreakdownLevel.MINOR&&wait!=0)||
                (level==TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED&&wait<30))
            throw new IllegalArgumentException("invalid breakdown phase durations");
    }

    private void applyBreakdownMetadata(TransportRandomEvent event, TransportRandomEvent.BreakdownLevel level,
            int rescueWaitMinutes, int repairMinutes, LocalDateTime simNow) {
        event.setBreakdownLevel(level);
        event.setBreakdownPhase(rescueWaitMinutes == 0
                ? TransportRandomEvent.BreakdownPhase.REPAIRING
                : TransportRandomEvent.BreakdownPhase.WAITING_RESCUE);
        event.setRescueWaitMinutes(rescueWaitMinutes);
        event.setRepairMinutes(repairMinutes);
        event.setRepairStartTime(simNow.plusMinutes(rescueWaitMinutes));
        event.setBreakdownRuleVersion(BreakdownDecisionPolicy.VERSION);
    }
    private String recoveryGuardFailure(TransportRandomEvent event,Vehicle vehicle){
        if(event.getOriginalAssignmentStatus()==null&&event.getOriginalLegIndex()==null)return null;
        if(vehicle.getCurrentStatus()!=Vehicle.VehicleStatus.BREAKDOWN)return "VEHICLE_STATUS_CHANGED";
        Assignment original = assignmentRepository.findById(event.getAssignmentId()).orElse(null);
        if (original == null) return "ASSIGNMENT_CHANGED";
        if (original.getAssignedVehicle() == null
                || !java.util.Objects.equals(original.getAssignedVehicle().getId(), event.getVehicleId())) {
            return "ASSIGNMENT_CHANGED";
        }
        if(original.getStatus()!=event.getOriginalAssignmentStatus())return "ASSIGNMENT_STATUS_CHANGED";
        Assignment current=assignmentRepository.findActiveAssignmentByVehicle(event.getVehicleId()).orElse(null);
        if(current==null||!java.util.Objects.equals(current.getId(),event.getAssignmentId()))return "ASSIGNMENT_CHANGED";
        Integer currentLeg = event.getOriginalDrivingPhaseKey() != null && drivingProgressService != null
                ? drivingProgressService.legIndex(current) : current.getCurrentActionIndex();
        if(!java.util.Objects.equals(currentLeg,event.getOriginalLegIndex()))return "ASSIGNMENT_STAGE_CHANGED";
        if(event.getOriginalDrivingPhaseKey()!=null && drivingProgressService!=null){
            var latest=drivingProgressService.latest(event.getVehicleId());
            if(latest==null||!java.util.Objects.equals(latest.getPhaseKey(),event.getOriginalDrivingPhaseKey()))return "DRIVING_PHASE_CHANGED";
        }
        return null;
    }

    public static class TransitionBlockedException extends IllegalStateException {
        public TransitionBlockedException(Long vehicleId) {
            super("vehicle transition is blocked by an active random event: " + vehicleId);
        }
    }
}
