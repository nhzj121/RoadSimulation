package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆 - 货物匹配记录实体
 * 用于记录每次货物与车辆的匹配结果
 */
@Entity
@Table(
    name = "vehicle_goods_match",
    indexes = {
        @Index(name = "idx_match_goods_id", columnList = "goods_id"),
        @Index(name = "idx_match_vehicle_id", columnList = "vehicle_id"),
        @Index(name = "idx_match_time", columnList = "match_time"),
        @Index(name = "idx_match_status", columnList = "match_status")
    }
)
public class VehicleGoodsMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联的货物 ID
    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    // 货物名称（冗余存储，便于查询）
    @Column(name = "goods_name", length = 200)
    private String goodsName;

    // 货物 SKU
    @Column(name = "goods_sku", length = 100)
    private String goodsSku;

    // 关联的车辆 ID
    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    // 车牌号（冗余存储）
    @Column(name = "license_plate", length = 25)
    private String licensePlate;

    // 匹配评分 (0-100)
    @Column(name = "match_score", columnDefinition = "DECIMAL(5,2) COMMENT '匹配评分 (0-100)'")
    private BigDecimal matchScore;

    // 是否完全匹配
    @Column(name = "is_fully_matched")
    private Boolean isFullyMatched = Boolean.FALSE;

    // 匹配状态
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", length = 20, nullable = false)
    private MatchStatus matchStatus;

    // 货物重量需求 (吨)
    @Column(name = "required_weight", columnDefinition = "DECIMAL(10,2) COMMENT '货物重量需求 (吨)'")
    private Double requiredWeight;

    // 货物体积需求 (m³)
    @Column(name = "required_volume", columnDefinition = "DECIMAL(10,2) COMMENT '货物体积需求 (m³)'")
    private Double requiredVolume;

    // 车辆载重能力 (吨)
    @Column(name = "vehicle_load_capacity", columnDefinition = "DECIMAL(10,2) COMMENT '车辆载重能力 (吨)'")
    private Double vehicleLoadCapacity;

    // 车辆容积能力 (m³)
    @Column(name = "vehicle_cargo_volume", columnDefinition = "DECIMAL(10,2) COMMENT '车辆容积能力 (m³)'")
    private Double vehicleCargoVolume;

    // 重量利用率
    @Column(name = "weight_utilization", columnDefinition = "DECIMAL(5,4) COMMENT '重量利用率'")
    private Double weightUtilization;

    // 容积利用率
    @Column(name = "volume_utilization", columnDefinition = "DECIMAL(5,4) COMMENT '容积利用率'")
    private Double volumeUtilization;

    // 是否需要温控
    @Column(name = "require_temp_control")
    private Boolean requireTempControl = Boolean.FALSE;

    // 危险品等级
    @Column(name = "hazmat_level", length = 20)
    private String hazmatLevel;

    // 出发地 POI ID
    @Column(name = "origin_poi_id")
    private Long originPoiId;

    // 出发地名称
    @Column(name = "origin_poi_name", length = 200)
    private String originPoiName;

    // 目的地 POI ID
    @Column(name = "destination_poi_id")
    private Long destinationPoiId;

    // 目的地名称
    @Column(name = "destination_poi_name", length = 200)
    private String destinationPoiName;

    // 距离 (公里)
    @Column(name = "distance_km", columnDefinition = "DECIMAL(10,2) COMMENT '距离 (公里)'")
    private Double distanceKm;

    // 匹配描述
    @Column(name = "match_description", length = 1000)
    private String matchDescription;

    // 匹配时间
    @Column(name = "match_time", nullable = false)
    private LocalDateTime matchTime = LocalDateTime.now();

    // 创建时间
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 更新时间
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // 操作人
    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    // ==================== 枚举 ====================
    public enum MatchStatus {
        PENDING,      // 待处理
        CONFIRMED,    // 已确认
        REJECTED,     // 已拒绝
        CANCELLED,    // 已取消
        COMPLETED     // 已完成（运输完成）
    }

    // ==================== 构造器 ====================
    public VehicleGoodsMatch() {}

    // ==================== Getter & Setter ====================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getGoodsSku() { return goodsSku; }
    public void setGoodsSku(String goodsSku) { this.goodsSku = goodsSku; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public BigDecimal getMatchScore() { return matchScore; }
    public void setMatchScore(BigDecimal matchScore) { this.matchScore = matchScore; }

    public Boolean getIsFullyMatched() { return isFullyMatched; }
    public void setIsFullyMatched(Boolean isFullyMatched) { this.isFullyMatched = isFullyMatched; }

    public MatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(MatchStatus matchStatus) { this.matchStatus = matchStatus; }

    public Double getRequiredWeight() { return requiredWeight; }
    public void setRequiredWeight(Double requiredWeight) { this.requiredWeight = requiredWeight; }

    public Double getRequiredVolume() { return requiredVolume; }
    public void setRequiredVolume(Double requiredVolume) { this.requiredVolume = requiredVolume; }

    public Double getVehicleLoadCapacity() { return vehicleLoadCapacity; }
    public void setVehicleLoadCapacity(Double vehicleLoadCapacity) { this.vehicleLoadCapacity = vehicleLoadCapacity; }

    public Double getVehicleCargoVolume() { return vehicleCargoVolume; }
    public void setVehicleCargoVolume(Double vehicleCargoVolume) { this.vehicleCargoVolume = vehicleCargoVolume; }

    public Double getWeightUtilization() { return weightUtilization; }
    public void setWeightUtilization(Double weightUtilization) { this.weightUtilization = weightUtilization; }

    public Double getVolumeUtilization() { return volumeUtilization; }
    public void setVolumeUtilization(Double volumeUtilization) { this.volumeUtilization = volumeUtilization; }

    public Boolean getRequireTempControl() { return requireTempControl; }
    public void setRequireTempControl(Boolean requireTempControl) { this.requireTempControl = requireTempControl; }

    public String getHazmatLevel() { return hazmatLevel; }
    public void setHazmatLevel(String hazmatLevel) { this.hazmatLevel = hazmatLevel; }

    public Long getOriginPoiId() { return originPoiId; }
    public void setOriginPoiId(Long originPoiId) { this.originPoiId = originPoiId; }

    public String getOriginPoiName() { return originPoiName; }
    public void setOriginPoiName(String originPoiName) { this.originPoiName = originPoiName; }

    public Long getDestinationPoiId() { return destinationPoiId; }
    public void setDestinationPoiId(Long destinationPoiId) { this.destinationPoiId = destinationPoiId; }

    public String getDestinationPoiName() { return destinationPoiName; }
    public void setDestinationPoiName(String destinationPoiName) { this.destinationPoiName = destinationPoiName; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public String getMatchDescription() { return matchDescription; }
    public void setMatchDescription(String matchDescription) { this.matchDescription = matchDescription; }

    public LocalDateTime getMatchTime() { return matchTime; }
    public void setMatchTime(LocalDateTime matchTime) { this.matchTime = matchTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "VehicleGoodsMatch{" +
                "id=" + id +
                ", goodsId=" + goodsId +
                ", goodsName='" + goodsName + '\'' +
                ", vehicleId=" + vehicleId +
                ", licensePlate='" + licensePlate + '\'' +
                ", matchScore=" + matchScore +
                ", matchStatus=" + matchStatus +
                '}';
    }
}
