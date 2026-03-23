package org.example.roadsimulation.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProcessingOrderDTO {
    
    private Long id;
    private String orderNo;
    private Long chainId;
    private String chainName;
    private String status;
    private Long inputGoodsId;
    private String inputGoodsName;
    private Double inputWeight;
    private Double inputVolume;
    private Long outputGoodsId;
    private String outputGoodsName;
    private Double expectedOutputWeight;
    private Double actualOutputWeight;
    private LocalDateTime orderTime;
    private LocalDateTime startTime;
    private LocalDateTime expectedFinishTime;
    private LocalDateTime actualFinishTime;
    private List<ProcessingTaskDTO> tasks = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public ProcessingOrderDTO() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getChainName() { return chainName; }
    public void setChainName(String chainName) { this.chainName = chainName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getInputGoodsId() { return inputGoodsId; }
    public void setInputGoodsId(Long inputGoodsId) { this.inputGoodsId = inputGoodsId; }
    public String getInputGoodsName() { return inputGoodsName; }
    public void setInputGoodsName(String inputGoodsName) { this.inputGoodsName = inputGoodsName; }
    public Double getInputWeight() { return inputWeight; }
    public void setInputWeight(Double inputWeight) { this.inputWeight = inputWeight; }
    public Double getInputVolume() { return inputVolume; }
    public void setInputVolume(Double inputVolume) { this.inputVolume = inputVolume; }
    public Long getOutputGoodsId() { return outputGoodsId; }
    public void setOutputGoodsId(Long outputGoodsId) { this.outputGoodsId = outputGoodsId; }
    public String getOutputGoodsName() { return outputGoodsName; }
    public void setOutputGoodsName(String outputGoodsName) { this.outputGoodsName = outputGoodsName; }
    public Double getExpectedOutputWeight() { return expectedOutputWeight; }
    public void setExpectedOutputWeight(Double expectedOutputWeight) { this.expectedOutputWeight = expectedOutputWeight; }
    public Double getActualOutputWeight() { return actualOutputWeight; }
    public void setActualOutputWeight(Double actualOutputWeight) { this.actualOutputWeight = actualOutputWeight; }
    public LocalDateTime getOrderTime() { return orderTime; }
    public void setOrderTime(LocalDateTime orderTime) { this.orderTime = orderTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getExpectedFinishTime() { return expectedFinishTime; }
    public void setExpectedFinishTime(LocalDateTime expectedFinishTime) { this.expectedFinishTime = expectedFinishTime; }
    public LocalDateTime getActualFinishTime() { return actualFinishTime; }
    public void setActualFinishTime(LocalDateTime actualFinishTime) { this.actualFinishTime = actualFinishTime; }
    public List<ProcessingTaskDTO> getTasks() { return tasks; }
    public void setTasks(List<ProcessingTaskDTO> tasks) { this.tasks = tasks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public void addTask(ProcessingTaskDTO task) {
        if (task != null) {
            tasks.add(task);
        }
    }
}
