package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class SandboxScenarioWorkspacePreparerIT {
    private static final ClassPathResource BASELINE = new ClassPathResource(
            "sandbox/baseline/baseline-v1.json");
    private static final ClassPathResource DEFAULT_SCENARIO = new ClassPathResource(
            "sandbox/scenarios/default-all-eligible-v1.json");

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>(DockerImageName.parse("mariadb:10.4.32"))
            .withDatabaseName(SandboxWorkspaceSafety.DATABASE_NAME)
            .withUsername("sandbox")
            .withPassword("sandbox")
            .withEnv("TZ", "Asia/Shanghai")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_general_ci",
                    "--sql-mode=NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION",
                    "--default-time-zone=+08:00");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void provision() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sandbox_workspace_marker (
                      marker_id TINYINT NOT NULL PRIMARY KEY,
                      workspace_kind VARCHAR(64) NOT NULL,
                      schema_version VARCHAR(64),control_schema_version VARCHAR(64),baseline_id VARCHAR(128),
                      restoration_payload_sha256 CHAR(64),simulation_facts_sha256 CHAR(64),
                      effective_base_data_sha256 CHAR(64),eligibility_policy_version VARCHAR(64),
                      scenario_key VARCHAR(128),scenario_revision INT,scenario_definition_sha256 CHAR(64),
                      effective_scenario_data_sha256 CHAR(64),
                      workspace_state ENUM('EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING',
                        'SCENARIO_DATA_READY','FAILED') NOT NULL,
                      prepared_at DATETIME(6),failure_code VARCHAR(80),failure_message VARCHAR(1000)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);
            statement.execute("""
                    INSERT INTO sandbox_workspace_marker(marker_id,workspace_kind,workspace_state)
                    VALUES (1,'ROAD_SIMULATION_SANDBOX','EMPTY')
                    ON DUPLICATE KEY UPDATE workspace_kind=VALUES(workspace_kind),workspace_state='EMPTY'
                    """);
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(
                    "sandbox/schema/sandbox-control-schema-v2.sql"), StandardCharsets.UTF_8));
            statement.execute("DELETE FROM sandbox_scenario_revision");
            statement.execute("DELETE FROM sandbox_scenario");
        }
        new SandboxWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper).prepare(BASELINE);
    }

    @Test
    void publishesDeduplicatesAndRestoresAtoBtoA() throws Exception {
        SandboxScenarioStore store = new SandboxScenarioStore(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
        SandboxScenarioWorkspacePreparer preparer = new SandboxScenarioWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);

        store.saveDraft(BASELINE, DEFAULT_SCENARIO);
        SandboxScenarioRevisionV1 a = store.publish(BASELINE, "all-eligible");
        assertEquals(a.revision(), store.publish(BASELINE, "all-eligible").revision());

        SandboxScenarioDefinitionV1 descriptiveChange = withDisplayMetadata(
                compiler().readDefinition(DEFAULT_SCENARIO), "新的显示名称", "不影响数据的说明");
        store.saveDraft(BASELINE, bytes(descriptiveChange));
        assertEquals(a.revision(), store.publish(BASELINE, "all-eligible").revision());

        SandboxScenarioDefinitionV1 dataChange = withFirstStageRatio(
                compiler().readDefinition(DEFAULT_SCENARIO), new BigDecimal("0.95"));
        store.saveDraft(BASELINE, bytes(dataChange));
        SandboxScenarioRevisionV1 changed = store.publish(BASELINE, "all-eligible");
        assertEquals(2, changed.revision());

        SandboxScenarioPreparationReport firstA = preparer.prepare(BASELINE, "all-eligible", a.revision());
        assertEquals(SandboxWorkspaceState.SCENARIO_DATA_READY, firstA.state());
        assertEquals(2602L, firstA.rowCounts().get("poi"));
        assertEquals(firstA.effectiveScenarioDataSha256(), preparer.verify(BASELINE).effectiveScenarioDataSha256());

        SandboxScenarioDefinitionV1 bDefinition = minimalFixedDefinition();
        store.saveDraft(BASELINE, bytes(bDefinition));
        SandboxScenarioRevisionV1 b = store.publish(BASELINE, bDefinition.scenarioKey());
        SandboxScenarioPreparationReport reportB = preparer.prepare(BASELINE, b.scenarioKey(), b.revision());
        assertEquals(1L, reportB.rowCounts().get("vehicle"));
        assertEquals(1L, reportB.rowCounts().get("enrollment"));
        assertEquals(1L, reportB.fixedVehicleCount());
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(*),SUM(current_poi_id IS NOT NULL),
                            SUM(current_longitude IS NOT NULL OR current_latitude IS NOT NULL)
                     FROM vehicle
                     """)) {
            rows.next();
            assertEquals(1L, rows.getLong(1));
            assertEquals(1L, rows.getLong(2));
            assertEquals(0L, rows.getLong(3));
        }

        SandboxScenarioPreparationReport secondA = preparer.prepare(BASELINE, "all-eligible", a.revision());
        assertEquals(firstA.effectiveScenarioDataSha256(), secondA.effectiveScenarioDataSha256());
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sandbox_scenario_revision")) {
            rows.next();
            assertEquals(3L, rows.getLong(1));
        }
    }

    @Test
    void failedPreparationPreservesPublishedHistoryAndCanBeRecovered() throws Exception {
        SandboxScenarioStore store = new SandboxScenarioStore(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
        store.saveDraft(BASELINE, DEFAULT_SCENARIO);
        SandboxScenarioRevisionV1 revision = store.publish(BASELINE, "all-eligible");

        SandboxScenarioWorkspacePreparer broken = new SandboxScenarioWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper,
                new ByteArrayResource("THIS IS NOT VALID SQL;".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SandboxWorkspaceException.class,
                () -> broken.prepare(BASELINE, revision.scenarioKey(), revision.revision()));

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet marker = statement.executeQuery(
                    "SELECT workspace_state FROM sandbox_workspace_marker WHERE marker_id=1")) {
                marker.next();
                assertEquals("FAILED", marker.getString(1));
            }
            try (ResultSet revisions = statement.executeQuery(
                    "SELECT COUNT(*) FROM sandbox_scenario_revision WHERE scenario_key='all-eligible'")) {
                revisions.next();
                assertEquals(1L, revisions.getLong(1));
            }
        }

        SandboxScenarioWorkspacePreparer healthy = new SandboxScenarioWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
        assertEquals(SandboxWorkspaceState.SCENARIO_DATA_READY,
                healthy.prepare(BASELINE, revision.scenarioKey(), revision.revision()).state());
    }

    private SandboxScenarioCompiler compiler() {
        return new SandboxScenarioCompiler(objectMapper);
    }

    private SandboxScenarioDefinitionV1 withDisplayMetadata(
            SandboxScenarioDefinitionV1 source,
            String displayName,
            String description
    ) {
        return new SandboxScenarioDefinitionV1(
                source.artifactVersion(), source.scenarioKey(), displayName, description,
                source.baseline(), source.selection(), source.overrides());
    }

    private SandboxScenarioDefinitionV1 withFirstStageRatio(
            SandboxScenarioDefinitionV1 source,
            BigDecimal ratio
    ) {
        long stageId = new SandboxBaselineLoader(objectMapper).load(BASELINE).baseline()
                .data().processingChains().get(0).stages().get(0).id();
        Overrides overrides = new Overrides(
                List.of(new StageOutputRatio(stageId, ratio)), List.of(), List.of(), List.of());
        return new SandboxScenarioDefinitionV1(
                source.artifactVersion(), source.scenarioKey(), source.displayName(), source.description(),
                source.baseline(), source.selection(), overrides);
    }

    private SandboxScenarioDefinitionV1 minimalFixedDefinition() {
        SandboxScenarioCompiler compiler = new SandboxScenarioCompiler(objectMapper);
        var loaded = new SandboxBaselineLoader(objectMapper).load(BASELINE);
        SandboxScenarioDefinitionV1 defaultDefinition = compiler.readDefinition(DEFAULT_SCENARIO);
        var all = compiler.compile(loaded, defaultDefinition).effectiveData().data();
        long vehicleId = all.vehicles().get(0).id();
        long goodsId = all.goods().get(0).id();
        long poiId = all.pois().stream().filter(poi -> "WAREHOUSE".equals(poi.poiType()))
                .findFirst().orElseThrow().id();
        return new SandboxScenarioDefinitionV1(
                SandboxScenarioDefinitionV1.ARTIFACT_VERSION,
                "minimal-fixed",
                "最小固定位置场景",
                null,
                defaultDefinition.baseline(),
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

    private ByteArrayResource bytes(Object value) throws Exception {
        return new ByteArrayResource(objectMapper.writeValueAsBytes(value));
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
    }
}
