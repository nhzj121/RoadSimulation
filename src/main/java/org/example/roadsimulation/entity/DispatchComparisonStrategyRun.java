package org.example.roadsimulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispatch_comparison_strategy_run")
public class DispatchComparisonStrategyRun {

    public enum StrategyRunStatus {
        RUNNING,
        PAUSED,
        COMPLETED,
        ABORTED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_run_id", nullable = false)
    private DispatchComparisonExperimentRun experimentRun;

    @Column(name = "strategy", nullable = false, length = 20)
    private String strategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StrategyRunStatus status = StrategyRunStatus.RUNNING;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "loop_count")
    private Integer loopCount = 0;

    @Column(name = "completed_items")
    private Integer completedItems = 0;

    @Column(name = "total_items")
    private Integer totalItems = 0;

    @Column(name = "vehicle_used_count")
    private Integer vehicleUsedCount = 0;

    @Column(name = "assignment_count")
    private Integer assignmentCount = 0;

    @Column(name = "cost_a")
    private Double costA;

    @Column(name = "cost_b")
    private Double costB;

    @Column(name = "cost_c")
    private Double costC;

    @Column(name = "cost_d")
    private Double costD;

    @Column(name = "cost_e")
    private Double costE;

    @Column(name = "all_cost")
    private Double allCost;

    @Column(name = "normalized_cost_a")
    private Double normalizedCostA;

    @Column(name = "normalized_cost_b")
    private Double normalizedCostB;

    @Column(name = "normalized_cost_c")
    private Double normalizedCostC;

    @Column(name = "normalized_cost_d")
    private Double normalizedCostD;

    @Column(name = "normalized_cost_e")
    private Double normalizedCostE;

    @Column(name = "normalized_all_cost")
    private Double normalizedAllCost;

    public Long getId() {
        return id;
    }

    public DispatchComparisonExperimentRun getExperimentRun() {
        return experimentRun;
    }

    public void setExperimentRun(DispatchComparisonExperimentRun experimentRun) {
        this.experimentRun = experimentRun;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public StrategyRunStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyRunStatus status) {
        this.status = status;
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

    public Integer getLoopCount() {
        return loopCount;
    }

    public void setLoopCount(Integer loopCount) {
        this.loopCount = loopCount;
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

    public Integer getVehicleUsedCount() {
        return vehicleUsedCount;
    }

    public void setVehicleUsedCount(Integer vehicleUsedCount) {
        this.vehicleUsedCount = vehicleUsedCount;
    }

    public Integer getAssignmentCount() {
        return assignmentCount;
    }

    public void setAssignmentCount(Integer assignmentCount) {
        this.assignmentCount = assignmentCount;
    }

    public Double getCostA() {
        return costA;
    }

    public void setCostA(Double costA) {
        this.costA = costA;
    }

    public Double getCostB() {
        return costB;
    }

    public void setCostB(Double costB) {
        this.costB = costB;
    }

    public Double getCostC() {
        return costC;
    }

    public void setCostC(Double costC) {
        this.costC = costC;
    }

    public Double getCostD() {
        return costD;
    }

    public void setCostD(Double costD) {
        this.costD = costD;
    }

    public Double getCostE() {
        return costE;
    }

    public void setCostE(Double costE) {
        this.costE = costE;
    }

    public Double getAllCost() {
        return allCost;
    }

    public void setAllCost(Double allCost) {
        this.allCost = allCost;
    }

    public Double getNormalizedCostA() {
        return normalizedCostA;
    }

    public void setNormalizedCostA(Double normalizedCostA) {
        this.normalizedCostA = normalizedCostA;
    }

    public Double getNormalizedCostB() {
        return normalizedCostB;
    }

    public void setNormalizedCostB(Double normalizedCostB) {
        this.normalizedCostB = normalizedCostB;
    }

    public Double getNormalizedCostC() {
        return normalizedCostC;
    }

    public void setNormalizedCostC(Double normalizedCostC) {
        this.normalizedCostC = normalizedCostC;
    }

    public Double getNormalizedCostD() {
        return normalizedCostD;
    }

    public void setNormalizedCostD(Double normalizedCostD) {
        this.normalizedCostD = normalizedCostD;
    }

    public Double getNormalizedCostE() {
        return normalizedCostE;
    }

    public void setNormalizedCostE(Double normalizedCostE) {
        this.normalizedCostE = normalizedCostE;
    }

    public Double getNormalizedAllCost() {
        return normalizedAllCost;
    }

    public void setNormalizedAllCost(Double normalizedAllCost) {
        this.normalizedAllCost = normalizedAllCost;
    }
}
