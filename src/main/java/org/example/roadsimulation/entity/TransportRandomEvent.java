package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "transport_random_event",
        indexes = {
                @Index(name = "idx_transport_event_status", columnList = "status"),
                @Index(name = "idx_transport_event_vehicle_status", columnList = "vehicle_id,status"),
                @Index(name = "idx_transport_event_assignment", columnList = "assignment_id")
        }
)
@Getter
@Setter
public class TransportRandomEvent {

    @Column(name = "run_id", length = 36)
    private String runId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    private TriggerSource triggerSource;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "license_plate", length = 50)
    private String licensePlate;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "planned_end_time", columnDefinition = "DATETIME(6)")
    private LocalDateTime plannedEndTime;

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Column(name = "speed_factor")
    private Double speedFactor;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_vehicle_status", length = 30)
    private Vehicle.VehicleStatus previousVehicleStatus;

    @Column(name = "remaining_status_seconds")
    private Long remainingStatusSeconds;

    @Column(name = "delay_seconds")
    private Long delaySeconds;

    @Column(name = "random_seed")
    private Long randomSeed;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING) @Column(name="breakdown_level", length=30)
    private BreakdownLevel breakdownLevel;
    @Enumerated(EnumType.STRING) @Column(name="breakdown_phase", length=30)
    private BreakdownPhase breakdownPhase;
    @Column(name="rescue_wait_minutes") private Integer rescueWaitMinutes;
    @Column(name="repair_minutes") private Integer repairMinutes;
    @Column(name="repair_start_time") private LocalDateTime repairStartTime;
    @Column(name="recovery_processed_time") private LocalDateTime recoveryProcessedTime;
    @Column(name="recovery_outcome", length=80) private String recoveryOutcome;
    @Column(name="breakdown_rule_version", length=40) private String breakdownRuleVersion;
    @Enumerated(EnumType.STRING) @Column(name="original_assignment_status", length=20)
    private Assignment.AssignmentStatus originalAssignmentStatus;
    @Column(name="original_leg_index") private Integer originalLegIndex;
    @Column(name="original_driving_phase_key", length=240) private String originalDrivingPhaseKey;

    @Column(name="replacement_wait_minutes") private Integer replacementWaitMinutes;
    @Column(name="replacement_vehicle_id") private Long replacementVehicleId;
    @Column(name="replacement_license_plate",length=50) private String replacementLicensePlate;
    @Column(name="replacement_selected_time") private LocalDateTime replacementSelectedTime;
    @Column(name="replacement_ready_time") private LocalDateTime replacementReadyTime;
    @Column(name="replacement_processed_time") private LocalDateTime replacementProcessedTime;
    @Column(name="replacement_outcome",length=80) private String replacementOutcome;
    @Column(name="required_load") private Double requiredLoad;
    @Column(name="required_volume") private Double requiredVolume;

    public enum BreakdownLevel { MINOR, ASSISTANCE_REQUIRED, REPLACEMENT_REQUIRED }
    public enum BreakdownPhase { WAITING_RESCUE, REPAIRING, RECOVERED, WAITING_REPLACEMENT, REPLACEMENT_PREPARING, REPLACED }

    public enum EventType {
        TRAFFIC_CONGESTION,
        VEHICLE_BREAKDOWN
    }

    public enum EventStatus {
        ACTIVE,
        RESOLVED
    }

    public enum TriggerSource {
        AUTO,
        MANUAL
    }
}
