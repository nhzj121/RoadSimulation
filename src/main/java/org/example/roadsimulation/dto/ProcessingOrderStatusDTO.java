package org.example.roadsimulation.dto;

import java.time.LocalDateTime;

/**
 * 加工订单状态 DTO
 */
public class ProcessingOrderStatusDTO {
    
    private Long orderId;
    private String orderNo;
    private String status;
    private Integer currentStageIndex;
    private String currentStageName;
    private Integer overallProgress;
    private LocalDateTime startTime;
    private LocalDateTime expectedFinishTime;
    private LocalDateTime actualFinishTime;
    
    public ProcessingOrderStatusDTO() {}
    
    // Getters & Setters
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStageIndex() { return currentStageIndex; }
    public void setCurrentStageIndex(Integer currentStageIndex) { this.currentStageIndex = currentStageIndex; }
    public String getCurrentStageName() { return currentStageName; }
    public void setCurrentStageName(String currentStageName) { this.currentStageName = currentStageName; }
    public Integer getOverallProgress() { return overallProgress; }
    public void setOverallProgress(Integer overallProgress) { this.overallProgress = overallProgress; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getExpectedFinishTime() { return expectedFinishTime; }
    public void setExpectedFinishTime(LocalDateTime expectedFinishTime) { this.expectedFinishTime = expectedFinishTime; }
    public LocalDateTime getActualFinishTime() { return actualFinishTime; }
    public void setActualFinishTime(LocalDateTime actualFinishTime) { this.actualFinishTime = actualFinishTime; }
}
