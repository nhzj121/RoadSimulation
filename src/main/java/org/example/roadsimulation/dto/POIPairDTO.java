// POIPairDTO.java
package org.example.roadsimulation.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class POIPairDTO {
    // getters and setters
    @Setter @Getter
    private Long startPOIId;
    @Setter @Getter
    private String startPOIName;
    @Setter @Getter
    private BigDecimal startLng;
    @Setter @Getter
    private BigDecimal startLat;
    @Setter @Getter
    private String startPOIType;

    @Setter @Getter
    private Long endPOIId;
    @Setter @Getter
    private String endPOIName;
    @Setter @Getter
    private BigDecimal endLng;
    @Setter @Getter
    private BigDecimal endLat;
    @Setter @Getter
    private String endPOIType;

    @Setter @Getter
    private String goodsName;
    @Setter @Getter
    private Integer quantity;

    @Setter @Getter
    private String pairId; // 唯一标识符，由startId_endId组成
    @Setter @Getter
    private LocalDateTime createdAt; // 配对创建时间
    @Setter @Getter
    private String status; // ACTIVE, COMPLETED, CANCELLED
    @Setter @Getter
    private LocalDateTime lastUpdated; // 最后更新时间

    // 扩展：Assignment信息
    @Setter @Getter
    private Long assignmentId;

    @Setter @Getter
    private String assignmentStatus;

    @Setter @Getter
    private Integer assignmentCurrentActionIndex;

    @Setter @Getter
    private LocalDateTime assignmentCreatedTime;

    @Setter @Getter
    private LocalDateTime assignmentStartTime;

    @Setter @Getter
    private LocalDateTime assignmentEndTime;

    // 扩展：Shipment信息
    @Setter @Getter
    private Long shipmentId;

    @Setter @Getter
    private String shipmentRefNo;

    @Setter @Getter
    private Double shipmentTotalWeight;

    @Setter @Getter
    private Double shipmentTotalVolume;

    @Setter @Getter
    private String shipmentStatus;

    // 扩展：Vehicle信息
    @Setter @Getter
    private Long vehicleId;

    @Setter @Getter
    private String vehicleLicensePlate;

    @Setter @Getter
    private String vehicleBrand;

    @Setter @Getter
    private String vehicleModelType;

    @Setter @Getter
    private Double vehicleCurrentLoad; // 当前载货量

    @Setter @Getter
    private Double vehicleMaxLoadCapacity; // 最大载重量

    @Setter @Getter
    private String vehicleStatus; // 车辆当前状态

    @Setter @Getter
    private BigDecimal vehicleLongitude; // 车辆当前位置

    @Setter @Getter
    private BigDecimal vehicleLatitude; // 车辆当前位置

    // 扩展：ShipmentItem信息
    @Setter @Getter
    private Long shipmentItemId;

    @Setter @Getter
    private String shipmentItemName;

    @Setter @Getter
    private Integer shipmentItemQuantity;

    @Setter @Getter
    private String shipmentItemSku;

    @Setter @Getter
    private Double shipmentItemTotalWeight;

    @Setter @Getter
    private Double shipmentItemTotalVolume;

}