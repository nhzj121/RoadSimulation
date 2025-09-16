package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 运单明细（与 Shipment 多对一；与 Goods 多对一）
 * 说明：冗余 name/sku 以便历史追溯（即使 Goods 主数据后来被修改）。
 */
@Entity
@Table(
        name = "shipment_item",
        indexes = {
                @Index(name = "idx_item_shipment", columnList = "shipment_id"),
                @Index(name = "idx_item_goods", columnList = "goods_id")
        }
)
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id")
    private Goods goods; // 可为空：支持临时品名直接录入

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    // 冗余字段（下单时快照）
    @NotNull
    @Size(max = 200, message = "品名长度不能超过200个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 100, message = "SKU长度不能超过100个字符")
    @Column(name = "sku")
    private String sku;

    @Min(value = 0, message = "数量不能为负数")
    @Column(name = "qty")
    private Integer qty;

    @Min(value = 0, message = "重量不能为负数")
    @Column(name = "weight")
    private Double weight; // 单项总重（非单件重）

    @Min(value = 0, message = "体积不能为负数")
    @Column(name = "volume")
    private Double volume; // 单项总体积

    public ShipmentItem() {}

    public ShipmentItem(@NotNull Shipment shipment, String name, Integer qty) {
        this.shipment = shipment;
        this.name = name;
        this.qty = qty;
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }

    public Goods getGoods() { return goods; }
    public void setGoods(Goods goods) { this.goods = goods; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    /// ShipmentItem与Assignment之间的方法
    public Assignment getAssignment() { return assignment; }

    // 设置运单清单分配到的任务，并维护双向关系
    public void setAssignment(Assignment assignment) {
        // 如果当前分配任务与要设置的分配任务相同，则不执行任何操作
        if (this.assignment == assignment) {
            return;
        }

        // 如果当前已有分配任务，先从该任务的集合中移除自己
        Assignment oldAssignment = this.assignment;
        if (oldAssignment != null) {
            oldAssignment.removeShipmentItem(this);
        }

        // 设置新的分配任务
        this.assignment = assignment;

        // 如果新分配任务不为空，将自己添加到新任务的集合中
        if (assignment != null) {
            assignment.addShipmentItem(this);
        }
    }



    @Override
    public String toString() {
        return "ShipmentItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", qty=" + qty +
                ", shipment=" + (shipment != null ? shipment.getId() : null) +
                ", goods=" + (goods != null ? goods.getId() : null) +
                ", assignment=" + (assignment != null ? assignment.getId() : null) +
                '}';
    }
}