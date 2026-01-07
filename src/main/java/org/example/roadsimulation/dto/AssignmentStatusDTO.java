package org.example.roadsimulation.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Assignment 状态跟踪DTO
 */
@Data
public class AssignmentStatusDTO {
    @Setter
    @Getter
    private Long assignmentId;
    @Setter @Getter
    private String pairId;  // 兼容之前的pairId
    @Setter @Getter
    private Long vehicleId;
    @Setter @Getter
    private LocalDateTime createdAt;
    @Setter @Getter
    private LocalDateTime lastUpdated;
    @Setter @Getter
    private boolean isActive;
    @Setter @Getter
    private boolean isDrawn;  // 是否已被前端绘制
    @Setter @Getter
    private Long shipmentId;

    public AssignmentStatusDTO() {}

    public AssignmentStatusDTO(Long assignmentId, String pairId, Long vehicleId, Long shipmentId) {
        this.assignmentId = assignmentId;
        this.pairId = pairId;
        this.vehicleId = vehicleId;
        this.shipmentId = shipmentId;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = this.createdAt;
        this.isActive = true;
        this.isDrawn = false;
    }
}