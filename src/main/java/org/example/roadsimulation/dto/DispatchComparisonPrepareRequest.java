package org.example.roadsimulation.dto;

import lombok.Data;

import java.util.List;

@Data
public class DispatchComparisonPrepareRequest {
    private Integer shipmentCount;
    private List<VehicleInitialPosition> vehicleInitialPositions;

    @Data
    public static class VehicleInitialPosition {
        private Long vehicleId;
        private Long poiId;
    }
}
