package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Actual execution record of one ProcessingStage in one ProductionBatch.
 */
@Entity
@Table(
        name = "processing_stage_execution",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processing_stage_execution",
                columnNames = {"batch_id", "stage_order"}
        ),
        indexes = {
                @Index(name = "idx_stage_execution_batch", columnList = "batch_id"),
                @Index(name = "idx_stage_execution_stage", columnList = "stage_id"),
                @Index(name = "idx_stage_execution_status", columnList = "status"),
                @Index(name = "idx_stage_execution_inbound", columnList = "inbound_shipment_id"),
                @Index(name = "idx_stage_execution_outbound", columnList = "outbound_shipment_id")
        }
)
public class ProcessingStageExecution {

    public enum ExecutionStatus {
        WAITING_INPUT, READY_TO_PROCESS, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_node_id", nullable = false)
    private ProductionPlanNode planNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private ProcessingStage stage;

    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processing_poi_id", nullable = false)
    private POI processingPOI;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    private ExecutionStatus status = ExecutionStatus.WAITING_INPUT;

    @Column(name = "actual_input_weight")
    private Double actualInputWeight;

    @Column(name = "actual_output_weight")
    private Double actualOutputWeight;

    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_shipment_id")
    private Shipment inboundShipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_shipment_id")
    private Shipment outboundShipment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ProcessingStageExecution() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProductionBatch getBatch() { return batch; }
    public void setBatch(ProductionBatch batch) { this.batch = batch; }
    public ProductionPlanNode getPlanNode() { return planNode; }
    public void setPlanNode(ProductionPlanNode planNode) { this.planNode = planNode; }
    public ProcessingStage getStage() { return stage; }
    public void setStage(ProcessingStage stage) { this.stage = stage; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public POI getProcessingPOI() { return processingPOI; }
    public void setProcessingPOI(POI processingPOI) { this.processingPOI = processingPOI; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public Double getActualInputWeight() { return actualInputWeight; }
    public void setActualInputWeight(Double actualInputWeight) { this.actualInputWeight = actualInputWeight; }
    public Double getActualOutputWeight() { return actualOutputWeight; }
    public void setActualOutputWeight(Double actualOutputWeight) { this.actualOutputWeight = actualOutputWeight; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Shipment getInboundShipment() { return inboundShipment; }
    public void setInboundShipment(Shipment inboundShipment) { this.inboundShipment = inboundShipment; }
    public Shipment getOutboundShipment() { return outboundShipment; }
    public void setOutboundShipment(Shipment outboundShipment) { this.outboundShipment = outboundShipment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
