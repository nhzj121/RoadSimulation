package org.example.roadsimulation.sandbox.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.example.roadsimulation.sandbox.run.CompiledSandboxRunSpecification;
import org.example.roadsimulation.sandbox.run.SandboxRunCompiler;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
import org.example.roadsimulation.sandbox.scenario.definition.CompiledSandboxScenario;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCodec;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Explicit, read-only source capture tool for the one-time driver extension of baseline v1.
 * It is skipped in normal test runs and never executes SQL that mutates the source database.
 */
class SandboxBaselineDriverCaptureToolTest {

    private static final List<String> COUNT_TABLES = List.of(
            "poi", "goods", "vehicle", "enrollment", "processing_chain",
            "processing_stage", "processing_stage_input", "processing_stage_edge",
            "driver", "driver_vehicle");

    @Test
    void rebuildsFormalV1WithCurrentDriverFacts() throws Exception {
        assumeTrue(Boolean.getBoolean("sandbox.baseline.capture.enabled"),
                "manual baseline capture is disabled");

        String jdbcUrl = System.getProperty("sandbox.baseline.capture.jdbc",
                "jdbc:mysql://127.0.0.1:3306/vehicle_scheduler"
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");
        String username = System.getProperty("sandbox.baseline.capture.username", "root");
        String password = System.getProperty("sandbox.baseline.capture.password", "");
        String capturedAt = requiredProperty("sandbox.baseline.capture.at");
        String baselineId = requiredProperty("sandbox.baseline.capture.id");
        String codeCommit = requiredProperty("sandbox.baseline.capture.commit");

        Path project = Path.of(System.getProperty("user.dir"));
        Path baselinePath = project.resolve("src/main/resources/sandbox/baseline/baseline-v1.json");
        Path archivePath = project.resolve(
                "src/main/resources/sandbox/baseline/archive/baseline-v1-pre-driver-20260928.json");
        Path scenarioPath = project.resolve(
                "src/main/resources/sandbox/scenarios/default-all-eligible-v1.json");
        Path runPath = project.resolve(
                "src/main/resources/sandbox/runs/default-production-original-v1.json");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ObjectNode baselineRoot = (ObjectNode) mapper.readTree(baselinePath.toFile());

        Files.createDirectories(archivePath.getParent());
        if (Files.notExists(archivePath)) {
            Files.copy(baselinePath, archivePath, StandardCopyOption.COPY_ATTRIBUTES);
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertEquals("vehicle_scheduler", connection.getCatalog(),
                    "capture must target the formal source database exactly");
            connection.setReadOnly(true);

            baselineRoot.put("baselineId", baselineId);
            ObjectNode source = (ObjectNode) baselineRoot.required("source");
            source.put("capturedAtUtc", capturedAt);
            source.put("captureMethod",
                    "previous formal baseline facts plus read-only driver snapshot; all source row counts revalidated");
            source.put("codeCommitObserved", codeCommit);
            source.put("codeWorktreeFrozen", false);
            source.set("tableRowCounts", sourceCounts(mapper, connection));

            ArrayNode excluded = (ArrayNode) baselineRoot.required("excludedSourceDomains");
            for (int index = excluded.size() - 1; index >= 0; index--) {
                if (List.of("driver", "driver_vehicle").contains(excluded.get(index).asText())) {
                    excluded.remove(index);
                }
            }

            ObjectNode data = (ObjectNode) baselineRoot.required("data");
            data.set("drivers", drivers(mapper, connection));
            data.set("driverVehicleBindings", bindings(mapper, connection));
        }

        // Hash the exact JSON representation that future processes will parse. JDBC BigDecimal
        // node scale can otherwise produce a one-run-only hash that changes after serialization.
        writePretty(mapper, baselinePath, baselineRoot);
        baselineRoot = (ObjectNode) mapper.readTree(baselinePath.toFile());
        LexicographicJsonSha256 hasher = new LexicographicJsonSha256(mapper);
        SandboxBaselineLoader loader = new SandboxBaselineLoader(mapper);
        ObjectNode fingerprints = (ObjectNode) baselineRoot.required("fingerprints");
        fingerprints.put("restorationPayloadSha256", hasher.hash(baselineRoot.required("data")));
        fingerprints.put("simulationFactsSha256",
                hasher.hash(loader.simulationFactsProjection(baselineRoot)));
        writePretty(mapper, baselinePath, baselineRoot);

        LoadedSandboxBaseline loaded = loader.load(new FileSystemResource(baselinePath));
        EffectiveBaseData effective = loader.selectAllEligible(loaded);
        assertEquals(255, effective.data().drivers().size());
        assertEquals(255, effective.data().driverVehicleBindings().size());

        ObjectNode scenarioRoot = (ObjectNode) mapper.readTree(scenarioPath.toFile());
        ObjectNode baselineReference = (ObjectNode) scenarioRoot.required("baseline");
        baselineReference.put("baselineId", loaded.baseline().baselineId());
        baselineReference.put("restorationPayloadSha256",
                loaded.baseline().fingerprints().restorationPayloadSha256());
        baselineReference.put("simulationFactsSha256",
                loaded.baseline().fingerprints().simulationFactsSha256());
        baselineReference.put("effectiveBaseDataSha256", effective.effectiveBaseDataSha256());
        writePretty(mapper, scenarioPath, scenarioRoot);

        SandboxScenarioCompiler scenarioCompiler = new SandboxScenarioCompiler(mapper);
        CompiledSandboxScenario scenario = scenarioCompiler.compile(
                new FileSystemResource(baselinePath), new FileSystemResource(scenarioPath));

        ObjectNode runRoot = (ObjectNode) mapper.readTree(runPath.toFile());
        ObjectNode runScenario = (ObjectNode) runRoot.required("scenario");
        runScenario.put("scenarioDefinitionSha256", scenario.scenarioDefinitionSha256());
        runScenario.put("effectiveScenarioDataSha256",
                scenario.effectiveData().effectiveScenarioDataSha256());
        writePretty(mapper, runPath, runRoot);

        SandboxScenarioRevisionV1 revision = new SandboxScenarioRevisionV1(
                SandboxScenarioRevisionV1.ARTIFACT_VERSION,
                scenario.normalizedDefinition().scenarioKey(),
                1,
                scenario.normalizedDefinition(),
                scenario.resolvedSelection(),
                new SandboxScenarioRevisionV1.Fingerprints(
                        SandboxScenarioCodec.CANONICALIZATION,
                        scenario.scenarioDefinitionSha256(),
                        scenario.effectiveData().baseDataProjectionSha256(),
                        scenario.effectiveData().effectiveScenarioDataSha256()),
                Instant.parse(capturedAt));
        SandboxRunCompiler runCompiler = new SandboxRunCompiler(mapper);
        SandboxRunSpecificationV1 run = runCompiler.read(new FileSystemResource(runPath));
        CompiledSandboxRunSpecification compiledRun = runCompiler.compile(run, revision, scenario);

        assertTrue(Files.size(archivePath) > 0);
        System.out.printf("baselineId=%s%nrestorationPayloadSha256=%s%nsimulationFactsSha256=%s%n"
                        + "effectiveBaseDataSha256=%s%nscenarioDefinitionSha256=%s%n"
                        + "effectiveScenarioDataSha256=%s%nrunSpecificationSha256=%s%n",
                loaded.baseline().baselineId(),
                loaded.baseline().fingerprints().restorationPayloadSha256(),
                loaded.baseline().fingerprints().simulationFactsSha256(),
                effective.effectiveBaseDataSha256(),
                scenario.scenarioDefinitionSha256(),
                scenario.effectiveData().effectiveScenarioDataSha256(),
                compiledRun.runSpecificationSha256());
    }

    private ObjectNode sourceCounts(ObjectMapper mapper, Connection connection) throws Exception {
        ObjectNode counts = mapper.createObjectNode();
        try (Statement statement = connection.createStatement()) {
            for (String table : COUNT_TABLES) {
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                    assertTrue(result.next());
                    counts.put(table, result.getInt(1));
                }
            }
        }
        return counts;
    }

    private ArrayNode drivers(ObjectMapper mapper, Connection connection) throws Exception {
        ArrayNode result = mapper.createArrayNode();
        String sql = "SELECT id, driver_name, driver_phone, pref_cargo, "
                + "pref_max_distance_km, pref_max_weight_tons FROM driver ORDER BY id";
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                ObjectNode driver = result.addObject();
                driver.put("id", rows.getLong("id"));
                putNullable(driver, "driverName", rows.getString("driver_name"));
                putNullable(driver, "driverPhone", rows.getString("driver_phone"));
                putNullable(driver, "preferredCargoType", rows.getString("pref_cargo"));
                putNullable(driver, "preferredMaxDistanceKm",
                        rows.getBigDecimal("pref_max_distance_km"));
                putNullable(driver, "preferredMaxWeightTons",
                        rows.getBigDecimal("pref_max_weight_tons"));
            }
        }
        return result;
    }

    private ArrayNode bindings(ObjectMapper mapper, Connection connection) throws Exception {
        ArrayNode result = mapper.createArrayNode();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT driver_id, vehicle_id FROM driver_vehicle ORDER BY vehicle_id, driver_id")) {
            while (rows.next()) {
                ObjectNode binding = result.addObject();
                binding.put("driverId", rows.getLong("driver_id"));
                binding.put("vehicleId", rows.getLong("vehicle_id"));
            }
        }
        return result;
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private void putNullable(ObjectNode node, String field, java.math.BigDecimal value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value.trim();
    }

    private void writePretty(ObjectMapper mapper, Path path, JsonNode value) throws Exception {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", System.lineSeparator());
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        String json = mapper.writer(printer).writeValueAsString(value)
                .replace("\" : ", "\": ")
                .replace("[ ]", "[]");
        Files.writeString(path, json + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
