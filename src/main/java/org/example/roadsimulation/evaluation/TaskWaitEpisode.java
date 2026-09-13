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

/** Phase 9A-1：一个 Assignment 的响应、启动及整体服务等待里程碑。 */
@Entity
@Table(
        name = "task_wait_episode",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_wait_run_assignment",
                columnNames = {"simulation_run_id", "assignment_id"}
        ),
        indexes = {
                @Index(name = "idx_task_wait_run", columnList = "simulation_run_id"),
                @Index(name = "idx_task_wait_run_outcome", columnList = "simulation_run_id,outcome")
        }
)
public class TaskWaitEpisode {

    public enum Outcome { WAITING_FOR_EXECUTION, STARTED, CANCELLED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_run_id", nullable = false, length = 100)
    private String simulationRunId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "earliest_demand_created_sim_time", nullable = false)
    private LocalDateTime earliestDemandCreatedSimTime;

    @Column(name = "assignment_confirmed_sim_time", nullable = false)
    private LocalDateTime assignmentConfirmedSimTime;

    @Column(name = "first_execution_sim_time")
    private LocalDateTime firstExecutionSimTime;

    @Column(name = "terminal_sim_time")
    private LocalDateTime terminalSimTime;

    @Column(name = "last_observed_sim_time", nullable = false)
    private LocalDateTime lastObservedSimTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private Outcome outcome;

    @Version
    private Long version;

    protected TaskWaitEpisode() {
        // Phase 9A-1：JPA 专用；确认事件必须经过 confirm(...)。
    }

    public static TaskWaitEpisode confirm(
            String simulationRunId,
            Long assignmentId,
            LocalDateTime earliestDemandCreatedAt,
            LocalDateTime assignmentConfirmedAt,
            LocalDateTime observedThrough
    ) {
        TaskWaitEpisode episode = new TaskWaitEpisode();
        episode.simulationRunId = requireText(simulationRunId, "simulationRunId");
        episode.assignmentId = requirePositive(assignmentId, "assignmentId");
        episode.earliestDemandCreatedSimTime = requireTime(
                earliestDemandCreatedAt, "earliestDemandCreatedAt");
        episode.assignmentConfirmedSimTime = requireTime(
                assignmentConfirmedAt, "assignmentConfirmedAt");
        if (episode.assignmentConfirmedSimTime.isBefore(episode.earliestDemandCreatedSimTime)) {
            throw new IllegalArgumentException("assignment confirmation cannot precede earliest demand");
        }
        episode.outcome = Outcome.WAITING_FOR_EXECUTION;
        episode.observeThrough(observedThrough);
        return episode;
    }

    public void observeThrough(LocalDateTime observedThrough) {
        LocalDateTime observed = requireTime(observedThrough, "observedThrough");
        if (observed.isBefore(assignmentConfirmedSimTime)) {
            throw new IllegalArgumentException("task wait observation cannot precede confirmation");
        }
        if (lastObservedSimTime == null || observed.isAfter(lastObservedSimTime)) {
            lastObservedSimTime = observed;
        }
    }

    public void markStarted(LocalDateTime firstExecutionAt) {
        LocalDateTime started = requireTime(firstExecutionAt, "firstExecutionAt");
        if (started.isBefore(assignmentConfirmedSimTime)) {
            throw new IllegalArgumentException("task execution cannot precede confirmation");
        }
        if (outcome != Outcome.WAITING_FOR_EXECUTION) {
            return;
        }
        firstExecutionSimTime = started;
        outcome = Outcome.STARTED;
        observeThrough(started);
    }

    public void terminate(LocalDateTime terminatedAt, Outcome terminalOutcome) {
        if (terminalOutcome != Outcome.CANCELLED && terminalOutcome != Outcome.FAILED) {
            throw new IllegalArgumentException("task terminal outcome must be CANCELLED or FAILED");
        }
        LocalDateTime terminal = requireTime(terminatedAt, "terminatedAt");
        if (terminal.isBefore(assignmentConfirmedSimTime)) {
            throw new IllegalArgumentException("task termination cannot precede confirmation");
        }
        if (outcome != Outcome.WAITING_FOR_EXECUTION) {
            return;
        }
        terminalSimTime = terminal;
        outcome = terminalOutcome;
        observeThrough(terminal);
    }

    public long responseSeconds() {
        return Duration.between(earliestDemandCreatedSimTime, assignmentConfirmedSimTime).getSeconds();
    }

    public long startWaitSeconds() {
        if (outcome != Outcome.STARTED || firstExecutionSimTime == null) {
            throw new IllegalStateException("start wait is only complete after first execution");
        }
        return Duration.between(assignmentConfirmedSimTime, firstExecutionSimTime).getSeconds();
    }

    public long observedServiceWaitSeconds(LocalDateTime tickEnd) {
        LocalDateTime end = switch (outcome) {
            case STARTED -> requireTime(firstExecutionSimTime, "firstExecutionSimTime");
            case CANCELLED, FAILED -> requireTime(terminalSimTime, "terminalSimTime");
            case WAITING_FOR_EXECUTION -> requireTime(tickEnd, "tickEnd");
        };
        if (end.isBefore(earliestDemandCreatedSimTime)) {
            throw new IllegalStateException("task service wait duration is negative");
        }
        return Duration.between(earliestDemandCreatedSimTime, end).getSeconds();
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
    public Long getAssignmentId() { return assignmentId; }
    public LocalDateTime getEarliestDemandCreatedSimTime() { return earliestDemandCreatedSimTime; }
    public LocalDateTime getAssignmentConfirmedSimTime() { return assignmentConfirmedSimTime; }
    public LocalDateTime getFirstExecutionSimTime() { return firstExecutionSimTime; }
    public LocalDateTime getTerminalSimTime() { return terminalSimTime; }
    public LocalDateTime getLastObservedSimTime() { return lastObservedSimTime; }
    public Outcome getOutcome() { return outcome; }
    public Long getVersion() { return version; }
}
