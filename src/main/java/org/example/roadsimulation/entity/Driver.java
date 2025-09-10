package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "Driver")
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_phone")
    private String driverPhone;

    public enum DriverStatus {
        IDLE,       // 空闲
        ASSIGNED,   // 任务中
        OFF         // 下线
    }

    // 在实体类中使用
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 20)
    private Driver.DriverStatus currentStatus;

    @ManyToMany // 1. 声明多对多关系
    @JoinTable( // 2. 定义连接表（中间表）
            name = "driver_vehicle", // 3. 指定中间表的表名为 'driver_vehicle'
            joinColumns = @JoinColumn(name = "driver_id"), // 4. 指定本实体（Driver）在中间表中的外键列名
            inverseJoinColumns = @JoinColumn(name = "vehicle_id") // 5. 指定关联实体（Vehicle）在中间表中的外键列名
    )
    private Set<Vehicle> vehicles = new HashSet<>(); // 6. 存储关联Vehicle对象的集合

    // Getter 和 Setter 方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public DriverStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(DriverStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Set<Vehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(Set<Vehicle> vehicles) {
        this.vehicles = vehicles;
    }

    // 添加和移除车辆的辅助方法
    public void addVehicle(Vehicle vehicle) {
        this.vehicles.add(vehicle);
        vehicle.getDrivers().add(this);
    }

    public void removeVehicle(Vehicle vehicle) {
        this.vehicles.remove(vehicle);
        vehicle.getDrivers().remove(this);
    }

    @Override
    public String toString() {
        return "Driver{" +
                "id=" + id +
                ", driverName='" + driverName + '\'' +
                ", driverPhone='" + driverPhone + '\'' +
                ", currentStatus=" + currentStatus +
                '}';
    }
}
