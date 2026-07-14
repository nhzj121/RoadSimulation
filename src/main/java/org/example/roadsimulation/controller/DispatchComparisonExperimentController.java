package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.DispatchComparisonOptionsDTO;
import org.example.roadsimulation.dto.DispatchComparisonPrepareRequest;
import org.example.roadsimulation.dto.DispatchComparisonScenarioDTO;
import org.example.roadsimulation.dto.DispatchComparisonVisualArrivalAckRequest;
import org.example.roadsimulation.dto.DispatchComparisonVisualRunResultDTO;
import org.example.roadsimulation.dto.DispatchComparisonVisualRunStatusDTO;
import org.example.roadsimulation.dto.DispatchComparisonVehicleDisplayInfoDTO;
import org.example.roadsimulation.service.DispatchComparisonExperimentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP control surface for preparing and driving the visual dispatch comparison state machine.
 */
@RestController
@RequestMapping("/api/simulation/experiments/dispatch-comparison")
public class DispatchComparisonExperimentController {

    private final DispatchComparisonExperimentService experimentService;

    public DispatchComparisonExperimentController(DispatchComparisonExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<DispatchComparisonOptionsDTO>> options() {
        return ResponseEntity.ok(ApiResponse.success("query success", experimentService.getPreparationOptions()));
    }

    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<DispatchComparisonScenarioDTO>> prepare(
            @RequestBody DispatchComparisonPrepareRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "experiment scenario prepared",
                    experimentService.prepareScenario(request)
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<DispatchComparisonScenarioDTO>> current() {
        return ResponseEntity.ok(ApiResponse.success("query success", experimentService.getCurrentScenario()));
    }

    @DeleteMapping("/current")
    public ResponseEntity<ApiResponse<Void>> clearCurrent() {
        try {
            experimentService.clearCurrentScenario();
            return ResponseEntity.ok(ApiResponse.success("experiment scenario marker cleared", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/start-visual-run")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunStatusDTO>> startVisualRun() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "visual experiment started",
                    experimentService.startVisualRun()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunStatusDTO>> pauseVisualRun() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "visual experiment paused",
                    experimentService.pauseVisualRun()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunStatusDTO>> resumeVisualRun() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "visual experiment resumed",
                    experimentService.resumeVisualRun()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/abort")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunStatusDTO>> abortVisualRun() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "visual experiment aborted",
                    experimentService.abortVisualRun()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/visual-arrival-ack")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunStatusDTO>> acknowledgeVisualArrival(
            @RequestBody DispatchComparisonVisualArrivalAckRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "visual arrival acknowledged",
                    experimentService.acknowledgeVisualArrival(request)
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/run-status")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunStatusDTO>> runStatus() {
        return ResponseEntity.ok(ApiResponse.success("query success", experimentService.getVisualRunStatus()));
    }

    @GetMapping("/latest-result")
    public ResponseEntity<ApiResponse<DispatchComparisonVisualRunResultDTO>> latestResult() {
        return ResponseEntity.ok(ApiResponse.success("query success", experimentService.getLatestVisualRunResult()));
    }

    @GetMapping("/vehicle-display-info")
    public ResponseEntity<ApiResponse<DispatchComparisonVehicleDisplayInfoDTO>> vehicleDisplayInfo(
            @RequestParam Long vehicleId,
            @RequestParam Long assignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "query success",
                experimentService.getVehicleDisplayInfo(vehicleId, assignmentId)
        ));
    }
}
