package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 加工链主表 - 定义完整的跨 POI 加工流程
 * 支持 Y 形加工链（多链合并）
 */
@Entity
@Table(name = "processing_chain")
public class ProcessingChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "chain_code", unique = true, length = 50)
    private String chainCode;

    @NotBlank
    @Column(name = "chain_name", length = 100)
    private String chainName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ChainStatus status = ChainStatus.ACTIVE;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "total_processing_time_minutes")
    private Integer totalProcessingTimeMinutes;

    @Column(name = "input_weight_per_cycle")
    private Double inputWeightPerCycle;

    @Column(name = "output_weight_per_cycle")
    private Double outputWeightPerCycle;

    @Column(name = "yield_rate")
    private Double yieldRate = 0.95;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 前驱加工链 IDs（用于 Y 形加工链合并）
     * 例如：加工链 C 的 predecessorChainIds = [A.id, B.id]
     */
    @ElementCollection
    @CollectionTable(name = "processing_chain_predecessors", 
                     joinColumns = @JoinColumn(name = "chain_id"))
    @Column(name = "predecessor_chain_id")
    private Set<Long> predecessorChainIds = new HashSet<>();

    /**
     * 合并工序 ID（在本链中的哪个工序进行合并）
     */
    @Column(name = "merge_stage_id")
    private Long mergeStageId;

    /**
     * 输入物料 JSON（描述从各上游链输入的物料）
     * 格式：{"chainId_A": "SEMIF_STEEL", "chainId_B": "SEMIF_WOOD"}
     */
    @Column(name = "input_materials", columnDefinition = "TEXT")
    private String inputMaterials;

    @OneToMany(mappedBy = "processingChain", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stageOrder ASC")
    private List<ProcessingStage> stages = new ArrayList<>();
    
    public ProcessingChain() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChainCode() { return chainCode; }
    public void setChainCode(String chainCode) { this.chainCode = chainCode; }
    public String getChainName() { return chainName; }
    public void setChainName(String chainName) { this.chainName = chainName; }
    public ChainStatus getStatus() { return status; }
    public void setStatus(ChainStatus status) { this.status = status; }
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
    public List<ProcessingStage> getStages() { return stages; }
    public void setStages(List<ProcessingStage> stages) { this.stages = stages; }

    public Set<Long> getPredecessorChainIds() { return predecessorChainIds; }
    public void setPredecessorChainIds(Set<Long> predecessorChainIds) { this.predecessorChainIds = predecessorChainIds; }
    public Long getMergeStageId() { return mergeStageId; }
    public void setMergeStageId(Long mergeStageId) { this.mergeStageId = mergeStageId; }
    public String getInputMaterials() { return inputMaterials; }
    public void setInputMaterials(String inputMaterials) { this.inputMaterials = inputMaterials; }

    /**
     * 添加前驱加工链 ID
     */
    public void addPredecessorChainId(Long chainId) {
        if (chainId != null) {
            predecessorChainIds.add(chainId);
        }
    }

    /**
     * 判断是否是合并加工链（Y 形的下游链）
     */
    public boolean isMergeChain() {
        return predecessorChainIds != null && !predecessorChainIds.isEmpty();
    }

    public void addStage(ProcessingStage stage) {
        if (stage != null) {
            stages.add(stage);
            stage.setProcessingChain(this);
        }
    }
    
    public void removeStage(ProcessingStage stage) {
        if (stage != null) {
            stages.remove(stage);
            stage.setProcessingChain(null);
        }
    }
    
    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum ChainStatus {
        ACTIVE,
        INACTIVE,
        MAINTENANCE
    }
    
    @Override
    public String toString() {
        return "ProcessingChain{" +
                "id=" + id +
                ", chainCode='" + chainCode + '\'' +
                ", chainName='" + chainName + '\'' +
                ", status=" + status +
                ", stages=" + stages.size() +
                '}';
    }
}
