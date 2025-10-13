package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.entity.POI;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "vehicle")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 20, message = "车牌号长度不能超过20个字符")
    @Column(name = "license_plate", nullable = false, unique = true)
    private String licensePlate;

    @Min(value = 0, message = "载重量不能为负数")
    @Column(name = "max_load_capacity", precision = 10)
    private Double maxLoadCapacity;

    // 对其他数值字段也可以添加类似的验证
    @Min(value = 0, message = "容积不能为负数")
    @Column(name = "cargo_volume")
    private Double cargoVolume;

    @Column(name = "brand")
    private String brand; // 品牌名称，如：东风、解放

    @Column(name = "model_type")
    private String modelType; // 具体车型

    @Column(name = "vehicle_type")
    private String vehicleType; // 平板车，高护栏，全封闭等
    // 在 Vehicle 实体类中添加这个字段（不要重复添加 vehicleType）
    @Column(name = "driver_name")
    private String driverName; // 当前司机姓名

    // 添加对应的getter和setter方法
    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }
    @Column(name = "current_load")
    private Double currentLoad; // 载重能力（吨）

    @Min(value = 0, message = "长度不能为负数")
    @Column(name = "length")
    private Double length;

    @Min(value = 0, message = "宽度不能为负数")
    @Column(name = "width")
    private Double width;

    @Min(value = 0, message = "高度不能为负数")
    @Column(name = "height")
    private Double height;

    @Column(name = "modbus_slave_id")
    private Integer modbusSlaveId; // Modbus从站ID

    @Column(name = "last_position_update")
    private LocalDateTime lastPositionUpdate;

    @Column(name = "is_online")
    private Boolean isOnline = false;

    // @ManyToOne: 多对一关系，多辆车可以关联到一个POI或一个Action
    // fetch = FetchType.LAZY: 懒加载，只有在访问关联对象时才从数据库加载
    // @JoinColumn: 指定外键列名，即从表的外键字段名
    /*
    * 主表：被关联的一方（这里是 POI 表），它有自己的主键（比如 poi_id），是 “一” 的那一方（一个 POI 可以对应多个订单）；
    * 从表：主动关联的一方（比如 Order 订单表，即当前实体对应的表），它需要通过 “外键” 关联主表的主键，是 “多” 的那一方（多个订单可能对应同一个 POI）。
    * */
    // 多辆车可以位于同一个POI
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_poi_id")
    private POI currentPOI;

    // 多对多关系 - 车辆可以被多个司机驾驶
    @ManyToMany(mappedBy = "vehicles") // 由Driver实体维护关系
    private Set<Driver> drivers = new HashSet<>();

    // 一对多关系：一辆车可以有多个任务分配
    @OneToMany(mappedBy = "assignedVehicle", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Assignment> assignments = new HashSet<>();

    // 当前所在的经度
    @Column(name = "current_longitude")
    private Double currentLongitude;
    // 当前所在的纬度
    @Column(name = "current_latitude")
    private Double currentLatitude;

    public enum VehicleStatus {
        IDLE,           // 空闲
        TRANSPORTING,   // 运输中
        UNLOADING,      // 卸货
        MAINTAINING,    // 保养
        REFUELING,      // 加油
        RESTING,        // 休息
        ACCIDENT        // 事故
    }

    // 在实体类中使用
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 20)
    private VehicleStatus currentStatus;

    public Vehicle(){

    }

    // 内容有参构造
    public Vehicle(String licensePlate, String brand, String modelType, Double maxLoadCapacity) {
        this.licensePlate = licensePlate;
        this.brand = brand;
        this.modelType = modelType;
        this.maxLoadCapacity = maxLoadCapacity;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getLicensePlate() {
        return licensePlate;
    }
    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }
    public Double getMaxLoadCapacity() {return maxLoadCapacity;}
    public void setMaxLoadCapacity(Double maxLoadCapacity) {this.maxLoadCapacity = maxLoadCapacity;}
    public String getBrand() {return brand;}
    public void setBrand(String brand) {this.brand = brand;}
    public String getModelType() {
        return modelType;
    }
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
    public String getVehicleType() {
        return vehicleType;
    }
    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Double getCurrentLoad() {
        return currentLoad;
    }
    public void setCurrentLoad(Double currentLoad) {
        this.currentLoad = currentLoad;
    }
    public Double getCargoVolume() {return cargoVolume;}
    public void setCargoVolume(Double cargoVolume) {this.cargoVolume = cargoVolume;}
    public Double getLength() {return length;}
    public void setLength(Double length) {this.length = length;}
    public Double getWidth() {return width;}
    public void setWidth(Double width) {this.width = width;}
    public Double getHeight() {return height;}
    public void setHeight(Double height) {this.height = height;}
    public Double getCurrentLongitude() {return currentLongitude;}
    public void setCurrentLongitude(Double currentLongitude) {this.currentLongitude = currentLongitude;}
    public Double getCurrentLatitude() {return currentLatitude;}
    public void setCurrentLatitude(Double currentLatitude) {this.currentLatitude = currentLatitude;}
    public VehicleStatus getCurrentStatus() {return currentStatus;}
    public void setCurrentStatus(VehicleStatus currentStatus) {this.currentStatus = currentStatus;}

    public Integer getModbusSlaveId() { return modbusSlaveId; }
    public void setModbusSlaveId(Integer modbusSlaveId) { this.modbusSlaveId = modbusSlaveId; }
    public LocalDateTime getLastPositionUpdate() { return lastPositionUpdate; }
    public void setLastPositionUpdate(LocalDateTime lastPositionUpdate) { this.lastPositionUpdate = lastPositionUpdate; }
    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }

    /// 车辆与驾驶员关系的方法
    // 添加getter和setter
    public Set<Driver> getDrivers() {
        return drivers;
    }
    // 添加司机到车辆
    public void addDriver(Driver driver) {
        this.drivers.add(driver);
        driver.getVehicles().add(this);
    }

    // 从车辆移除司机
    public void removeDriver(Driver driver) {
        this.drivers.remove(driver);
        driver.getVehicles().remove(this);
    }
    public void setDrivers(Set<Driver> drivers) {
        // 先清除现有关系
        for (Driver driver : new HashSet<>(this.drivers)) {
            this.removeDriver(driver);
        }

        // 添加新关系
        for (Driver driver : drivers) {
            this.addDriver(driver);
        }
    }

    /// 车辆与关键点的关系点的方法
    public POI getCurrentPOI() {
        return currentPOI;
    }

    // 核心方法：设置车辆当前所在的POI，并维护双向关系
    public void setCurrentPOI(POI currentPOI) {
        // 如果当前POI与要设置的POI相同，则不执行任何操作
        if (this.currentPOI == currentPOI) {
            return;
        }

        // 如果当前已有POI，先从该POI的集合中移除自己
        POI oldPOI = this.currentPOI;
        if (oldPOI != null) {
            oldPOI.internalRemoveVehicle(this);
        }

        // 设置新的POI
        this.currentPOI = currentPOI;

        // 如果新POI不为空，将自己添加到新POI的集合中
        if (currentPOI != null) {
            currentPOI.internalAddVehicle(this);
        }

    }


    ///  Vehicle和Assignment之间关系的维护代码
    // 获取车辆的所有任务分配
    public Set<Assignment> getAssignments() {
        return assignments;
    }

    // 设置车辆的任务分配
    public void setAssignments(Set<Assignment> assignments) {
        this.assignments = assignments;
    }

    // 添加任务分配到车辆
    public void addAssignment(Assignment assignment) {
        this.assignments.add(assignment);
        assignment.setAssignedVehicle(this);
    }

    // 从车辆移除任务分配
    public void removeAssignment(Assignment assignment) {
        this.assignments.remove(assignment);
        assignment.setAssignedVehicle(null);
    }

    // 获取当前进行中的任务
    public Assignment getCurrentAssignment() {
        return assignments.stream()
                .filter(Assignment::isInProgress)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", licensePlate='" + licensePlate + '\'' +
                ", modelType='" + modelType + '\'' +
                ", currentStatus=" + currentStatus +
                ", currentPOI=" + (currentPOI != null && currentPOI.getId() != null ? currentPOI.getId() : "null") +
                '}';
    }
}
