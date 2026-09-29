package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.scenario.definition.CompiledSandboxScenario;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
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

/** JDBC control-plane storage for deterministic run drafts and immutable revisions. */
public final class SandboxRunSpecificationStore {
    public static final String CONTROL_SCHEMA_VERSION = "sandbox-control-schema/v3";
    public static final String V2_CONTROL_SCHEMA_VERSION = "sandbox-control-schema/v4";

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    private final SandboxBaselineLoader baselineLoader;
    private final SandboxScenarioCompiler scenarioCompiler;
    private final SandboxScenarioStore scenarioStore;
    private final SandboxRunCompiler runCompiler;
    private final SandboxRandomProtocol randomProtocol;

    public SandboxRunSpecificationStore(
            String jdbcUrl,
            String username,
            String password,
            ObjectMapper objectMapper
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.baselineLoader = new SandboxBaselineLoader(objectMapper);
        this.scenarioCompiler = new SandboxScenarioCompiler(objectMapper);
        this.scenarioStore = new SandboxScenarioStore(jdbcUrl, username, password, objectMapper);
        this.runCompiler = new SandboxRunCompiler(objectMapper);
        this.randomProtocol = new SandboxRandomProtocol(objectMapper);
    }

    public SandboxRunCompilationReport compile(Resource baselineResource, Resource runSpecResource) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        SandboxRunSpecificationV1 specification = runCompiler.read(runSpecResource);
        return report(compile(baseline, specification));
    }

    public SandboxRunCompilationReport saveDraft(Resource baselineResource, Resource runSpecResource) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        SandboxRunSpecificationV1 source = runCompiler.read(runSpecResource);
        CompiledSandboxRunSpecification compiled = compile(baseline, source);
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sandbox_run_spec(
                        run_spec_key,display_name,description,draft_json,draft_updated_at,row_version,archived
                    ) VALUES (?,?,?,?,CURRENT_TIMESTAMP(6),0,b'0')
                    ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),description=VALUES(description),
                        draft_json=VALUES(draft_json),draft_updated_at=CURRENT_TIMESTAMP(6),
                        row_version=row_version+1,archived=b'0'
                    """)) {
                statement.setString(1, compiled.normalizedSpecification().runSpecKey());
                statement.setString(2, compiled.normalizedSpecification().displayName());
                statement.setString(3, compiled.normalizedSpecification().description());
                statement.setString(4, json(compiled.normalizedSpecification()));
                statement.executeUpdate();
            }
            return report(compiled);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("RUN_SPEC_DRAFT_SAVE_FAILED", "Cannot save run specification draft", exception);
        }
    }

    public SandboxRunSpecificationRevisionV1 publish(Resource baselineResource, String runSpecKey) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            connection.setAutoCommit(false);
            try {
                SandboxRunSpecificationV1 draft = readDraftForUpdate(connection, runSpecKey);
                CompiledSandboxRunSpecification compiled = compile(baseline, draft);
                SandboxRunSpecificationRevisionV1 existing = findByHash(
                        connection, runSpecKey, compiled.runSpecificationSha256());
                if (existing != null) {
                    connection.commit();
                    return existing;
                }
                int revisionNumber = nextRevision(connection, runSpecKey);
                SandboxRunSpecificationRevisionV1 revision = new SandboxRunSpecificationRevisionV1(
                        SandboxRunSpecificationRevisionV1.ARTIFACT_VERSION,
                        runSpecKey,
                        revisionNumber,
                        compiled.normalizedSpecification(),
                        compiled.algorithmProfile(),
                        compiled.vehicleInitialStates(),
                        new SandboxRunSpecificationRevisionV1.Fingerprints(
                                SandboxRunCodec.CANONICALIZATION,
                                compiled.runSpecificationSha256(),
                                compiled.resolvedVehicleInitialStateSha256(),
                                compiled.preparedRunFactsSha256()),
                        Instant.now());
                insertRevision(connection, revision);
                insertVehicleInitialStates(connection, revision);
                connection.commit();
                return revision;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("RUN_SPEC_PUBLISH_FAILED", "Cannot publish run specification", exception);
        }
    }

    public SandboxRunSpecificationRevisionV1 loadRevision(String runSpecKey, int revision) {
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            return loadRevision(connection, runSpecKey, revision);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("RUN_SPEC_REVISION_READ_FAILED", "Cannot read run specification revision", exception);
        }
    }

    public CompiledSandboxRunSpecification recompile(
            Resource baselineResource,
            SandboxRunSpecificationRevisionV1 revision
    ) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        return compile(baseline, revision.specification());
    }

    private CompiledSandboxRunSpecification compile(
            LoadedSandboxBaseline baseline,
            SandboxRunSpecificationV1 specification
    ) {
        SandboxRunSpecificationV1.ScenarioReference reference = specification.scenario();
        if (reference == null) {
            throw new SandboxRunException("MISSING_SCENARIO_REFERENCE", "scenario is required");
        }
        SandboxScenarioRevisionV1 scenarioRevision = scenarioStore.loadRevision(
                reference.scenarioKey(), reference.revision());
        CompiledSandboxScenario scenario = scenarioCompiler.compile(baseline, scenarioRevision.definition());
        return runCompiler.compile(specification, scenarioRevision, scenario);
    }

    private SandboxRunSpecificationV1 readDraftForUpdate(Connection connection, String runSpecKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT draft_json FROM sandbox_run_spec WHERE run_spec_key=? AND archived=b'0' FOR UPDATE")) {
            statement.setString(1, runSpecKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SandboxWorkspaceException("RUN_SPEC_DRAFT_NOT_FOUND", "Run specification draft does not exist");
                }
                return runCompiler.read(new ByteArrayResource(rows.getString(1).getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private SandboxRunSpecificationRevisionV1 findByHash(
            Connection connection,
            String runSpecKey,
            String hash
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT revision_json FROM sandbox_run_spec_revision
                WHERE run_spec_key=? AND run_specification_sha256=?
                """)) {
            statement.setString(1, runSpecKey);
            statement.setString(2, hash);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? parseRevision(rows.getString(1)) : null;
            }
        }
    }

    private int nextRevision(Connection connection, String runSpecKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(revision_no),0)+1 FROM sandbox_run_spec_revision WHERE run_spec_key=?")) {
            statement.setString(1, runSpecKey);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void insertRevision(Connection connection, SandboxRunSpecificationRevisionV1 revision)
            throws SQLException {
        SandboxRunSpecificationV1 specification = revision.specification();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sandbox_run_spec_revision(
                    run_spec_key,revision_no,scenario_key,scenario_revision,artifact_version,
                    random_protocol_id,root_seed,algorithm_profile_id,run_specification_sha256,
                    resolved_vehicle_initial_state_sha256,prepared_run_facts_sha256,
                    revision_json,published_at,archived
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,b'0')
                """)) {
            statement.setString(1, revision.runSpecKey());
            statement.setInt(2, revision.revision());
            statement.setString(3, specification.scenario().scenarioKey());
            statement.setInt(4, specification.scenario().revision());
            statement.setString(5, revision.artifactVersion());
            statement.setString(6, specification.random().protocolId());
            statement.setString(7, specification.random().rootSeed());
            statement.setString(8, revision.algorithmProfile().profileId());
            statement.setString(9, revision.fingerprints().runSpecificationSha256());
            statement.setString(10, revision.fingerprints().resolvedVehicleInitialStateSha256());
            statement.setString(11, revision.fingerprints().preparedRunFactsSha256());
            statement.setString(12, json(revision));
            statement.setTimestamp(13, Timestamp.from(revision.publishedAtUtc()));
            statement.executeUpdate();
        }
    }

    private void insertVehicleInitialStates(
            Connection connection,
            SandboxRunSpecificationRevisionV1 revision
    ) throws SQLException {
        insertVehicleInitialStates(connection,revision.runSpecKey(),revision.revision(),revision.vehicleInitialStates());
    }

    private void insertVehicleInitialStates(Connection connection,String key,int revision,
            java.util.List<SandboxVehicleInitialState> states) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sandbox_run_vehicle_initial_state(
                    run_spec_key,revision_no,vehicle_id,initialization_policy,poi_id,
                    decision_domain,decision_key,derived_seed_hex
                ) VALUES (?,?,?,?,?,?,?,?)
                """)) {
            for (SandboxVehicleInitialState state : states) {
                statement.setString(1, key);
                statement.setInt(2, revision);
                statement.setLong(3, state.vehicleId());
                statement.setString(4, state.initializationPolicy());
                statement.setLong(5, state.poiId());
                statement.setString(6, state.decisionDomain());
                statement.setString(7, state.decisionKey());
                statement.setString(8, state.derivedSeedHex());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    SandboxRunSpecificationRevisionV1 loadRevision(
            Connection connection,
            String runSpecKey,
            int revision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT revision_json FROM sandbox_run_spec_revision
                WHERE run_spec_key=? AND revision_no=? AND archived=b'0'
                """)) {
            statement.setString(1, runSpecKey);
            statement.setInt(2, revision);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SandboxWorkspaceException(
                            "RUN_SPEC_REVISION_NOT_FOUND", "Published run specification revision does not exist");
                }
                return parseRevision(rows.getString(1));
            }
        }
    }

    private SandboxRunSpecificationRevisionV1 parseRevision(String json) {
        try {
            return objectMapper.readValue(json, SandboxRunSpecificationRevisionV1.class);
        } catch (JsonProcessingException exception) {
            throw new SandboxWorkspaceException(
                    "INVALID_STORED_RUN_SPEC", "Stored run specification revision is invalid", exception);
        }
    }

    private SandboxRunCompilationReport report(CompiledSandboxRunSpecification compiled) {
        long fixed = compiled.vehicleInitialStates().stream()
                .filter(state -> SandboxVehicleInitialState.FIXED_POI.equals(state.initializationPolicy()))
                .count();
        return new SandboxRunCompilationReport(
                compiled.normalizedSpecification().scenario().scenarioKey(),
                compiled.normalizedSpecification().scenario().revision(),
                compiled.normalizedSpecification().runSpecKey(),
                compiled.normalizedSpecification().demand().mode(),
                compiled.normalizedSpecification().dispatch().strategy(),
                compiled.algorithmProfile().profileId(),
                compiled.normalizedSpecification().random().protocolId(),
                randomProtocol.rootSeedFingerprint(compiled.normalizedSpecification().random().rootSeed()),
                compiled.runSpecificationSha256(),
                compiled.resolvedVehicleInitialStateSha256(),
                compiled.preparedRunFactsSha256(),
                compiled.deterministicSimulationRunId(),
                compiled.vehicleInitialStates().size(),
                fixed,
                compiled.vehicleInitialStates().size() - fixed,
                compiled.eligibleVehicleInitialPoiCount());
    }

    public CompiledSandboxRunSpecificationV2 compileV2(Resource baseline,Resource specification) {
        return compileV2(baselineLoader.load(baseline),new SandboxRunCompilerV2(objectMapper).read(specification));
    }

    private CompiledSandboxRunSpecificationV2 compileV2(LoadedSandboxBaseline baseline,SandboxRunSpecificationV2 specification) {
        if(specification==null || specification.scenario()==null)throw new SandboxRunException("MISSING_SCENARIO_REFERENCE","scenario is required");
        var scenarioRevision=scenarioStore.loadRevision(specification.scenario().scenarioKey(),specification.scenario().revision());
        var scenario=scenarioCompiler.compile(baseline,scenarioRevision.definition());
        return new SandboxRunCompilerV2(objectMapper).compile(specification,scenarioRevision,scenario);
    }

    public CompiledSandboxRunSpecificationV2 saveDraftV2(Resource baseline,Resource specification) {
        var compiled=compileV2(baseline,specification);var spec=compiled.normalizedSpecification();
        try(Connection connection=openConnection()) {
            configure(connection);safety.requireSafeTarget(jdbcUrl,connection);requireV2ControlSchema(connection);
            try(var statement=connection.prepareStatement("""
                    INSERT INTO sandbox_run_spec(run_spec_key,display_name,description,draft_json,draft_updated_at,row_version,archived)
                    VALUES(?,?,?,?,CURRENT_TIMESTAMP(6),0,b'0')
                    ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),description=VALUES(description),
                      draft_json=VALUES(draft_json),draft_updated_at=CURRENT_TIMESTAMP(6),row_version=row_version+1,archived=b'0'
                    """)) {
                statement.setString(1,spec.runSpecKey());statement.setString(2,spec.displayName());statement.setString(3,spec.description());
                statement.setString(4,json(spec));statement.executeUpdate();
            }
            return compiled;
        } catch(SQLException ex) { throw new SandboxWorkspaceException("RUN_SPEC_DRAFT_SAVE_FAILED","Cannot save v2 draft",ex); }
    }

    public SandboxRunSpecificationRevisionV2 publishV2(Resource baselineResource,String key) {
        var baseline=baselineLoader.load(baselineResource);
        try(Connection connection=openConnection()) {
            configure(connection);safety.requireSafeTarget(jdbcUrl,connection);requireV2ControlSchema(connection);
            connection.setAutoCommit(false);
            try {
                SandboxRunSpecificationV2 draft;
                try(var statement=connection.prepareStatement("SELECT draft_json FROM sandbox_run_spec WHERE run_spec_key=? AND archived=b'0' FOR UPDATE")) {
                    statement.setString(1,key);
                    try(var rows=statement.executeQuery()) {
                        if(!rows.next())throw new SandboxWorkspaceException("RUN_SPEC_DRAFT_NOT_FOUND","No v2 draft");
                        draft=new SandboxRunCompilerV2(objectMapper).read(new ByteArrayResource(rows.getString(1).getBytes(StandardCharsets.UTF_8)));
                    }
                }
                var compiled=compileV2(baseline,draft);
                if(!key.equals(compiled.normalizedSpecification().runSpecKey()))
                    throw new SandboxWorkspaceException("RUN_SPEC_DRAFT_KEY_MISMATCH","Draft key differs from control-table key");
                try(var statement=connection.prepareStatement("SELECT revision_json FROM sandbox_run_spec_revision WHERE run_spec_key=? AND run_specification_sha256=?")) {
                    statement.setString(1,key);statement.setString(2,compiled.runSpecificationSha256());
                    try(var rows=statement.executeQuery()) {
                        if(rows.next()) { var found=parseV2(rows.getString(1));connection.commit();return found; }
                    }
                }
                var revision=new SandboxRunSpecificationRevisionV2(SandboxRunSpecificationRevisionV2.ARTIFACT_VERSION,key,
                        nextRevision(connection,key),compiled.normalizedSpecification(),compiled.algorithmProfile(),compiled.vehicleInitialStates(),
                        compiled.weatherTimeline(),compiled.eventConfiguration(),new SandboxRunSpecificationRevisionV2.Fingerprints(
                                SandboxRunCodec.CANONICALIZATION,compiled.runSpecificationSha256(),compiled.resolvedVehicleInitialStateSha256(),
                                compiled.preparedRunFactsSha256(),compiled.weatherTimelineSha256(),compiled.eventConfigurationSha256()),Instant.now());
                insertRevisionV2(connection,revision);
                insertVehicleInitialStates(connection,key,revision.revision(),revision.vehicleInitialStates());
                connection.commit();return revision;
            } catch(Exception ex) { connection.rollback();throw ex; }
        } catch(SQLException ex) { throw new SandboxWorkspaceException("RUN_SPEC_PUBLISH_FAILED","Cannot publish v2 run",ex); }
    }

    private void insertRevisionV2(Connection connection,SandboxRunSpecificationRevisionV2 revision) throws SQLException {
        var spec=revision.specification();var facts=revision.fingerprints();
        try(var statement=connection.prepareStatement("""
                INSERT INTO sandbox_run_spec_revision(run_spec_key,revision_no,scenario_key,scenario_revision,artifact_version,
                  random_protocol_id,root_seed,algorithm_profile_id,run_specification_sha256,resolved_vehicle_initial_state_sha256,
                  prepared_run_facts_sha256,revision_json,published_at,archived) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,b'0')
                """)) {
            statement.setString(1,revision.runSpecKey());statement.setInt(2,revision.revision());
            statement.setString(3,spec.scenario().scenarioKey());statement.setInt(4,spec.scenario().revision());
            statement.setString(5,revision.artifactVersion());statement.setString(6,spec.random().protocolId());statement.setString(7,spec.random().rootSeed());
            statement.setString(8,revision.algorithmProfile().profileId());statement.setString(9,facts.runSpecificationSha256());
            statement.setString(10,facts.resolvedVehicleInitialStateSha256());statement.setString(11,facts.preparedRunFactsSha256());
            statement.setString(12,json(revision));statement.setTimestamp(13,Timestamp.from(revision.publishedAtUtc()));statement.executeUpdate();
        }
    }

    public boolean isRevisionV2(String key,int revision) { return storedVersion(key,revision,false).equals(SandboxRunSpecificationRevisionV2.ARTIFACT_VERSION); }
    public boolean isDraftV2(String key) { return storedVersion(key,0,true).equals(SandboxRunSpecificationV2.ARTIFACT_VERSION); }
    private String storedVersion(String key,int revision,boolean draft) {
        try(Connection connection=openConnection()) {
            configure(connection);safety.requireSafeTarget(jdbcUrl,connection);requireControlSchema(connection);
            String sql=draft?"SELECT draft_json FROM sandbox_run_spec WHERE run_spec_key=? AND archived=b'0'"
                    :"SELECT revision_json FROM sandbox_run_spec_revision WHERE run_spec_key=? AND revision_no=? AND archived=b'0'";
            try(var statement=connection.prepareStatement(sql)) {
                statement.setString(1,key);if(!draft)statement.setInt(2,revision);
                try(var rows=statement.executeQuery()) {
                    if(!rows.next())throw new SandboxWorkspaceException("RUN_SPEC_NOT_FOUND","Run draft or revision not found");
                    return objectMapper.readTree(rows.getString(1)).path("artifactVersion").asText();
                }
            }
        } catch(java.io.IOException|SQLException ex) { throw new SandboxWorkspaceException("RUN_SPEC_REVISION_READ_FAILED","Cannot determine stored run version",ex); }
    }

    public SandboxRunSpecificationRevisionV2 loadRevisionV2(String key,int revision) {
        try(Connection connection=openConnection()) {
            configure(connection);safety.requireSafeTarget(jdbcUrl,connection);requireV2ControlSchema(connection);
            try(var statement=connection.prepareStatement("SELECT revision_json FROM sandbox_run_spec_revision WHERE run_spec_key=? AND revision_no=? AND archived=b'0'")) {
                statement.setString(1,key);statement.setInt(2,revision);
                try(var rows=statement.executeQuery()) {
                    if(!rows.next())throw new SandboxWorkspaceException("RUN_SPEC_REVISION_NOT_FOUND","Published v2 run not found");
                    return parseV2(rows.getString(1));
                }
            }
        } catch(SQLException ex) { throw new SandboxWorkspaceException("RUN_SPEC_REVISION_READ_FAILED","Cannot read v2 revision",ex); }
    }
    private SandboxRunSpecificationRevisionV2 parseV2(String encoded) {
        try {
            var revision=objectMapper.readValue(encoded,SandboxRunSpecificationRevisionV2.class);
            if(!SandboxRunSpecificationRevisionV2.ARTIFACT_VERSION.equals(revision.artifactVersion()))
                throw new SandboxWorkspaceException("UNSUPPORTED_RUN_SPEC_VERSION","Not a published v2 artifact");
            return revision;
        } catch(JsonProcessingException ex) { throw new SandboxWorkspaceException("INVALID_STORED_RUN_SPEC","Invalid v2 revision JSON",ex); }
    }
    private void requireV2ControlSchema(Connection connection) throws SQLException {
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if(!rows.next() || !V2_CONTROL_SCHEMA_VERSION.equals(rows.getString(1)))
                throw new SandboxWorkspaceException("CONTROL_SCHEMA_NOT_READY","Run the additive sandbox-control-schema/v4 upgrade first");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new SandboxRunException("RUN_SPEC_JSON_WRITE_FAILED", "Cannot encode run specification JSON", exception);
        }
    }

    private void requireControlSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT control_schema_version FROM sandbox_workspace_marker
                WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX'
                """)) {
            if (!rows.next() || !java.util.Set.of(CONTROL_SCHEMA_VERSION,V2_CONTROL_SCHEMA_VERSION).contains(rows.getString(1))) {
                throw new SandboxWorkspaceException(
                        "CONTROL_SCHEMA_NOT_READY", "Run the phase-three sandbox provisioning upgrade first");
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
}
