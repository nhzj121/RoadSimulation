package org.example.roadsimulation.sandbox.run;

public final class SandboxRunException extends RuntimeException {
    private final String errorCode;

    public SandboxRunException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SandboxRunException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
