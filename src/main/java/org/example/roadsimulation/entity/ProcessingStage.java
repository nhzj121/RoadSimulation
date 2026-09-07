package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Static definition of one operation in a processing chain.
 * Actual execution is represented by ProcessingStageExecution.
 */
@Entity
@Table(name = "processing_stage")
public class ProcessingStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id", nullable = false)
    private ProcessingChain processingChain;

    @NotNull
    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    @NotBlank
    @Size(max = 100)
    @Column(name = "stage_name", length = 100, nullable = false)
    private String stageName;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poi_id", nullable = false)
    private POI processingPOI;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_goods_id")
    private Goods inputGoods;

    @Size(max = 50)
    @Column(name = "input_goods_sku", length = 50)
    private String inputGoodsSku;

    @Positive
    @Column(name = "input_weight_ratio")
    private Double inputWeightRatio = 1.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_goods_id")
    private Goods outputGoods;

    @Size(max = 50)
    @Column(name = "output_goods_sku", length = 50)
    private String outputGoodsSku;

    @Positive
    @Column(name = "output_weight_ratio")
    private Double outputWeightRatio = 1.0;

    @NotNull
    @Column(name = "processing_time_minutes", nullable = false)
    private Integer processingTimeMinutes = 60;

    @Column(name = "max_capacity_per_cycle")
    private Double maxCapacityPerCycle;

    @Column(name = "min_batch_size")
    private Double minBatchSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ProcessingStage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProcessingChain getProcessingChain() { return processingChain; }
    public void setProcessingChain(ProcessingChain processingChain) { this.processingChain = processingChain; }

    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public POI getProcessingPOI() { return processingPOI; }
    public void setProcessingPOI(POI processingPOI) { this.processingPOI = processingPOI; }

    public Goods getInputGoods() { return inputGoods; }
    public void setInputGoods(Goods inputGoods) { this.inputGoods = inputGoods; }

    public String getInputGoodsSku() { return inputGoodsSku; }
    public void setInputGoodsSku(String inputGoodsSku) { this.inputGoodsSku = inputGoodsSku; }

    public Double getInputWeightRatio() { return inputWeightRatio; }
    public void setInputWeightRatio(Double inputWeightRatio) { this.inputWeightRatio = inputWeightRatio; }

    public Goods getOutputGoods() { return outputGoods; }
    public void setOutputGoods(Goods outputGoods) { this.outputGoods = outputGoods; }

    public String getOutputGoodsSku() { return outputGoodsSku; }
    public void setOutputGoodsSku(String outputGoodsSku) { this.outputGoodsSku = outputGoodsSku; }

    public Double getOutputWeightRatio() { return outputWeightRatio; }
    public void setOutputWeightRatio(Double outputWeightRatio) { this.outputWeightRatio = outputWeightRatio; }

    public Integer getProcessingTimeMinutes() { return processingTimeMinutes; }
    public void setProcessingTimeMinutes(Integer processingTimeMinutes) { this.processingTimeMinutes = processingTimeMinutes; }

    public Double getMaxCapacityPerCycle() { return maxCapacityPerCycle; }
    public void setMaxCapacityPerCycle(Double maxCapacityPerCycle) { this.maxCapacityPerCycle = maxCapacityPerCycle; }

    public Double getMinBatchSize() { return minBatchSize; }
    public void setMinBatchSize(Double minBatchSize) { this.minBatchSize = minBatchSize; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "ProcessingStage{" +
                "id=" + id +
                ", stageOrder=" + stageOrder +
                ", stageName='" + stageName + '\'' +
                ", processingPOI=" + (processingPOI != null ? processingPOI.getId() : null) +
                ", inputSku='" + inputGoodsSku + '\'' +
                ", outputSku='" + outputGoodsSku + '\'' +
                ", outputWeightRatio=" + outputWeightRatio +
                '}';
    }
}
