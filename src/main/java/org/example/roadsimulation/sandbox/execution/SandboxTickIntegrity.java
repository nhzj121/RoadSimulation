package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;

import java.util.Map;

/** Integrity hashes protect stored artifacts; they are not floating-point comparison tolerances. */
public final class SandboxTickIntegrity {
    private SandboxTickIntegrity() {}

    public static String hash(ObjectMapper json, SimulationTick tick, String facts, String business,
                              String evaluation, String previous) {
        return new LexicographicJsonSha256(json).hashObject(Map.of(
                "artifactVersion", "sandbox-execution-tick/v1", "tick", tick,
                "factsSha256", facts, "businessFactsSha256", business,
                "evaluationSha256", evaluation, "previousTickSha256", previous));
    }
}
