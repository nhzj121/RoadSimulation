package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name="poi_id")
    private POI poi;

    @ManyToOne
    @JoinColumn(name="goods_id")
    private Goods goods;

    // 创建的时间
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "quantity") // 例如：在该 POI 的货物数量
    private Integer quantity;

    // 进行修改的对象和时间
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    // 构造方法
    public Enrollment() {}

    public Enrollment(POI poi, Goods goods) {
        this.poi = poi;
        this.goods = goods;
    }

    public Enrollment(POI poi, Goods goods, Integer quantity) {
        this.poi = poi;
        this.goods = goods;
        this.quantity = quantity;
    }

    // Getter & Setter
    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public void setPoi(POI poi) {this.poi = poi;}
    public POI getPoi() {return poi;}
    public void setGoods(Goods goods) {this.goods = goods;}
    public  Goods getGoods() {return goods;}

    // 四元组字段的getter和setter
    public LocalDateTime getCreatedTime() {return createdAt;}
    public void setCreatedTime(LocalDateTime createdTime) {this.createdAt = createdTime;}
    public String getUpdatedBy() {return updatedBy;}
    public void setUpdatedBy(String updatedBy) {this.updatedBy = updatedBy;}
    public LocalDateTime getUpdatedTime() {return updatedTime;}
    public void setUpdatedTime(LocalDateTime updatedTime) {this.updatedTime = updatedTime;}

}
