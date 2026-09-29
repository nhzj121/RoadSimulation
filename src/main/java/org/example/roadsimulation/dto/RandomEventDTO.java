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
    private String breakdownLevel;
    private String breakdownPhase;
    private Integer rescueWaitMinutes;
    private Integer repairMinutes;
    private LocalDateTime repairStartTime;
    private LocalDateTime recoveryProcessedTime;
    private String recoveryOutcome;
    private String breakdownRuleVersion;
    private Integer replacementWaitMinutes;
    private Long replacementVehicleId;
    private Long originalDriverId;
    private Long replacementDriverId;
    private String replacementLicensePlate;
    private LocalDateTime replacementSelectedTime;
    private LocalDateTime replacementReadyTime;
    private LocalDateTime replacementProcessedTime;
    private String replacementOutcome;
    private Double requiredLoad;
    private Double requiredVolume;

    public static RandomEventDTO from(TransportRandomEvent event) {
        RandomEventDTO dto = new RandomEventDTO();
        dto.setEventId(event.getId());
        dto.setEventType(event.getEventType() == null ? null : event.getEventType().name());
        dto.setEventTypeText(eventTypeText(event.getEventType()));
        dto.setStatus(event.getStatus() == null ? null : event.getStatus().name());
        dto.setTriggerSource(event.getTriggerSource() == null ? null : event.getTriggerSource().name());
        dto.setVehicleId(event.getVehicleId());
        dto.setLicensePlate(event.getLicensePlate());
        dto.setAssignmentId(event.getAssignmentId());
        dto.setStartTime(event.getStartTime());
        dto.setPlannedEndTime(event.getPlannedEndTime());
        dto.setResolvedTime(event.getResolvedTime());
        dto.setSpeedFactor(event.getSpeedFactor());
        dto.setDelaySeconds(event.getDelaySeconds());
        dto.setDescription(event.getDescription());
        dto.setBreakdownLevel(event.getBreakdownLevel()==null?null:event.getBreakdownLevel().name());
        dto.setBreakdownPhase(event.getBreakdownPhase()==null?null:event.getBreakdownPhase().name());
        dto.setRescueWaitMinutes(event.getRescueWaitMinutes());
        dto.setRepairMinutes(event.getRepairMinutes());
        dto.setRepairStartTime(event.getRepairStartTime());
        dto.setRecoveryProcessedTime(event.getRecoveryProcessedTime());
        dto.setRecoveryOutcome(event.getRecoveryOutcome());
        dto.setBreakdownRuleVersion(event.getBreakdownRuleVersion());
        dto.setReplacementWaitMinutes(event.getReplacementWaitMinutes());
        dto.setReplacementVehicleId(event.getReplacementVehicleId());
        dto.setOriginalDriverId(event.getOriginalDriverId());dto.setReplacementDriverId(event.getReplacementDriverId());
        dto.setReplacementLicensePlate(event.getReplacementLicensePlate());
        dto.setReplacementSelectedTime(event.getReplacementSelectedTime());
        dto.setReplacementReadyTime(event.getReplacementReadyTime());
        dto.setReplacementProcessedTime(event.getReplacementProcessedTime());
        dto.setReplacementOutcome(event.getReplacementOutcome());
        dto.setRequiredLoad(event.getRequiredLoad());
        dto.setRequiredVolume(event.getRequiredVolume());
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
