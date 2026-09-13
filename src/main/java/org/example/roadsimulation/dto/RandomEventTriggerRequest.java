package org.example.roadsimulation.dto;

import lombok.Data;
import org.example.roadsimulation.entity.TransportRandomEvent;

@Data
public class RandomEventTriggerRequest {
    private TransportRandomEvent.EventType eventType;
    private Long vehicleId;
    private Integer durationMinutes;
    private TransportRandomEvent.BreakdownLevel breakdownLevel;
    private Integer rescueWaitMinutes;
    private Integer repairMinutes;
}
