package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** One named input of a processing stage. A Y-shape merge stage has multiple rows. */
@Entity
@Table(
        name = "processing_stage_input",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processing_stage_input",
                columnNames = {"stage_id", "input_key"}
        ),
        indexes = @Index(name = "idx_processing_stage_input_stage", columnList = "stage_id")
)
public class ProcessingStageInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private ProcessingStage stage;

    @Size(max = 50)
    @Column(name = "input_key", length = 50, nullable = false)
    private String inputKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id")
    private Goods goods;

    @Size(max = 100)
    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Positive
    @Column(name = "input_share", nullable = false)
    private Double inputShare;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ProcessingStageInput() {}

    public ProcessingStageInput(ProcessingStage stage, String inputKey, String sku, Double inputShare) {
        this.stage = stage;
        this.inputKey = inputKey;
        this.sku = sku;
        this.inputShare = inputShare;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProcessingStage getStage() { return stage; }
    public void setStage(ProcessingStage stage) { this.stage = stage; }
    public String getInputKey() { return inputKey; }
    public void setInputKey(String inputKey) { this.inputKey = inputKey; }
    public Goods getGoods() { return goods; }
    public void setGoods(Goods goods) { this.goods = goods; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Double getInputShare() { return inputShare; }
    public void setInputShare(Double inputShare) { this.inputShare = inputShare; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
