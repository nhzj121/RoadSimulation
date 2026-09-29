package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_replacement_attempt", indexes = {
        @Index(name = "idx_replacement_attempt_event", columnList = "event_id"),
        @Index(name = "idx_replacement_attempt_run", columnList = "run_id")
})
@Data
public class VehicleReplacementAttempt {
    public enum AttemptStatus { RESERVED, CANCELLED, COMPLETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="run_id", length=36) private String runId;
    @Column(name="event_id", nullable=false) private Long eventId;
    @Column(name="assignment_id", nullable=false) private Long assignmentId;
    @Column(name="original_vehicle_id", nullable=false) private Long originalVehicleId;
    @Column(name="original_license_plate", length=50) private String originalLicensePlate;
    @Column(name="replacement_vehicle_id", nullable=false) private Long replacementVehicleId;
    @Column(name="original_driver_id") private Long originalDriverId;
    @Column(name="replacement_driver_id") private Long replacementDriverId;
    @Column(name="replacement_license_plate", length=50) private String replacementLicensePlate;
    @Column(name="selected_time", nullable=false) private LocalDateTime selectedTime;
    @Column(name="ready_time", nullable=false) private LocalDateTime readyTime;
    @Column(name="handoff_time") private LocalDateTime handoffTime;
    @Column(name="wait_minutes", nullable=false) private Integer waitMinutes;
    @Column(name="required_load", nullable=false) private Double requiredLoad;
    @Column(name="required_volume", nullable=false) private Double requiredVolume;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AttemptStatus status;
    @Column(length=80) private String outcome;
    @Column(name="rule_version",length=40) private String ruleVersion;
}
