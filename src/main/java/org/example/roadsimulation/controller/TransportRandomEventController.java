package org.example.roadsimulation.controller;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.RandomEventDTO;
import org.example.roadsimulation.dto.RandomEventTriggerRequest;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.service.TransportRandomEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulation/random-events")
public class TransportRandomEventController {
    private final TransportRandomEventService eventService;
    private final SimulationContext simulationContext;

    public TransportRandomEventController(
            TransportRandomEventService eventService,
            SimulationContext simulationContext
    ) {
        this.eventService = eventService;
        this.simulationContext = simulationContext;
    }

    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<RandomEventDTO>> trigger(@RequestBody RandomEventTriggerRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("request body is required");
            }
            TransportRandomEvent event = eventService.triggerManually(
                    request.getEventType(),
                    request.getVehicleId(),
                    request.getDurationMinutes(),
                    simulationContext.getCurrentSimTime()
            );
            return ResponseEntity.ok(ApiResponse.success("random event triggered", RandomEventDTO.from(event)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/active")
    public ApiResponse<List<RandomEventDTO>> active() {
        return ApiResponse.success(eventService.getActiveEvents().stream().map(RandomEventDTO::from).toList());
    }

    @GetMapping("/history")
    public ApiResponse<List<RandomEventDTO>> history(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(eventService.getHistory(limit).stream().map(RandomEventDTO::from).toList());
    }
}
