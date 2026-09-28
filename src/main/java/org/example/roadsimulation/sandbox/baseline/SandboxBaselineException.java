package org.example.roadsimulation.sandbox.baseline;

public class SandboxBaselineException extends IllegalArgumentException {
    public SandboxBaselineException(String message) {
        super(message);
    }

    public SandboxBaselineException(String message, Throwable cause) {
        super(message, cause);
    }
}
