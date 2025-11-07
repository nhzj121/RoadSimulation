package org.example.roadsimulation.dto;

public class VehicleMatchingCriteria {
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
}