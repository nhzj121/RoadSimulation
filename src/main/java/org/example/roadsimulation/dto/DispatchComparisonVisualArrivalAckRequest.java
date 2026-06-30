package org.example.roadsimulation.dto;

public class DispatchComparisonVisualArrivalAckRequest {

    private Long assignmentId;
    private Long vehicleId;

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }
}
