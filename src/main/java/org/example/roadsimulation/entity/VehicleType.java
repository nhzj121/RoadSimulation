package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicle_type")
public class VehicleType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 100, message = "车型名称长度不能超过100个字符")
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 50, message = "车型代码长度不能超过50个字符")
    @Column(name = "code", unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private VehicleCategory category;

    @Min(value = 0, message = "最大载重不能为负数")
    @Column(name = "max_load_weight", nullable = false)
    private Double maxLoadWeight;

    @Min(value = 0, message = "最大容积不能为负数")
    @Column(name = "max_load_volume", nullable = false)
    private Double maxLoadVolume;

    @Min(value = 0, message = "最小载重不能为负数")
    @Column(name = "min_load_weight")
    private Double minLoadWeight = 0.0;

    @Column(name = "has_temp_control")
    private Boolean hasTempControl = Boolean.FALSE;

    @Size(max = 200)
    @Column(name = "allowed_hazmat_levels")
    private String allowedHazmatLevels;

    @Size(max = 500)
    @Column(name = "special_features")
    private String specialFeatures;

    @Min(value = 0, message = "百公里油耗不能为负数")
    @Column(name = "fuel_consumption")
    private Double fuelConsumption;

    @Column(name = "is_available")
    private Boolean isAvailable = Boolean.TRUE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "vehicleType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Vehicle> vehicles = new ArrayList<>();

    // Constructors
    public VehicleType() {}

    public VehicleType(String name, String code, VehicleCategory category,
                       Double maxLoadWeight, Double maxLoadVolume) {
        this.name = name;
        this.code = code;
        this.category = category;
        this.maxLoadWeight = maxLoadWeight;
        this.maxLoadVolume = maxLoadVolume;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public VehicleCategory getCategory() { return category; }
    public void setCategory(VehicleCategory category) { this.category = category; }
    public Double getMaxLoadWeight() { return maxLoadWeight; }
    public void setMaxLoadWeight(Double maxLoadWeight) { this.maxLoadWeight = maxLoadWeight; }
    public Double getMaxLoadVolume() { return maxLoadVolume; }
    public void setMaxLoadVolume(Double maxLoadVolume) { this.maxLoadVolume = maxLoadVolume; }
    public Double getMinLoadWeight() { return minLoadWeight; }
    public void setMinLoadWeight(Double minLoadWeight) { this.minLoadWeight = minLoadWeight; }
    public Boolean getHasTempControl() { return hasTempControl; }
    public void setHasTempControl(Boolean hasTempControl) { this.hasTempControl = hasTempControl; }
    public String getAllowedHazmatLevels() { return allowedHazmatLevels; }
    public void setAllowedHazmatLevels(String allowedHazmatLevels) { this.allowedHazmatLevels = allowedHazmatLevels; }
    public String getSpecialFeatures() { return specialFeatures; }
    public void setSpecialFeatures(String specialFeatures) { this.specialFeatures = specialFeatures; }
    public Double getFuelConsumption() { return fuelConsumption; }
    public void setFuelConsumption(Double fuelConsumption) { this.fuelConsumption = fuelConsumption; }
    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean available) { isAvailable = available; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<Vehicle> getVehicles() { return vehicles; }
    public void setVehicles(List<Vehicle> vehicles) { this.vehicles = vehicles; }

    // Utility methods
    public boolean supportsHazmatLevel(String level) {
        if (allowedHazmatLevels == null || level == null) return false;
        String[] levels = allowedHazmatLevels.split(",");
        for (String allowedLevel : levels) {
            if (allowedLevel.trim().equals(level)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSpecialFeature(String feature) {
        if (specialFeatures == null || feature == null) return false;
        return specialFeatures.toLowerCase().contains(feature.toLowerCase());
    }

    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }
}