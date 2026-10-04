package org.example.roadsimulation.sandbox.scenario.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** JDBC control-plane storage for mutable drafts and immutable published revisions. */
public final class SandboxScenarioStore {
    public static final String CONTROL_SCHEMA_VERSION = "sandbox-control-schema/v2";
    public static final String RUN_CONTROL_SCHEMA_VERSION = "sandbox-control-schema/v3";

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    private final SandboxBaselineLoader baselineLoader;
    private final SandboxScenarioCompiler compiler;

    public SandboxScenarioStore(
            String jdbcUrl,
            String username,
            String password,
            ObjectMapper objectMapper
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.baselineLoader = new SandboxBaselineLoader(objectMapper);
        this.compiler = new SandboxScenarioCompiler(objectMapper);
    }

    public SandboxScenarioCompilationReport compile(Resource baseline, Resource scenario) {
        CompiledSandboxScenario compiled = compiler.compile(baseline, scenario);
        return report(compiled);
    }

    public SandboxScenarioCompilationReport saveDraft(Resource baselineResource, Resource scenarioResource) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        CompiledSandboxScenario compiled = compiler.compile(baseline, compiler.readDefinition(scenarioResource));
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            String json = json(compiled.normalizedDefinition());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sandbox_scenario(
                        scenario_key,display_name,description,draft_json,draft_updated_at,row_version,archived
                    ) VALUES (?,?,?,?,CURRENT_TIMESTAMP(6),0,b'0')
                    ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),description=VALUES(description),
                        draft_json=VALUES(draft_json),draft_updated_at=CURRENT_TIMESTAMP(6),
                        row_version=row_version+1,archived=b'0'
                    """)) {
                statement.setString(1, compiled.normalizedDefinition().scenarioKey());
                statement.setString(2, compiled.normalizedDefinition().displayName());
                statement.setString(3, compiled.normalizedDefinition().description());
                statement.setString(4, json);
                statement.executeUpdate();
            }
            return report(compiled);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("SCENARIO_DRAFT_SAVE_FAILED", "Cannot save scenario draft", exception);
        }
    }

    public SandboxScenarioRevisionV1 publish(Resource baselineResource, String scenarioKey) {
        return publish(baselineResource, scenarioKey, null, null);
    }

    public org.example.roadsimulation.sandbox.workspace.SandboxDraftGuard.Saved<SandboxScenarioCompilationReport>
            saveDraft(Resource baselineResource, Resource scenarioResource, long expectedVersion) {
        var baseline = baselineLoader.load(baselineResource);
        var compiled = compiler.compile(baseline, compiler.readDefinition(scenarioResource));
        var definition = compiled.normalizedDefinition();
        try (var connection = openConnection()) {
            configure(connection); safety.requireSafeTarget(jdbcUrl, connection); requireControlSchema(connection);
            long version = org.example.roadsimulation.sandbox.workspace.SandboxDraftGuard.save(connection, true,
                    definition.scenarioKey(), definition.displayName(), definition.description(), json(definition), expectedVersion);
            return new org.example.roadsimulation.sandbox.workspace.SandboxDraftGuard.Saved<>(version, report(compiled));
        } catch (SQLException failure) {
            throw new SandboxWorkspaceException("SCENARIO_DRAFT_SAVE_FAILED", "Cannot save scenario draft", failure);
        }
    }

    public SandboxScenarioRevisionV1 publish(Resource baselineResource, String scenarioKey,
                                            Long expectedVersion, String expectedHash) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            connection.setAutoCommit(false);
            try {
                SandboxScenarioDefinitionV1 draft = readDraftForUpdate(connection, scenarioKey);
                CompiledSandboxScenario compiled = compiler.compile(baseline, draft);
                try (var statement = connection.prepareStatement("SELECT row_version FROM sandbox_scenario WHERE scenario_key=?")) {
                    statement.setString(1, scenarioKey);
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        org.example.roadsimulation.sandbox.workspace.SandboxDraftGuard.publishing(
                                expectedVersion, expectedHash, rows.getLong(1), compiled.scenarioDefinitionSha256());
                    }
                }
                SandboxScenarioRevisionV1 existing = findByDefinitionHash(
                        connection, scenarioKey, compiled.scenarioDefinitionSha256());
                if (existing != null) {
                    connection.commit();
                    return existing;
                }
                int revisionNumber = nextRevision(connection, scenarioKey);
                Instant publishedAt = Instant.now();
                SandboxScenarioRevisionV1 revision = new SandboxScenarioRevisionV1(
                        SandboxScenarioRevisionV1.ARTIFACT_VERSION,
                        scenarioKey,
                        revisionNumber,
                        compiled.normalizedDefinition(),
                        compiled.resolvedSelection(),
                        new SandboxScenarioRevisionV1.Fingerprints(
                                SandboxScenarioCodec.CANONICALIZATION,
                                compiled.scenarioDefinitionSha256(),
                                compiled.effectiveData().baseDataProjectionSha256(),
                                compiled.effectiveData().effectiveScenarioDataSha256()),
                        publishedAt);
                insertRevision(connection, revision, baseline.baseline().baselineId());
                connection.commit();
                return revision;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("SCENARIO_PUBLISH_FAILED", "Cannot publish scenario revision", exception);
        }
    }

    public SandboxScenarioRevisionV1 loadRevision(String scenarioKey, int revision) {
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            return loadRevision(connection, scenarioKey, revision);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("SCENARIO_REVISION_READ_FAILED", "Cannot read scenario revision", exception);
        }
    }

    SandboxScenarioRevisionV1 loadRevision(Connection connection, String scenarioKey, int revision)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT revision_json FROM sandbox_scenario_revision
                WHERE scenario_key=? AND revision_no=? AND archived=b'0'
                """)) {
            statement.setString(1, scenarioKey);
            statement.setInt(2, revision);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SandboxWorkspaceException(
                            "SCENARIO_REVISION_NOT_FOUND", "Published scenario revision does not exist");
                }
                return parseRevision(rows.getString(1));
            }
        }
    }

    private SandboxScenarioDefinitionV1 readDraftForUpdate(Connection connection, String scenarioKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT draft_json FROM sandbox_scenario WHERE scenario_key=? AND archived=b'0' FOR UPDATE")) {
            statement.setString(1, scenarioKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SandboxWorkspaceException("SCENARIO_DRAFT_NOT_FOUND", "Scenario draft does not exist");
                }
                byte[] json = rows.getString(1).getBytes(StandardCharsets.UTF_8);
                return compiler.readDefinition(new ByteArrayResource(json));
            }
        }
    }

    private SandboxScenarioRevisionV1 findByDefinitionHash(
            Connection connection,
            String scenarioKey,
            String hash
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT revision_json FROM sandbox_scenario_revision
                WHERE scenario_key=? AND scenario_definition_sha256=?
                """)) {
            statement.setString(1, scenarioKey);
            statement.setString(2, hash);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? parseRevision(rows.getString(1)) : null;
            }
        }
    }

    private int nextRevision(Connection connection, String scenarioKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(revision_no),0)+1 FROM sandbox_scenario_revision WHERE scenario_key=?")) {
            statement.setString(1, scenarioKey);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void insertRevision(
            Connection connection,
            SandboxScenarioRevisionV1 revision,
            String baselineId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sandbox_scenario_revision(
                    scenario_key,revision_no,artifact_version,baseline_id,
                    scenario_definition_sha256,base_data_projection_sha256,
                    effective_scenario_data_sha256,revision_json,published_at,archived
                ) VALUES (?,?,?,?,?,?,?,?,?,b'0')
                """)) {
            statement.setString(1, revision.scenarioKey());
            statement.setInt(2, revision.revision());
            statement.setString(3, revision.artifactVersion());
            statement.setString(4, baselineId);
            statement.setString(5, revision.fingerprints().scenarioDefinitionSha256());
            statement.setString(6, revision.fingerprints().baseDataProjectionSha256());
            statement.setString(7, revision.fingerprints().effectiveScenarioDataSha256());
            statement.setString(8, json(revision));
            statement.setTimestamp(9, Timestamp.from(revision.publishedAtUtc()));
            statement.executeUpdate();
        }
    }

    private SandboxScenarioRevisionV1 parseRevision(String json) {
        try {
            return objectMapper.readValue(json, SandboxScenarioRevisionV1.class);
        } catch (JsonProcessingException exception) {
            throw new SandboxWorkspaceException(
                    "INVALID_STORED_SCENARIO", "Stored scenario revision is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new SandboxScenarioException("SCENARIO_JSON_WRITE_FAILED", "Cannot encode scenario JSON", exception);
        }
    }

    private SandboxScenarioCompilationReport report(CompiledSandboxScenario compiled) {
        DataCounts counts = counts(compiled.effectiveData().data());
        return new SandboxScenarioCompilationReport(
                compiled.normalizedDefinition().scenarioKey(),
                compiled.normalizedDefinition().baseline().baselineId(),
                compiled.scenarioDefinitionSha256(),
                compiled.effectiveData().baseDataProjectionSha256(),
                compiled.effectiveData().effectiveScenarioDataSha256(),
                compiled.resolvedSelection(),
                counts.values(),
                compiled.effectiveData().vehicleInitializations().size());
    }

    private DataCounts counts(org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data data) {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("poi", (long) data.pois().size());
        values.put("goods", (long) data.goods().size());
        values.put("vehicle", (long) data.vehicles().size());
        values.put("driver", (long) data.drivers().size());
        values.put("driver_vehicle", (long) data.driverVehicleBindings().size());
        values.put("processing_chain", (long) data.processingChains().size());
        values.put("processing_stage", data.processingChains().stream().mapToLong(c -> c.stages().size()).sum());
        values.put("processing_stage_input", data.processingChains().stream()
                .flatMap(c -> c.stages().stream()).mapToLong(s -> s.inputs().size()).sum());
        values.put("processing_stage_edge", data.processingChains().stream().mapToLong(c -> c.edges().size()).sum());
        values.put("enrollment", (long) data.initialInventories().size());
        return new DataCounts(Map.copyOf(values));
    }

    private void requireControlSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT control_schema_version FROM sandbox_workspace_marker
                WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX'
                """)) {
            if (!rows.next() || !Set.of(CONTROL_SCHEMA_VERSION, RUN_CONTROL_SCHEMA_VERSION, "sandbox-control-schema/v4", "sandbox-control-schema/v5", "sandbox-control-schema/v6", "sandbox-control-schema/v7", "sandbox-control-schema/v8", "sandbox-control-schema/v9")
                    .contains(rows.getString(1))) {
                throw new SandboxWorkspaceException(
                        "CONTROL_SCHEMA_NOT_READY", "Run the phase-two sandbox provisioning upgrade first");
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION sql_mode='NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION'");
            statement.execute("SET SESSION time_zone='+08:00'");
            statement.execute("SET NAMES utf8mb4");
        }
    }

    private record DataCounts(Map<String, Long> values) {}
}
