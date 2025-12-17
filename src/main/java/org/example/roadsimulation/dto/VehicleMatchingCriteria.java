package org.example.roadsimulation.dto;

public class VehicleMatchingCriteria {
    // ... 已有字段 ...

    private Double minLoadCapacity;      // 最小载重需求
    private Double maxLoadCapacity;      // 最大载重需求
    private Double requiredVolume;       // 所需容积
    private Boolean requireTempControl;  // 是否需要温控
    private String hazmatLevel;          // 危险品等级
    private Double minLength;            // 最小长度需求
    private Double minWidth;             // 最小宽度需求
    private Double minHeight;            // 最小高度需求
    private String vehicleType;          // 特定车型要求
    private String brand;                // 品牌要求

    // 新增字段：用于就近匹配
    private Long originPoiId;           // 货物出发地POI ID
    private Double maxDistanceKm;        // 最大可接受距离（公里）
    private Boolean prioritizeDistance;  // 是否优先考虑距离

    // 构造器
    public VehicleMatchingCriteria() {}

    // Getter和Setter
    public Double getMinLoadCapacity() { return minLoadCapacity; }
    public void setMinLoadCapacity(Double minLoadCapacity) { this.minLoadCapacity = minLoadCapacity; }

    public Double getMaxLoadCapacity() { return maxLoadCapacity; }
    public void setMaxLoadCapacity(Double maxLoadCapacity) { this.maxLoadCapacity = maxLoadCapacity; }

    public Double getRequiredVolume() { return requiredVolume; }
    public void setRequiredVolume(Double requiredVolume) { this.requiredVolume = requiredVolume; }

    public Boolean getRequireTempControl() { return requireTempControl; }
    public void setRequireTempControl(Boolean requireTempControl) { this.requireTempControl = requireTempControl; }

    public String getHazmatLevel() { return hazmatLevel; }
    public void setHazmatLevel(String hazmatLevel) { this.hazmatLevel = hazmatLevel; }

    public Double getMinLength() { return minLength; }
    public void setMinLength(Double minLength) { this.minLength = minLength; }

    public Double getMinWidth() { return minWidth; }
    public void setMinWidth(Double minWidth) { this.minWidth = minWidth; }

    public Double getMinHeight() { return minHeight; }
    public void setMinHeight(Double minHeight) { this.minHeight = minHeight; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    // 新增字段的Getter和Setter
    public Long getOriginPoiId() { return originPoiId; }
    public void setOriginPoiId(Long originPoiId) { this.originPoiId = originPoiId; }

    public Double getMaxDistanceKm() { return maxDistanceKm; }
    public void setMaxDistanceKm(Double maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }

    public Boolean getPrioritizeDistance() { return prioritizeDistance; }
    public void setPrioritizeDistance(Boolean prioritizeDistance) { this.prioritizeDistance = prioritizeDistance; }
}