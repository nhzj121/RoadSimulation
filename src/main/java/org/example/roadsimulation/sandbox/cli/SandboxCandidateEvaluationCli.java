package org.example.roadsimulation.sandbox.cli;

import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.optimization.OptimizationJson;
import org.example.roadsimulation.sandbox.optimization.SandboxCandidateEvaluator;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.nio.file.*;
import java.util.HashMap;
import java.util.Set;

/** Headless, explicitly requested G3 comparison. Outputs a new directory of verified evidence. */
public final class SandboxCandidateEvaluationCli {
    private SandboxCandidateEvaluationCli() {}

    public static void main(String[] args) {
        try {
            var result = execute(args, new SandboxManagementSettings(System.getenv("SANDBOX_DB_URL"),
                    System.getenv("SANDBOX_DB_USER"), System.getenv("SANDBOX_DB_PASSWORD")));
            System.out.println(result.scoring().selection().outcome());
            if (result.stopped() || result.scoring().selection().selectedPlanFingerprint() == null) System.exit(2);
        } catch (Exception failure) {
            String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode() : "EVALUATION_REQUEST_FAILED";
            System.err.println("sandbox-candidate-evaluation[" + code + "]");
            System.exit(2);
        }
    }

    public static SandboxCandidateEvaluator.Manifest execute(String[] args, SandboxManagementSettings settings) throws Exception {
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
        var request = OptimizationJson.read(Files.readString(Path.of(options.get("request"))), SandboxCandidateEvaluator.Request.class);
        var output = Path.of(options.get("output-dir"));
        var evaluator = SandboxCandidateEvaluator.open(settings, request.runSpecKey(), request.runSpecRevision(),
                request.baselineResource(), output, request.timeoutSeconds());
        evaluator.evaluateBaseline(request.cacheMode());
        for (var candidate : request.candidates()) evaluator.evaluateCandidate(candidate, request.cacheMode());
        var result = evaluator.report(request.preference());
        boolean interrupted = Thread.interrupted();
        try {
            Files.writeString(output.resolve("evaluation.json"), OptimizationJson.write(result), StandardOpenOption.CREATE_NEW);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        return result;
    }
}
