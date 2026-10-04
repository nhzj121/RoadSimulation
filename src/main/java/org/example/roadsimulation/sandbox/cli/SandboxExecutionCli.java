package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.execution.SandboxControlledRunExecutor;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionLease;
import org.example.roadsimulation.sandbox.execution.SandboxRuntimeFactCollector;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.execution.bootstrap.SandboxControlledApplication;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.Map;

/** Internal, headless execution command. Never activates the ordinary application. */
public final class SandboxExecutionCli {
    private SandboxExecutionCli() {}

    public static void main(String[] arguments) {
        // Devtools discovers and reinvokes a main class before context initializers run.
        // A management command is a one-shot process, never a restartable application.
        System.setProperty("spring.devtools.restart.enabled", "false");
        int exitCode = 0;
        try {
            var options = parse(arguments);
            var json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            var result = execute(options, json);
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception failure) {
            String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode() : "SANDBOX_EXECUTION_FAILED";
            System.err.println("sandbox-error[" + code + "]: " + failure.getMessage());
            exitCode = 2;
        } finally {
            SandboxWorkspaceCli.shutdownMysqlCleanupThread();
        }
        if (exitCode != 0) System.exit(exitCode);
    }

    static Map<String, Object> execute(Map<String, String> options, ObjectMapper json) throws Exception {
        String command = options.get("command");
        if (!java.util.Set.of("execute-run", "show-execution", "verify-execution", "export-execution", "inspect-job", "close-abnormal-job").contains(command == null ? "" : command)) {
            throw new SandboxWorkspaceException("UNSUPPORTED_COMMAND", "Expected execute-run, show-execution, verify-execution, export-execution, inspect-job or close-abnormal-job");
        }
        String password = System.getenv("SANDBOX_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new SandboxWorkspaceException("MISSING_CREDENTIAL", "SANDBOX_DB_PASSWORD must be set");
        }
        String url = options.getOrDefault("jdbc-url", System.getenv("SANDBOX_DB_URL"));
        if (url == null || url.isBlank()) {
            throw new SandboxWorkspaceException("MISSING_ARGUMENT", "Specify --jdbc-url or SANDBOX_DB_URL; no database fallback is allowed");
        }
        String user = options.getOrDefault("username", System.getenv().getOrDefault("SANDBOX_DB_USER", "road_sandbox_runtime"));
        if ("inspect-job".equals(command) || "close-abnormal-job".equals(command)) {
            if (!java.util.Set.of("command", "jdbc-url", "username", "job-id", "inspection-sha256").containsAll(options.keySet())
                    || ("inspect-job".equals(command) && options.containsKey("inspection-sha256"))) {
                throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Maintenance accepts only job identity and explicit inspection confirmation");
            }
            var maintenance = new org.example.roadsimulation.sandbox.management.SandboxAbnormalClosureService(
                    new org.example.roadsimulation.sandbox.management.SandboxManagementSettings(url, user, password), json);
            String id = required(options, "job-id");
            return "inspect-job".equals(command) ? maintenance.inspect(id)
                    : maintenance.close(id, required(options, "inspection-sha256"));
        }
        if (!"execute-run".equals(command)) {
            var store = new SandboxExecutionStore(url, user, password, json);
            String id = required(options, "execution-id");
            if ("verify-execution".equals(command)) return store.verify(id);
            var artifact = store.export(id);
            if ("show-execution".equals(command)) return Map.of("execution", artifact);
            var destination = java.nio.file.Path.of(required(options, "output")).toAbsolutePath().normalize();
            java.nio.file.Files.write(destination, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(artifact),
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            return Map.of("executionId", id, "output", destination.toString(), "integrity", "VERIFIED");
        }
        String key = required(options, "run-spec-key");
        int revision;
        try { revision = Integer.parseInt(required(options, "revision")); }
        catch (NumberFormatException failure) { throw new SandboxWorkspaceException("INVALID_ARGUMENT", "revision must be a positive integer"); }
        if (revision <= 0) throw new SandboxWorkspaceException("INVALID_ARGUMENT", "revision must be positive");
        String baseline = options.getOrDefault("baseline", "classpath:sandbox/baseline/baseline-v1.json");
        var resource = new DefaultResourceLoader().getResource(baseline);
        int[] completed = {0};
        try (var lease = SandboxExecutionLease.acquire(url, user, password, resource, key, revision, json)) {
            lease.create(json);
            try {
                SandboxControlledRunExecutor.Result result;
                try (var context = SandboxControlledApplication.open(url, user, password,
                        Map.of("sandbox.baseline-resource", baseline))) {
                    lease.markRunning();
                    result = context.getBean(SandboxControlledRunExecutor.class)
                            .execute(lease, (tick, evaluation) -> {
                                lease.recordTick(tick, evaluation, context.getBean(SandboxRuntimeFactCollector.class).capture(), json);
                                completed[0]++;
                            });
                }
                new SandboxExecutionStore(url, user, password, json).verify(lease.executionId());
                lease.finish(completed[0], null);
                return Map.of("executionId", lease.executionId(), "status", "COMPLETED", "result", result);
            } catch (Exception failure) {
                try { lease.finish(completed[0], failure); }
                catch (Exception persistence) { failure.addSuppressed(persistence); }
                throw failure;
            }
        }
    }

    static Map<String, String> parse(String[] arguments) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String argument : arguments) {
            if (!argument.startsWith("--") && !options.containsKey("command")) options.put("command", argument);
            else if (argument.startsWith("--") && argument.indexOf('=') > 2) {
                int separator = argument.indexOf('=');
                String name = argument.substring(2, separator);
                if (!java.util.Set.of("jdbc-url", "username", "baseline", "run-spec-key", "revision", "execution-id", "output", "job-id", "inspection-sha256").contains(name)
                        || options.putIfAbsent(name, argument.substring(separator + 1)) != null) {
                    throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Unsupported or duplicate option: " + name);
                }
            } else throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Expected --name=value");
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new SandboxWorkspaceException("MISSING_ARGUMENT", "Missing --" + name);
        return value;
    }
}
