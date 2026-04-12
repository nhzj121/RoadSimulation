package org.example.roadsimulation.dto;

import java.time.LocalDateTime;

public class ShipmentItemDTO {
    private Long id;
    private String name;
    private String sku;
    private Integer qty;
    private Double weight;
    private Double volume;

    // 关联信息
    private Long shipmentId;
    private String shipmentRefNo;
    private Long goodsId;
    private String goodsName;
    private Long assignmentId;

    // 位置信息
    private Long originPOIId;
    private String originPOIName;
    private Long destPOIId;
    private String destPOIName;

    // 状态信息
    private String status;
    private LocalDateTime loadedTime;
    private LocalDateTime unloadedTime;

    // 元数据
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }

    public String getShipmentRefNo() { return shipmentRefNo; }
    public void setShipmentRefNo(String shipmentRefNo) { this.shipmentRefNo = shipmentRefNo; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }

    public Long getOriginPOIId() { return originPOIId; }
    public void setOriginPOIId(Long originPOIId) { this.originPOIId = originPOIId; }

    public String getOriginPOIName() { return originPOIName; }
    public void setOriginPOIName(String originPOIName) { this.originPOIName = originPOIName; }

    public Long getDestPOIId() { return destPOIId; }
    public void setDestPOIId(Long destPOIId) { this.destPOIId = destPOIId; }

    public String getDestPOIName() { return destPOIName; }
    public void setDestPOIName(String destPOIName) { this.destPOIName = destPOIName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLoadedTime() { return loadedTime; }
    public void setLoadedTime(LocalDateTime loadedTime) { this.loadedTime = loadedTime; }

    public LocalDateTime getUnloadedTime() { return unloadedTime; }
    public void setUnloadedTime(LocalDateTime unloadedTime) { this.unloadedTime = unloadedTime; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
