package org.example.roadsimulation.sandbox.management;

import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** Server-owned configuration. Intentionally not a record: credentials must never be serialized. */
public final class SandboxManagementSettings {
    private final String url, user, password;
    public SandboxManagementSettings(String url, String user, String password) {
        if (!SandboxWorkspaceSafety.DATABASE_NAME.equals(new SandboxWorkspaceSafety().databaseName(url)))
            throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Management requires the dedicated sandbox database");
        if (user == null || user.isBlank() || password == null || password.isBlank())
            throw new SandboxWorkspaceException("MISSING_CREDENTIAL", "Configure sandbox credentials on the backend");
        if (url.matches("(?i).*[?&;](?:password|user|username)=.*"))
            throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Credentials must not be embedded in JDBC URL");
        this.url = url; this.user = user; this.password = password;
    }
    public String url() { return url; }
    public String user() { return user; }
    public String password() { return password; }
    public Resource baseline() { return new ClassPathResource("sandbox/baseline/baseline-v1.json"); }
}
