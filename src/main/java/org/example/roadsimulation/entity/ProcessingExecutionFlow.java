package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Runtime material flow corresponding to one ProductionPlanFlow. */
@Entity
@Table(
        name = "processing_execution_flow",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processing_execution_flow",
                columnNames = {"batch_id", "to_execution_id", "input_key"}
        ),
        indexes = {
                @Index(name = "idx_processing_execution_flow_batch", columnList = "batch_id"),
                @Index(name = "idx_processing_execution_flow_from", columnList = "from_execution_id"),
                @Index(name = "idx_processing_execution_flow_to", columnList = "to_execution_id"),
                @Index(name = "idx_processing_execution_flow_shipment", columnList = "shipment_id")
        }
)
public class ProcessingExecutionFlow {

    public enum FlowStatus {
        PLANNED, WAITING_TRANSPORT, IN_TRANSIT, DELIVERED, FAILED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_flow_id", nullable = false)
    private ProductionPlanFlow planFlow;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_execution_id")
    private ProcessingStageExecution fromExecution;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_execution_id", nullable = false)
    private ProcessingStageExecution toExecution;

    @Size(max = 50)
    @Column(name = "input_key", length = 50, nullable = false)
    private String inputKey;

    @Size(max = 100)
    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "planned_weight", nullable = false)
    private Double plannedWeight;

    @Column(name = "actual_weight")
    private Double actualWeight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private FlowStatus status = FlowStatus.PLANNED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ProcessingExecutionFlow() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProductionBatch getBatch() { return batch; }
    public void setBatch(ProductionBatch batch) { this.batch = batch; }
    public ProductionPlanFlow getPlanFlow() { return planFlow; }
    public void setPlanFlow(ProductionPlanFlow planFlow) { this.planFlow = planFlow; }
    public ProcessingStageExecution getFromExecution() { return fromExecution; }
    public void setFromExecution(ProcessingStageExecution fromExecution) { this.fromExecution = fromExecution; }
    public ProcessingStageExecution getToExecution() { return toExecution; }
    public void setToExecution(ProcessingStageExecution toExecution) { this.toExecution = toExecution; }
    public String getInputKey() { return inputKey; }
    public void setInputKey(String inputKey) { this.inputKey = inputKey; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Double getPlannedWeight() { return plannedWeight; }
    public void setPlannedWeight(Double plannedWeight) { this.plannedWeight = plannedWeight; }
    public Double getActualWeight() { return actualWeight; }
    public void setActualWeight(Double actualWeight) { this.actualWeight = actualWeight; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }
    public FlowStatus getStatus() { return status; }
    public void setStatus(FlowStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
