// POIPairDTO.java
package org.example.roadsimulation.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class POIPairDTO {
    // getters and setters
    @Setter
    @Getter
    private Long startPOIId;
    @Setter
    @Getter
    private String startPOIName;
    @Setter
    @Getter
    private BigDecimal startLng;
    @Setter
    @Getter
    private BigDecimal startLat;
    @Setter
    @Getter
    private String startPOIType;

    @Setter
    @Getter
    private Long endPOIId;
    @Setter
    @Getter
    private String endPOIName;
    @Setter
    @Getter
    private BigDecimal endLng;
    @Setter
    @Getter
    private BigDecimal endLat;
    @Setter
    @Getter
    private String endPOIType;

    @Setter
    @Getter
    private String goodsName;
    @Setter
    @Getter
    private Integer quantity;
    @Setter
    @Getter
    private String shipmentRefNo;

    @Setter
    @Getter
    private String pairId; // 唯一标识符，由startId_endId组成
    @Setter
    @Getter
    private LocalDateTime createdAt; // 配对创建时间
    @Setter
    @Getter
    private String status; // ACTIVE, COMPLETED, CANCELLED
    @Setter
    @Getter
    private LocalDateTime lastUpdated; // 最后更新时间

}