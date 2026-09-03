package org.example.roadsimulation.dto;

import lombok.Data;
import org.example.roadsimulation.entity.TransportRandomEvent;

import java.time.LocalDateTime;

@Data
public class RandomEventDTO {
    private Long eventId;
    private String eventType;
    private String eventTypeText;
    private String status;
    private String triggerSource;
    private Long vehicleId;
    private String licensePlate;
    private Long assignmentId;
    private LocalDateTime startTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime resolvedTime;
    private Double speedFactor;
    private Long delaySeconds;
    private String description;

    public static RandomEventDTO from(TransportRandomEvent event) {
        RandomEventDTO dto = new RandomEventDTO();
        dto.setEventId(event.getId());
        dto.setEventType(event.getEventType() == null ? null : event.getEventType().name());
        dto.setEventTypeText(eventTypeText(event.getEventType()));
        dto.setStatus(event.getStatus() == null ? null : event.getStatus().name());
        dto.setTriggerSource(event.getTriggerSource() == null ? null : event.getTriggerSource().name());
        if (event.getVehicle() != null) {
            dto.setVehicleId(event.getVehicle().getId());
            dto.setLicensePlate(event.getVehicle().getLicensePlate());
        }
        if (event.getAssignment() != null) {
            dto.setAssignmentId(event.getAssignment().getId());
        }
        dto.setStartTime(event.getStartTime());
        dto.setPlannedEndTime(event.getPlannedEndTime());
        dto.setResolvedTime(event.getResolvedTime());
        dto.setSpeedFactor(event.getSpeedFactor());
        dto.setDelaySeconds(event.getDelaySeconds());
        dto.setDescription(event.getDescription());
        return dto;
    }

    private static String eventTypeText(TransportRandomEvent.EventType type) {
        if (type == TransportRandomEvent.EventType.TRAFFIC_CONGESTION) {
            return "交通拥堵";
        }
        if (type == TransportRandomEvent.EventType.VEHICLE_BREAKDOWN) {
            return "车辆故障";
        }
        return "未知事件";
    }
}
