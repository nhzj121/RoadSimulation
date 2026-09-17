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
import java.util.Objects;

/** Phase 9A-1：一件 ShipmentItem 从需求产生到首次有载运输的等待事实。 */
@Entity
@Table(
        name = "cargo_wait_episode",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cargo_wait_run_item",
                columnNames = {"simulation_run_id", "shipment_item_id"}
        ),
        indexes = {
                @Index(name = "idx_cargo_wait_run", columnList = "simulation_run_id"),
                @Index(name = "idx_cargo_wait_run_outcome", columnList = "simulation_run_id,outcome")
        }
)
public class CargoWaitEpisode {

    public enum Outcome { OPEN, TRANSPORT_STARTED, CANCELLED }
    public enum StartSource { ENTITY_CREATED_TIME, FIRST_OBSERVED_TICK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_run_id", nullable = false, length = 100)
    private String simulationRunId;

    @Column(name = "shipment_item_id", nullable = false)
    private Long shipmentItemId;

    @Column(name = "weight_tonnes", nullable = false)
    private Double weightTonnes;

    @Column(name = "started_sim_time", nullable = false)
    private LocalDateTime startedSimTime;

    @Column(name = "ended_sim_time")
    private LocalDateTime endedSimTime;

    @Column(name = "last_observed_sim_time", nullable = false)
    private LocalDateTime lastObservedSimTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 24)
    private Outcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_source", nullable = false, length = 32)
    private StartSource startSource;

    @Version
    private Long version;

    protected CargoWaitEpisode() {
        // Phase 9A-1：JPA 专用；创建必须经过 open(...)。
    }

    public static CargoWaitEpisode open(
            String simulationRunId,
            Long shipmentItemId,
            double weightTonnes,
            LocalDateTime startedAt,
            LocalDateTime observedThrough,
            StartSource startSource
    ) {
        if (!Double.isFinite(weightTonnes) || weightTonnes < 0.0) {
            throw new IllegalArgumentException("weightTonnes must be finite and non-negative");
        }
        CargoWaitEpisode episode = new CargoWaitEpisode();
        episode.simulationRunId = requireText(simulationRunId, "simulationRunId");
        episode.shipmentItemId = requirePositive(shipmentItemId, "shipmentItemId");
        episode.weightTonnes = weightTonnes;
        episode.startedSimTime = requireTime(startedAt, "startedAt");
        episode.startSource = Objects.requireNonNull(startSource, "startSource is required");
        episode.outcome = Outcome.OPEN;
        episode.observeThrough(observedThrough);
        return episode;
    }

    public void observeThrough(LocalDateTime observedThrough) {
        LocalDateTime observed = requireTime(observedThrough, "observedThrough");
        if (observed.isBefore(startedSimTime)) {
            throw new IllegalArgumentException("cargo wait observation cannot precede start");
        }
        if (lastObservedSimTime == null || observed.isAfter(lastObservedSimTime)) {
            lastObservedSimTime = observed;
        }
    }

    public void finish(LocalDateTime endedAt, Outcome terminalOutcome) {
        if (terminalOutcome != Outcome.TRANSPORT_STARTED && terminalOutcome != Outcome.CANCELLED) {
            throw new IllegalArgumentException("cargo wait terminal outcome is required");
        }
        LocalDateTime ended = requireTime(endedAt, "endedAt");
        if (ended.isBefore(startedSimTime)) {
            throw new IllegalArgumentException("cargo wait end cannot precede start");
        }
        if (outcome != Outcome.OPEN) {
            return;
        }
        endedSimTime = ended;
        outcome = terminalOutcome;
        observeThrough(ended);
    }

    public long observedSeconds(LocalDateTime tickEnd) {
        LocalDateTime end = outcome == Outcome.OPEN
                ? requireTime(tickEnd, "tickEnd")
                : requireTime(endedSimTime, "endedSimTime");
        if (end.isBefore(startedSimTime)) {
            throw new IllegalStateException("cargo wait duration is negative");
        }
        return Duration.between(startedSimTime, end).getSeconds();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static LocalDateTime requireTime(LocalDateTime value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public Long getId() { return id; }
    public String getSimulationRunId() { return simulationRunId; }
    public Long getShipmentItemId() { return shipmentItemId; }
    public Double getWeightTonnes() { return weightTonnes; }
    public LocalDateTime getStartedSimTime() { return startedSimTime; }
    public LocalDateTime getEndedSimTime() { return endedSimTime; }
    public LocalDateTime getLastObservedSimTime() { return lastObservedSimTime; }
    public Outcome getOutcome() { return outcome; }
    public StartSource getStartSource() { return startSource; }
    public Long getVersion() { return version; }
}
