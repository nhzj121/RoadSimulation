package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseData;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseDataCodec;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class SandboxWorkspacePreparerIT {

    private static final String IMAGE = "mariadb:10.4.32";
    private static final ClassPathResource BASELINE = new ClassPathResource(
            "sandbox/baseline/baseline-v1.json");

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>(DockerImageName.parse(IMAGE))
            .withDatabaseName(SandboxWorkspaceSafety.DATABASE_NAME)
            .withUsername("sandbox")
            .withPassword("sandbox")
            .withEnv("TZ", "Asia/Shanghai")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--sql-mode=NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION",
                    "--default-time-zone=+08:00");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void provisionSafetyMarker() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sandbox_workspace_marker (
                      marker_id TINYINT NOT NULL PRIMARY KEY,
                      workspace_kind VARCHAR(64) NOT NULL,
                      schema_version VARCHAR(64), control_schema_version VARCHAR(64), baseline_id VARCHAR(128),
                      restoration_payload_sha256 CHAR(64), simulation_facts_sha256 CHAR(64),
                      effective_base_data_sha256 CHAR(64), eligibility_policy_version VARCHAR(64),
                      scenario_key VARCHAR(128), scenario_revision INT,
                      scenario_definition_sha256 CHAR(64), effective_scenario_data_sha256 CHAR(64),
                      workspace_state ENUM('EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING',
                        'SCENARIO_DATA_READY','FAILED') NOT NULL,
                      prepared_at DATETIME(6), failure_code VARCHAR(80), failure_message VARCHAR(1000)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);
            statement.execute("""
                    INSERT INTO sandbox_workspace_marker(marker_id,workspace_kind,workspace_state)
                    VALUES (1,'ROAD_SIMULATION_SANDBOX','EMPTY')
                    ON DUPLICATE KEY UPDATE workspace_kind=VALUES(workspace_kind),workspace_state='EMPTY'
                    """);
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(
                    "sandbox/schema/sandbox-control-schema-v2.sql"), StandardCharsets.UTF_8));
        }
    }

    @Test
    void restoresFormalDataTwiceAndSurvivesAtoBtoA() throws Exception {
        SandboxBaselineLoader loader = new SandboxBaselineLoader(objectMapper);
        LoadedSandboxBaseline loaded = loader.load(BASELINE);
        EffectiveBaseData full = loader.selectAllEligible(loaded);
        SandboxWorkspacePreparer preparer = preparer();

        SandboxPreparationReport first = preparer.prepare(loaded, full);
        SandboxPreparationReport second = preparer.prepare(loaded, full);
        assertEquals(SandboxWorkspaceState.BASE_DATA_READY, first.state());
        assertEquals(first.effectiveBaseDataSha256(), second.effectiveBaseDataSha256());
        assertEquals(Map.of(
                "poi", 2602L, "goods", 10L, "vehicle", 85L, "processing_chain", 4L,
                "processing_stage", 15L, "processing_stage_input", 15L,
                "processing_stage_edge", 11L, "enrollment", 0L,
                "driver", 255L, "driver_vehicle", 255L), second.rowCounts());
        assertEquals(second.effectiveBaseDataSha256(), preparer.verify(BASELINE).effectiveBaseDataSha256());

        EffectiveBaseData minimal = minimalData(loaded, full);
        SandboxPreparationReport b = preparer.prepare(loaded, minimal);
        assertEquals(4L, b.rowCounts().get("poi"));
        assertEquals(3L, b.rowCounts().get("goods"));
        assertEquals(1L, b.rowCounts().get("vehicle"));

        SandboxPreparationReport restoredA = preparer.prepare(loaded, full);
        assertEquals(first.effectiveBaseDataSha256(), restoredA.effectiveBaseDataSha256());
        assertEquals(89L, nextAutoIncrement("vehicle"));
        assertEquals(5104L, nextAutoIncrement("processing_stage"));
    }

    @Test
    void failedSchemaBuildLeavesWorkspaceFailed() throws Exception {
        SandboxBaselineLoader loader = new SandboxBaselineLoader(objectMapper);
        LoadedSandboxBaseline loaded = loader.load(BASELINE);
        EffectiveBaseData full = loader.selectAllEligible(loaded);
        SandboxWorkspacePreparer broken = new SandboxWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper,
                new ByteArrayResource("CREATE TABLE broken(".getBytes(StandardCharsets.UTF_8)));

        assertThrows(SandboxWorkspaceException.class, () -> broken.prepare(loaded, full));
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT workspace_state FROM sandbox_workspace_marker WHERE marker_id=1")) {
            rows.next();
            assertEquals("FAILED", rows.getString(1));
        }
    }

    private EffectiveBaseData minimalData(LoadedSandboxBaseline loaded, EffectiveBaseData full) {
        var chain = full.data().processingChains().get(0);
        Set<Long> poiIds = chain.stages().stream()
                .map(SandboxBaselinePackageV1.ProcessingStage::processingPoiId).collect(java.util.stream.Collectors.toSet());
        Set<Long> goodsIds = new java.util.HashSet<>();
        chain.stages().forEach(stage -> {
            if (stage.legacyInputGoodsId() != null) goodsIds.add(stage.legacyInputGoodsId());
            if (stage.outputGoodsId() != null) goodsIds.add(stage.outputGoodsId());
            stage.inputs().forEach(input -> goodsIds.add(input.goodsId()));
        });
        var vehicle = full.data().vehicles().get(0);
        var bindings = full.data().driverVehicleBindings().stream()
                .filter(binding -> binding.vehicleId() == vehicle.id()).toList();
        Set<Long> driverIds = bindings.stream()
                .map(SandboxBaselinePackageV1.DriverVehicleBinding::driverId)
                .collect(java.util.stream.Collectors.toSet());
        var data = new SandboxBaselinePackageV1.Data(
                full.data().pois().stream().filter(value -> poiIds.contains(value.id())).toList(),
                full.data().goods().stream().filter(value -> goodsIds.contains(value.id())).toList(),
                java.util.List.of(vehicle),
                java.util.List.of(chain),
                java.util.List.of(),
                full.data().drivers().stream().filter(driver -> driverIds.contains(driver.id())).toList(),
                bindings);
        EffectiveBaseDataCodec codec = new EffectiveBaseDataCodec(objectMapper);
        String selection = "TEST_MINIMAL";
        String policy = loaded.baseline().eligibilityPolicy().policyVersion();
        return new EffectiveBaseData(selection, policy, codec.normalize(data), codec.hash(selection, policy, data));
    }

    private SandboxWorkspacePreparer preparer() {
        return new SandboxWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
    }

    private long nextAutoIncrement(String table) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT AUTO_INCREMENT FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?
                     """)) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("table metadata not found: " + table);
                }
                return rows.getLong(1);
            }
        }
    }
}
