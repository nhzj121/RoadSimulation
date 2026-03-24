package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 25)
    @Column(name = "license_plate", nullable = false, unique = true)
    private String licensePlate;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime = LocalDateTime.now();

    @Min(0)
    @Column(name = "max_load_capacity")
    private Double maxLoadCapacity;

    @Column(name = "brand")
    private String brand;

    @Column(name = "model_type")
    private String modelType;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "driver_name")
    private String driverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status")
    private VehicleStatus currentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private VehicleStatus previousStatus;

    @Column(name = "status_start_time")
    private LocalDateTime statusStartTime;

    @Column(name = "status_duration_seconds")
    private Long statusDurationSeconds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_poi_id")
    private POI currentPOI;

    @Column(name = "current_longitude")
    private BigDecimal currentLongitude;

    @Column(name = "current_latitude")
    private BigDecimal currentLatitude;

    @ManyToMany(mappedBy = "vehicles")
    private Set<Driver> drivers = new HashSet<>();

    @OneToMany(mappedBy = "assignedVehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Assignment> assignments = new HashSet<>();

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    // ==================== 新增指标属性 ====================
    @Column(name = "loading_wait_time")
    private Long loadingWaitTime; // 秒

    @Column(name = "empty_driving_time")
    private Long emptyDrivingTime; // 秒

    @Column(name = "empty_driving_distance")
    private Double emptyDrivingDistance; // 公里

    @Column(name = "total_driving_time")
    private Long totalDrivingTime; // 秒

    @Column(name = "total_driving_distance")
    private Double totalDrivingDistance; // 公里

    // ==================== 补充之前缺失的属性 ====================
    @Column(name = "cargo_volume")
    private Double cargoVolume;

    @Column(name = "length")
    private Double length;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Column(name = "current_load")
    private Double currentLoad;

    @Column(name = "suitable_goods")
    private String suitableGoods;

    @Column(name = "current_volumn")
    private Double currentVolume;

    // 枚举
    public enum VehicleStatus {
        IDLE, ORDER_DRIVING, LOADING, TRANSPORT_DRIVING, UNLOADING, WAITING, BREAKDOWN
    }

    // ==================== 任务相关便捷方法 ====================

    /**
     * 获取当前进行中的任务
     */
    public Assignment getCurrentAssignment() {
        if (assignments == null || assignments.isEmpty()) {
            return null;
        }

        return assignments.stream()
                .filter(Objects::nonNull)
                .filter(Assignment::isInProgress)
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否存在进行中的任务
     */
    public boolean hasAssignmentInProgress() {
        return getCurrentAssignment() != null;
    }

    /**
     * 添加任务并维护双向关系
     */
    public void addAssignment(Assignment assignment) {
        if (assignment == null) {
            return;
        }

        if (assignments == null) {
            assignments = new HashSet<>();
        }

        assignments.add(assignment);
        if (assignment.getAssignedVehicle() != this) {
            assignment.setAssignedVehicle(this);
        }
    }

    /**
     * 移除任务并维护双向关系
     */
    public void removeAssignment(Assignment assignment) {
        if (assignment == null || assignments == null) {
            return;
        }

        assignments.remove(assignment);
        if (assignment.getAssignedVehicle() == this) {
            assignment.setAssignedVehicle(null);
        }
    }

    // ==================== Getter & Setter ====================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public Double getMaxLoadCapacity() { return maxLoadCapacity; }
    public void setMaxLoadCapacity(Double maxLoadCapacity) { this.maxLoadCapacity = maxLoadCapacity; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public VehicleStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(VehicleStatus currentStatus) { this.currentStatus = currentStatus; }

    public VehicleStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(VehicleStatus previousStatus) { this.previousStatus = previousStatus; }

    public LocalDateTime getStatusStartTime() { return statusStartTime; }
    public void setStatusStartTime(LocalDateTime statusStartTime) { this.statusStartTime = statusStartTime; }

    public Long getStatusDurationSeconds() { return statusDurationSeconds; }
    public void setStatusDurationSeconds(Long statusDurationSeconds) { this.statusDurationSeconds = statusDurationSeconds; }

    public POI getCurrentPOI() { return currentPOI; }
    public void setCurrentPOI(POI currentPOI) {
        if (this.currentPOI == currentPOI) {
            return;
        }

        POI oldPOI = this.currentPOI;
        this.currentPOI = currentPOI;

        if (oldPOI != null) {
            oldPOI.internalRemoveVehicle(this);
        }
        if (currentPOI != null) {
            currentPOI.internalAddVehicle(this);
        }
    }

    public BigDecimal getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(BigDecimal currentLongitude) { this.currentLongitude = currentLongitude; }

    public BigDecimal getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(BigDecimal currentLatitude) { this.currentLatitude = currentLatitude; }

    public Set<Driver> getDrivers() { return drivers; }
    public void setDrivers(Set<Driver> drivers) { this.drivers = drivers; }

    public Set<Assignment> getAssignments() { return assignments; }
    public void setAssignments(Set<Assignment> assignments) { this.assignments = assignments; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    public Long getLoadingWaitTime() { return loadingWaitTime; }
    public void setLoadingWaitTime(Long loadingWaitTime) { this.loadingWaitTime = loadingWaitTime; }

    public Long getEmptyDrivingTime() { return emptyDrivingTime; }
    public void setEmptyDrivingTime(Long emptyDrivingTime) { this.emptyDrivingTime = emptyDrivingTime; }

    public Double getEmptyDrivingDistance() { return emptyDrivingDistance; }
    public void setEmptyDrivingDistance(Double emptyDrivingDistance) { this.emptyDrivingDistance = emptyDrivingDistance; }

    public Long getTotalDrivingTime() { return totalDrivingTime; }
    public void setTotalDrivingTime(Long totalDrivingTime) { this.totalDrivingTime = totalDrivingTime; }

    public Double getTotalDrivingDistance() { return totalDrivingDistance; }
    public void setTotalDrivingDistance(Double totalDrivingDistance) { this.totalDrivingDistance = totalDrivingDistance; }

    public Double getCargoVolume() { return cargoVolume; }
    public void setCargoVolume(Double cargoVolume) { this.cargoVolume = cargoVolume; }

    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }

    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public Double getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(Double currentLoad) { this.currentLoad = currentLoad; }

    public String getSuitableGoods() { return suitableGoods; }
    public void setSuitableGoods(String suitableGoods) { this.suitableGoods = suitableGoods; }

    public Double getCurrentVolumn() { return currentVolume; }
    public void setCurrentVolumn(Double currentVolumn) { this.currentVolume = currentVolume; }

    public Duration getStatusDuration() {
        return statusDurationSeconds == null ? null : Duration.ofSeconds(statusDurationSeconds);
    }

    public void setStatusDuration(Duration statusDuration) {
        this.statusDurationSeconds = statusDuration == null ? null : statusDuration.getSeconds();
    }

    public LocalDateTime getStatusEndTime() {
        if (statusStartTime == null || statusDurationSeconds == null) {
            return null;
        }
        return statusStartTime.plusSeconds(statusDurationSeconds);
    }

    public void setStatusEndTime(LocalDateTime statusEndTime) {
        if (statusStartTime == null || statusEndTime == null) {
            this.statusDurationSeconds = null;
            return;
        }
        this.statusDurationSeconds = Duration.between(statusStartTime, statusEndTime).getSeconds();
    }


}
