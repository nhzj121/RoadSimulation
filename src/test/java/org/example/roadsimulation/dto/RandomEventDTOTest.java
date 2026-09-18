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
        event.setBreakdownLevel(TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED);
        event.setBreakdownPhase(TransportRandomEvent.BreakdownPhase.WAITING_RESCUE);
        event.setRescueWaitMinutes(30);
        event.setRepairMinutes(90);
        event.setBreakdownRuleVersion("breakdown-v2");
        event.setReplacementWaitMinutes(60);event.setReplacementVehicleId(21L);event.setReplacementLicensePlate("T");
        event.setRequiredLoad(9.0);event.setRequiredVolume(8.0);

        RandomEventDTO dto = RandomEventDTO.from(event);

        assertEquals(7L, dto.getEventId());
        assertEquals("TRAFFIC_CONGESTION", dto.getEventType());
        assertEquals("交通拥堵", dto.getEventTypeText());
        assertEquals(12L, dto.getVehicleId());
        assertEquals(88L, dto.getAssignmentId());
        assertEquals(0.4, dto.getSpeedFactor(), 1e-9);
        assertEquals("ASSISTANCE_REQUIRED", dto.getBreakdownLevel());
        assertEquals("WAITING_RESCUE", dto.getBreakdownPhase());
        assertEquals(30, dto.getRescueWaitMinutes());
        assertEquals(90, dto.getRepairMinutes());
        assertEquals("breakdown-v2", dto.getBreakdownRuleVersion());
        assertEquals(60,dto.getReplacementWaitMinutes());assertEquals(21L,dto.getReplacementVehicleId());
        assertEquals("T",dto.getReplacementLicensePlate());assertEquals(9.0,dto.getRequiredLoad());assertEquals(8.0,dto.getRequiredVolume());
    }
}
