package org.example.roadsimulation.dto;

import java.util.List;

public class VehicleMatchingRequest {
    private Long goodsId;
    private Integer quantity;
    private Double totalWeight;
    private Double totalVolume;
    private Boolean requireTempControl;
    private String hazmatLevel;
    private List<String> specialRequirements;
    private String destination;
    private Integer urgencyLevel;
    private Double startLongitude;
    private Double startLatitude;
    private Double endLongitude;
    private Double endLatitude;

    public VehicleMatchingRequest() {}

    public VehicleMatchingRequest(Long goodsId, Integer quantity) {
        this.goodsId = goodsId;
        this.quantity = quantity;
    }

    // Getters and Setters
    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(Double totalWeight) { this.totalWeight = totalWeight; }
    public Double getTotalVolume() { return totalVolume; }
    public void setTotalVolume(Double totalVolume) { this.totalVolume = totalVolume; }
    public Boolean getRequireTempControl() { return requireTempControl; }
    public void setRequireTempControl(Boolean requireTempControl) { this.requireTempControl = requireTempControl; }
    public String getHazmatLevel() { return hazmatLevel; }
    public void setHazmatLevel(String hazmatLevel) { this.hazmatLevel = hazmatLevel; }
    public List<String> getSpecialRequirements() { return specialRequirements; }
    public void setSpecialRequirements(List<String> specialRequirements) { this.specialRequirements = specialRequirements; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(Integer urgencyLevel) { this.urgencyLevel = urgencyLevel; }
    public Double getStartLongitude() { return startLongitude; }
    public void setStartLongitude(Double startLongitude) { this.startLongitude = startLongitude; }
    public Double getStartLatitude() { return startLatitude; }
    public void setStartLatitude(Double startLatitude) { this.startLatitude = startLatitude; }
    public Double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(Double endLongitude) { this.endLongitude = endLongitude; }
    public Double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(Double endLatitude) { this.endLatitude = endLatitude; }
}