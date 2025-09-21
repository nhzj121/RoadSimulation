package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.Assignment;

import java.time.LocalDateTime;
import java.util.Map;

public class GoodsTransportStats {
    private int totalShipments;
    private double totalWeight;
    private double totalVolume;
    private LocalDateTime lastTransportDate;
    private Map<Assignment.AssignmentStatus, Long> statusCount;

    // 构造函数
    public GoodsTransportStats() {}

    // Getter和Setter方法
    public int getTotalShipments() {
        return totalShipments;
    }

    public void setTotalShipments(int totalShipments) {
        this.totalShipments = totalShipments;
    }

    public double getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(double totalWeight) {
        this.totalWeight = totalWeight;
    }

    public double getTotalVolume() {
        return totalVolume;
    }

    public void setTotalVolume(double totalVolume) {
        this.totalVolume = totalVolume;
    }

    public LocalDateTime getLastTransportDate() {
        return lastTransportDate;
    }

    public void setLastTransportDate(LocalDateTime lastTransportDate) {
        this.lastTransportDate = lastTransportDate;
    }

    public Map<Assignment.AssignmentStatus, Long> getStatusCount() {
        return statusCount;
    }

    public void setStatusCount(Map<Assignment.AssignmentStatus, Long> statusCount) {
        this.statusCount = statusCount;
    }

    @Override
    public String toString() {
        return "GoodsTransportStats{" +
                "totalShipments=" + totalShipments +
                ", totalWeight=" + totalWeight +
                ", totalVolume=" + totalVolume +
                ", lastTransportDate=" + lastTransportDate +
                ", statusCount=" + statusCount +
                '}';
    }
}