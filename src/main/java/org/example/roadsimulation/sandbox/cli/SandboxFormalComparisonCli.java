package org.example.roadsimulation.sandbox.cli;

import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.optimization.FormalSandboxComparison;
import org.example.roadsimulation.sandbox.optimization.HeuristicTraceReplay;
import org.example.roadsimulation.sandbox.optimization.OptimizationJson;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;

/** Headless G6-A entry point for calibration and approved formal experiments. */
public final class SandboxFormalComparisonCli {
    private SandboxFormalComparisonCli() {}

    public static void main(String[] args) {
        try {
            execute(args, new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"),
                    System.getenv("SANDBOX_DB_USER"), System.getenv("SANDBOX_DB_PASSWORD")));
        } catch (Exception failure) {
            String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode()
                    : failure.getClass().getSimpleName();
            System.err.println("sandbox-formal-comparison[" + code + "] " + failure.getMessage());
            System.exit(2);
        }
    }

    public static void execute(String[] args, SandboxManagementSettings settings) throws Exception {
        if (args.length == 0 || !Set.of("calibrate", "compare", "replay-heuristic").contains(args[0]))
            throw new IllegalArgumentException("Expected calibrate, compare, or replay-heuristic");
        boolean replay = "replay-heuristic".equals(args[0]);
        var allowed = replay ? Set.of("execution-id", "output-dir") : Set.of("request", "output-dir", "completed-search-dir");
        var options = new HashMap<String, String>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            int separator = arg.indexOf('=');
            if (!arg.startsWith("--") || separator < 3 || separator == arg.length() - 1)
                throw new IllegalArgumentException("Expected --name=value");
            String key = arg.substring(2, separator);
            if (!allowed.contains(key)
                    || options.putIfAbsent(key, arg.substring(separator + 1)) != null)
                throw new IllegalArgumentException("Unknown or duplicate option");
        }
        if (replay) {
            if (!options.keySet().equals(allowed))
                throw new IllegalArgumentException("--execution-id and --output-dir are required");
            new HeuristicTraceReplay(settings).execute(options.get("execution-id"), Path.of(options.get("output-dir")));
            return;
        }
        if (!options.containsKey("request") || !options.containsKey("output-dir"))
            throw new IllegalArgumentException("--request and --output-dir are required");
        var request = OptimizationJson.read(Files.readString(Path.of(options.get("request"))), FormalSandboxComparison.Request.class);
        var output = Path.of(options.get("output-dir"));
        var comparison = new FormalSandboxComparison(settings);
        String completedSearch = options.get("completed-search-dir");
        if ("calibrate".equals(args[0])) {
            comparison.calibrate(request, output, completedSearch == null ? null : Path.of(completedSearch));
        } else {
            if (completedSearch != null) throw new IllegalArgumentException("compare always starts fresh executions");
            comparison.compare(request, output);
        }
    }
}
