package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ProductionPlan records one demand-driven production requirement.
 *
 * <p>The plan is calculated backward from the final product demand. It is an
 * immutable calculation result; runtime progress is tracked by ProductionBatch
 * and ProcessingStageExecution.</p>
 */
@Entity
@Table(
        name = "production_plan",
        uniqueConstraints = @UniqueConstraint(name = "uk_production_plan_no", columnNames = "plan_no"),
        indexes = {
                @Index(name = "idx_production_plan_chain", columnList = "chain_id"),
                @Index(name = "idx_production_plan_status", columnList = "status")
        }
)
public class ProductionPlan {

    public enum PlanStatus {
        DRAFT, CALCULATED, RELEASED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_no", length = 50, nullable = false, unique = true)
    private String planNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id", nullable = false)
    private ProcessingChain chain;

    @Column(name = "final_sku", length = 100)
    private String finalSku;

    @Column(name = "final_demand_weight", nullable = false)
    private Double finalDemandWeight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_poi_id")
    private POI sourcePOI;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "random_seed")
    private Long randomSeed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    private List<ProductionPlanNode> nodes = new ArrayList<>();

    public ProductionPlan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public ProcessingChain getChain() { return chain; }
    public void setChain(ProcessingChain chain) { this.chain = chain; }
    public String getFinalSku() { return finalSku; }
    public void setFinalSku(String finalSku) { this.finalSku = finalSku; }
    public Double getFinalDemandWeight() { return finalDemandWeight; }
    public void setFinalDemandWeight(Double finalDemandWeight) { this.finalDemandWeight = finalDemandWeight; }
    public POI getSourcePOI() { return sourcePOI; }
    public void setSourcePOI(POI sourcePOI) { this.sourcePOI = sourcePOI; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public Long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Long randomSeed) { this.randomSeed = randomSeed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ProductionPlanNode> getNodes() { return nodes; }
    public void setNodes(List<ProductionPlanNode> nodes) { this.nodes = nodes; }

    @PreUpdate
    public void touchUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
