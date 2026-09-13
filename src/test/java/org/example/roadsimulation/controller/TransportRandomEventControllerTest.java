package org.example.roadsimulation.controller;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.RandomEventDTO;
import org.example.roadsimulation.dto.RandomEventTriggerRequest;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.service.TransportRandomEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransportRandomEventControllerTest {

    private TransportRandomEventService eventService;
    private SimulationContext simulationContext;
    private TransportRandomEventController controller;
    private LocalDateTime simNow;

    @BeforeEach
    void setUp() {
        eventService = mock(TransportRandomEventService.class);
        simulationContext = mock(SimulationContext.class);
        controller = new TransportRandomEventController(eventService, simulationContext);
        simNow = LocalDateTime.of(2026, 1, 1, 8, 0);
        when(simulationContext.getCurrentSimTime()).thenReturn(simNow);
    }

    @Test
    void manualTriggerReturnsCreatedEvent() {
        RandomEventTriggerRequest request = new RandomEventTriggerRequest();
        request.setEventType(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN);
        request.setVehicleId(12L);
        request.setDurationMinutes(60);
        TransportRandomEvent event = activeEvent(12L);
        when(eventService.triggerManually(request.getEventType(), 12L, 60, simNow)).thenReturn(event);

        ResponseEntity<ApiResponse<RandomEventDTO>> response = controller.trigger(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(12L, response.getBody().getData().getVehicleId());
    }

    @Test
    void manualTriggerConflictReturns409() {
        RandomEventTriggerRequest request = new RandomEventTriggerRequest();
        request.setEventType(TransportRandomEvent.EventType.TRAFFIC_CONGESTION);
        request.setVehicleId(12L);
        when(eventService.triggerManually(any(), eq(12L), isNull(), eq(simNow)))
                .thenThrow(new IllegalStateException("vehicle already has an active event"));

        ResponseEntity<ApiResponse<RandomEventDTO>> response = controller.trigger(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void activeReturnsMappedEvents() {
        when(eventService.getActiveEvents()).thenReturn(List.of(activeEvent(12L)));

        ApiResponse<List<RandomEventDTO>> response = controller.active();

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().size());
        assertEquals("VEHICLE_BREAKDOWN", response.getData().get(0).getEventType());
    }

    @Test void v2RequestPassesIndependentBreakdownFields() {
        RandomEventTriggerRequest request = new RandomEventTriggerRequest();
        request.setEventType(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN);
        request.setVehicleId(12L);
        request.setBreakdownLevel(TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED);
        request.setRescueWaitMinutes(30);
        request.setRepairMinutes(90);
        when(eventService.triggerManually(request.getEventType(), 12L, null, request.getBreakdownLevel(), 30, 90, simNow))
                .thenReturn(activeEvent(12L));
        assertEquals(HttpStatus.OK, controller.trigger(request).getStatusCode());
        verify(eventService).triggerManually(request.getEventType(), 12L, null, request.getBreakdownLevel(), 30, 90, simNow);
    }

    @Test void realJsonRequestBindsV2Fields() throws Exception {
        when(eventService.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN, 12L, null,
                TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED, 30, 90, simNow)).thenReturn(activeEvent(12L));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/simulation/random-events/trigger")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"eventType":"VEHICLE_BREAKDOWN","vehicleId":12,
                         "breakdownLevel":"ASSISTANCE_REQUIRED","rescueWaitMinutes":30,"repairMinutes":90}
                        """))
                .andExpect(status().isOk());
    }

    private TransportRandomEvent activeEvent(Long vehicleId) {
        TransportRandomEvent event = new TransportRandomEvent();
        event.setId(100L);
        event.setVehicleId(vehicleId);
        event.setLicensePlate("川A-0012");
        event.setEventType(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN);
        event.setStatus(TransportRandomEvent.EventStatus.ACTIVE);
        event.setTriggerSource(TransportRandomEvent.TriggerSource.MANUAL);
        event.setStartTime(simNow);
        event.setPlannedEndTime(simNow.plusHours(1));
        return event;
    }
}
