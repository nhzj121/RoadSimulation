package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;

import java.util.Comparator;
import java.util.List;

/** Canonical fingerprints for deterministic run inputs and resolved initial state. */
public final class SandboxRunCodec {
    public static final String CANONICALIZATION = "lexicographic-json-v1";

    private final ObjectMapper objectMapper;
    private final LexicographicJsonSha256 hash;

    public SandboxRunCodec(ObjectMapper objectMapper) {
        // Run-spec JSON defines LocalDateTime as an ISO-8601 string.  Do not let a
        // caller's ObjectMapper preference (timestamp array vs. string) change a
        // published fingerprint between the CLI, tests and the runtime process.
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.hash = new LexicographicJsonSha256(this.objectMapper);
    }

    public String runSpecificationHash(
            SandboxRunSpecificationV1 specification,
            SandboxAlgorithmProfile profile
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("scenario", objectMapper.valueToTree(specification.scenario()));
        payload.set("simulationClock", objectMapper.valueToTree(specification.simulationClock()));
        payload.set("demand", objectMapper.valueToTree(specification.demand()));
        payload.set("dispatch", objectMapper.valueToTree(specification.dispatch()));
        payload.set("environment", objectMapper.valueToTree(specification.environment()));
        payload.set("vehicleInitialization", objectMapper.valueToTree(specification.vehicleInitialization()));
        payload.set("random", objectMapper.valueToTree(specification.random()));
        payload.set("algorithmProfile", objectMapper.valueToTree(profile));
        return hash.hash(payload);
    }

    public String vehicleInitialStateHash(List<SandboxVehicleInitialState> states) {
        return hash.hashObject(states.stream()
                .sorted(Comparator.comparingLong(SandboxVehicleInitialState::vehicleId))
                .toList());
    }

    public String preparedRunFactsHash(
            String effectiveScenarioDataSha256,
            String runSpecificationSha256,
            String vehicleInitialStateSha256
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("effectiveScenarioDataSha256", effectiveScenarioDataSha256);
        payload.put("runSpecificationSha256", runSpecificationSha256);
        payload.put("resolvedVehicleInitialStateSha256", vehicleInitialStateSha256);
        return hash.hash(payload);
    }
}
