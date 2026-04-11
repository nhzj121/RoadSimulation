package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
public class ShipmentItemDTO {
    @Setter
    @Getter
    private Long id;
    @Setter @Getter
    private String name;
    @Setter @Getter
    private String sku;
    @Setter @Getter
    private Integer qty;
    @Setter @Getter
    private Double weight;
    @Setter @Getter
    private Double volume;

    // 关联信息
    @Setter @Getter
    private Long shipmentId;
    @Setter @Getter
    private String shipmentRefNo;
    @Setter @Getter
    private Long goodsId;
    @Setter @Getter
    private String goodsName;
    @Setter @Getter
    private Long assignmentId;

    // 位置信息
    @Setter @Getter
    private Long originPOIId;
    @Setter @Getter
    private String originPOIName;
    @Setter @Getter
    private Long destPOIId;
    @Setter @Getter
    private String destPOIName;

    // 状态信息
    @Setter @Getter
    private String status; // LOADED, IN_TRANSIT, UNLOADED, etc.
    @Setter @Getter
    private LocalDateTime loadedTime;
    @Setter @Getter
    private LocalDateTime unloadedTime;

    // 元数据
    @Setter @Getter
    private LocalDateTime createdTime;
    @Setter @Getter
    private LocalDateTime updatedTime;
}