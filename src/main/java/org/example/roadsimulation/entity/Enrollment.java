package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "enrollment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_poi_goods", columnNames = {"poi_id", "goods_id"})
})
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="poi_id", nullable = false)
    private POI poi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="goods_id", nullable = false)
    private Goods goods;

    // 创建的时间
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    @Column(name = "version")
    private Integer version;

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

    // 每次更新前自动刷新更新时间
    @PreUpdate
    public void preUpdate() {
        this.updatedTime = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Enrollment)) return false;
        Enrollment that = (Enrollment) o;

        // 如果双方都已经持久化，直接比较 ID
        if (this.id != null && that.id != null) {
            return Objects.equals(this.id, that.id);
        }

        // 如果还未持久化（ID 为 null），则通过业务主键 (POI + Goods) 来判断是否是同一条记录
        return Objects.equals(this.poi, that.poi) &&
                Objects.equals(this.goods, that.goods);
    }

    @Override
    public int hashCode() {
        // 使用固定的哈希值或基于业务主键，避免在 HashSet/List 中因状态改变导致内存泄漏或找不到对象
        return getClass().hashCode();
    }

    // Getter & Setter
    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getVersion(){return version;}
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
