package org.example.roadsimulation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "POI")
public class POI {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // POI名称，如："成都仓库", "重庆配送中心"

    @Column(name = "longitude")
    private Double longitude; // 经度

    @Column(name = "latitude")
    private Double latitude; // 纬度

    @Column(name = "type")
    private String type; // 类型，如："仓库", "配送中心", "加油站"



    // 必须的无参构造函数
    public POI() {
    }

    // 带参数的构造函数
    public POI(String name, Double longitude, Double latitude, String type) {
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.type = type;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "POI{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                ", type='" + type + '\'' +
                '}';
    }
}