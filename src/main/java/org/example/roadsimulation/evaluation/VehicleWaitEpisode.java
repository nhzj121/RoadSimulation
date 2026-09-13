package org.example.roadsimulation.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Phase 9A-1：车辆处于“可用且无活动任务”状态的一段空闲等待事实。
 *
 * <p>只保存运行与车辆标量标识，不关联 Vehicle 外键。评价事实不得阻止车辆或任务清理。</p>
 */
@Entity
@Table(
        name = "vehicle_wait_episode",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vehicle_wait_run_vehicle_start",
                columnNames = {"simulation_run_id", "vehicle_id", "started_sim_time"}
        ),
        indexes = {
                @Index(name = "idx_vehicle_wait_run", columnList = "simulation_run_id"),
                @Index(name = "idx_vehicle_wait_run_status", columnList = "simulation_run_id,status")
        }
)
public class VehicleWaitEpisode {

    public enum Status { OPEN, CLOSED }
    public enum EndReason { ASSIGNMENT_CONFIRMED, LEFT_AVAILABLE_IDLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_run_id", nullable = false, length = 100)
    private String simulationRunId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "started_sim_time", nullable = false)
    private LocalDateTime startedSimTime;

    @Column(name = "ended_sim_time")
    private LocalDateTime endedSimTime;

    @Column(name = "last_observed_sim_time", nullable = false)
    private LocalDateTime lastObservedSimTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private EndReason endReason;

    @Version
    private Long version;

    protected VehicleWaitEpisode() {
        // Phase 9A-1：JPA 专用；创建必须经过 open(...) 的时间与标识校验。
    }

    public static VehicleWaitEpisode open(
            String simulationRunId,
            Long vehicleId,
            LocalDateTime startedAt,
            LocalDateTime observedThrough
    ) {
        VehicleWaitEpisode episode = new VehicleWaitEpisode();
        episode.simulationRunId = requireText(simulationRunId, "simulationRunId");
        episode.vehicleId = requirePositive(vehicleId, "vehicleId");
        episode.startedSimTime = requireTime(startedAt, "startedAt");
        episode.status = Status.OPEN;
        episode.observeThrough(observedThrough);
        return episode;
    }

    public void observeThrough(LocalDateTime observedThrough) {
        LocalDateTime observed = requireTime(observedThrough, "observedThrough");
        if (observed.isBefore(startedSimTime)) {
            throw new IllegalArgumentException("vehicle wait observation cannot precede start");
        }
        if (lastObservedSimTime == null || observed.isAfter(lastObservedSimTime)) {
            lastObservedSimTime = observed;
        }
    }

    public void close(LocalDateTime endedAt, EndReason reason) {
        LocalDateTime ended = requireTime(endedAt, "endedAt");
        if (ended.isBefore(startedSimTime)) {
            throw new IllegalArgumentException("vehicle wait end cannot precede start");
        }
        if (status == Status.CLOSED) {
            return;
        }
        endedSimTime = ended;
        endReason = java.util.Objects.requireNonNull(reason, "endReason is required");
        status = Status.CLOSED;
        observeThrough(ended);
    }

    public long observedSeconds(LocalDateTime tickEnd) {
        LocalDateTime end = status == Status.CLOSED
                ? requireTime(endedSimTime, "endedSimTime")
                : requireTime(tickEnd, "tickEnd");
        if (end.isBefore(startedSimTime)) {
            throw new IllegalStateException("vehicle wait duration is negative");
        }
        return Duration.between(startedSimTime, end).getSeconds();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static LocalDateTime requireTime(LocalDateTime value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public Long getId() { return id; }
    public String getSimulationRunId() { return simulationRunId; }
    public Long getVehicleId() { return vehicleId; }
    public LocalDateTime getStartedSimTime() { return startedSimTime; }
    public LocalDateTime getEndedSimTime() { return endedSimTime; }
    public LocalDateTime getLastObservedSimTime() { return lastObservedSimTime; }
    public Status getStatus() { return status; }
    public EndReason getEndReason() { return endReason; }
    public Long getVersion() { return version; }
}
