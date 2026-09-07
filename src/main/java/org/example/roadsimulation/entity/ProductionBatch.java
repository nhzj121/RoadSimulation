package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime execution of a ProductionPlan.
 */
@Entity
@Table(
        name = "production_batch",
        uniqueConstraints = @UniqueConstraint(name = "uk_production_batch_no", columnNames = "batch_no"),
        indexes = {
                @Index(name = "idx_production_batch_plan", columnList = "plan_id"),
                @Index(name = "idx_production_batch_status", columnList = "status")
        }
)
public class ProductionBatch {

    public enum BatchStatus {
        CREATED, WAITING_MATERIAL, INBOUND_TRANSPORT, PROCESSING,
        INTER_STAGE_TRANSPORT, COMPLETED, FAILED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", length = 50, nullable = false, unique = true)
    private String batchNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id", nullable = false)
    private ProcessingChain chain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BatchStatus status = BatchStatus.CREATED;

    @Column(name = "planned_final_output_weight")
    private Double plannedFinalOutputWeight;

    @Column(name = "actual_final_output_weight")
    private Double actualFinalOutputWeight;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    private List<ProcessingStageExecution> executions = new ArrayList<>();

    public ProductionBatch() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public ProductionPlan getPlan() { return plan; }
    public void setPlan(ProductionPlan plan) { this.plan = plan; }
    public ProcessingChain getChain() { return chain; }
    public void setChain(ProcessingChain chain) { this.chain = chain; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public Double getPlannedFinalOutputWeight() { return plannedFinalOutputWeight; }
    public void setPlannedFinalOutputWeight(Double plannedFinalOutputWeight) { this.plannedFinalOutputWeight = plannedFinalOutputWeight; }
    public Double getActualFinalOutputWeight() { return actualFinalOutputWeight; }
    public void setActualFinalOutputWeight(Double actualFinalOutputWeight) { this.actualFinalOutputWeight = actualFinalOutputWeight; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ProcessingStageExecution> getExecutions() { return executions; }
    public void setExecutions(List<ProcessingStageExecution> executions) { this.executions = executions; }

    @PreUpdate
    public void touchUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
