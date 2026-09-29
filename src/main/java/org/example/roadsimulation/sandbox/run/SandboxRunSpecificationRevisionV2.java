package org.example.roadsimulation.sandbox.run;

import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.service.FrozenTransportEventConfiguration;
import java.time.Instant;
import java.util.List;

public record SandboxRunSpecificationRevisionV2(
        String artifactVersion,String runSpecKey,int revision,SandboxRunSpecificationV2 specification,
        SandboxAlgorithmProfile algorithmProfile,List<SandboxVehicleInitialState> vehicleInitialStates,
        List<WeatherScenarioDTO.TimeSlice> weatherTimeline,FrozenTransportEventConfiguration eventConfiguration,
        Fingerprints fingerprints,Instant publishedAtUtc) {
    public static final String ARTIFACT_VERSION="sandbox-run-specification-revision/v2";
    public SandboxRunSpecificationRevisionV2 {
        vehicleInitialStates=List.copyOf(vehicleInitialStates);weatherTimeline=List.copyOf(weatherTimeline);
    }
    public record Fingerprints(String canonicalization,String runSpecificationSha256,
            String resolvedVehicleInitialStateSha256,String preparedRunFactsSha256,
            String weatherTimelineSha256,String eventConfigurationSha256) {}
}
