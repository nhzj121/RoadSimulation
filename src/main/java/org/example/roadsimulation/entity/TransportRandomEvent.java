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

    @Column(name = "planned_end_time", nullable = false)
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
