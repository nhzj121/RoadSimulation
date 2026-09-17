package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Planned input/output quantity for one stage in a ProductionPlan.
 */
@Entity
@Table(
        name = "production_plan_node",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_production_plan_node_stage",
                columnNames = {"plan_id", "stage_order"}
        ),
        indexes = {
                @Index(name = "idx_production_plan_node_plan", columnList = "plan_id"),
                @Index(name = "idx_production_plan_node_stage", columnList = "stage_id")
        }
)
public class ProductionPlanNode {

    public enum NodeStatus {
        PLANNED, READY, WAITING_TRANSPORT, IN_TRANSIT,
        READY_TO_PROCESS, PROCESSING, COMPLETED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private ProcessingStage stage;

    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    @Column(name = "input_sku", length = 100)
    private String inputSku;

    @Column(name = "output_sku", length = 100)
    private String outputSku;

    @Column(name = "planned_input_weight", nullable = false)
    private Double plannedInputWeight;

    @Column(name = "planned_output_weight", nullable = false)
    private Double plannedOutputWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    private NodeStatus status = NodeStatus.PLANNED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ProductionPlanNode() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProductionPlan getPlan() { return plan; }
    public void setPlan(ProductionPlan plan) { this.plan = plan; }
    public ProcessingStage getStage() { return stage; }
    public void setStage(ProcessingStage stage) { this.stage = stage; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public String getInputSku() { return inputSku; }
    public void setInputSku(String inputSku) { this.inputSku = inputSku; }
    public String getOutputSku() { return outputSku; }
    public void setOutputSku(String outputSku) { this.outputSku = outputSku; }
    public Double getPlannedInputWeight() { return plannedInputWeight; }
    public void setPlannedInputWeight(Double plannedInputWeight) { this.plannedInputWeight = plannedInputWeight; }
    public Double getPlannedOutputWeight() { return plannedOutputWeight; }
    public void setPlannedOutputWeight(Double plannedOutputWeight) { this.plannedOutputWeight = plannedOutputWeight; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
