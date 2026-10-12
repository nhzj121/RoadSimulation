package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.run.SandboxRunCompilerV2;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;

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

    @Test
    void writesImportableJsonWithoutMavenLogsAndRefusesOverwrite(@TempDir Path directory) throws Exception {
        var compiler = new SandboxRunCompilerV2(objectMapper);
        var specification = compiler.read(new ClassPathResource("sandbox/runs/default-production-original-v2.json"));
        Path output = directory.resolve("resolved-v2.json");
        assertEquals(output, SandboxWorkspaceCli.writeResolvedSpecification(
                output.toString(), specification, objectMapper));
        var encoded = Files.readString(output);
        assertEquals("2026-01-01T00:00:00", objectMapper.readTree(encoded)
                .path("simulationClock").path("startLocalDateTime").asText());
        assertEquals(specification, objectMapper.readValue(encoded, specification.getClass()));
        assertEquals("RUN_SPEC_OUTPUT_FAILED", assertThrows(SandboxWorkspaceException.class,
                () -> SandboxWorkspaceCli.writeResolvedSpecification(
                        output.toString(), specification, objectMapper)).errorCode());
        assertEquals(encoded, Files.readString(output));
    }
}
