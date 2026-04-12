package org.example.roadsimulation.dto;

/**
 * 创建加工订单请求 DTO
 */
public class CreateProcessingOrderRequest {
    
    private Double inputWeight;
    private String createdBy;
    
    public CreateProcessingOrderRequest() {}
    
    public CreateProcessingOrderRequest(Double inputWeight, String createdBy) {
        this.inputWeight = inputWeight;
        this.createdBy = createdBy;
    }
    
    public Double getInputWeight() { return inputWeight; }
    public void setInputWeight(Double inputWeight) { this.inputWeight = inputWeight; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
