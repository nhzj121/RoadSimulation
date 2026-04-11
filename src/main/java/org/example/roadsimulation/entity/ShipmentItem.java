package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 运单明细 - 方案 A：直接添加加工字段
 */
@Entity
@Table(
        name = "shipment_item",
        indexes = {
                @Index(name = "idx_item_shipment", columnList = "shipment_id"),
                @Index(name = "idx_item_goods", columnList = "goods_id"),
                @Index(name = "idx_item_stage", columnList = "stage_id"),
                @Index(name = "idx_item_processing_status", columnList = "processing_status")
        }
)
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "运单不能为空")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id")
    private Goods goods;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    // ==================== 加工特有字段（方案 A）====================
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private ProcessingStage stage;  // 关联的工序
    
    @Column(name = "stage_order")
    private Integer stageOrder;  // 工序顺序
    
    @Column(name = "stage_name", length = 100)
    private String stageName;  // 工序名称
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_poi_id")
    private POI processingPOI;  // 加工点 POI
    
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 20)
    private ProcessingItemStatus processingStatus = ProcessingItemStatus.WAITING;  // 加工状态
    
    @Column(name = "processed_weight")
    private Double processedWeight;  // 已加工重量
    
    @Column(name = "progress_percent")
    private Integer progressPercent = 0;  // 加工进度 0-100
    
    @Column(name = "processing_start_time")
    private LocalDateTime processingStartTime;  // 开始加工时间
    
    @Column(name = "processing_end_time")
    private LocalDateTime processingEndTime;  // 完成加工时间
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_assignment_id")
    private Assignment inboundAssignment;  // 原料运入任务
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_assignment_id")
    private Assignment outboundAssignment;  // 成品运出任务

    // ==================== 原有字段 ====================

    @NotNull
    @Size(max = 200, message = "品名长度不能超过 200 个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    public enum ShipmentItemStatus {
        NOT_ASSIGNED, ASSIGNED, LOADED, IN_TRANSIT, DELIVERED
    }

    /**
     * 加工物料项状态
     */
    public enum ProcessingItemStatus {
        WAITING,      // 等待加工
        READY,        // 已就绪
        PROCESSING,   // 加工中
        COMPLETED,    // 已完成
        BLOCKED,      // 阻塞
        FAILED,       // 失败
        CANCELLED     // 已取消
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ShipmentItem.ShipmentItemStatus status = ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED;

    @Size(max = 100, message = "SKU 长度不能超过 100 个字符")
    @Column(name = "sku")
    private String sku;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    @Column(name = "qty")
    private Integer qty;

    @Min(value = 0, message = "重量不能为负数")
    @Column(name = "weight")
    private Double weight;

    @Min(value = 0, message = "体积不能为负数")
    @Column(name = "volume")
    private Double volume;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    public ShipmentItem() {}

    public ShipmentItem(@NotNull Shipment shipment, String name, Integer qty, String sku, Double weight, Double volume) {
        this.shipment = shipment;
        this.name = name;
        this.qty = qty;
        this.sku = sku;
        this.weight = weight;
        this.volume = volume;
    }

    // ==================== Getter & Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) {
        if (this.shipment == shipment) return;
        Shipment oldShipment = this.shipment;
        this.shipment = shipment;
        if (oldShipment != null) {
            oldShipment.removeItem(this);
        }
        if (shipment != null) {
            shipment.addItem(this);
        }
    }

    public Goods getGoods() { return goods; }
    public void setGoods(Goods goods) { this.goods = goods; }

    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) {
        if (this.assignment == assignment) return;
        Assignment oldAssignment = this.assignment;
        if (oldAssignment != null) {
            oldAssignment.getShipmentItems().remove(this);
        }
        this.assignment = assignment;
        if (assignment != null) {
            assignment.addShipmentItem(this);
        }
    }

    // 加工特有字段 Getter & Setter
    public ProcessingStage getStage() { return stage; }
    public void setStage(ProcessingStage stage) { this.stage = stage; }

    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public POI getProcessingPOI() { return processingPOI; }
    public void setProcessingPOI(POI processingPOI) { this.processingPOI = processingPOI; }

    public ProcessingItemStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingItemStatus processingStatus) { this.processingStatus = processingStatus; }

    public Double getProcessedWeight() { return processedWeight; }
    public void setProcessedWeight(Double processedWeight) { this.processedWeight = processedWeight; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public LocalDateTime getProcessingStartTime() { return processingStartTime; }
    public void setProcessingStartTime(LocalDateTime processingStartTime) { this.processingStartTime = processingStartTime; }

    public LocalDateTime getProcessingEndTime() { return processingEndTime; }
    public void setProcessingEndTime(LocalDateTime processingEndTime) { this.processingEndTime = processingEndTime; }

    public Assignment getInboundAssignment() { return inboundAssignment; }
    public void setInboundAssignment(Assignment inboundAssignment) { this.inboundAssignment = inboundAssignment; }

    public Assignment getOutboundAssignment() { return outboundAssignment; }
    public void setOutboundAssignment(Assignment outboundAssignment) { this.outboundAssignment = outboundAssignment; }

    // 原有字段 Getter & Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public ShipmentItemStatus getStatus() { return status; }
    public void setStatus(ShipmentItemStatus status) { this.status = status; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    @Override
    public String toString() {
        return "ShipmentItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", stageName='" + stageName + '\'' +
                ", processingStatus=" + processingStatus +
                ", progress=" + progressPercent +
                '%'+
                '}';
    }
}
