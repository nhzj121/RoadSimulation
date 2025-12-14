package org.example.roadsimulation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
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

    // 创建的时间
    @Column(name = "created_time")
    private LocalDateTime createdTime;

    // 在实体类中使用
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 20)
    private Driver.DriverStatus currentStatus;
    /// 驾驶员与车辆关系的构建
    /// 多对多关系  驾驶员是关系的拥有者
    @ManyToMany // 1. 声明多对多关系，通常不需要设置级联
    @JoinTable( // 2. 定义连接表（中间表）
            name = "driver_vehicle", // 3. 指定中间表的表名为 'driver_vehicle'
            joinColumns = @JoinColumn(name = "driver_id"), // 4. 指定本实体（Driver）在中间表中的外键列名
            inverseJoinColumns = @JoinColumn(name = "vehicle_id") // 5. 指定关联实体（Vehicle）在中间表中的外键列名
    )
    private Set<Vehicle> vehicles = new HashSet<>(); // 6. 存储关联Vehicle对象的集合

    // 添加与 Assignment 的一对多关系
    @OneToMany(mappedBy = "assignedDriver", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Assignment> assignments = new HashSet<>();

    // 进行修改的对象和时间
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // Getter 和 Setter 方法
    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}
    public String getDriverName() {return driverName;}
    public void setDriverName(String driverName) {this.driverName = driverName;}
    public String getDriverPhone() {return driverPhone;}
    public void setDriverPhone(String driverPhone) {this.driverPhone = driverPhone;}
    public DriverStatus getCurrentStatus() {return currentStatus;}
    public void setCurrentStatus(DriverStatus currentStatus) {this.currentStatus = currentStatus;}
    public Set<Vehicle> getVehicles() {return vehicles;}
    public void setVehicles(Set<Vehicle> vehicles) {this.vehicles = vehicles;}

    // 四元组字段的getter和setter
    public LocalDateTime getCreatedTime() {return createdTime;}
    public void setCreatedTime(LocalDateTime createdTime) {this.createdTime = createdTime;}
    public String getUpdatedBy() {return updatedBy;}
    public void setUpdatedBy(String updatedBy) {this.updatedBy = updatedBy;}
    public LocalDateTime getUpdatedTime() {return updatedTime;}
    public void setUpdatedTime(LocalDateTime updatedTime) {this.updatedTime = updatedTime;}

    // 添加和移除车辆的辅助方法
    public void addVehicle(Vehicle vehicle) {
        this.vehicles.add(vehicle);
        vehicle.getDrivers().add(this);
    }
    public void removeVehicle(Vehicle vehicle) {
        this.vehicles.remove(vehicle);
        vehicle.getDrivers().remove(this);
    }

    // 与Assignment相关的方法
    public Set<Assignment> getAssignments() {
        return assignments;
    }
    public void setAssignments(Set<Assignment> assignments) {
        this.assignments = assignments;
    }
    // 添加任务分配到驾驶员
    public void addAssignment(Assignment assignment) {
        this.assignments.add(assignment);
        assignment.setAssignedDriver(this);
    }
    // 从驾驶员移除任务分配
    public void removeAssignment(Assignment assignment) {
        this.assignments.remove(assignment);
        assignment.setAssignedDriver(null);
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
        return "Driver{" +
                "id=" + id +
                ", driverName='" + driverName + '\'' +
                ", driverPhone='" + driverPhone + '\'' +
                ", currentStatus=" + currentStatus +
                '}';
    }
}
