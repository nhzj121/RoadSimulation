package org.example.roadsimulation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchComparisonOptionsDTO {
    private List<VehicleOption> vehicles = new ArrayList<>();
    private List<PoiOption> pois = new ArrayList<>();
    private Integer vehicleCount;
    private Integer candidateInitialPoiCount;
    private String placementPolicy;

    @Data
    public static class VehicleOption {
        private Long vehicleId;
        private String licensePlate;
        private String currentStatus;
        private Long currentPoiId;
        private String currentPoiName;
    }

    @Data
    public static class PoiOption {
        private Long poiId;
        private String poiName;
        private String poiType;
        private BigDecimal longitude;
        private BigDecimal latitude;
    }
}
