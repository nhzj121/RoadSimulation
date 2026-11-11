package org.example.roadsimulation.sandbox.cli;

import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.optimization.FulfillmentReplay;
import org.example.roadsimulation.sandbox.optimization.OptimizationJson;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.nio.file.*;

/** Read-only replay of verified sandbox execution IDs, with one new local report. */
public final class SandboxFulfillmentReplayCli {
    private SandboxFulfillmentReplayCli() {}

    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("Expected request JSON and output JSON paths");
            var request = OptimizationJson.read(Files.readString(Path.of(args[0])), FulfillmentReplay.Request.class);
            var settings = new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"), System.getenv("SANDBOX_DB_USER"),
                    System.getenv("SANDBOX_DB_PASSWORD"));
            var result = FulfillmentReplay.execute(settings, request);
            Files.writeString(Path.of(args[1]), OptimizationJson.write(result), StandardOpenOption.CREATE_NEW);
        } catch (Exception failure) {
            String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode() : "FULFILLMENT_REPLAY_FAILED";
            System.err.println("sandbox-fulfillment-replay[" + code + "]");
            System.exit(2);
        }
    }
}
