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

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "quantity") // 例如：在该 POI 的货物数量
    private Integer quantity;

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
}
