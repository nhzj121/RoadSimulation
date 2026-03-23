package org.example.roadsimulation.dto;

import java.time.LocalDateTime;

public class ProcessingTaskDTO {
    
    private Long id;
    private Long orderId;
    private Long stageId;
    private String stageName;
    private Integer stageOrder;
    private String status;
    private Integer progressPercent;
    private Double processedWeight;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long inboundAssignmentId;
    private Long outboundAssignmentId;
    private Long poiId;
    private String poiName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public ProcessingTaskDTO() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public Double getProcessedWeight() { return processedWeight; }
    public void setProcessedWeight(Double processedWeight) { this.processedWeight = processedWeight; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Long getInboundAssignmentId() { return inboundAssignmentId; }
    public void setInboundAssignmentId(Long inboundAssignmentId) { this.inboundAssignmentId = inboundAssignmentId; }
    public Long getOutboundAssignmentId() { return outboundAssignmentId; }
    public void setOutboundAssignmentId(Long outboundAssignmentId) { this.outboundAssignmentId = outboundAssignmentId; }
    public Long getPoiId() { return poiId; }
    public void setPoiId(Long poiId) { this.poiId = poiId; }
    public String getPoiName() { return poiName; }
    public void setPoiName(String poiName) { this.poiName = poiName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
