package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.service.ManualReplacementRoutePlanner;
import org.example.roadsimulation.service.ManualReplacementService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

@RestController
@Profile("!sandbox-runtime")
@RequestMapping("/api/simulation/manual-replacements")
public class ManualReplacementController {
    private final ManualReplacementService service;
    private final SimulationMainLoop loop;
    private final ManualReplacementRoutePlanner routePlanner;

    public ManualReplacementController(ManualReplacementService service, SimulationMainLoop loop,
                                       ManualReplacementRoutePlanner routePlanner) {
        this.service = service;
        this.loop = loop;
        this.routePlanner = routePlanner;
    }

    @GetMapping
    public ApiResponse<ManualReplacementService.Monitor> monitor() {
        return ApiResponse.success(service.monitor());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ManualReplacementService.Snapshot>> start(@RequestBody ManualReplacementService.Start input) {
        return mutate(() -> service.start(input));
    }

    @GetMapping("/{id}/approach-route")
    public ResponseEntity<ApiResponse<ManualReplacementRoutePlanner.Route>> approach(
            @PathVariable long id, @RequestParam String runId, @RequestParam long attemptId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    routePlanner.plan(id, new ManualReplacementService.Operation(runId, attemptId))));
        } catch (ResponseStatusException rejected) {
            return ResponseEntity.status(rejected.getStatusCode()).body(ApiResponse.error(rejected.getReason()));
        }
    }

    @PostMapping("/{id}/arrived")
    public ResponseEntity<ApiResponse<ManualReplacementService.Snapshot>> arrived(@PathVariable long id, @RequestBody ManualReplacementService.Operation input) {
        return mutate(() -> service.arrived(id, input));
    }

    @PostMapping("/{id}/finished")
    public ResponseEntity<ApiResponse<ManualReplacementService.Snapshot>> finished(@PathVariable long id, @RequestBody ManualReplacementService.Operation input) {
        return mutate(() -> service.finished(id, input));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ManualReplacementService.Snapshot>> cancel(@PathVariable long id, @RequestBody ManualReplacementService.Operation input) {
        return mutate(() -> service.cancel(id, input));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<ManualReplacementService.Snapshot>> restore(@PathVariable long id, @RequestBody ManualReplacementService.Operation input) {
        return mutate(() -> service.restore(id, input));
    }

    private ResponseEntity<ApiResponse<ManualReplacementService.Snapshot>> mutate(
            Supplier<ManualReplacementService.Snapshot> operation) {
        try {
            return ResponseEntity.ok(ApiResponse.success(loop.withSimulationMutationLock(operation)));
        } catch (ResponseStatusException rejected) {
            return ResponseEntity.status(rejected.getStatusCode()).body(ApiResponse.error(rejected.getReason()));
        } catch (IllegalStateException conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(conflict.getMessage()));
        }
    }
}
