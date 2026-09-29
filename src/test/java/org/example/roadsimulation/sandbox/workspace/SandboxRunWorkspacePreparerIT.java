package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV1;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV2;
import org.example.roadsimulation.sandbox.run.SandboxRunCompilerV2;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class SandboxRunWorkspacePreparerIT {
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
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("DROP TABLE IF EXISTS sandbox_run_vehicle_initial_state");
            statement.execute("DROP TABLE IF EXISTS sandbox_run_spec_revision");
            statement.execute("DROP TABLE IF EXISTS sandbox_run_spec");
            statement.execute("DROP TABLE IF EXISTS sandbox_scenario_revision");
            statement.execute("DROP TABLE IF EXISTS sandbox_scenario");
            statement.execute("DROP TABLE IF EXISTS sandbox_workspace_marker");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
            statement.execute("""
                    CREATE TABLE sandbox_workspace_marker (
                      marker_id TINYINT NOT NULL PRIMARY KEY,
                      workspace_kind VARCHAR(64) NOT NULL,
                      schema_version VARCHAR(64),baseline_id VARCHAR(128),
                      restoration_payload_sha256 CHAR(64),simulation_facts_sha256 CHAR(64),
                      effective_base_data_sha256 CHAR(64),eligibility_policy_version VARCHAR(64),
                      workspace_state ENUM('EMPTY','PREPARING','BASE_DATA_READY','FAILED') NOT NULL,
                      prepared_at DATETIME(6),failure_code VARCHAR(80),failure_message VARCHAR(1000)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);
            statement.execute("""
                    INSERT INTO sandbox_workspace_marker(marker_id,workspace_kind,workspace_state)
                    VALUES (1,'ROAD_SIMULATION_SANDBOX','EMPTY')
                    """);
            ScriptUtils.executeSqlScript(connection, encoded("sandbox/schema/sandbox-control-schema-v2.sql"));
            ScriptUtils.executeSqlScript(connection, encoded("sandbox/schema/sandbox-control-schema-v3.sql"));
            ScriptUtils.executeSqlScript(connection, encoded("sandbox/schema/sandbox-control-schema-v4.sql"));
        }
        new SandboxWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper).prepare(BASELINE);
    }

    @Test
    void publishesDeduplicatesAndRestoresRunAtoBtoA() throws Exception {
        SandboxScenarioRevisionV1 scenario = publishScenario();
        SandboxRunSpecificationStore store = runStore();
        SandboxRunWorkspacePreparer preparer = runPreparer();

        SandboxRunSpecificationV1 aDefinition = specification(scenario, "20260927", "初始说明");
        store.saveDraft(BASELINE, bytes(aDefinition));
        SandboxRunSpecificationRevisionV1 a = store.publish(BASELINE, aDefinition.runSpecKey());
        assertEquals(a.revision(), store.publish(BASELINE, aDefinition.runSpecKey()).revision());

        SandboxRunSpecificationV1 descriptionOnly = specification(scenario, "20260927", "不同说明");
        store.saveDraft(BASELINE, bytes(descriptionOnly));
        assertEquals(a.revision(), store.publish(BASELINE, aDefinition.runSpecKey()).revision());

        SandboxRunPreparationReport firstA = preparer.prepare(BASELINE, a.runSpecKey(), a.revision());
        assertEquals(SandboxWorkspaceState.RUN_SPEC_READY, firstA.state());
        assertEquals(85L, firstA.rowCounts().get("vehicle"));
        assertEquals(0L, firstA.fixedVehicleCount());
        assertEquals(85L, firstA.randomVehicleCount());
        assertEquals(1016, firstA.eligibleVehicleInitialPoiCount());
        assertEquals(firstA.preparedRunFactsSha256(), preparer.verify(BASELINE).preparedRunFactsSha256());
        assertVehicleInitialState();

        SandboxRunSpecificationV1 bDefinition = specification(scenario, "20260928", "另一个种子");
        store.saveDraft(BASELINE, bytes(bDefinition));
        SandboxRunSpecificationRevisionV1 b = store.publish(BASELINE, bDefinition.runSpecKey());
        assertEquals(2, b.revision());
        assertNotEquals(a.fingerprints().runSpecificationSha256(), b.fingerprints().runSpecificationSha256());
        SandboxRunPreparationReport reportB = preparer.prepare(BASELINE, b.runSpecKey(), b.revision());
        assertNotEquals(firstA.preparedRunFactsSha256(), reportB.preparedRunFactsSha256());

        SandboxRunPreparationReport secondA = preparer.prepare(BASELINE, a.runSpecKey(), a.revision());
        assertEquals(firstA.preparedRunFactsSha256(), secondA.preparedRunFactsSha256());
        assertEquals(firstA.resolvedVehicleInitialStateSha256(), secondA.resolvedVehicleInitialStateSha256());
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sandbox_run_spec_revision")) {
            rows.next();
            assertEquals(2L, rows.getLong(1));
        }
    }

    @Test
    void failedPreparationPreservesRevisionsAndCanRecover() throws Exception {
        SandboxScenarioRevisionV1 scenario = publishScenario();
        SandboxRunSpecificationStore store = runStore();
        SandboxRunSpecificationV1 definition = specification(scenario, "20260927", null);
        store.saveDraft(BASELINE, bytes(definition));
        SandboxRunSpecificationRevisionV1 revision = store.publish(BASELINE, definition.runSpecKey());

        SandboxRunWorkspacePreparer broken = new SandboxRunWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper,
                new ByteArrayResource("THIS IS NOT VALID SQL;".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SandboxWorkspaceException.class,
                () -> broken.prepare(BASELINE, revision.runSpecKey(), revision.revision()));

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet marker = statement.executeQuery("""
                    SELECT workspace_state,failure_phase FROM sandbox_workspace_marker WHERE marker_id=1
                    """)) {
                marker.next();
                assertEquals("FAILED", marker.getString(1));
                assertEquals("RUN_SPEC_PREPARING", marker.getString(2));
            }
            try (ResultSet revisions = statement.executeQuery(
                    "SELECT COUNT(*) FROM sandbox_run_spec_revision")) {
                revisions.next();
                assertEquals(1L, revisions.getLong(1));
            }
        }

        assertEquals(SandboxWorkspaceState.RUN_SPEC_READY,
                runPreparer().prepare(BASELINE, revision.runSpecKey(), revision.revision()).state());
    }

    @Test void v2PreservesV1JsonAndSupportsPublishPrepareVerifyAtoBtoA() throws Exception {
        var scenario=publishScenario();var store=runStore();var preparer=runPreparer();
        var old=specification(scenario,"20260927","historical v1");
        store.saveDraft(BASELINE,bytes(old));var historical=store.publish(BASELINE,old.runSpecKey());
        String originalJson;
        try(var c=connection();var rows=c.createStatement().executeQuery("SELECT revision_json FROM sandbox_run_spec_revision WHERE revision_no=1")) {
            rows.next();originalJson=rows.getString(1);
            ScriptUtils.executeSqlScript(c,encoded("sandbox/schema/sandbox-control-schema-v4.sql"));
        }
        var template=new SandboxRunCompilerV2(objectMapper).read(new ClassPathResource("sandbox/runs/default-production-original-v2.json"));
        var aDef=new SandboxRunSpecificationV2(template.artifactVersion(),old.runSpecKey(),"v2",null,old.scenario(),old.simulationClock(),
                old.demand(),old.dispatch(),template.weather(),template.events(),old.vehicleInitialization(),old.driverBehavior(),old.random());
        store.saveDraftV2(BASELINE,bytes(aDef));var a=store.publishV2(BASELINE,aDef.runSpecKey());
        assertEquals(2,a.revision());assertEquals(a.revision(),store.publishV2(BASELINE,aDef.runSpecKey()).revision());
        assertEquals(a,store.loadRevisionV2(a.runSpecKey(),a.revision()));
        var firstA=preparer.prepare(BASELINE,a.runSpecKey(),a.revision());
        assertEquals(SandboxRunSpecificationV2.ARTIFACT_VERSION,firstA.artifactVersion());
        assertEquals(a.fingerprints().weatherTimelineSha256(),firstA.weatherTimelineSha256());
        assertEquals(firstA.preparedRunFactsSha256(),preparer.verify(BASELINE).preparedRunFactsSha256());
        var runtimeConfig=new org.example.roadsimulation.config.SimulationRuntimeConfig();
        var clock=new org.example.roadsimulation.core.SimulationContext();
        var runtime=new org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(MARIADB.getJdbcUrl(),MARIADB.getUsername(),MARIADB.getPassword()),
                objectMapper,runtimeConfig,clock,false);
        runtime.loadAndFreeze();
        assertEquals(a,runtime.revisionV2());assertEquals(a.specification().simulationClock(),runtime.simulationClock());
        assertEquals(firstA.deterministicSimulationRunId(),runtime.deterministicSimulationRunId());
        var domain=org.example.roadsimulation.sandbox.random.SandboxRandomDomain.DRIVER_BEHAVIOR_TRANSITION;
        var key=java.util.Map.of("driverId",1L,"loopIndex",2);
        var protocol=new SandboxRandomProtocol(objectMapper);String root=a.specification().random().rootSeed();
        assertEquals(protocol.deriveSeed(root,domain,key),runtime.deriveSeed(domain,key));
        assertEquals(protocol.deriveSeedHex(root,domain,key),runtime.deriveSeedHex(domain,key));
        assertEquals(protocol.random(root,domain,key).nextLong(),runtime.random(domain,key).nextLong());
        assertEquals(protocol.random(root,domain,key).nextInt(1000),runtime.javaRandom(domain,key).nextInt(1000));
        assertEquals("PERIODIC_ENVIRONMENT_DISABLED",assertThrows(SandboxWorkspaceException.class,runtime::environmentPhaseSeed).errorCode());
        assertEquals(0,clock.getLoopCount());assertEquals(false,clock.isRunning());
        var off=new SandboxRunSpecificationV2.Events(template.events().ruleVersion(),false,false,"DERIVED_FROM_ROOT",
                template.events().congestion(),template.events().breakdown(),template.events().breakdownPolicy());
        var bDef=new SandboxRunSpecificationV2(aDef.artifactVersion(),aDef.runSpecKey(),aDef.displayName(),null,aDef.scenario(),
                aDef.simulationClock(),aDef.demand(),aDef.dispatch(),aDef.weather(),off,aDef.vehicleInitialization(),aDef.driverBehavior(),aDef.random());
        store.saveDraftV2(BASELINE,bytes(bDef));var b=store.publishV2(BASELINE,bDef.runSpecKey());
        assertEquals(3,b.revision());preparer.prepare(BASELINE,b.runSpecKey(),b.revision());
        var secondA=preparer.prepare(BASELINE,a.runSpecKey(),a.revision());
        assertEquals(firstA.preparedRunFactsSha256(),secondA.preparedRunFactsSha256());
        assertEquals(firstA.weatherTimelineSha256(),secondA.weatherTimelineSha256());
        assertEquals(historical,store.loadRevision(old.runSpecKey(),1));
        try(var c=connection();var rows=c.createStatement().executeQuery("SELECT revision_json FROM sandbox_run_spec_revision WHERE revision_no=1")) {
            rows.next();assertEquals(originalJson,rows.getString(1));
        }
        try(var c=connection();var s=c.createStatement()) {
            s.execute("UPDATE sandbox_workspace_marker SET weather_timeline_sha256=REPEAT('0',64)");
            assertEquals("RUN_MARKER_MISMATCH",assertThrows(SandboxWorkspaceException.class,()->preparer.verify(BASELINE)).errorCode());
        }
        assertEquals(SandboxWorkspaceState.RUN_SPEC_READY,preparer.prepare(BASELINE,a.runSpecKey(),a.revision()).state());
        new SandboxWorkspacePreparer(MARIADB.getJdbcUrl(),MARIADB.getUsername(),MARIADB.getPassword(),objectMapper).prepare(BASELINE);
        try(var c=connection();var rows=c.createStatement().executeQuery(
                "SELECT run_spec_key,run_artifact_version,weather_timeline_sha256 FROM sandbox_workspace_marker WHERE marker_id=1")) {
            rows.next();assertEquals(null,rows.getString(1));assertEquals(null,rows.getString(2));assertEquals(null,rows.getString(3));
        }
        assertEquals(a,store.loadRevisionV2(a.runSpecKey(),a.revision()));
    }

    private SandboxScenarioRevisionV1 publishScenario() {
        SandboxScenarioStore scenarios = new SandboxScenarioStore(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
        scenarios.saveDraft(BASELINE, DEFAULT_SCENARIO);
        SandboxScenarioRevisionV1 revision = scenarios.publish(BASELINE, "all-eligible");
        new SandboxScenarioWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper)
                .prepare(BASELINE, revision.scenarioKey(), revision.revision());
        return revision;
    }

    private SandboxRunSpecificationV1 specification(
            SandboxScenarioRevisionV1 scenario,
            String rootSeed,
            String description
    ) {
        return new SandboxRunSpecificationV1(
                SandboxRunSpecificationV1.ARTIFACT_VERSION,
                "production-baseline",
                "生产需求确定性运行",
                description,
                new SandboxRunSpecificationV1.ScenarioReference(
                        scenario.scenarioKey(), scenario.revision(),
                        scenario.fingerprints().scenarioDefinitionSha256(),
                        scenario.fingerprints().effectiveScenarioDataSha256()),
                new SandboxRunSpecificationV1.SimulationClock(
                        LocalDateTime.of(2026, 1, 1, 0, 0), 1800, 48),
                new SandboxRunSpecificationV1.Demand("PRODUCTION", 6, false),
                new SandboxRunSpecificationV1.Dispatch("ORIGINAL", 3, "original-vrp/v1"),
                new SandboxRunSpecificationV1.Environment(
                        "deterministic-network-cycle", "1", 100, 60.0,
                        "PROGRESS_AFFECTING", "DERIVED_FROM_ROOT"),
                new SandboxRunSpecificationV1.VehicleInitialization(
                        "RANDOM_ELIGIBLE_POI",
                        List.of("WAREHOUSE", "DISTRIBUTION_CENTER"),
                        "CURRENT_POI"),
                new SandboxRunSpecificationV1.RandomProtocol(
                        SandboxRandomProtocol.PROTOCOL_ID, rootSeed));
    }

    private void assertVehicleInitialState() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(*),SUM(current_poi_id IS NOT NULL),
                            SUM(current_longitude IS NOT NULL OR current_latitude IS NOT NULL),
                            SUM(current_status='IDLE')
                     FROM vehicle
                     """)) {
            rows.next();
            assertEquals(85L, rows.getLong(1));
            assertEquals(85L, rows.getLong(2));
            assertEquals(0L, rows.getLong(3));
            assertEquals(85L, rows.getLong(4));
        }
    }

    private SandboxRunSpecificationStore runStore() {
        return new SandboxRunSpecificationStore(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
    }

    private SandboxRunWorkspacePreparer runPreparer() {
        return new SandboxRunWorkspacePreparer(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), objectMapper);
    }

    private ByteArrayResource bytes(Object value) throws Exception {
        return new ByteArrayResource(objectMapper.writeValueAsBytes(value));
    }

    private EncodedResource encoded(String path) {
        return new EncodedResource(new ClassPathResource(path), StandardCharsets.UTF_8);
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
    }
}
