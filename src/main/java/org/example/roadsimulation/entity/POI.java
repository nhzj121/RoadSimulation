package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * POI（Point of Interest）实体类
 *
 * 功能：
 * 1. 表示系统中的关键点，如仓库、配送中心、工厂等
 * 2. 与 Vehicle 维护一对多双向关系（一个 POI 可包含多辆车）
 * 3. 支持基本属性：名称、经纬度、类型
 */
@Entity
@Table(name = "POI")
public class POI {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // POI 唯一主键

    @Column(nullable = false)
    private String name; // POI 名称

    @Column(
        precision = 9,    // 总位数：3位整数 + 6位小数 = 9位
        scale = 6,         // 小数位：8位（满足绝大多数场景，0.01米精度）
        nullable = false,  // 经度为必填字段，禁止 null
        columnDefinition = "DECIMAL(9,6) COMMENT '经度（范围：-180~180，精度6位小数）'"
    )
    private BigDecimal longitude; // 经度

    @Column(
        precision = 9,
        scale = 6,
        nullable = false,
        columnDefinition = "DECIMAL(10,6) COMMENT '纬度（范围：-90~90，精度6位小数）'"
    )
    private BigDecimal latitude;

    /**
     * POI 类型枚举
     */
    public enum POIType {
        WAREHOUSE,              // 仓库
        DISTRIBUTION_CENTER,    // 配送中心
        FACTORY,                // 工厂
        GAS_STATION,            // 加油站
        MAINTENANCE_CENTER,     // 维修中心
        REST_AREA               // 休息区
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "poi_type", length = 20)
    private POIType poiType; // POI 类型

    /**
     * 一对多关系：一个 POI 可以包含多辆车
     */
    @OneToMany(mappedBy = "currentPOI", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Vehicle> vehiclesAtLocation = new HashSet<>();

    // ================= 构造方法 =================
    public POI() {}

    public POI(String name, BigDecimal longitude, BigDecimal latitude, POIType type) {
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.poiType = type;
    }

    // ================= Getter & Setter =================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public POIType getPoiType() { return poiType; }
    public void setPoiType(POIType poiType) { this.poiType = poiType; }

    public Set<Vehicle> getVehiclesAtLocation() { return vehiclesAtLocation; }

    // ================= Vehicle 双向关系维护 =================
    /**
     * 添加车辆到 POI
     * @param vehicle 车辆对象
     */
    public void addVehicleAtLocation(Vehicle vehicle) {
        if(vehicle != null) {
            vehicle.setCurrentPOI(this); // 调用 Vehicle 的方法保证双向关系
        }
    }

    /**
     * 从 POI 移除车辆
     * @param vehicle 车辆对象
     */
    public void removeVehicleAtLocation(Vehicle vehicle) {
        if(vehicle != null) {
            vehicle.setCurrentPOI(null); // 调用 Vehicle 的方法保证双向关系
        }
    }

    /**
     * 内部方法，仅供 Vehicle 实体调用
     */
    protected void internalAddVehicle(Vehicle vehicle) {
        this.vehiclesAtLocation.add(vehicle);
    }

    /**
     * 内部方法，仅供 Vehicle 实体调用
     */
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
                ", poiType=" + poiType +
                '}';
    }
}
