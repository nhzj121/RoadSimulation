package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Directed edge from one stage output to one named input of the next stage. */
@Entity
@Table(
        name = "processing_stage_edge",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_processing_stage_edge_input", columnNames = "to_stage_input_id"),
                @UniqueConstraint(
                        name = "uk_processing_stage_edge",
                        columnNames = {"from_stage_id", "to_stage_id", "to_stage_input_id"}
                )
        },
        indexes = {
                @Index(name = "idx_processing_stage_edge_chain", columnList = "chain_id"),
                @Index(name = "idx_processing_stage_edge_from", columnList = "from_stage_id"),
                @Index(name = "idx_processing_stage_edge_to", columnList = "to_stage_id")
        }
)
public class ProcessingStageEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id", nullable = false)
    private ProcessingChain chain;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_stage_id", nullable = false)
    private ProcessingStage fromStage;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stage_id", nullable = false)
    private ProcessingStage toStage;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stage_input_id", nullable = false)
    private ProcessingStageInput toStageInput;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ProcessingStageEdge() {}

    public ProcessingStageEdge(
            ProcessingChain chain,
            ProcessingStage fromStage,
            ProcessingStage toStage,
            ProcessingStageInput toStageInput
    ) {
        this.chain = chain;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.toStageInput = toStageInput;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProcessingChain getChain() { return chain; }
    public void setChain(ProcessingChain chain) { this.chain = chain; }
    public ProcessingStage getFromStage() { return fromStage; }
    public void setFromStage(ProcessingStage fromStage) { this.fromStage = fromStage; }
    public ProcessingStage getToStage() { return toStage; }
    public void setToStage(ProcessingStage toStage) { this.toStage = toStage; }
    public ProcessingStageInput getToStageInput() { return toStageInput; }
    public void setToStageInput(ProcessingStageInput toStageInput) { this.toStageInput = toStageInput; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
