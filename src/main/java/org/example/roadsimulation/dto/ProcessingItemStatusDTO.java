package org.example.roadsimulation.dto;

import java.time.LocalDateTime;

/**
 * 加工物料项状态 DTO
 */
public class ProcessingItemStatusDTO {

    private Long id;
    private Long itemId;
    private String stageName;
    private Integer stageOrder;
    private String processingStatus;
    private Integer progressPercent;
    private Double processedWeight;
    private LocalDateTime processingStartTime;
    private LocalDateTime processingEndTime;
    private Long inboundAssignmentId;
    private Long outboundAssignmentId;

    public ProcessingItemStatusDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public Double getProcessedWeight() { return processedWeight; }
    public void setProcessedWeight(Double processedWeight) { this.processedWeight = processedWeight; }

    public LocalDateTime getProcessingStartTime() { return processingStartTime; }
    public void setProcessingStartTime(LocalDateTime processingStartTime) { this.processingStartTime = processingStartTime; }

    public LocalDateTime getProcessingEndTime() { return processingEndTime; }
    public void setProcessingEndTime(LocalDateTime processingEndTime) { this.processingEndTime = processingEndTime; }

    public Long getInboundAssignmentId() { return inboundAssignmentId; }
    public void setInboundAssignmentId(Long inboundAssignmentId) { this.inboundAssignmentId = inboundAssignmentId; }

    public Long getOutboundAssignmentId() { return outboundAssignmentId; }
    public void setOutboundAssignmentId(Long outboundAssignmentId) { this.outboundAssignmentId = outboundAssignmentId; }
}
