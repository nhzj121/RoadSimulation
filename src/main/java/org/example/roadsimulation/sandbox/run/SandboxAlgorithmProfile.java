package org.example.roadsimulation.sandbox.run;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact immutable algorithm parameters persisted in a run specification revision. */
public record SandboxAlgorithmProfile(
        String profileId,
        String strategy,
        Map<String, Object> parameters
) {
    public SandboxAlgorithmProfile {
        parameters = immutableMap(parameters);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> immutableMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key,
                    value instanceof Map<?, ?> nested
                            ? immutableMap((Map<String, ?>) nested)
                            : value));
        }
        return Collections.unmodifiableMap(copy);
    }
}
