package org.example.roadsimulation.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 加工链统计 DTO
 */
public class ProcessingChainStatsDTO {
    
    private Long chainId;
    private String chainName;
    private Integer totalOrders;
    private Integer pendingOrders;
    private Integer inProcessOrders;
    private Integer completedOrders;
    private Integer cancelledOrders;
    private Double totalInputWeight;
    private Double totalOutputWeight;
    private Double avgYieldRate;
    private Map<String, Integer> stageStats = new HashMap<>();
    
    public ProcessingChainStatsDTO() {}
    
    // Getters & Setters
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getChainName() { return chainName; }
    public void setChainName(String chainName) { this.chainName = chainName; }
    public Integer getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Integer totalOrders) { this.totalOrders = totalOrders; }
    public Integer getPendingOrders() { return pendingOrders; }
    public void setPendingOrders(Integer pendingOrders) { this.pendingOrders = pendingOrders; }
    public Integer getInProcessOrders() { return inProcessOrders; }
    public void setInProcessOrders(Integer inProcessOrders) { this.inProcessOrders = inProcessOrders; }
    public Integer getCompletedOrders() { return completedOrders; }
    public void setCompletedOrders(Integer completedOrders) { this.completedOrders = completedOrders; }
    public Integer getCancelledOrders() { return cancelledOrders; }
    public void setCancelledOrders(Integer cancelledOrders) { this.cancelledOrders = cancelledOrders; }
    public Double getTotalInputWeight() { return totalInputWeight; }
    public void setTotalInputWeight(Double totalInputWeight) { this.totalInputWeight = totalInputWeight; }
    public Double getTotalOutputWeight() { return totalOutputWeight; }
    public void setTotalOutputWeight(Double totalOutputWeight) { this.totalOutputWeight = totalOutputWeight; }
    public Double getAvgYieldRate() { return avgYieldRate; }
    public void setAvgYieldRate(Double avgYieldRate) { this.avgYieldRate = avgYieldRate; }
    public Map<String, Integer> getStageStats() { return stageStats; }
    public void setStageStats(Map<String, Integer> stageStats) { this.stageStats = stageStats; }
}
