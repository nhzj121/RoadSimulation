package org.example.roadsimulation.sandbox.cli;

import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.optimization.GlobalPlanSearch;
import org.example.roadsimulation.sandbox.optimization.OptimizationJson;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.nio.file.*;
import java.util.HashMap;
import java.util.Set;

/** Headless G4 search. The result archive distinguishes a completed search from an interrupted search. */
public final class SandboxGlobalPlanSearchCli {
    private SandboxGlobalPlanSearchCli() {}

    public static void main(String[] args) {
        try {
            var result = execute(args, new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"),
                    System.getenv("SANDBOX_DB_USER"), System.getenv("SANDBOX_DB_PASSWORD")));
            System.out.println(result.outcome() + " " + result.stop());
            if (result.interrupted() || result.outcome() == GlobalPlanSearch.Outcome.NO_VALID_BASELINE) System.exit(2);
        } catch (Exception failure) {
            String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode() : "SEARCH_REQUEST_FAILED";
            System.err.println("sandbox-global-plan-search[" + code + "]");
            System.exit(2);
        }
    }

    public static GlobalPlanSearch.Result execute(String[] args, SandboxManagementSettings settings) throws Exception {
        var options = new HashMap<String, String>();
        for (String arg : args) {
            int separator = arg.indexOf('=');
            if (!arg.startsWith("--") || separator < 3) throw new IllegalArgumentException("Expected --name=value");
            String key = arg.substring(2, separator);
            if (!Set.of("request", "output-dir").contains(key) || options.putIfAbsent(key, arg.substring(separator + 1)) != null)
                throw new IllegalArgumentException("Unknown or duplicate option");
        }
        if (options.size() != 2 || options.values().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Both --request and --output-dir are required");
        var request = OptimizationJson.read(Files.readString(Path.of(options.get("request"))), GlobalPlanSearch.Request.class);
        return GlobalPlanSearch.execute(settings, request, Path.of(options.get("output-dir")));
    }
}
