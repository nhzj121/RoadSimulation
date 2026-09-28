package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV1;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Explicit opt-in phase-three verification against the provisioned XAMPP sandbox only. */
@EnabledIfEnvironmentVariable(named = "SANDBOX_XAMPP_IT", matches = "(?i)true")
class SandboxRunWorkspaceXamppIT {
    private static final ClassPathResource BASELINE = new ClassPathResource(
            "sandbox/baseline/baseline-v1.json");
    private static final ClassPathResource SCENARIO = new ClassPathResource(
            "sandbox/scenarios/default-all-eligible-v1.json");
    private static final ClassPathResource RUN_SPEC = new ClassPathResource(
            "sandbox/runs/default-production-original-v1.json");

    @Test
    void preparesAndVerifiesOfficialRunSpecification() throws Exception {
        String url = environmentOr("SANDBOX_DB_URL", defaultUrl());
        String user = environmentOr("SANDBOX_DB_USER", "road_sandbox_runtime");
        String password = requiredEnvironment("SANDBOX_DB_PASSWORD");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        new SandboxWorkspacePreparer(url, user, password, mapper).prepare(BASELINE);
        SandboxScenarioStore scenarios = new SandboxScenarioStore(url, user, password, mapper);
        scenarios.saveDraft(BASELINE, SCENARIO);
        SandboxScenarioRevisionV1 scenario = scenarios.publish(BASELINE, "all-eligible");
        new SandboxScenarioWorkspacePreparer(url, user, password, mapper)
                .prepare(BASELINE, scenario.scenarioKey(), scenario.revision());

        SandboxRunSpecificationStore runs = new SandboxRunSpecificationStore(url, user, password, mapper);
        SandboxRunSpecificationV1 template = mapper.readValue(
                RUN_SPEC.getInputStream(), SandboxRunSpecificationV1.class);
        SandboxRunSpecificationV1 resolvedTemplate = withScenarioRevision(template, scenario);
        runs.saveDraft(BASELINE, new ByteArrayResource(mapper.writeValueAsBytes(resolvedTemplate)));
        SandboxRunSpecificationRevisionV1 revision = runs.publish(
                BASELINE, "production-baseline-seed-20260927");
        SandboxRunWorkspacePreparer preparer = new SandboxRunWorkspacePreparer(
                url, user, password, mapper);
        SandboxRunPreparationReport prepared = preparer.prepare(
                BASELINE, revision.runSpecKey(), revision.revision());
        SandboxRunPreparationReport verified = preparer.verify(BASELINE);

        assertEquals(SandboxWorkspaceState.RUN_SPEC_READY, prepared.state());
        assertEquals(prepared.preparedRunFactsSha256(), verified.preparedRunFactsSha256());
        assertEquals(85L, prepared.rowCounts().get("vehicle"));
        assertEquals(85L, prepared.randomVehicleCount());
        assertEquals(1016, prepared.eligibleVehicleInitialPoiCount());

        SandboxRunSpecificationV1 bDefinition = withRootSeed(
                revision.specification(), "20260928");
        runs.saveDraft(BASELINE, new ByteArrayResource(mapper.writeValueAsBytes(bDefinition)));
        SandboxRunSpecificationRevisionV1 revisionB = runs.publish(
                BASELINE, revision.runSpecKey());
        SandboxRunPreparationReport preparedB = preparer.prepare(
                BASELINE, revisionB.runSpecKey(), revisionB.revision());
        assertNotEquals(prepared.preparedRunFactsSha256(), preparedB.preparedRunFactsSha256());

        SandboxRunPreparationReport restoredA = preparer.prepare(
                BASELINE, revision.runSpecKey(), revision.revision());
        assertEquals(prepared.preparedRunFactsSha256(), restoredA.preparedRunFactsSha256());
        assertEquals(prepared.resolvedVehicleInitialStateSha256(),
                restoredA.resolvedVehicleInitialStateSha256());

        SandboxRunWorkspacePreparer broken = new SandboxRunWorkspacePreparer(
                url, user, password, mapper,
                new ByteArrayResource("THIS IS NOT VALID SQL;".getBytes(StandardCharsets.UTF_8)));
        long revisionCountBeforeFailure = revisionCount(url, user, password);
        assertThrows(SandboxWorkspaceException.class,
                () -> broken.prepare(BASELINE, revision.runSpecKey(), revision.revision()));
        assertFailedMarkerAndPreservedRevisions(
                url, user, password, revisionCountBeforeFailure);

        SandboxRunPreparationReport recoveredA = preparer.prepare(
                BASELINE, revision.runSpecKey(), revision.revision());
        assertEquals(SandboxWorkspaceState.RUN_SPEC_READY, recoveredA.state());
        assertEquals(prepared.preparedRunFactsSha256(), recoveredA.preparedRunFactsSha256());
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
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

    private SandboxRunSpecificationV1 withRootSeed(
            SandboxRunSpecificationV1 source,
            String rootSeed
    ) {
        return new SandboxRunSpecificationV1(
                source.artifactVersion(), source.runSpecKey(), source.displayName(), source.description(),
                source.scenario(), source.simulationClock(), source.demand(), source.dispatch(),
                source.environment(), source.vehicleInitialization(), source.driverBehavior(),
                new SandboxRunSpecificationV1.RandomProtocol(
                        source.random().protocolId(), rootSeed));
    }

    private SandboxRunSpecificationV1 withScenarioRevision(
            SandboxRunSpecificationV1 source,
            SandboxScenarioRevisionV1 scenario
    ) {
        return new SandboxRunSpecificationV1(
                source.artifactVersion(), source.runSpecKey(), source.displayName(), source.description(),
                new SandboxRunSpecificationV1.ScenarioReference(
                        scenario.scenarioKey(), scenario.revision(),
                        scenario.fingerprints().scenarioDefinitionSha256(),
                        scenario.fingerprints().effectiveScenarioDataSha256()),
                source.simulationClock(), source.demand(), source.dispatch(), source.environment(),
                source.vehicleInitialization(), source.driverBehavior(), source.random());
    }

    private void assertFailedMarkerAndPreservedRevisions(
            String url,
            String user,
            String password,
            long expectedRevisionCount
    ) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet marker = statement.executeQuery("""
                    SELECT workspace_state,failure_phase
                    FROM sandbox_workspace_marker WHERE marker_id=1
                    """)) {
                marker.next();
                assertEquals("FAILED", marker.getString(1));
                assertEquals("RUN_SPEC_PREPARING", marker.getString(2));
            }
            try (ResultSet revisions = statement.executeQuery(
                    "SELECT COUNT(*) FROM sandbox_run_spec_revision")) {
                revisions.next();
                assertEquals(expectedRevisionCount, revisions.getLong(1));
            }
        }
    }

    private long revisionCount(String url, String user, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet revisions = statement.executeQuery(
                     "SELECT COUNT(*) FROM sandbox_run_spec_revision")) {
            revisions.next();
            return revisions.getLong(1);
        }
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

    private static String environmentOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
