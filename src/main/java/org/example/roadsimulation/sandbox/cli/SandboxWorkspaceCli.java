package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseData;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.run.SandboxRunException;
import org.example.roadsimulation.sandbox.run.SandboxRunCompilerV2;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV2;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioException;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
import org.example.roadsimulation.sandbox.workspace.SandboxScenarioWorkspacePreparer;
import org.example.roadsimulation.sandbox.workspace.SandboxRunWorkspacePreparer;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspacePreparer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Map;

/** Standalone sandbox administration command. It does not start the simulation application. */
public final class SandboxWorkspaceCli {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            + "&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL";

    private SandboxWorkspaceCli() {}

    public static void main(String[] args) throws Exception {
        int exitCode = 0;
        try {
            Map<String, String> options = parseOptions(args);
            String command = options.getOrDefault("command", "verify");
            Resource baseline = baselineResource(options.getOrDefault(
                    "baseline", "classpath:sandbox/baseline/baseline-v1.json"));
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            Object result;
            if ("compile-scenario".equals(command)) {
                Resource scenario = baselineResource(options.getOrDefault(
                        "scenario", "classpath:sandbox/scenarios/default-all-eligible-v1.json"));
                result = new SandboxScenarioStore("", "", "", objectMapper).compile(baseline, scenario);
            } else {
                String jdbcUrl = value(options, "jdbc-url", "SANDBOX_DB_URL", DEFAULT_URL);
                String username = value(options, "username", "SANDBOX_DB_USER", "road_sandbox_runtime");
                String password = System.getenv("SANDBOX_DB_PASSWORD");
                if (password == null || password.isBlank()) {
                    throw new SandboxWorkspaceException(
                            "MISSING_CREDENTIAL", "SANDBOX_DB_PASSWORD must be set in the process environment");
                }
                SandboxWorkspacePreparer preparer = new SandboxWorkspacePreparer(
                        jdbcUrl, username, password, objectMapper);
                SandboxScenarioStore scenarios = new SandboxScenarioStore(
                        jdbcUrl, username, password, objectMapper);
                SandboxScenarioWorkspacePreparer scenarioPreparer = new SandboxScenarioWorkspacePreparer(
                        jdbcUrl, username, password, objectMapper);
                SandboxRunSpecificationStore runs = new SandboxRunSpecificationStore(
                        jdbcUrl, username, password, objectMapper);
                SandboxRunWorkspacePreparer runPreparer = new SandboxRunWorkspacePreparer(
                        jdbcUrl, username, password, objectMapper);
                result = switch (command) {
                    case "prepare" -> {
                        requirePhaseOneSelection(options);
                        yield preparer.prepare(baseline);
                    }
                    case "verify" -> {
                        requirePhaseOneSelection(options);
                        yield preparer.verify(baseline);
                    }
                    case "save-draft" -> scenarios.saveDraft(baseline, baselineResource(required(options, "scenario")));
                    case "publish-scenario" -> scenarios.publish(baseline, required(options, "scenario-key"));
                    case "export-scenario" -> scenarios.loadRevision(
                            required(options, "scenario-key"), positiveInt(options, "revision"));
                    case "prepare-scenario" -> scenarioPreparer.prepare(
                            baseline, required(options, "scenario-key"), positiveInt(options, "revision"));
                    case "verify-scenario" -> scenarioPreparer.verify(baseline);
                    case "compile-run-spec" -> {
                        Resource source=baselineResource(required(options,"run-spec"));
                        if (isV2(source,objectMapper)) yield runs.compileV2(baseline,source);
                        yield runs.compile(baseline,source);
                    }
                    case "save-run-draft" -> {
                        Resource source=baselineResource(required(options,"run-spec"));
                        if (isV2(source,objectMapper)) yield runs.saveDraftV2(baseline,source);
                        throw new SandboxWorkspaceException("RUN_SPEC_V2_REQUIRED","New run drafts require explicit v2; v1 is retained for historical verification");
                    }
                    case "publish-run-spec" -> {
                        String key=required(options,"run-spec-key");
                        if(runs.isDraftV2(key)) yield runs.publishV2(baseline,key);
                        throw new SandboxWorkspaceException("RUN_SPEC_V2_REQUIRED","New formal publication requires v2");
                    }
                    case "export-run-spec" -> {
                        String key=required(options,"run-spec-key");int rev=positiveInt(options,"revision");
                        if(runs.isRevisionV2(key,rev)) yield runs.loadRevisionV2(key,rev);
                        yield runs.loadRevision(key,rev);
                    }
                    case "prepare-run" -> runPreparer.prepare(
                            baseline, required(options, "run-spec-key"), positiveInt(options, "revision"));
                    case "verify-run" -> runPreparer.verify(baseline);
                    case "derive-seed" -> deriveSeed(options, runs, objectMapper);
                    default -> throw new SandboxWorkspaceException(
                            "UNSUPPORTED_COMMAND",
                            "Command must be prepare, verify, compile-scenario, save-draft, "
                                    + "publish-scenario, export-scenario, prepare-scenario, verify-scenario, "
                                    + "compile-run-spec, save-run-draft, publish-run-spec, export-run-spec, "
                                    + "prepare-run, verify-run or derive-seed: "
                                    + command);
                };
            }
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (SandboxRunException exception) {
            System.err.println("sandbox-error[" + exception.errorCode() + "]: " + exception.getMessage());
            exitCode = 2;
        } catch (SandboxScenarioException exception) {
            System.err.println("sandbox-error[" + exception.errorCode() + "]: " + exception.getMessage());
            exitCode = 2;
        } catch (SandboxWorkspaceException exception) {
            System.err.println("sandbox-error[" + exception.errorCode() + "]: " + exception.getMessage());
            exitCode = 2;
        } catch (RuntimeException exception) {
            System.err.println("sandbox-error[INVALID_BASELINE_OR_CONFIGURATION]: " + exception.getMessage());
            exitCode = 2;
        } finally {
            shutdownMysqlCleanupThread();
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void shutdownMysqlCleanupThread() {
        try {
            Class<?> cleanup = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            cleanup.getMethod("checkedShutdown").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // MariaDB's driver does not create this MySQL Connector/J cleanup thread.
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String argument : args) {
            if (!argument.startsWith("--") && !result.containsKey("command")) {
                result.put("command", argument);
                continue;
            }
            if (!argument.startsWith("--") || !argument.contains("=")) {
                throw new SandboxWorkspaceException(
                        "INVALID_ARGUMENT", "Expected --name=value argument: " + argument);
            }
            int separator = argument.indexOf('=');
            result.put(argument.substring(2, separator), argument.substring(separator + 1));
        }
        return result;
    }

    private static String value(
            Map<String, String> options,
            String option,
            String environment,
            String defaultValue
    ) {
        String explicit = options.get(option);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String fromEnvironment = System.getenv(environment);
        return fromEnvironment == null || fromEnvironment.isBlank() ? defaultValue : fromEnvironment;
    }

    private static void requirePhaseOneSelection(Map<String, String> options) {
        String selection = options.getOrDefault("selection", EffectiveBaseData.ALL_ELIGIBLE_V1);
        if (!EffectiveBaseData.ALL_ELIGIBLE_V1.equals(selection)) {
            throw new SandboxWorkspaceException(
                    "UNSUPPORTED_SELECTION", "Phase one supports only ALL_ELIGIBLE_V1");
        }
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new SandboxWorkspaceException("MISSING_ARGUMENT", "--" + name + " is required");
        }
        return value;
    }

    private static int positiveInt(Map<String, String> options, String name) {
        try {
            int value = Integer.parseInt(required(options, name));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new SandboxWorkspaceException("INVALID_ARGUMENT", "--" + name + " must be a positive integer");
        }
    }

    private static Object deriveSeed(
            Map<String, String> options,
            SandboxRunSpecificationStore runs,
            ObjectMapper objectMapper
    ) {
        String runSpecKey = required(options, "run-spec-key");
        int revision = positiveInt(options, "revision");
        String requestedDomain = required(options, "domain");
        SandboxRandomDomain domain = Arrays.stream(SandboxRandomDomain.values())
                .filter(candidate -> candidate.id().equals(requestedDomain)
                        || candidate.name().equals(requestedDomain))
                .findFirst()
                .orElseThrow(() -> new SandboxWorkspaceException(
                        "UNKNOWN_RANDOM_DOMAIN", "Unknown random domain: " + requestedDomain));
        Map<String, Object> key = parseRandomKeyOption(required(options, "key"), objectMapper);
        SandboxRandomProtocol protocol = new SandboxRandomProtocol(objectMapper);
        String rootSeed = runs.isRevisionV2(runSpecKey,revision)
                ? runs.loadRevisionV2(runSpecKey,revision).specification().random().rootSeed()
                : runs.loadRevision(runSpecKey,revision).specification().random().rootSeed();
        return Map.of(
                "runSpecKey", runSpecKey,
                "revision", revision,
                "domain", domain.id(),
                "key", key,
                "derivedSeedHex", protocol.deriveSeedHex(rootSeed, domain, key));
    }

    static Map<String, Object> parseRandomKeyOption(String option, ObjectMapper objectMapper) {
        String json = option;
        if (option.startsWith("base64url:")) {
            try {
                json = new String(Base64.getUrlDecoder().decode(
                        option.substring("base64url:".length())), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                throw new SandboxWorkspaceException(
                        "INVALID_RANDOM_KEY", "--key base64url payload is invalid", exception);
            }
        }
        try {
            var parsed = objectMapper.readTree(json);
            if (!parsed.isObject()) {
                throw new SandboxWorkspaceException(
                        "INVALID_RANDOM_KEY", "--key must encode a JSON object");
            }
            return objectMapper.convertValue(
                    parsed, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new SandboxWorkspaceException(
                    "INVALID_RANDOM_KEY", "--key must encode a valid JSON object", exception);
        }
    }

    private static boolean isV2(Resource resource,ObjectMapper mapper) {
        try(var stream=resource.getInputStream()) {
            return SandboxRunSpecificationV2.ARTIFACT_VERSION.equals(mapper.readTree(stream).path("artifactVersion").asText());
        } catch(java.io.IOException ex) {
            throw new SandboxWorkspaceException("RUN_SPEC_READ_FAILED","Cannot read run specification",ex);
        }
    }

    private static Resource baselineResource(String location) {
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length()));
        }
        return new FileSystemResource(location);
    }
}
