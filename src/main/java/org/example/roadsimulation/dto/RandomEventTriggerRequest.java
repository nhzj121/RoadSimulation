package org.example.roadsimulation.dto;

import lombok.Data;
import org.example.roadsimulation.entity.TransportRandomEvent;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Data
public class RandomEventTriggerRequest {
    private TransportRandomEvent.EventType eventType;
    private Long vehicleId;
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer durationMinutes;
    private TransportRandomEvent.BreakdownLevel breakdownLevel;
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer rescueWaitMinutes;
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer repairMinutes;
}
