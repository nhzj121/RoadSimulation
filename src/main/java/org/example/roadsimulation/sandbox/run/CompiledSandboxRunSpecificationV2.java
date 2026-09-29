package org.example.roadsimulation.sandbox.run;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.service.FrozenTransportEventConfiguration;
import java.util.List;

public record CompiledSandboxRunSpecificationV2(
        SandboxRunSpecificationV2 normalizedSpecification, SandboxAlgorithmProfile algorithmProfile,
        List<SandboxVehicleInitialState> vehicleInitialStates, int eligibleVehicleInitialPoiCount,
        List<WeatherScenarioDTO.TimeSlice> weatherTimeline,
        FrozenTransportEventConfiguration eventConfiguration,
        String weatherTimelineSha256, String eventConfigurationSha256,
        String runSpecificationSha256, String resolvedVehicleInitialStateSha256,
        String preparedRunFactsSha256, String deterministicSimulationRunId) {
    public CompiledSandboxRunSpecificationV2 {
        vehicleInitialStates=List.copyOf(vehicleInitialStates);weatherTimeline=List.copyOf(weatherTimeline);
    }
}
