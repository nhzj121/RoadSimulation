package org.example.roadsimulation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "route")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_poi_id")
    private Long startPoiId; // 起点POI ID

    @Column(name = "end_poi_id")
    private Long endPoiId; // 终点POI ID

    @Column(name = "distance")
    private Double distance; // 距离（公里）

    @Column(name = "estimated_time")
    private Integer estimatedTime; // 预计时间（分钟）

    // 必须的无参构造函数
    public Route() {
    }

    // Getter 和 Setter 方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStartPoiId() {
        return startPoiId;
    }

    public void setStartPoiId(Long startPoiId) {
        this.startPoiId = startPoiId;
    }

    public Long getEndPoiId() {
        return endPoiId;
    }

    public void setEndPoiId(Long endPoiId) {
        this.endPoiId = endPoiId;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Integer getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(Integer estimatedTime) {
        this.estimatedTime = estimatedTime;
    }
}