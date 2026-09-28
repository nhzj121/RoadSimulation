package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseData;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseDataCodec;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineValidator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the fixed phase-one sandbox workspace from an authenticated baseline JSON package.
 * This class never connects to the source database.
 */
public final class SandboxWorkspacePreparer {

    public static final String SCHEMA_VERSION = "sandbox-schema/v1";
    public static final String DEFAULT_SCHEMA_RESOURCE = "sandbox/schema/sandbox-schema-v1.sql";

    private static final Set<String> BASE_DATA_TABLES = Set.of(
            "poi", "goods", "vehicle", "processing_chain", "processing_stage",
            "processing_stage_input", "processing_stage_edge", "enrollment"
    );
    static final Set<String> CONTROL_TABLES = Set.of(
            SandboxWorkspaceSafety.MARKER_TABLE,
            SandboxWorkspaceSafety.SCENARIO_TABLE,
            SandboxWorkspaceSafety.SCENARIO_REVISION_TABLE,
            SandboxWorkspaceSafety.RUN_SPEC_TABLE,
            SandboxWorkspaceSafety.RUN_SPEC_REVISION_TABLE,
            SandboxWorkspaceSafety.RUN_VEHICLE_INITIAL_STATE_TABLE
    );

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Resource schemaResource;
    private final SandboxWorkspaceSafety safety;
    private final SandboxBaselineLoader baselineLoader;
    private final EffectiveBaseDataCodec effectiveDataCodec;
    private final SandboxBaselineValidator baselineValidator;
    private final SandboxWorkspaceDataAccess dataAccess;

    public SandboxWorkspacePreparer(
            String jdbcUrl,
            String username,
            String password,
            ObjectMapper objectMapper
    ) {
        this(jdbcUrl, username, password, objectMapper,
                new ClassPathResource(DEFAULT_SCHEMA_RESOURCE));
    }

    SandboxWorkspacePreparer(
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
        this.safety = new SandboxWorkspaceSafety();
        this.baselineLoader = new SandboxBaselineLoader(objectMapper);
        this.effectiveDataCodec = new EffectiveBaseDataCodec(objectMapper);
        this.baselineValidator = new SandboxBaselineValidator();
        this.dataAccess = new SandboxWorkspaceDataAccess();
    }

    public SandboxPreparationReport prepare(Resource baselineResource) {
        LoadedSandboxBaseline loaded = baselineLoader.load(baselineResource);
        EffectiveBaseData effective = baselineLoader.selectAllEligible(loaded);
        return prepare(loaded, effective);
    }

    SandboxPreparationReport prepare(LoadedSandboxBaseline loaded, EffectiveBaseData effective) {
        baselineValidator.validateBaseline(loaded.baseline());
        baselineValidator.validateEffectiveData(effective.data());
        String suppliedHash = effectiveDataCodec.hash(
                effective.selectionMode(), effective.eligibilityPolicyVersion(), effective.data());
        if (!suppliedHash.equals(effective.effectiveBaseDataSha256())) {
            throw new SandboxWorkspaceException(
                    "EFFECTIVE_DATA_HASH_MISMATCH", "Effective base data hash is invalid");
        }
        try (Connection connection = openConnection()) {
            configureSession(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            safety.acquirePreparationLock(connection);
            boolean markerCanBeUpdated = false;
            try {
                updateMarkerPreparing(connection, loaded, effective);
                markerCanBeUpdated = true;
                rebuildSchema(connection);
                restoreAndVerify(connection, loaded, effective);
                return report(connection, loaded, effective, SandboxWorkspaceState.BASE_DATA_READY, null, null);
            } catch (Exception exception) {
                rollbackQuietly(connection);
                if (markerCanBeUpdated) {
                    markFailed(connection, exception);
                }
                if (exception instanceof SandboxWorkspaceException workspaceException) {
                    throw workspaceException;
                }
                throw new SandboxWorkspaceException(
                        "PREPARATION_FAILED", "Sandbox workspace preparation failed", exception);
            } finally {
                safety.releasePreparationLock(connection);
            }
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException(
                    "DATABASE_CONNECTION_FAILED", "Cannot prepare sandbox workspace", exception);
        }
    }

    public SandboxPreparationReport verify(Resource baselineResource) {
        LoadedSandboxBaseline loaded = baselineLoader.load(baselineResource);
        EffectiveBaseData effective = baselineLoader.selectAllEligible(loaded);
        try (Connection connection = openConnection()) {
            configureSession(connection);
            safety.requireSafeTarget(jdbcUrl, connection);
            requireMarkerReadyAndMatching(connection, loaded, effective);
            verifyRestoredData(connection, effective);
            return report(connection, loaded, effective, SandboxWorkspaceState.BASE_DATA_READY, null, null);
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException("VERIFICATION_FAILED", "Workspace verification failed", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void configureSession(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION sql_mode='NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION'");
            statement.execute("SET SESSION time_zone='+08:00'");
            statement.execute("SET NAMES utf8mb4");
        }
    }

    private void updateMarkerPreparing(
            Connection connection,
            LoadedSandboxBaseline loaded,
            EffectiveBaseData effective
    ) throws SQLException {
        String sql = """
                UPDATE sandbox_workspace_marker
                SET schema_version=?, baseline_id=?, restoration_payload_sha256=?,
                    simulation_facts_sha256=?, effective_base_data_sha256=?,
                    eligibility_policy_version=?, scenario_key=NULL, scenario_revision=NULL,
                    scenario_definition_sha256=NULL, effective_scenario_data_sha256=NULL,
                    workspace_state='PREPARING',
                    prepared_at=NULL, failure_code=NULL, failure_message=NULL
                WHERE marker_id=1 AND workspace_kind=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SCHEMA_VERSION);
            statement.setString(2, loaded.baseline().baselineId());
            statement.setString(3, loaded.baseline().fingerprints().restorationPayloadSha256());
            statement.setString(4, loaded.baseline().fingerprints().simulationFactsSha256());
            statement.setString(5, effective.effectiveBaseDataSha256());
            statement.setString(6, effective.eligibilityPolicyVersion());
            statement.setString(7, SandboxWorkspaceSafety.WORKSPACE_KIND);
            if (statement.executeUpdate() != 1) {
                throw new SandboxWorkspaceException("MISSING_MARKER", "Cannot update sandbox safety marker");
            }
        }
    }

    private void rebuildSchema(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = """
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = rows.getString(1);
                    if (!CONTROL_TABLES.contains(table)) {
                        tables.add(table);
                    }
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
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(schemaResource, StandardCharsets.UTF_8)
            );
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    private void restoreAndVerify(
            Connection connection,
            LoadedSandboxBaseline loaded,
            EffectiveBaseData effective
    ) throws SQLException {
        connection.setAutoCommit(false);
        try {
            dataAccess.restore(connection, effective.data(), loaded.baseline().source().capturedAtUtc());
            verifyRestoredData(connection, effective);
            markReady(connection);
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

    private void verifyRestoredData(Connection connection, EffectiveBaseData effective) throws SQLException {
        var restored = effectiveDataCodec.normalize(dataAccess.read(connection));
        baselineValidator.validateEffectiveData(restored);
        String actualHash = effectiveDataCodec.hash(
                effective.selectionMode(), effective.eligibilityPolicyVersion(), restored);
        if (!effective.effectiveBaseDataSha256().equals(actualHash)) {
            throw new SandboxWorkspaceException(
                    "RESTORED_DATA_HASH_MISMATCH",
                    "Restored data differs from the selected baseline: expected="
                            + effective.effectiveBaseDataSha256() + ", actual=" + actualHash);
        }
        verifyCounts(connection, effective);
        verifyRuntimeTablesEmpty(connection);
        verifyCriticalFacts(connection);
    }

    private void verifyCounts(Connection connection, EffectiveBaseData effective) throws SQLException {
        Map<String, Long> actual = dataAccess.counts(connection);
        Map<String, Long> expected = Map.of(
                "poi", (long) effective.data().pois().size(),
                "goods", (long) effective.data().goods().size(),
                "vehicle", (long) effective.data().vehicles().size(),
                "processing_chain", (long) effective.data().processingChains().size(),
                "processing_stage", effective.data().processingChains().stream()
                        .mapToLong(chain -> chain.stages().size()).sum(),
                "processing_stage_input", effective.data().processingChains().stream()
                        .flatMap(chain -> chain.stages().stream())
                        .mapToLong(stage -> stage.inputs().size()).sum(),
                "processing_stage_edge", effective.data().processingChains().stream()
                        .mapToLong(chain -> chain.edges().size()).sum(),
                "enrollment", (long) effective.data().initialInventories().size()
        );
        if (!expected.equals(actual)) {
            throw new SandboxWorkspaceException(
                    "ROW_COUNT_MISMATCH", "Restored row counts differ: expected=" + expected + ", actual=" + actual);
        }
    }

    void verifyRuntimeTablesEmpty(Connection connection) throws SQLException {
        Set<String> allowed = new HashSet<>(BASE_DATA_TABLES);
        allowed.addAll(CONTROL_TABLES);
        String sql = """
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME
                """;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String table = rows.getString(1);
                if (allowed.contains(table)) {
                    continue;
                }
                try (Statement countStatement = connection.createStatement();
                     ResultSet count = countStatement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                    count.next();
                    if (count.getLong(1) != 0) {
                        throw new SandboxWorkspaceException(
                                "RUNTIME_TABLE_NOT_EMPTY", "Runtime table is not empty: " + table);
                    }
                }
            }
        }
    }

    private void verifyCriticalFacts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT s.poi_id,p.poi_type FROM processing_stage s
                     JOIN poi p ON p.id=s.poi_id WHERE s.id=5102
                     """)) {
            if (rows.next() && (rows.getLong(1) != 4762L
                    || !"TIRE_MANUFACTURING_PLANT".equals(rows.getString(2)))) {
                throw new SandboxWorkspaceException(
                        "CRITICAL_FACT_MISMATCH", "Processing stage 5102 is not bound to POI 4762");
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(*) FROM processing_stage
                     WHERE input_weight_ratio<>1.0 OR output_weight_ratio<>1.0
                        OR input_weight_ratio IS NULL OR output_weight_ratio IS NULL
                     """)) {
            rows.next();
            if (rows.getLong(1) != 0) {
                throw new SandboxWorkspaceException(
                        "CRITICAL_FACT_MISMATCH", "Processing-stage ratios are not all 1.0");
            }
        }
        assertAbsent(connection, "goods", 3L);
        assertAbsent(connection, "vehicle", 5L);
        assertAbsent(connection, "vehicle", 10L);
        assertAbsent(connection, "poi", 3466L);
    }

    private void assertAbsent(Connection connection, String table, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM `" + table + "` WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getLong(1) != 0) {
                    throw new SandboxWorkspaceException(
                            "EXCLUDED_RECORD_PRESENT", "Excluded record is present: " + table + "/" + id);
                }
            }
        }
    }

    private void markReady(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE sandbox_workspace_marker
                SET workspace_state='BASE_DATA_READY', prepared_at=CURRENT_TIMESTAMP(6),
                    failure_code=NULL, failure_message=NULL
                WHERE marker_id=1 AND workspace_state='PREPARING'
                """)) {
            if (statement.executeUpdate() != 1) {
                throw new SandboxWorkspaceException("INVALID_STATE", "Cannot mark workspace BASE_DATA_READY");
            }
        }
    }

    private void markFailed(Connection connection, Exception failure) {
        try {
            connection.setAutoCommit(true);
            String code = failure instanceof SandboxWorkspaceException workspaceException
                    ? workspaceException.errorCode() : "PREPARATION_FAILED";
            String message = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker
                    SET workspace_state='FAILED', failure_code=?, failure_message=?, prepared_at=NULL
                    WHERE marker_id=1
                    """)) {
                statement.setString(1, code);
                statement.setString(2, message);
                statement.executeUpdate();
            }
        } catch (SQLException ignored) {
            // The caller still receives the original failure.
        }
    }

    private void requireMarkerReadyAndMatching(
            Connection connection,
            LoadedSandboxBaseline loaded,
            EffectiveBaseData effective
    ) throws SQLException {
        String sql = """
                SELECT schema_version,baseline_id,restoration_payload_sha256,simulation_facts_sha256,
                       effective_base_data_sha256,eligibility_policy_version,workspace_state
                FROM sandbox_workspace_marker WHERE marker_id=1
                """;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()
                    || !SCHEMA_VERSION.equals(rows.getString(1))
                    || !loaded.baseline().baselineId().equals(rows.getString(2))
                    || !loaded.baseline().fingerprints().restorationPayloadSha256().equals(rows.getString(3))
                    || !loaded.baseline().fingerprints().simulationFactsSha256().equals(rows.getString(4))
                    || !effective.effectiveBaseDataSha256().equals(rows.getString(5))
                    || !effective.eligibilityPolicyVersion().equals(rows.getString(6))
                    || !SandboxWorkspaceState.BASE_DATA_READY.name().equals(rows.getString(7))) {
                throw new SandboxWorkspaceException(
                        "MARKER_MISMATCH", "Workspace marker does not match the requested baseline");
            }
        }
    }

    private SandboxPreparationReport report(
            Connection connection,
            LoadedSandboxBaseline loaded,
            EffectiveBaseData effective,
            SandboxWorkspaceState state,
            String errorCode,
            String errorMessage
    ) throws SQLException {
        return new SandboxPreparationReport(
                state,
                SCHEMA_VERSION,
                loaded.baseline().baselineId(),
                loaded.baseline().fingerprints().restorationPayloadSha256(),
                loaded.baseline().fingerprints().simulationFactsSha256(),
                effective.effectiveBaseDataSha256(),
                effective.eligibilityPolicyVersion(),
                dataAccess.counts(connection),
                errorCode,
                errorMessage
        );
    }

    private void rollbackQuietly(Connection connection) {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException ignored) {
            // Preserve original exception.
        }
    }
}
