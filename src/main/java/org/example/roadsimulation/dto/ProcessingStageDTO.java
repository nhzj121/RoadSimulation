package org.example.roadsimulation.dto;

import java.time.LocalDateTime;

public class ProcessingStageDTO {
    
    private Long id;
    private Integer stageOrder;
    private String stageName;
    private String description;
    private Long poiId;
    private String poiName;
    private String poiType;
    private Long inputGoodsId;
    private String inputGoodsSku;
    private String inputGoodsName;
    private Double inputWeightRatio;
    private Long outputGoodsId;
    private String outputGoodsSku;
    private String outputGoodsName;
    private Double outputWeightRatio;
    private Integer processingTimeMinutes;
    private Double maxCapacityPerCycle;
    private Double minBatchSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public ProcessingStageDTO() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getPoiId() { return poiId; }
    public void setPoiId(Long poiId) { this.poiId = poiId; }
    public String getPoiName() { return poiName; }
    public void setPoiName(String poiName) { this.poiName = poiName; }
    public String getPoiType() { return poiType; }
    public void setPoiType(String poiType) { this.poiType = poiType; }
    public Long getInputGoodsId() { return inputGoodsId; }
    public void setInputGoodsId(Long inputGoodsId) { this.inputGoodsId = inputGoodsId; }
    public String getInputGoodsSku() { return inputGoodsSku; }
    public void setInputGoodsSku(String inputGoodsSku) { this.inputGoodsSku = inputGoodsSku; }
    public String getInputGoodsName() { return inputGoodsName; }
    public void setInputGoodsName(String inputGoodsName) { this.inputGoodsName = inputGoodsName; }
    public Double getInputWeightRatio() { return inputWeightRatio; }
    public void setInputWeightRatio(Double inputWeightRatio) { this.inputWeightRatio = inputWeightRatio; }
    public Long getOutputGoodsId() { return outputGoodsId; }
    public void setOutputGoodsId(Long outputGoodsId) { this.outputGoodsId = outputGoodsId; }
    public String getOutputGoodsSku() { return outputGoodsSku; }
    public void setOutputGoodsSku(String outputGoodsSku) { this.outputGoodsSku = outputGoodsSku; }
    public String getOutputGoodsName() { return outputGoodsName; }
    public void setOutputGoodsName(String outputGoodsName) { this.outputGoodsName = outputGoodsName; }
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
}
