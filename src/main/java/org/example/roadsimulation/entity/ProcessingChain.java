package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Static definition of a linear processing chain.
 * Runtime demand and execution are represented by the production domain.
 */
@Entity
@Table(name = "processing_chain")
public class ProcessingChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "chain_code", unique = true, length = 50, nullable = false)
    private String chainCode;

    @NotBlank
    @Size(max = 100)
    @Column(name = "chain_name", length = 100, nullable = false)
    private String chainName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ChainStatus status = ChainStatus.ACTIVE;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "processingChain", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stageOrder ASC")
    private List<ProcessingStage> stages = new ArrayList<>();

    public ProcessingChain() {}

    public enum ChainStatus {
        ACTIVE,
        INACTIVE,
        MAINTENANCE
    }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<ProcessingStage> getStages() { return stages; }
    public void setStages(List<ProcessingStage> stages) { this.stages = stages; }

    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "ProcessingChain{" +
                "id=" + id +
                ", chainCode='" + chainCode + '\'' +
                ", chainName='" + chainName + '\'' +
                ", status=" + status +
                ", stages=" + (stages == null ? 0 : stages.size()) +
                '}';
    }
}
