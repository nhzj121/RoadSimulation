package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxWorkspaceCliTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acceptsDirectJsonAndBase64UrlTransportForTheSameStableKey() {
        Map<String, Object> direct = SandboxWorkspaceCli.parseRandomKeyOption(
                "{\"vehicleId\":1}", objectMapper);
        Map<String, Object> encoded = SandboxWorkspaceCli.parseRandomKeyOption(
                "base64url:eyJ2ZWhpY2xlSWQiOjF9", objectMapper);

        assertEquals(Map.of("vehicleId", 1), direct);
        assertEquals(direct, encoded);
    }

    @Test
    void rejectsInvalidBase64UrlAndNonObjectJson() {
        assertEquals("INVALID_RANDOM_KEY", assertThrows(SandboxWorkspaceException.class,
                () -> SandboxWorkspaceCli.parseRandomKeyOption(
                        "base64url:***", objectMapper)).errorCode());
        assertEquals("INVALID_RANDOM_KEY", assertThrows(SandboxWorkspaceException.class,
                () -> SandboxWorkspaceCli.parseRandomKeyOption(
                        "[1]", objectMapper)).errorCode());
    }
}
