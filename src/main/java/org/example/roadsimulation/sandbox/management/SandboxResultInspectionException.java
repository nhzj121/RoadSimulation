package org.example.roadsimulation.sandbox.management;

import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

/** Safe diagnostic identity only; never includes worker logs or connection details. */
public final class SandboxResultInspectionException extends SandboxWorkspaceException {
    private final String jobId, reason;
    public SandboxResultInspectionException(String jobId, String reason) {
        super("INSPECTION_REQUIRED", "Result requires inspected closure");
        this.jobId = jobId; this.reason = reason;
    }
    public String jobId() { return jobId; }
    public String reason() { return reason; }
}
