package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 加工订单 - 一次完整的跨 POI 加工任务实例
 */
@Entity
@Table(name = "processing_order")
public class ProcessingOrder {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(name = "order_no", unique = true, length = 50)
    private String orderNo;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chain_id", nullable = false)
    private ProcessingChain processingChain;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_goods_id")
    private Goods inputGoods;
    
    @Column(name = "input_goods_sku", length = 50)
    private String inputGoodsSku;
    
    @Column(name = "input_weight")
    private Double inputWeight;
    
    @Column(name = "input_volume")
    private Double inputVolume;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_goods_id")
    private Goods outputGoods;
    
    @Column(name = "expected_output_weight")
    private Double expectedOutputWeight;
    
    @Column(name = "actual_output_weight")
    private Double actualOutputWeight;
    
    @Column(name = "order_time")
    private LocalDateTime orderTime = LocalDateTime.now();
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "expected_finish_time")
    private LocalDateTime expectedFinishTime;
    
    @Column(name = "actual_finish_time")
    private LocalDateTime actualFinishTime;
    
    @OneToMany(mappedBy = "processingOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProcessingTask> tasks = new ArrayList<>();
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public ProcessingOrder() {}
    
    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public ProcessingChain getProcessingChain() { return processingChain; }
    public void setProcessingChain(ProcessingChain processingChain) { this.processingChain = processingChain; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Goods getInputGoods() { return inputGoods; }
    public void setInputGoods(Goods inputGoods) { this.inputGoods = inputGoods; }
    public String getInputGoodsSku() { return inputGoodsSku; }
    public void setInputGoodsSku(String inputGoodsSku) { this.inputGoodsSku = inputGoodsSku; }
    public Double getInputWeight() { return inputWeight; }
    public void setInputWeight(Double inputWeight) { this.inputWeight = inputWeight; }
    public Double getInputVolume() { return inputVolume; }
    public void setInputVolume(Double inputVolume) { this.inputVolume = inputVolume; }
    public Goods getOutputGoods() { return outputGoods; }
    public void setOutputGoods(Goods outputGoods) { this.outputGoods = outputGoods; }
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
    public List<ProcessingTask> getTasks() { return tasks; }
    public void setTasks(List<ProcessingTask> tasks) { this.tasks = tasks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public void addTask(ProcessingTask task) {
        if (task != null) {
            tasks.add(task);
            task.setProcessingOrder(this);
        }
    }
    
    public void removeTask(ProcessingTask task) {
        if (task != null) {
            tasks.remove(task);
            task.setProcessingOrder(null);
        }
    }
    
    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum OrderStatus {
        PENDING,
        IN_PROCESS,
        COMPLETED,
        CANCELLED
    }
    
    @Override
    public String toString() {
        return "ProcessingOrder{" +
                "id=" + id +
                ", orderNo='" + orderNo + '\'' +
                ", status=" + status +
                ", chain=" + (processingChain != null ? processingChain.getChainName() : "null") +
                ", inputWeight=" + inputWeight +
                ", outputWeight=" + expectedOutputWeight +
                '}';
    }
}
