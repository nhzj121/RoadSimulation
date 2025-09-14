package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Goods（货物主数据）
 * 作为标准化的货物/SKU 定义，ShipmentItem 引用它以实现复用与分类管理。
 */
@Entity
@Table(
        name = "goods",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_goods_sku", columnNames = "sku")
        }
)
public class Goods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 200, message = "货物名称长度不能超过200个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 100, message = "SKU长度不能超过100个字符")
    @Column(name = "sku", unique = true)
    private String sku;

    @Size(max = 100, message = "类别长度不能超过100个字符")
    @Column(name = "category")
    private String category;

    @Size(max = 500, message = "描述长度不能超过500个字符")
    @Column(name = "description")
    private String description;

    @Min(value = 0, message = "单件重量不能为负数")
    @Column(name = "weight_per_unit")
    private Double weightPerUnit; // kg

    @Min(value = 0, message = "单件体积不能为负数")
    @Column(name = "volume_per_unit")
    private Double volumePerUnit; // m3

    @Column(name = "require_temp")
    private Boolean requireTemp = Boolean.FALSE; // 是否需要温控

    @Size(max = 50)
    @Column(name = "hazmat_level")
    private String hazmatLevel; // 危险品等级（可选）

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays; // 保质期（天）

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Goods() {}

    public Goods(String name, String sku) {
        this.name = name;
        this.sku = sku;
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getWeightPerUnit() { return weightPerUnit; }
    public void setWeightPerUnit(Double weightPerUnit) { this.weightPerUnit = weightPerUnit; }

    public Double getVolumePerUnit() { return volumePerUnit; }
    public void setVolumePerUnit(Double volumePerUnit) { this.volumePerUnit = volumePerUnit; }

    public Boolean getRequireTemp() { return requireTemp; }
    public void setRequireTemp(Boolean requireTemp) { this.requireTemp = requireTemp; }

    public String getHazmatLevel() { return hazmatLevel; }
    public void setHazmatLevel(String hazmatLevel) { this.hazmatLevel = hazmatLevel; }

    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Goods{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sku='" + sku + '\'' +
                '}';
    }
}