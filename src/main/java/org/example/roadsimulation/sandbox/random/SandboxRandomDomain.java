package org.example.roadsimulation.sandbox.random;

/**
 * Versioned random domains used by deterministic sandbox runs.
 *
 * <p>The external id is part of the persisted protocol. Renaming an id changes
 * the derived stream and therefore requires a new protocol version.</p>
 */
public enum SandboxRandomDomain {
    VEHICLE_INITIAL_POI("VEHICLE_INITIAL_POI/v1"),
    PRODUCTION_FINAL_QUANTITY("PRODUCTION_FINAL_QUANTITY/v1"),
    PRODUCTION_SINK_POI("PRODUCTION_SINK_POI/v1"),
    PRODUCTION_STAGE_POI("PRODUCTION_STAGE_POI/v1"),
    HEURISTIC_INITIAL_POPULATION("HEURISTIC_INITIAL_POPULATION/v1"),
    HEURISTIC_EVOLUTION("HEURISTIC_EVOLUTION/v1"),
    DRIVER_BEHAVIOR_TRANSITION("DRIVER_BEHAVIOR_TRANSITION/v1"),
    ENVIRONMENT_PHASE("ENVIRONMENT_PHASE/v1");

    private final String id;

    SandboxRandomDomain(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
