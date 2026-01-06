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
import java.util.Set;

/**
 * 车辆实体类 - 最终优化完整版
 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 25, message = "车牌号长度不能超过25个字符")
    @Column(name = "license_plate", nullable = false, unique = true, length = 25)
    private String licensePlate;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime = LocalDateTime.now();

    @Min(value = 0, message = "载重量不能为负数")
    @Column(name = "max_load_capacity", precision = 10)
    private Double maxLoadCapacity;

    @Min(value = 0, message = "容积不能为负数")
    @Column(name = "cargo_volume", precision = 10)
    private Double cargoVolume;

    @Column(name = "brand", length = 50)
    private String brand;

    @Column(name = "model_type", length = 100)
    private String modelType;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(name = "driver_name", length = 50)
    private String driverName;

    // 当前状态
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 30)
    private VehicleStatus currentStatus;

    // 新增：上一个状态（用于日志和审计）
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private VehicleStatus previousStatus;

    // 状态开始时间
    @Column(name = "status_start_time")
    private LocalDateTime statusStartTime;

    // 状态持续时间（秒）
    @Column(name = "status_duration_seconds")
    private Long statusDurationSeconds;

    @Column(name = "current_load", precision = 10)
    private Double currentLoad;

    @Min(value = 0, message = "长度不能为负数")
    @Column(name = "length", precision = 8)
    private Double length;

    @Min(value = 0, message = "宽度不能为负数")
    @Column(name = "width", precision = 8)
    private Double width;

    @Min(value = 0, message = "高度不能为负数")
    @Column(name = "height", precision = 8)
    private Double height;

    // 当前位置 POI
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_poi_id")
    private POI currentPOI;

    // 当前坐标
    @Column(name = "current_longitude", precision = 10)
    private BigDecimal currentLongitude;

    @Column(name = "current_latitude", precision = 10)
    private BigDecimal currentLatitude;

    // 与司机多对多
    @ManyToMany(mappedBy = "vehicles")
    private Set<Driver> drivers = new HashSet<>();

    // 与任务一对多
    @OneToMany(mappedBy = "assignedVehicle", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Assignment> assignments = new HashSet<>();

    // 四元组字段
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    // 自动更新 updatedTime
    @PreUpdate
    public void preUpdate() {
        this.updatedTime = LocalDateTime.now();
    }

    // ==================== 枚举 ====================
    public enum VehicleStatus {
        IDLE(0),
        ORDER_DRIVING(1),
        LOADING(2),
        TRANSPORT_DRIVING(3),
        UNLOADING(4),
        WAITING(5),
        BREAKDOWN(6);

        private final int index;
        VehicleStatus(int index) { this.index = index; }
        public int getIndex() { return index; }
    }

    // ==================== 构造器 ====================
    public Vehicle() {}

    public Vehicle(String licensePlate, String brand, String modelType, Double maxLoadCapacity) {
        this.licensePlate = licensePlate;
        this.brand = brand;
        this.modelType = modelType;
        this.maxLoadCapacity = maxLoadCapacity;
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

    public Double getCargoVolume() { return cargoVolume; }
    public void setCargoVolume(Double cargoVolume) { this.cargoVolume = cargoVolume; }

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

    public Duration getStatusDuration() {
        return statusDurationSeconds != null ? Duration.ofSeconds(statusDurationSeconds) : Duration.ZERO;
    }
    public void setStatusDuration(Duration duration) {
        this.statusDurationSeconds = duration != null ? duration.getSeconds() : 0L;
    }
    public LocalDateTime getStatusEndTime() {
        return statusStartTime != null ? statusStartTime.plus(getStatusDuration()) : null;
    }

    public Double getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(Double currentLoad) { this.currentLoad = currentLoad; }

    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }

    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public POI getCurrentPOI() { return currentPOI; }
    public void setCurrentPOI(POI currentPOI) {
        if (this.currentPOI == currentPOI) return;
        POI oldPOI = this.currentPOI;
        if (oldPOI != null) oldPOI.internalRemoveVehicle(this);
        this.currentPOI = currentPOI;
        if (currentPOI != null) currentPOI.internalAddVehicle(this);
    }

    public BigDecimal getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(BigDecimal currentLongitude) { this.currentLongitude = currentLongitude; }

    public BigDecimal getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(BigDecimal currentLatitude) { this.currentLatitude = currentLatitude; }

    public Set<Driver> getDrivers() { return drivers; }
    public void setDrivers(Set<Driver> drivers) {
        for (Driver d : new HashSet<>(this.drivers)) removeDriver(d);
        for (Driver d : drivers) addDriver(d);
    }
    public void addDriver(Driver driver) {
        this.drivers.add(driver);
        driver.getVehicles().add(this);
    }
    public void removeDriver(Driver driver) {
        this.drivers.remove(driver);
        driver.getVehicles().remove(this);
    }

    public Set<Assignment> getAssignments() { return assignments; }
    public void setAssignments(Set<Assignment> assignments) { this.assignments = assignments; }
    public void addAssignment(Assignment assignment) {
        this.assignments.add(assignment);
        assignment.setAssignedVehicle(this);
    }
    public void removeAssignment(Assignment assignment) {
        this.assignments.remove(assignment);
        assignment.setAssignedVehicle(null);
    }

    // 关键：获取当前活跃任务（兼容 ASSIGNED 和 IN_PROGRESS）
    public Assignment getCurrentAssignment() {
        return assignments.stream()
                .filter(a -> a.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS ||
                        a.getStatus() == Assignment.AssignmentStatus.ASSIGNED)
                .findFirst()
                .orElse(null);
    }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", licensePlate='" + licensePlate + '\'' +
                ", modelType='" + modelType + '\'' +
                ", currentStatus=" + currentStatus +
                ", previousStatus=" + previousStatus +
                ", statusStartTime=" + statusStartTime +
                ", statusDuration=" + getStatusDuration() +
                ", currentPOI=" + (currentPOI != null && currentPOI.getId() != null ? currentPOI.getId() : "null") +
                '}';
    }
}