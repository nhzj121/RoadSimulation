package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomEventDTOTest {

    @Test
    void exposesStableVehicleAssignmentAndDisplayFields() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setLicensePlate("川A-0012");
        Assignment assignment = new Assignment();
        assignment.setId(88L);
        TransportRandomEvent event = new TransportRandomEvent();
        event.setId(7L);
        event.setEventType(TransportRandomEvent.EventType.TRAFFIC_CONGESTION);
        event.setStatus(TransportRandomEvent.EventStatus.ACTIVE);
        event.setTriggerSource(TransportRandomEvent.TriggerSource.MANUAL);
        event.setVehicleId(vehicle.getId());
        event.setLicensePlate(vehicle.getLicensePlate());
        event.setAssignmentId(assignment.getId());
        event.setStartTime(LocalDateTime.of(2026, 1, 1, 8, 0));
        event.setPlannedEndTime(LocalDateTime.of(2026, 1, 1, 9, 0));
        event.setSpeedFactor(0.4);
        event.setDelaySeconds(0L);
        event.setDescription("交通拥堵，车辆减速行驶");

        RandomEventDTO dto = RandomEventDTO.from(event);

        assertEquals(7L, dto.getEventId());
        assertEquals("TRAFFIC_CONGESTION", dto.getEventType());
        assertEquals("交通拥堵", dto.getEventTypeText());
        assertEquals(12L, dto.getVehicleId());
        assertEquals(88L, dto.getAssignmentId());
        assertEquals(0.4, dto.getSpeedFactor(), 1e-9);
    }
}
