package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.scenario.definition.CompiledSandboxScenario;
import org.example.roadsimulation.sandbox.scenario.definition.EffectiveScenarioData;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Materializes one immutable scenario revision into the fixed sandbox workspace. */
public final class SandboxScenarioWorkspacePreparer {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Resource schemaResource;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    private final SandboxBaselineLoader baselineLoader;
    private final SandboxScenarioCompiler compiler;
    private final SandboxScenarioCodec codec;
    private final SandboxScenarioStore store;
    private final SandboxWorkspaceDataAccess dataAccess = new SandboxWorkspaceDataAccess();
    private final SandboxWorkspacePreparer phaseOneVerifier;

    public SandboxScenarioWorkspacePreparer(
            String jdbcUrl,
            String username,
            String password,
            ObjectMapper objectMapper
    ) {
        this(jdbcUrl, username, password, objectMapper,
                new ClassPathResource(SandboxWorkspacePreparer.DEFAULT_SCHEMA_RESOURCE));
    }

    SandboxScenarioWorkspacePreparer(
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
        this.compiler = new SandboxScenarioCompiler(objectMapper);
        this.codec = new SandboxScenarioCodec(objectMapper);
        this.store = new SandboxScenarioStore(jdbcUrl, username, password, objectMapper);
        this.phaseOneVerifier = new SandboxWorkspacePreparer(jdbcUrl, username, password, objectMapper, schemaResource);
    }

    public SandboxScenarioPreparationReport prepare(
            Resource baselineResource,
            String scenarioKey,
            int revisionNumber
    ) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        SandboxScenarioRevisionV1 revision = store.loadRevision(scenarioKey, revisionNumber);
        CompiledSandboxScenario compiled = compiler.compile(baseline, revision.definition());
        validatePublishedRevision(revision, compiled, scenarioKey, revisionNumber);

        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            requireCompatibleMarker(connection, baseline);
            safety.acquirePreparationLock(connection);
            safety.requireNoUnfinishedExecution(connection);
            boolean markerUpdated = false;
            try {
                markPreparing(connection, revision);
                markerUpdated = true;
                rebuildBusinessSchema(connection);
                restoreAndVerify(connection, baseline, revision, compiled);
                return report(connection, baseline, revision, compiled, SandboxWorkspaceState.SCENARIO_DATA_READY);
            } catch (Exception exception) {
                rollbackQuietly(connection);
                if (markerUpdated) {
                    markFailed(connection, exception);
                }
                if (exception instanceof SandboxWorkspaceException workspaceException) {
                    throw workspaceException;
                }
                throw new SandboxWorkspaceException(
                        "SCENARIO_PREPARATION_FAILED", "Scenario preparation failed", exception);
            } finally {
                safety.releasePreparationLock(connection);
            }
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException(
                    "SCENARIO_DATABASE_CONNECTION_FAILED", "Cannot prepare scenario workspace", exception);
        }
    }

    public SandboxScenarioPreparationReport verify(Resource baselineResource) {
        LoadedSandboxBaseline baseline = baselineLoader.load(baselineResource);
        try (Connection connection = openConnection()) {
            configure(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireControlSchema(connection);
            ActiveScenario active = activeScenario(connection);
            SandboxScenarioRevisionV1 revision = store.loadRevision(active.scenarioKey(), active.revision());
            CompiledSandboxScenario compiled = compiler.compile(baseline, revision.definition());
            validatePublishedRevision(revision, compiled, active.scenarioKey(), active.revision());
            requireScenarioMarkerMatching(connection, baseline, revision, compiled);
            verifyRestoredScenario(connection, compiled);
            return report(connection, baseline, revision, compiled, SandboxWorkspaceState.SCENARIO_DATA_READY);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("SCENARIO_VERIFICATION_FAILED", "Scenario verification failed", exception);
        }
    }

    private void validatePublishedRevision(
            SandboxScenarioRevisionV1 revision,
            CompiledSandboxScenario compiled,
            String scenarioKey,
            int revisionNumber
    ) {
        if (!SandboxScenarioRevisionV1.ARTIFACT_VERSION.equals(revision.artifactVersion())
                || !scenarioKey.equals(revision.scenarioKey())
                || revisionNumber != revision.revision()
                || revisionNumber <= 0
                || !compiled.resolvedSelection().equals(revision.resolvedSelection())
                || !compiled.scenarioDefinitionSha256().equals(
                revision.fingerprints().scenarioDefinitionSha256())
                || !compiled.effectiveData().baseDataProjectionSha256().equals(
                revision.fingerprints().baseDataProjectionSha256())
                || !compiled.effectiveData().effectiveScenarioDataSha256().equals(
                revision.fingerprints().effectiveScenarioDataSha256())) {
            throw new SandboxWorkspaceException(
                    "SCENARIO_REVISION_MISMATCH", "Published scenario revision does not match its baseline and hashes");
        }
    }

    private void markPreparing(Connection connection, SandboxScenarioRevisionV1 revision) throws SQLException {
        safety.clearExecutionReference(connection);
        SandboxRunMarkerMaintenance.clear(connection);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE sandbox_workspace_marker
                SET scenario_key=?,scenario_revision=?,scenario_definition_sha256=?,
                    effective_scenario_data_sha256=?,workspace_state='SCENARIO_PREPARING',
                    prepared_at=NULL,failure_code=NULL,failure_message=NULL
                WHERE marker_id=1 AND workspace_kind=?
                """)) {
            statement.setString(1, revision.scenarioKey());
            statement.setInt(2, revision.revision());
            statement.setString(3, revision.fingerprints().scenarioDefinitionSha256());
            statement.setString(4, revision.fingerprints().effectiveScenarioDataSha256());
            statement.setString(5, SandboxWorkspaceSafety.WORKSPACE_KIND);
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

    private void restoreAndVerify(
            Connection connection,
            LoadedSandboxBaseline baseline,
            SandboxScenarioRevisionV1 revision,
            CompiledSandboxScenario compiled
    ) throws SQLException {
        connection.setAutoCommit(false);
        try {
            dataAccess.restore(
                    connection,
                    compiled.effectiveData().data(),
                    baseline.baseline().source().capturedAtUtc(),
                    compiled.effectiveData().fixedVehiclePois());
            verifyRestoredScenario(connection, compiled);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker
                    SET workspace_state='SCENARIO_DATA_READY',prepared_at=CURRENT_TIMESTAMP(6),
                        failure_code=NULL,failure_message=NULL
                    WHERE marker_id=1 AND workspace_state='SCENARIO_PREPARING'
                    """)) {
                if (statement.executeUpdate() != 1) {
                    throw new SandboxWorkspaceException(
                            "INVALID_STATE", "Cannot mark workspace SCENARIO_DATA_READY");
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

    private void verifyRestoredScenario(Connection connection, CompiledSandboxScenario compiled)
            throws SQLException {
        var restoredData = codec.normalizeData(dataAccess.read(connection));
        Map<Long, Long> restoredPois = dataAccess.readFixedVehiclePois(connection);
        List<EffectiveScenarioData.VehicleInitialization> restoredInitializations = restoredPois.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new EffectiveScenarioData.VehicleInitialization(
                        entry.getKey(), "FIXED_POI", entry.getValue()))
                .toList();
        String restoredHash = codec.effectiveDataHash(restoredData, restoredInitializations);
        if (!compiled.effectiveData().effectiveScenarioDataSha256().equals(restoredHash)) {
            throw new SandboxWorkspaceException(
                    "RESTORED_SCENARIO_HASH_MISMATCH", "Database scenario facts do not match the published revision");
        }
        if (!compiled.effectiveData().fixedVehiclePois().equals(restoredPois)) {
            throw new SandboxWorkspaceException(
                    "VEHICLE_INITIAL_POI_MISMATCH", "Fixed vehicle initial POIs differ after restoration");
        }
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT COUNT(*) FROM vehicle
                WHERE current_longitude IS NOT NULL OR current_latitude IS NOT NULL
                """)) {
            rows.next();
            if (rows.getLong(1) != 0) {
                throw new SandboxWorkspaceException(
                        "VEHICLE_COORDINATES_NOT_NEUTRAL", "Scenario preparation must leave vehicle coordinates empty");
            }
        }
        Map<String, Long> expected = expectedCounts(compiled.effectiveData());
        Map<String, Long> actual = dataAccess.counts(connection);
        if (!expected.equals(actual)) {
            throw new SandboxWorkspaceException(
                    "SCENARIO_ROW_COUNT_MISMATCH", "Scenario row counts differ: expected=" + expected + ", actual=" + actual);
        }
        phaseOneVerifier.verifyRuntimeTablesEmpty(connection);
    }

    private void requireCompatibleMarker(Connection connection, LoadedSandboxBaseline baseline) throws SQLException {
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
                    || !List.of("BASE_DATA_READY", "SCENARIO_DATA_READY", "FAILED").contains(rows.getString(6))) {
                throw new SandboxWorkspaceException(
                        "BASE_WORKSPACE_NOT_READY", "Workspace marker is not compatible with the formal baseline");
            }
        }
    }

    private void requireScenarioMarkerMatching(
            Connection connection,
            LoadedSandboxBaseline baseline,
            SandboxScenarioRevisionV1 revision,
            CompiledSandboxScenario compiled
    ) throws SQLException {
        requireCompatibleMarkerValues(connection, baseline);
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT scenario_key,scenario_revision,scenario_definition_sha256,
                       effective_scenario_data_sha256,workspace_state
                FROM sandbox_workspace_marker WHERE marker_id=1
                """)) {
            if (!rows.next()
                    || !revision.scenarioKey().equals(rows.getString(1))
                    || revision.revision() != rows.getInt(2)
                    || !compiled.scenarioDefinitionSha256().equals(rows.getString(3))
                    || !compiled.effectiveData().effectiveScenarioDataSha256().equals(rows.getString(4))
                    || !SandboxWorkspaceState.SCENARIO_DATA_READY.name().equals(rows.getString(5))) {
                throw new SandboxWorkspaceException(
                        "SCENARIO_MARKER_MISMATCH", "Workspace marker does not match the published scenario revision");
            }
        }
    }

    private void requireCompatibleMarkerValues(Connection connection, LoadedSandboxBaseline baseline)
            throws SQLException {
        var eligible = baselineLoader.selectAllEligible(baseline);
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT schema_version,baseline_id,restoration_payload_sha256,simulation_facts_sha256,
                       effective_base_data_sha256 FROM sandbox_workspace_marker WHERE marker_id=1
                """)) {
            if (!rows.next()
                    || !SandboxWorkspacePreparer.SCHEMA_VERSION.equals(rows.getString(1))
                    || !baseline.baseline().baselineId().equals(rows.getString(2))
                    || !baseline.baseline().fingerprints().restorationPayloadSha256().equals(rows.getString(3))
                    || !baseline.baseline().fingerprints().simulationFactsSha256().equals(rows.getString(4))
                    || !eligible.effectiveBaseDataSha256().equals(rows.getString(5))) {
                throw new SandboxWorkspaceException(
                        "BASELINE_MARKER_MISMATCH", "Workspace baseline marker does not match");
            }
        }
    }

    private ActiveScenario activeScenario(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                SELECT scenario_key,scenario_revision,workspace_state
                FROM sandbox_workspace_marker WHERE marker_id=1
                """)) {
            if (!rows.next() || rows.getString(1) == null || rows.getInt(2) <= 0
                    || !SandboxWorkspaceState.SCENARIO_DATA_READY.name().equals(rows.getString(3))) {
                throw new SandboxWorkspaceException(
                        "SCENARIO_NOT_READY", "Workspace does not contain a ready scenario revision");
            }
            return new ActiveScenario(rows.getString(1), rows.getInt(2));
        }
    }

    private void requireControlSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if (!rows.next() || !java.util.Set.of(
                    SandboxScenarioStore.CONTROL_SCHEMA_VERSION,
                    SandboxScenarioStore.RUN_CONTROL_SCHEMA_VERSION, "sandbox-control-schema/v4", "sandbox-control-schema/v5", "sandbox-control-schema/v6", "sandbox-control-schema/v7", "sandbox-control-schema/v8", "sandbox-control-schema/v9").contains(rows.getString(1))) {
                throw new SandboxWorkspaceException(
                        "CONTROL_SCHEMA_NOT_READY", "Run the phase-two sandbox provisioning upgrade first");
            }
        }
    }

    private SandboxScenarioPreparationReport report(
            Connection connection,
            LoadedSandboxBaseline baseline,
            SandboxScenarioRevisionV1 revision,
            CompiledSandboxScenario compiled,
            SandboxWorkspaceState state
    ) throws SQLException {
        return new SandboxScenarioPreparationReport(
                state,
                SandboxWorkspacePreparer.SCHEMA_VERSION,
                SandboxScenarioStore.CONTROL_SCHEMA_VERSION,
                baseline.baseline().baselineId(),
                revision.scenarioKey(),
                revision.revision(),
                compiled.scenarioDefinitionSha256(),
                compiled.effectiveData().baseDataProjectionSha256(),
                compiled.effectiveData().effectiveScenarioDataSha256(),
                dataAccess.counts(connection),
                compiled.effectiveData().vehicleInitializations().size(),
                null,
                null);
    }

    private Map<String, Long> expectedCounts(EffectiveScenarioData effective) {
        var data = effective.data();
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
        return Map.copyOf(values);
    }

    private void markFailed(Connection connection, Exception failure) {
        try {
            connection.setAutoCommit(true);
            String code = failure instanceof SandboxWorkspaceException workspace
                    ? workspace.errorCode() : "SCENARIO_PREPARATION_FAILED";
            String message = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker
                    SET workspace_state='FAILED',failure_code=?,failure_message=?,prepared_at=NULL
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

    private record ActiveScenario(String scenarioKey, int revision) {}
}
