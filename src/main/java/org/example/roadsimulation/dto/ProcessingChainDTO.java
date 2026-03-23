package org.example.roadsimulation.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProcessingChainDTO {
    
    private Long id;
    private String chainCode;
    private String chainName;
    private String status;
    private String description;
    private Integer totalProcessingTimeMinutes;
    private Double inputWeightPerCycle;
    private Double outputWeightPerCycle;
    private Double yieldRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProcessingStageDTO> stages = new ArrayList<>();
    
    public ProcessingChainDTO() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChainCode() { return chainCode; }
    public void setChainCode(String chainCode) { this.chainCode = chainCode; }
    public String getChainName() { return chainName; }
    public void setChainName(String chainName) { this.chainName = chainName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getTotalProcessingTimeMinutes() { return totalProcessingTimeMinutes; }
    public void setTotalProcessingTimeMinutes(Integer totalProcessingTimeMinutes) { this.totalProcessingTimeMinutes = totalProcessingTimeMinutes; }
    public Double getInputWeightPerCycle() { return inputWeightPerCycle; }
    public void setInputWeightPerCycle(Double inputWeightPerCycle) { this.inputWeightPerCycle = inputWeightPerCycle; }
    public Double getOutputWeightPerCycle() { return outputWeightPerCycle; }
    public void setOutputWeightPerCycle(Double outputWeightPerCycle) { this.outputWeightPerCycle = outputWeightPerCycle; }
    public Double getYieldRate() { return yieldRate; }
    public void setYieldRate(Double yieldRate) { this.yieldRate = yieldRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ProcessingStageDTO> getStages() { return stages; }
    public void setStages(List<ProcessingStageDTO> stages) { this.stages = stages; }
    
    public void addStage(ProcessingStageDTO stage) {
        if (stage != null) {
            stages.add(stage);
        }
    }
}
