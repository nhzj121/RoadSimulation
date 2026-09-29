package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class DrivingProgress {
    @Id @Column(length = 240) private String phaseKey;
    private String runId;
    private Long vehicleId;
    private Long assignmentId;
    private Integer legIndex;
    @Enumerated(EnumType.STRING) private Vehicle.VehicleStatus drivingStatus;
    private LocalDateTime phaseStart;
    private LocalDateTime lastSettledTime;
    private double initialWorkSeconds;
    private double remainingWorkSeconds;
    private double affectedSeconds;
    private double lostWorkSeconds;
    private LocalDateTime modelCompletedTime;
    private LocalDateTime observedCompletedTime;
}
