package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.DispatchComparisonPrepareRequest;
import org.example.roadsimulation.dto.DispatchComparisonOptionsDTO;
import org.example.roadsimulation.dto.DispatchComparisonScenarioDTO;
import org.example.roadsimulation.dto.DispatchComparisonVisualArrivalAckRequest;
import org.example.roadsimulation.dto.DispatchComparisonVisualRunResultDTO;
import org.example.roadsimulation.dto.DispatchComparisonVisualRunStatusDTO;
import org.example.roadsimulation.dto.DispatchComparisonVehicleDisplayInfoDTO;

/**
 * Stateful control surface for the frontend-driven ORIGINAL then HEURISTIC comparison run.
 */
public interface DispatchComparisonExperimentService {

    DispatchComparisonOptionsDTO getPreparationOptions();

    DispatchComparisonScenarioDTO prepareScenario(DispatchComparisonPrepareRequest request);

    DispatchComparisonScenarioDTO getCurrentScenario();

    void clearCurrentScenario();

    DispatchComparisonVisualRunStatusDTO startVisualRun();

    DispatchComparisonVisualRunStatusDTO pauseVisualRun();

    DispatchComparisonVisualRunStatusDTO resumeVisualRun();

    DispatchComparisonVisualRunStatusDTO abortVisualRun();

    DispatchComparisonVisualRunStatusDTO acknowledgeVisualArrival(DispatchComparisonVisualArrivalAckRequest request);

    DispatchComparisonVisualRunStatusDTO getVisualRunStatus();

    DispatchComparisonVisualRunResultDTO getLatestVisualRunResult();

    DispatchComparisonVehicleDisplayInfoDTO getVehicleDisplayInfo(Long vehicleId, Long assignmentId);
}
