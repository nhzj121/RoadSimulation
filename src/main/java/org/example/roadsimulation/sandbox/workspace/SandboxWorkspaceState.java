package org.example.roadsimulation.sandbox.workspace;

public enum SandboxWorkspaceState {
    EMPTY,
    PREPARING,
    BASE_DATA_READY,
    SCENARIO_PREPARING,
    SCENARIO_DATA_READY,
    RUN_SPEC_PREPARING,
    RUN_SPEC_READY,
    EXECUTION_RUNNING,
    EXECUTION_COMPLETED,
    EXECUTION_CANCELLED,
    FAILED
}
