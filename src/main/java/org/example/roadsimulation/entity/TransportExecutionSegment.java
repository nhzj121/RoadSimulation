package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Actual-owner facts; AssignmentLeg retains the immutable route/planning owner. */
@Entity
@Table(name="transport_execution_segment", uniqueConstraints=@UniqueConstraint(
        name="uk_execution_segment_tick", columnNames={"leg_id","loop_index","fragment_index"}),
        indexes={@Index(name="idx_execution_segment_run",columnList="run_id"),
                @Index(name="idx_execution_segment_vehicle",columnList="vehicle_id")})
@Getter @Setter
public class TransportExecutionSegment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="run_id",nullable=false,length=36) private String runId;
    @Column(name="assignment_id",nullable=false) private Long assignmentId;
    @Column(name="leg_id",nullable=false) private Long legId;
    @Column(name="loop_index",nullable=false) private Integer loopIndex;
    @Column(name="fragment_index",nullable=false) private Integer fragmentIndex;
    @Column(name="vehicle_id",nullable=false) private Long vehicleId;
    @Column(name="driver_id",nullable=false) private Long driverId;
    @Column(name="from_sim_time",nullable=false) private LocalDateTime fromSimTime;
    @Column(name="to_sim_time",nullable=false) private LocalDateTime toSimTime;
    @Column(name="distance_meters",nullable=false) private Double distanceMeters;
    @Column(name="driving_seconds",nullable=false) private Long drivingSeconds;
    // Missing capacity invalidates evaluation, not authoritative transport progress.
    @Column(name="capacity_tonnes") private Double capacityTonnes;
    @Column(name="load_tonnes",nullable=false) private Double loadTonnes;
    @Enumerated(EnumType.STRING) @Column(name="load_state",nullable=false,length=20)
    private AssignmentLeg.LoadState loadState;
    @Column(name="travel_time_factor",nullable=false) private Double travelTimeFactor;
    @Column(name="energy_liters",nullable=false) private Double energyLiters;
    @Column(name="emission_kg",nullable=false) private Double emissionKg;
    @Column(name="energy_valid",nullable=false) private boolean energyValid;
    @Column(name="emission_model_id",length=80) private String emissionModelId;
    @Column(name="vehicle_class_code",length=40) private String vehicleClassCode;
}
