package org.example.roadsimulation.sandbox.scenario.definition;

public final class SandboxScenarioException extends RuntimeException {
    private final String errorCode;

    public SandboxScenarioException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SandboxScenarioException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
