package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;

import java.time.LocalDateTime;

/**
 * POI运单记录 —— 记录一对POI之间当前活跃的运单信息。
 */
public class POIShipmentRecord {

    private final String pairKey;
    private final Long sourcePoiId;
    private final Long destPoiId;
    private final Long shipmentId;
    private final LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private boolean active;

    public POIShipmentRecord(POI source, POI dest, Shipment shipment) {
        this.pairKey = source.getId() + "_" + dest.getId();
        this.sourcePoiId = source.getId();
        this.destPoiId = dest.getId();
        this.shipmentId = shipment != null ? shipment.getId() : null;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = this.createdAt;
        this.active = true;
    }

    public String getPairKey() { return pairKey; }
    public Long getSourcePoiId() { return sourcePoiId; }
    public Long getDestPoiId() { return destPoiId; }
    public Long getShipmentId() { return shipmentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
