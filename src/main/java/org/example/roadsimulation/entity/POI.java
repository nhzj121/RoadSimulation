package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "POI")
public class POI {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // POI名称，如："成都仓库", "重庆配送中心"

    @Column(name = "longitude", precision = 9)
    private Double longitude; // 经度，保留6位小数

    @Column(name = "latitude", precision = 9)
    private Double latitude; // 纬度，保留6位小数

    // 枚举类型定义
    public enum POIType {
        WAREHOUSE,              // 仓库
        DISTRIBUTION_CENTER,    // 配送中心
        FACTORY,                // 工厂
        GAS_STATION,            // 加油站
        MAINTENANCE_CENTER,     // 维修中心
        REST_AREA               // 休息区
    }

    // 在实体类中使用
    @Enumerated(EnumType.STRING)
    @Column(name = "POI_type", length = 20)
    private POI.POIType POItype;

    @OneToMany(mappedBy = "currentPOI", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Vehicle> vehiclesAtLocation = new HashSet<>();

    // 必须的无参构造函数
    public POI() {
    }

    // 带参数的构造函数
    public POI(String name, Double longitude, Double latitude, POI.POIType type) {
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.POItype = type;
    }

    // Getter 和 Setter 方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public POI.POIType getType() {
        return POItype;
    }

    public void setType(POI.POIType type) {
        this.POItype = type;
    }

    public Set<Vehicle> getVehiclesAtLocation() {
        return vehiclesAtLocation;
    }

    // 辅助方法：添加车辆到此POI
    public void addVehicleAtLocation(Vehicle vehicle) {
        // 调用Vehicle的方法来设置关系，确保双向一致性
        vehicle.setCurrentPOI(this);
    }

    // 辅助方法：从此POI移除车辆
    public void removeVehicleAtLocation(Vehicle vehicle) {
        // 调用Vehicle的方法来移除关系，确保双向一致性
        vehicle.setCurrentPOI(null);
    }

    // 内部方法：仅供Vehicle实体调用，用于维护双向关系
    protected void internalAddVehicle(Vehicle vehicle) {
        this.vehiclesAtLocation.add(vehicle);
    }

    // 内部方法：仅供Vehicle实体调用，用于维护双向关系
    protected void internalRemoveVehicle(Vehicle vehicle) {
        this.vehiclesAtLocation.remove(vehicle);
    }

    @Override
    public String toString() {
        return "POI{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                ", type='" + POItype + '\'' +
                '}';
    }
}