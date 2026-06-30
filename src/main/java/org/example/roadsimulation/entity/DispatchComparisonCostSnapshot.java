package org.example.roadsimulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispatch_comparison_cost_snapshot")
public class DispatchComparisonCostSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_run_id", nullable = false)
    private DispatchComparisonStrategyRun strategyRun;

    @Column(name = "loop_count")
    private Integer loopCount;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "sim_time")
    private LocalDateTime simTime;

    @Column(name = "completed_items")
    private Integer completedItems;

    @Column(name = "total_items")
    private Integer totalItems;

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

    @Column(name = "normalized_all_cost")
    private Double normalizedAllCost;

    public Long getId() {
        return id;
    }

    public DispatchComparisonStrategyRun getStrategyRun() {
        return strategyRun;
    }

    public void setStrategyRun(DispatchComparisonStrategyRun strategyRun) {
        this.strategyRun = strategyRun;
    }

    public Integer getLoopCount() {
        return loopCount;
    }

    public void setLoopCount(Integer loopCount) {
        this.loopCount = loopCount;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public LocalDateTime getSimTime() {
        return simTime;
    }

    public void setSimTime(LocalDateTime simTime) {
        this.simTime = simTime;
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

    public Double getNormalizedAllCost() {
        return normalizedAllCost;
    }

    public void setNormalizedAllCost(Double normalizedAllCost) {
        this.normalizedAllCost = normalizedAllCost;
    }
}
