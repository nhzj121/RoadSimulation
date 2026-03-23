package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 加工任务 - 单个工序的执行实例
 */
@Entity
@Table(name = "processing_task")
public class ProcessingTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProcessingOrder processingOrder;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private ProcessingStage stage;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TaskStatus status = TaskStatus.WAITING;
    
    @Column(name = "progress_percent")
    private Integer progressPercent = 0;
    
    @Column(name = "processed_weight")
    private Double processedWeight;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_assignment_id")
    private Assignment inboundAssignment;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_assignment_id")
    private Assignment outboundAssignment;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public ProcessingTask() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProcessingOrder getProcessingOrder() { return processingOrder; }
    public void setProcessingOrder(ProcessingOrder processingOrder) { this.processingOrder = processingOrder; }
    public ProcessingStage getStage() { return stage; }
    public void setStage(ProcessingStage stage) { this.stage = stage; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public Double getProcessedWeight() { return processedWeight; }
    public void setProcessedWeight(Double processedWeight) { this.processedWeight = processedWeight; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Assignment getInboundAssignment() { return inboundAssignment; }
    public void setInboundAssignment(Assignment inboundAssignment) { this.inboundAssignment = inboundAssignment; }
    public Assignment getOutboundAssignment() { return outboundAssignment; }
    public void setOutboundAssignment(Assignment outboundAssignment) { this.outboundAssignment = outboundAssignment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum TaskStatus {
        WAITING,
        PROCESSING,
        COMPLETED,
        BLOCKED,
        FAILED,
        CANCELLED
    }
    
    @Override
    public String toString() {
        return "ProcessingTask{" +
                "id=" + id +
                ", stageName='" + (stage != null ? stage.getStageName() : "null") + '\'' +
                ", status=" + status +
                ", progress=" + progressPercent +
                '%'+
                '}';
    }
}
