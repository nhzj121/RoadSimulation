package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Planned material flow into one stage input. External flows have no upstream node. */
@Entity
@Table(
        name = "production_plan_flow",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_production_plan_flow",
                columnNames = {"plan_id", "to_node_id", "input_key"}
        ),
        indexes = {
                @Index(name = "idx_production_plan_flow_plan", columnList = "plan_id"),
                @Index(name = "idx_production_plan_flow_from", columnList = "from_node_id"),
                @Index(name = "idx_production_plan_flow_to", columnList = "to_node_id")
        }
)
public class ProductionPlanFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlan plan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id")
    private ProductionPlanNode fromNode;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_node_id", nullable = false)
    private ProductionPlanNode toNode;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_input_id")
    private ProcessingStageInput stageInput;

    @Size(max = 50)
    @Column(name = "input_key", length = 50, nullable = false)
    private String inputKey;

    @Size(max = 100)
    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "planned_weight", nullable = false)
    private Double plannedWeight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_poi_id")
    private POI sourcePOI;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ProductionPlanFlow() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProductionPlan getPlan() { return plan; }
    public void setPlan(ProductionPlan plan) { this.plan = plan; }
    public ProductionPlanNode getFromNode() { return fromNode; }
    public void setFromNode(ProductionPlanNode fromNode) { this.fromNode = fromNode; }
    public ProductionPlanNode getToNode() { return toNode; }
    public void setToNode(ProductionPlanNode toNode) { this.toNode = toNode; }
    public ProcessingStageInput getStageInput() { return stageInput; }
    public void setStageInput(ProcessingStageInput stageInput) { this.stageInput = stageInput; }
    public String getInputKey() { return inputKey; }
    public void setInputKey(String inputKey) { this.inputKey = inputKey; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Double getPlannedWeight() { return plannedWeight; }
    public void setPlannedWeight(Double plannedWeight) { this.plannedWeight = plannedWeight; }
    public POI getSourcePOI() { return sourcePOI; }
    public void setSourcePOI(POI sourcePOI) { this.sourcePOI = sourcePOI; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
