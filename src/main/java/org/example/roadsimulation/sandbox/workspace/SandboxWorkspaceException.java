package org.example.roadsimulation.sandbox.workspace;

public class SandboxWorkspaceException extends IllegalStateException {
    private final String errorCode;

    public SandboxWorkspaceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SandboxWorkspaceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
