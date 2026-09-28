package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.run.CompiledSandboxRunSpecification;
import org.example.roadsimulation.sandbox.run.SandboxRunCodec;
import org.example.roadsimulation.sandbox.run.SandboxRunCompiler;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV1;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.run.SandboxVehicleInitialState;
import org.example.roadsimulation.sandbox.scenario.definition.CompiledSandboxScenario;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCodec;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restores a published scenario and materializes one immutable deterministic run input. */
public final class SandboxRunWorkspacePreparer {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Resource schemaResource;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    private final SandboxBaselineLoader baselineLoader;
    private final SandboxScenarioCompiler scenarioCompiler;
    private final SandboxScenarioCodec scenarioCodec;
    private final SandboxScenarioStore scenarioStore;
    private final SandboxRunCompiler runCompiler;
    private final SandboxRunCodec runCodec;
    private final SandboxRandomProtocol randomProtocol;
    private final SandboxRunSpecificationStore runStore;
    private final SandboxWorkspaceDataAccess dataAccess = new SandboxWorkspaceDataAccess();
    private final SandboxWorkspacePreparer baseVerifier;

    public SandboxRunWorkspacePreparer(
            String jdbcUrl,
            String username,
            String password,
            ObjectMapper objectMapper
    ) {
        this(jdbcUrl, username, password, objectMapper,
                new ClassPathResource(SandboxWorkspacePreparer.DEFAULT_SCHEMA_RESOURCE));
    }

    SandboxRunWorkspacePreparer(
            String jdbcUrl,
            String username,
            String password,
            ObjectMapper objectMapper,
            Resource schemaResource
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.schemaResource = schemaResource;
        this.baselineLoader = new SandboxBaselineLoader(objectMapper);
        this.scenarioCompiler = new SandboxScenarioCompiler(objectMapper);
        this.scenarioCodec = new SandboxScenarioCodec(objectMapper);
        this.scenarioStore = new SandboxScenarioStore(jdbcUrl, username, password, objectMapper);
        this.runCompiler = new SandboxRunCompiler(objectMapper);
        this.runCodec = new SandboxRunCodec(objectMapper);
        this.randomProtocol = new SandboxRandomProtocol(objectMapper);
        this.runStore = new SandboxRunSpecificationStore(jdbcUrl, username, password, objectMapper);
        this.baseVerifier = new SandboxWorkspacePreparer(jdbcUrl, username, password, objectMapper, schemaResource);
    }

    public SandboxRunPreparationReport prepare(
            Resource baselineResource,
            String runSpecKey,
            int runSpecRevision
    ) {
        PreparedInputs inputs = loadAndValidate(baselineResource, runSpecKey, runSpecRevision);
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            requireCompatibleBaselineMarker(connection, inputs.baseline());
            safety.acquirePreparationLock(connection);
            boolean markerUpdated = false;
            try {
                markPreparing(connection, inputs);
                markerUpdated = true;
                rebuildBusinessSchema(connection);
                restoreAndVerify(connection, inputs);
                return report(connection, inputs, SandboxWorkspaceState.RUN_SPEC_READY);
            } catch (Exception exception) {
                rollbackQuietly(connection);
                if (markerUpdated) {
                    markFailed(connection, exception);
                }
                if (exception instanceof SandboxWorkspaceException workspaceException) {
                    throw workspaceException;
                }
                throw new SandboxWorkspaceException(
                        "RUN_PREPARATION_FAILED", "Deterministic run preparation failed", exception);
            } finally {
                safety.releasePreparationLock(connection);
            }
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException(
                    "RUN_DATABASE_CONNECTION_FAILED", "Cannot prepare deterministic run workspace", exception);
        }
    }

    public SandboxRunPreparationReport verify(Resource baselineResource) {
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            ActiveRun active = activeRun(connection);
            PreparedInputs inputs = loadAndValidate(
                    baselineResource, active.runSpecKey(), active.runSpecRevision());
            requireReadyMarkerMatching(connection, inputs);
            verifyRestoredRun(connection, inputs);
            return report(connection, inputs, SandboxWorkspaceState.RUN_SPEC_READY);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException(
                    "RUN_VERIFICATION_FAILED", "Deterministic run verification failed", exception);
        }
    }

    private PreparedInputs loadAndValidate(
            Resource baselineResource,
            String runSpecKey,
            int runSpecRevision
    ) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        SandboxRunSpecificationRevisionV1 revision = runStore.loadRevision(runSpecKey, runSpecRevision);
        SandboxScenarioRevisionV1 scenarioRevision = scenarioStore.loadRevision(
                revision.specification().scenario().scenarioKey(),
                revision.specification().scenario().revision());
        CompiledSandboxScenario scenario = scenarioCompiler.compile(baseline, scenarioRevision.definition());
        CompiledSandboxRunSpecification run = runCompiler.compile(
                revision.specification(), scenarioRevision, scenario);
        validatePublishedRevision(revision, runSpecKey, runSpecRevision, run);
        return new PreparedInputs(baseline, scenarioRevision, scenario, revision, run);
    }

    private void validatePublishedRevision(
            SandboxRunSpecificationRevisionV1 revision,
            String runSpecKey,
            int runSpecRevision,
            CompiledSandboxRunSpecification compiled
    ) {
        if (!SandboxRunSpecificationRevisionV1.ARTIFACT_VERSION.equals(revision.artifactVersion())
                || !runSpecKey.equals(revision.runSpecKey())
                || runSpecRevision <= 0
                || runSpecRevision != revision.revision()
                || !compiled.algorithmProfile().equals(revision.algorithmProfile())
                || !compiled.vehicleInitialStates().equals(revision.vehicleInitialStates())
                || !compiled.runSpecificationSha256().equals(
                revision.fingerprints().runSpecificationSha256())
                || !compiled.resolvedVehicleInitialStateSha256().equals(
                revision.fingerprints().resolvedVehicleInitialStateSha256())
                || !compiled.preparedRunFactsSha256().equals(
                revision.fingerprints().preparedRunFactsSha256())) {
            throw new SandboxWorkspaceException(
                    "RUN_SPEC_REVISION_MISMATCH", "Published run specification does not match its compiled facts");
        }
    }

    private void markPreparing(Connection connection, PreparedInputs inputs) throws SQLException {
        var revision = inputs.runRevision();
        var compiled = inputs.run();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE sandbox_workspace_marker
                SET scenario_key=?,scenario_revision=?,scenario_definition_sha256=?,
                    effective_scenario_data_sha256=?,run_spec_key=?,run_spec_revision=?,
                    run_specification_sha256=?,random_protocol_id=?,root_seed_fingerprint=?,
                    resolved_vehicle_initial_state_sha256=?,prepared_run_facts_sha256=?,
                    workspace_state='RUN_SPEC_PREPARING',prepared_at=NULL,
                    failure_code=NULL,failure_message=NULL,failure_phase=NULL
                WHERE marker_id=1 AND workspace_kind=?
                """)) {
            statement.setString(1, inputs.scenarioRevision().scenarioKey());
            statement.setInt(2, inputs.scenarioRevision().revision());
            statement.setString(3, inputs.scenario().scenarioDefinitionSha256());
            statement.setString(4, inputs.scenario().effectiveData().effectiveScenarioDataSha256());
            statement.setString(5, revision.runSpecKey());
            statement.setInt(6, revision.revision());
            statement.setString(7, compiled.runSpecificationSha256());
            statement.setString(8, revision.specification().random().protocolId());
            statement.setString(9, randomProtocol.rootSeedFingerprint(
                    revision.specification().random().rootSeed()));
            statement.setString(10, compiled.resolvedVehicleInitialStateSha256());
            statement.setString(11, compiled.preparedRunFactsSha256());
            statement.setString(12, SandboxWorkspaceSafety.WORKSPACE_KIND);
            if (statement.executeUpdate() != 1) {
                throw new SandboxWorkspaceException("MISSING_MARKER", "Cannot update sandbox safety marker");
            }
        }
    }

    private void rebuildBusinessSchema(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME
                """)) {
            while (rows.next()) {
                String table = rows.getString(1);
                if (!SandboxWorkspacePreparer.CONTROL_TABLES.contains(table)) {
                    tables.add(table);
                }
            }
        }
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                for (String table : tables) {
                    statement.execute("DROP TABLE `" + table.replace("`", "``") + "`");
                }
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(schemaResource, StandardCharsets.UTF_8));
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    private void restoreAndVerify(Connection connection, PreparedInputs inputs) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Map<Long, Long> initialPois = inputs.run().vehicleInitialStates().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            SandboxVehicleInitialState::vehicleId,
                            SandboxVehicleInitialState::poiId));
            dataAccess.restore(
                    connection,
                    inputs.scenario().effectiveData().data(),
                    inputs.baseline().baseline().source().capturedAtUtc(),
                    initialPois);
            verifyRestoredRun(connection, inputs);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker
                    SET workspace_state='RUN_SPEC_READY',prepared_at=CURRENT_TIMESTAMP(6),
                        failure_code=NULL,failure_message=NULL,failure_phase=NULL
                    WHERE marker_id=1 AND workspace_state='RUN_SPEC_PREPARING'
                    """)) {
                if (statement.executeUpdate() != 1) {
                    throw new SandboxWorkspaceException("INVALID_STATE", "Cannot mark workspace RUN_SPEC_READY");
                }
            }
            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            if (exception instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void verifyRestoredRun(Connection connection, PreparedInputs inputs) throws SQLException {
        var restored = scenarioCodec.normalizeData(dataAccess.read(connection));
        String restoredScenarioHash = scenarioCodec.effectiveDataHash(
                restored, inputs.scenario().effectiveData().vehicleInitializations());
        if (!inputs.scenario().effectiveData().effectiveScenarioDataSha256().equals(restoredScenarioHash)) {
            throw new SandboxWorkspaceException(
                    "RESTORED_SCENARIO_HASH_MISMATCH", "Restored scenario data differs from the published revision");
        }

        Map<Long, Long> expectedPois = inputs.run().vehicleInitialStates().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SandboxVehicleInitialState::vehicleId,
                        SandboxVehicleInitialState::poiId));
        Map<Long, Long> actualPois = dataAccess.readFixedVehiclePois(connection);
        if (!expectedPois.equals(actualPois)) {
            throw new SandboxWorkspaceException(
                    "RUN_VEHICLE_INITIAL_STATE_MISMATCH", "Vehicle initial POIs differ from the run revision");
        }

        List<SandboxVehicleInitialState> storedStates = readStoredInitialStates(
                connection, inputs.runRevision().runSpecKey(), inputs.runRevision().revision());
        if (!storedStates.equals(inputs.run().vehicleInitialStates())) {
            throw new SandboxWorkspaceException(
                    "STORED_RUN_INITIAL_STATE_MISMATCH", "Stored vehicle initial-state facts differ from the run revision");
        }
        String stateHash = runCodec.vehicleInitialStateHash(storedStates);
        String preparedHash = runCodec.preparedRunFactsHash(
                restoredScenarioHash, inputs.run().runSpecificationSha256(), stateHash);
        if (!inputs.run().resolvedVehicleInitialStateSha256().equals(stateHash)
                || !inputs.run().preparedRunFactsSha256().equals(preparedHash)) {
            throw new SandboxWorkspaceException(
                    "RESTORED_RUN_HASH_MISMATCH", "Restored deterministic run facts do not match published hashes");
        }

        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT COUNT(*) FROM vehicle
                WHERE current_poi_id IS NULL OR current_status<>'IDLE'
                   OR current_longitude IS NOT NULL OR current_latitude IS NOT NULL
                """)) {
            rows.next();
            if (rows.getLong(1) != 0) {
                throw new SandboxWorkspaceException(
                        "INVALID_VEHICLE_INITIAL_STATE", "Vehicle position/status is not a deterministic POI initial state");
            }
        }
        baseVerifier.verifyRuntimeTablesEmpty(connection);
    }

    private List<SandboxVehicleInitialState> readStoredInitialStates(
            Connection connection,
            String runSpecKey,
            int revision
    ) throws SQLException {
        List<SandboxVehicleInitialState> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT vehicle_id,initialization_policy,poi_id,decision_domain,decision_key,derived_seed_hex
                FROM sandbox_run_vehicle_initial_state
                WHERE run_spec_key=? AND revision_no=? ORDER BY vehicle_id
                """)) {
            statement.setString(1, runSpecKey);
            statement.setInt(2, revision);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new SandboxVehicleInitialState(
                            rows.getLong(1), rows.getString(2), rows.getLong(3),
                            rows.getString(4), rows.getString(5), rows.getString(6)));
                }
            }
        }
        return result.stream().sorted(Comparator.comparingLong(SandboxVehicleInitialState::vehicleId)).toList();
    }

    private void requireCompatibleBaselineMarker(Connection connection, LoadedSandboxBaseline baseline)
            throws SQLException {
        var eligible = baselineLoader.selectAllEligible(baseline);
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT schema_version,baseline_id,restoration_payload_sha256,simulation_facts_sha256,
                       effective_base_data_sha256,workspace_state
                FROM sandbox_workspace_marker WHERE marker_id=1
                """)) {
            if (!rows.next()
                    || !SandboxWorkspacePreparer.SCHEMA_VERSION.equals(rows.getString(1))
                    || !baseline.baseline().baselineId().equals(rows.getString(2))
                    || !baseline.baseline().fingerprints().restorationPayloadSha256().equals(rows.getString(3))
                    || !baseline.baseline().fingerprints().simulationFactsSha256().equals(rows.getString(4))
                    || !eligible.effectiveBaseDataSha256().equals(rows.getString(5))
                    || !List.of("SCENARIO_DATA_READY", "RUN_SPEC_READY", "FAILED").contains(rows.getString(6))) {
                throw new SandboxWorkspaceException(
                        "SCENARIO_WORKSPACE_NOT_READY", "Workspace baseline marker is not ready for a run specification");
            }
        }
    }

    private void requireReadyMarkerMatching(Connection connection, PreparedInputs inputs) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT scenario_key,scenario_revision,run_spec_key,run_spec_revision,
                       run_specification_sha256,random_protocol_id,root_seed_fingerprint,
                       resolved_vehicle_initial_state_sha256,prepared_run_facts_sha256,workspace_state
                FROM sandbox_workspace_marker WHERE marker_id=1
                """)) {
            if (!rows.next()
                    || !inputs.scenarioRevision().scenarioKey().equals(rows.getString(1))
                    || inputs.scenarioRevision().revision() != rows.getInt(2)
                    || !inputs.runRevision().runSpecKey().equals(rows.getString(3))
                    || inputs.runRevision().revision() != rows.getInt(4)
                    || !inputs.run().runSpecificationSha256().equals(rows.getString(5))
                    || !inputs.runRevision().specification().random().protocolId().equals(rows.getString(6))
                    || !randomProtocol.rootSeedFingerprint(
                    inputs.runRevision().specification().random().rootSeed()).equals(rows.getString(7))
                    || !inputs.run().resolvedVehicleInitialStateSha256().equals(rows.getString(8))
                    || !inputs.run().preparedRunFactsSha256().equals(rows.getString(9))
                    || !SandboxWorkspaceState.RUN_SPEC_READY.name().equals(rows.getString(10))) {
                throw new SandboxWorkspaceException(
                        "RUN_MARKER_MISMATCH", "Workspace marker does not match the active run specification");
            }
        }
    }

    private ActiveRun activeRun(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT run_spec_key,run_spec_revision,workspace_state
                FROM sandbox_workspace_marker WHERE marker_id=1
                """)) {
            if (!rows.next() || rows.getString(1) == null || rows.getInt(2) <= 0
                    || !SandboxWorkspaceState.RUN_SPEC_READY.name().equals(rows.getString(3))) {
                throw new SandboxWorkspaceException(
                        "RUN_SPEC_NOT_READY", "Workspace does not contain a ready run specification");
            }
            return new ActiveRun(rows.getString(1), rows.getInt(2));
        }
    }

    private void requireControlSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if (!rows.next() || !SandboxRunSpecificationStore.CONTROL_SCHEMA_VERSION.equals(rows.getString(1))) {
                throw new SandboxWorkspaceException(
                        "CONTROL_SCHEMA_NOT_READY", "Run the phase-three sandbox provisioning upgrade first");
            }
        }
    }

    private SandboxRunPreparationReport report(
            Connection connection,
            PreparedInputs inputs,
            SandboxWorkspaceState state
    ) throws SQLException {
        long fixed = inputs.run().vehicleInitialStates().stream()
                .filter(value -> SandboxVehicleInitialState.FIXED_POI.equals(value.initializationPolicy()))
                .count();
        var spec = inputs.runRevision().specification();
        return new SandboxRunPreparationReport(
                state,
                SandboxWorkspacePreparer.SCHEMA_VERSION,
                SandboxRunSpecificationStore.CONTROL_SCHEMA_VERSION,
                inputs.baseline().baseline().baselineId(),
                inputs.scenarioRevision().scenarioKey(),
                inputs.scenarioRevision().revision(),
                inputs.runRevision().runSpecKey(),
                inputs.runRevision().revision(),
                spec.demand().mode(),
                spec.dispatch().strategy(),
                inputs.run().algorithmProfile().profileId(),
                spec.random().protocolId(),
                randomProtocol.rootSeedFingerprint(spec.random().rootSeed()),
                inputs.run().runSpecificationSha256(),
                inputs.run().resolvedVehicleInitialStateSha256(),
                inputs.run().preparedRunFactsSha256(),
                inputs.run().deterministicSimulationRunId(),
                dataAccess.counts(connection),
                fixed,
                inputs.run().vehicleInitialStates().size() - fixed,
                inputs.run().eligibleVehicleInitialPoiCount(),
                null,
                null);
    }

    private void markFailed(Connection connection, Exception failure) {
        try {
            connection.setAutoCommit(true);
            String code = failure instanceof SandboxWorkspaceException workspace
                    ? workspace.errorCode() : "RUN_PREPARATION_FAILED";
            String message = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker
                    SET workspace_state='FAILED',failure_code=?,failure_message=?,
                        failure_phase='RUN_SPEC_PREPARING',prepared_at=NULL
                    WHERE marker_id=1
                    """)) {
                statement.setString(1, code);
                statement.setString(2, message);
                statement.executeUpdate();
            }
        } catch (SQLException ignored) {
            // Preserve the original preparation error.
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

    private void rollbackQuietly(Connection connection) {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException ignored) {
            // Preserve the original exception.
        }
    }

    private record PreparedInputs(
            LoadedSandboxBaseline baseline,
            SandboxScenarioRevisionV1 scenarioRevision,
            CompiledSandboxScenario scenario,
            SandboxRunSpecificationRevisionV1 runRevision,
            CompiledSandboxRunSpecification run
    ) {}

    private record ActiveRun(String runSpecKey, int runSpecRevision) {}
}
