package org.example.roadsimulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispatch_comparison_experiment_run")
public class DispatchComparisonExperimentRun {

    public enum RunStatus {
        PREPARED,
        RUNNING_ORIGINAL,
        PAUSED_ORIGINAL,
        REBUILDING_FOR_HEURISTIC,
        RUNNING_HEURISTIC,
        PAUSED_HEURISTIC,
        COMPLETED,
        ABORTED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experiment_id", nullable = false, length = 80)
    private String experimentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private RunStatus status = RunStatus.PREPARED;

    @Column(name = "current_strategy", length = 20)
    private String currentStrategy;

    @Column(name = "current_loop")
    private Integer currentLoop = 0;

    @Column(name = "max_loops")
    private Integer maxLoops = 360;

    @Column(name = "shipment_count")
    private Integer shipmentCount = 0;

    @Column(name = "vehicle_count")
    private Integer vehicleCount = 0;

    @Column(name = "completed_items")
    private Integer completedItems = 0;

    @Column(name = "total_items")
    private Integer totalItems = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Lob
    @Column(name = "scenario_json", columnDefinition = "TEXT")
    private String scenarioJson;

    public Long getId() {
        return id;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getCurrentStrategy() {
        return currentStrategy;
    }

    public void setCurrentStrategy(String currentStrategy) {
        this.currentStrategy = currentStrategy;
    }

    public Integer getCurrentLoop() {
        return currentLoop;
    }

    public void setCurrentLoop(Integer currentLoop) {
        this.currentLoop = currentLoop;
    }

    public Integer getMaxLoops() {
        return maxLoops;
    }

    public void setMaxLoops(Integer maxLoops) {
        this.maxLoops = maxLoops;
    }

    public Integer getShipmentCount() {
        return shipmentCount;
    }

    public void setShipmentCount(Integer shipmentCount) {
        this.shipmentCount = shipmentCount;
    }

    public Integer getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(Integer vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public Integer getCompletedItems() {
        return completedItems;
    }

    public void setCompletedItems(Integer completedItems) {
        this.completedItems = completedItems;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getScenarioJson() {
        return scenarioJson;
    }

    public void setScenarioJson(String scenarioJson) {
        this.scenarioJson = scenarioJson;
    }
}
