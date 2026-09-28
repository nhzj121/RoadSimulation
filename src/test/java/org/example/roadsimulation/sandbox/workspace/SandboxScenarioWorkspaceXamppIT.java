package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Explicit opt-in phase-two integration test for a locally provisioned XAMPP sandbox. */
@EnabledIfEnvironmentVariable(named = "SANDBOX_XAMPP_IT", matches = "(?i)true")
class SandboxScenarioWorkspaceXamppIT {
    private static final ClassPathResource BASELINE = new ClassPathResource(
            "sandbox/baseline/baseline-v1.json");
    private static final ClassPathResource DEFAULT_SCENARIO = new ClassPathResource(
            "sandbox/scenarios/default-all-eligible-v1.json");
    private static final String TEST_KEY = "phase2-xampp-verification";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void restoresAtoBtoAAndRecoversAfterFailedPreparation() throws Exception {
        String jdbcUrl = environmentOr("SANDBOX_DB_URL", defaultUrl());
        String username = environmentOr("SANDBOX_DB_USER", "road_sandbox_runtime");
        String password = requiredEnvironment("SANDBOX_DB_PASSWORD");
        SandboxScenarioStore store = new SandboxScenarioStore(
                jdbcUrl, username, password, objectMapper);
        SandboxScenarioWorkspacePreparer healthy = new SandboxScenarioWorkspacePreparer(
                jdbcUrl, username, password, objectMapper);

        store.saveDraft(BASELINE, DEFAULT_SCENARIO);
        SandboxScenarioRevisionV1 official = store.publish(BASELINE, "all-eligible");
        try {
            SandboxScenarioDefinitionV1 aDefinition = withKey(
                    new SandboxScenarioCompiler(objectMapper).readDefinition(DEFAULT_SCENARIO), TEST_KEY);
            store.saveDraft(BASELINE, bytes(aDefinition));
            SandboxScenarioRevisionV1 a = store.publish(BASELINE, TEST_KEY);
            SandboxScenarioPreparationReport firstA = healthy.prepare(BASELINE, TEST_KEY, a.revision());

            SandboxScenarioDefinitionV1 bDefinition = minimalFixedDefinition(TEST_KEY);
            store.saveDraft(BASELINE, bytes(bDefinition));
            SandboxScenarioRevisionV1 b = store.publish(BASELINE, TEST_KEY);
            assertEquals(a.revision() + 1, b.revision());
            SandboxScenarioPreparationReport reportB = healthy.prepare(BASELINE, TEST_KEY, b.revision());
            assertEquals(1L, reportB.rowCounts().get("vehicle"));
            assertEquals(1L, reportB.rowCounts().get("enrollment"));
            assertEquals(1L, reportB.fixedVehicleCount());

            SandboxScenarioPreparationReport secondA = healthy.prepare(BASELINE, TEST_KEY, a.revision());
            assertEquals(firstA.effectiveScenarioDataSha256(), secondA.effectiveScenarioDataSha256());

            SandboxScenarioWorkspacePreparer broken = new SandboxScenarioWorkspacePreparer(
                    jdbcUrl, username, password, objectMapper,
                    new ByteArrayResource("THIS IS NOT VALID SQL;".getBytes(StandardCharsets.UTF_8)));
            assertThrows(SandboxWorkspaceException.class,
                    () -> broken.prepare(BASELINE, TEST_KEY, b.revision()));
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                 Statement statement = connection.createStatement()) {
                try (ResultSet marker = statement.executeQuery(
                        "SELECT workspace_state FROM sandbox_workspace_marker WHERE marker_id=1")) {
                    marker.next();
                    assertEquals("FAILED", marker.getString(1));
                }
                try (ResultSet revisions = statement.executeQuery(
                        "SELECT COUNT(*) FROM sandbox_scenario_revision WHERE scenario_key='" + TEST_KEY + "'")) {
                    revisions.next();
                    assertEquals(2L, revisions.getLong(1));
                }
            }

            assertEquals(SandboxWorkspaceState.SCENARIO_DATA_READY,
                    healthy.prepare(BASELINE, TEST_KEY, a.revision()).state());
        } finally {
            healthy.prepare(BASELINE, official.scenarioKey(), official.revision());
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM sandbox_scenario_revision WHERE scenario_key='" + TEST_KEY + "'");
                statement.executeUpdate(
                        "DELETE FROM sandbox_scenario WHERE scenario_key='" + TEST_KEY + "'");
            }
        }
    }

    private SandboxScenarioDefinitionV1 minimalFixedDefinition(String scenarioKey) {
        SandboxScenarioCompiler compiler = new SandboxScenarioCompiler(objectMapper);
        var loaded = new SandboxBaselineLoader(objectMapper).load(BASELINE);
        SandboxScenarioDefinitionV1 defaults = compiler.readDefinition(DEFAULT_SCENARIO);
        var all = compiler.compile(loaded, defaults).effectiveData().data();
        long vehicleId = all.vehicles().get(0).id();
        long goodsId = all.goods().get(0).id();
        long poiId = all.pois().stream().filter(poi -> "WAREHOUSE".equals(poi.poiType()))
                .findFirst().orElseThrow().id();
        return new SandboxScenarioDefinitionV1(
                SandboxScenarioDefinitionV1.ARTIFACT_VERSION,
                scenarioKey,
                "XAMPP最小固定位置验证",
                null,
                defaults.baseline(),
                new Selection(
                        new VehicleSelection(SelectionMode.EXPLICIT_IDS, List.of(vehicleId), List.of(),
                                null, null, null, null, List.of(), List.of()),
                        new PoiSelection(SelectionMode.EXPLICIT_IDS, List.of(poiId), List.of(), List.of()),
                        new GoodsSelection(SelectionMode.EXPLICIT_IDS, List.of(goodsId), List.of(), List.of()),
                        new IdSelection(SelectionMode.EXPLICIT_IDS, List.of(), List.of())),
                new Overrides(List.of(), List.of(),
                        List.of(new InitialInventory(poiId, goodsId, 5)),
                        List.of(new VehicleInitialPoi(vehicleId, poiId, VehicleInitialPoi.FIXED_POI))));
    }

    private SandboxScenarioDefinitionV1 withKey(SandboxScenarioDefinitionV1 source, String key) {
        return new SandboxScenarioDefinitionV1(
                source.artifactVersion(), key, source.displayName(), source.description(),
                source.baseline(), source.selection(), source.overrides());
    }

    private ByteArrayResource bytes(Object value) throws Exception {
        return new ByteArrayResource(objectMapper.writeValueAsBytes(value));
    }

    private static String defaultUrl() {
        return "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL";
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String environmentOr(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
