package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.workspace.*;
import org.springframework.core.io.Resource;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;

/** Holds the same database lock as all destructive preparation for the entire run. */
public final class SandboxExecutionLease implements AutoCloseable {
    private final Connection connection;
    private final SandboxWorkspaceSafety safety;
    private final SandboxRunPreparationReport preparation;
    private final SandboxRunSpecificationRevisionV2 revision;
    private final JsonNode baselinePackage;
    private final String jobId;
    private final String executionId = UUID.randomUUID().toString();
    private boolean created;
    // Execution phase, not the management job's failure-closing status. Moving a
    // failed job to FINALIZING must not erase the phase in which it actually failed.
    private String executionPhase = "STARTUP";

    private SandboxExecutionLease(Connection connection, SandboxWorkspaceSafety safety,
                                  SandboxRunPreparationReport preparation,
                                  SandboxRunSpecificationRevisionV2 revision, JsonNode baselinePackage, String jobId) {
        this.connection = connection;
        this.safety = safety;
        this.preparation = preparation;
        this.revision = revision;
        this.baselinePackage = baselinePackage.deepCopy();
        this.jobId = jobId;
    }

    public static SandboxExecutionLease acquire(String url, String user, String password,
            Resource baseline, String key, int revisionNumber, ObjectMapper json) {
        return acquire(url, user, password, baseline, key, revisionNumber, json, null);
    }

    /** A management worker prepares and executes under one lock/connection, without a handoff gap. */
    public static SandboxExecutionLease prepareAndAcquire(String url, String user, String password,
            Resource baseline, String key, int revisionNumber, ObjectMapper json, String jobId) {
        if (jobId == null) throw new SandboxWorkspaceException("MISSING_JOB", "Worker requires a reserved job");
        return acquire(url, user, password, baseline, key, revisionNumber, json, jobId);
    }

    private static SandboxExecutionLease acquire(String url, String user, String password,
            Resource baseline, String key, int revisionNumber, ObjectMapper json, String jobId) {
        Connection connection = null;
        var safety = new SandboxWorkspaceSafety();
        boolean locked = false;
        try {
            if (!SandboxWorkspaceSafety.DATABASE_NAME.equals(safety.databaseName(url))) {
                throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Execution requires the dedicated sandbox database");
            }
            connection = DriverManager.getConnection(url, user, password);
            safety.requireSafeTarget(url, connection);
            safety.acquirePreparationLock(connection);
            locked = true;
            safety.requireManagementJobOwner(connection, jobId);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
                if (!rows.next() || !java.util.Set.of(SandboxRunSpecificationStore.JOURNAL_CONTROL_SCHEMA_VERSION,
                        SandboxRunSpecificationStore.MANAGEMENT_CONTROL_SCHEMA_VERSION,
                        SandboxRunSpecificationStore.LIFECYCLE_CONTROL_SCHEMA_VERSION,
                        SandboxRunSpecificationStore.START_REQUEST_CONTROL_SCHEMA_VERSION).contains(rows.getString(1))) {
                    throw new SandboxWorkspaceException("EXECUTION_CONTROL_SCHEMA_NOT_READY",
                            "Provision the additive sandbox-control-schema/v6 upgrade before execution");
                }
            }
            if (jobId != null) new SandboxRunWorkspacePreparer(url, user, password, json)
                    .prepareWithHeldLock(connection, baseline, key, revisionNumber, jobId);
            var prepared = new SandboxRunWorkspacePreparer(url, user, password, json).verify(baseline);
            if (!key.equals(prepared.runSpecKey()) || revisionNumber != prepared.runSpecRevision()
                    || !"sandbox-run-specification/v2".equals(prepared.artifactVersion())) {
                throw new SandboxWorkspaceException("EXECUTION_REVISION_MISMATCH",
                        "Execution accepts only the explicitly requested, currently prepared v2 revision");
            }
            var published = new SandboxRunSpecificationStore(url, user, password, json)
                    .loadRevisionV2(key, revisionNumber);
            try (var input = baseline.getInputStream()) {
                return new SandboxExecutionLease(connection, safety, prepared, published, json.readTree(input), jobId);
            }
        } catch (Exception failure) {
            if (connection != null) {
                if (locked) safety.releasePreparationLock(connection);
                try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
            }
            if (failure instanceof SandboxWorkspaceException workspace) throw workspace;
            throw new SandboxWorkspaceException("EXECUTION_LEASE_FAILED", "Cannot acquire execution workspace", failure);
        }
    }

    public String executionId() { return executionId; }
    public SandboxRunPreparationReport preparation() { return preparation; }
    public SandboxRunSpecificationRevisionV2 revision() { return revision; }

    public void requireHeld() {
        try (var statement = connection.prepareStatement("SELECT IS_USED_LOCK(?)=CONNECTION_ID()")) {
            statement.setString(1, SandboxWorkspaceSafety.LOCK_NAME);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) {
                    throw new SandboxWorkspaceException("EXECUTION_LEASE_LOST", "Workspace execution lock was lost");
                }
            }
        } catch (SQLException failure) {
            throw new SandboxWorkspaceException("EXECUTION_LEASE_LOST", "Cannot verify workspace execution lock", failure);
        }
    }

    public void requireCompatible(org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext runtime) {
        requireHeld();
        if (!runtime.isV2() || !preparation.deterministicSimulationRunId().equals(runtime.deterministicSimulationRunId())
                || !revision.runSpecKey().equals(runtime.revisionV2().runSpecKey())
                || revision.revision() != runtime.revisionV2().revision()) {
            throw new SandboxWorkspaceException("EXECUTION_REVISION_MISMATCH", "Runtime is not bound to this execution lease");
        }
        try (var query = connection.prepareStatement("""
                SELECT COUNT(*) FROM sandbox_workspace_marker WHERE marker_id=1
                  AND workspace_state='EXECUTION_RUNNING' AND active_execution_id=?
                """)) {
            query.setString(1, executionId);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) {
                    throw new SandboxWorkspaceException("EXECUTION_NOT_RUNNING", "Execution must own the running marker");
                }
            }
        } catch (SQLException failure) {
            throw new SandboxWorkspaceException("EXECUTION_STATE_READ_FAILED", "Cannot verify running marker", failure);
        }
    }

    public void create(ObjectMapper json) {
        requireHeld();
        try {
            // Read the published JSON using this authenticated, already locked connection.
            JsonNode scenarioRevision;
            try (var query = connection.prepareStatement("SELECT revision_json FROM sandbox_scenario_revision WHERE scenario_key=? AND revision_no=?")) {
                query.setString(1, preparation.scenarioKey()); query.setInt(2, preparation.scenarioRevision());
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Published scenario disappeared");
                    scenarioRevision = json.readTree(rows.getString(1));
                }
            }
            ObjectNode manifestNode = json.valueToTree(Map.of(
                    "artifactVersion", "sandbox-execution-manifest/v2",
                    "preparation", preparation,
                    "runRevision", revision,
                    "javaVersion", System.getProperty("java.version"),
                    "tickDurationSeconds", revision.specification().simulationClock().tickDurationSeconds()));
            manifestNode.set("baselinePackage", baselinePackage);
            manifestNode.set("scenarioRevision", scenarioRevision);
            manifestNode.set("initialDatabaseFacts", new SandboxBusinessFactCapture(json).readDatabase(connection));
            var canonical = new LexicographicJsonSha256(json);
            String manifest = new String(canonical.canonicalBytes(manifestNode), java.nio.charset.StandardCharsets.UTF_8);
            requirePacketCapacity(manifest);
            transact(() -> {
                try (var insert = connection.prepareStatement("""
                        INSERT INTO sandbox_execution(execution_id,run_spec_key,run_spec_revision,baseline_id,
                          deterministic_run_id,prepared_run_facts_sha256,requested_loops,execution_status,manifest_json,manifest_sha256)
                        VALUES(?,?,?,?,?,?,?,'CREATED',?,?)
                        """)) {
                    insert.setString(1, executionId); insert.setString(2, preparation.runSpecKey());
                    insert.setInt(3, preparation.runSpecRevision()); insert.setString(4, preparation.baselineId());
                    insert.setString(5, preparation.deterministicSimulationRunId());
                    insert.setString(6, preparation.preparedRunFactsSha256());
                    insert.setInt(7, revision.specification().simulationClock().totalLoops()); insert.setString(8, manifest);
                    insert.setString(9, canonical.hash(manifestNode));
                    insert.executeUpdate();
                }
                if (jobId != null) {
                    safety.requireManagementJobOwner(connection, jobId);
                    try (var bind = connection.prepareStatement("UPDATE sandbox_management_job SET execution_id=?,updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND job_status='PREPARING' AND execution_id IS NULL")) {
                        bind.setString(1, executionId); bind.setString(2, jobId);
                        if (bind.executeUpdate() != 1) throw new SQLException("Cannot atomically bind execution to its preparing job");
                    }
                }
            });
            created = true;
        } catch (Exception failure) {
            if (failure instanceof SandboxWorkspaceException workspace) throw workspace;
            throw new SandboxWorkspaceException("EXECUTION_CREATE_FAILED", "Cannot create execution record", failure);
        }
    }

    /** The completed counter advances atomically with the durable tick, never before it. */
    public void recordTick(SimulationTick tick, EvaluationSnapshot evaluation, ObjectNode runtimeFacts, ObjectMapper json) {
        requireHeld();
        if (!preparation.deterministicSimulationRunId().equals(evaluation.simulationRunId())
                || tick.loopIndex() != evaluation.loopIndex() || !tick.tickEnd().equals(evaluation.simTime())
                || evaluation.snapshotStatus() == org.example.roadsimulation.evaluation.EvaluationSnapshotStatus.FAILED
                || evaluation.failedAssignmentCount() > 0
                || evaluation.errorCodes().contains(org.example.roadsimulation.evaluation.EvaluationSnapshotService.HISTORY_PERSIST_FAILED)) {
            throw new SandboxWorkspaceException("JOURNAL_TICK_MISMATCH", "Evaluation does not belong to the completed tick");
        }
        transact(() -> {
            String previous;
            try (var query = connection.prepareStatement("SELECT manifest_sha256,completed_loops FROM sandbox_execution WHERE execution_id=? AND execution_status='RUNNING' FOR UPDATE")) {
                query.setString(1, executionId);
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || rows.getInt(2) != tick.loopIndex()) throw new SQLException("Tick is duplicated, out of order, or execution is not RUNNING");
                    previous = rows.getString(1);
                }
            }
            if (tick.loopIndex() > 0) {
                try (var query = connection.prepareStatement("SELECT tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
                    query.setString(1, executionId); query.setInt(2, tick.loopIndex() - 1);
                    try (var rows = query.executeQuery()) { if (!rows.next()) throw new SQLException("Previous tick missing"); previous = rows.getString(1); }
                }
            }
            var capture = new SandboxBusinessFactCapture(json);
            ObjectNode facts = json.createObjectNode(); facts.put("artifactVersion", SandboxBusinessFactCapture.VERSION);
            facts.put("canonicalization", SandboxExecutionJson.VERSION);
            facts.set("tick", json.valueToTree(tick)); facts.set("database", capture.readDatabase(connection));
            facts.set("runtime", runtimeFacts.deepCopy());
            SandboxBusinessFactCapture.requireFinite(facts);
            ObjectNode business = capture.projectFacts(facts);
            JsonNode evaluationNode = json.valueToTree(evaluation);
            var canonical = new SandboxExecutionJson(json);
            String factsHash = canonical.hash(facts), businessHash = canonical.hash(business), evaluationHash = canonical.hash(evaluationNode);
            String tickHash = SandboxTickIntegrity.hash(json, tick, factsHash, businessHash, evaluationHash, previous);
            String factsText = new String(canonical.canonicalBytes(facts), java.nio.charset.StandardCharsets.UTF_8);
            String businessText = new String(canonical.canonicalBytes(business), java.nio.charset.StandardCharsets.UTF_8);
            String evaluationText = new String(canonical.canonicalBytes(evaluationNode), java.nio.charset.StandardCharsets.UTF_8);
            requirePacketCapacity(factsText, businessText, evaluationText);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO sandbox_execution_tick(execution_id,loop_index,tick_start,tick_end,
                      facts_json,facts_sha256,business_facts_json,business_facts_sha256,evaluation_json,
                      evaluation_sha256,previous_tick_sha256,tick_sha256) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                insert.setString(1, executionId); insert.setInt(2, tick.loopIndex());
                insert.setTimestamp(3, Timestamp.valueOf(tick.tickStart())); insert.setTimestamp(4, Timestamp.valueOf(tick.tickEnd()));
                insert.setString(5, factsText); insert.setString(6, factsHash);
                insert.setString(7, businessText); insert.setString(8, businessHash);
                insert.setString(9, evaluationText); insert.setString(10, evaluationHash);
                insert.setString(11, previous); insert.setString(12, tickHash); insert.executeUpdate();
            }
            try (var update = connection.prepareStatement("UPDATE sandbox_execution SET completed_loops=completed_loops+1 WHERE execution_id=? AND execution_status='RUNNING' AND completed_loops=?")) {
                update.setString(1, executionId); update.setInt(2, tick.loopIndex());
                if (update.executeUpdate() != 1) throw new SQLException("Tick completion changed concurrently");
            }
        });
        // Once the entire horizon is durably recorded, no next business tick exists.
        // A subsequent boundary/status transaction failure belongs to finalization.
        if (tick.loopIndex() + 1 == revision.specification().simulationClock().totalLoops())
            executionPhase = "FINALIZATION";
    }

    public void markRunning() {
        requireHeld();
        transact(() -> {
            try (var update = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker SET workspace_state='EXECUTION_RUNNING',active_execution_id=?,
                      failure_code=NULL,failure_message=NULL,failure_phase=NULL
                    WHERE marker_id=1 AND workspace_state='RUN_SPEC_READY'
                      AND run_spec_key=? AND run_spec_revision=? AND prepared_run_facts_sha256=?
                    """)) {
                update.setString(1, executionId); update.setString(2, preparation.runSpecKey());
                update.setInt(3, preparation.runSpecRevision()); update.setString(4, preparation.preparedRunFactsSha256());
                if (update.executeUpdate() != 1) throw new SQLException("Prepared marker changed before execution");
            }
            try (var update = connection.prepareStatement(
                    "UPDATE sandbox_execution SET execution_status='RUNNING' WHERE execution_id=? AND execution_status='CREATED'")) {
                update.setString(1, executionId);
                if (update.executeUpdate() != 1) throw new SQLException("Execution record is not CREATED");
            }
            if (jobId != null) {
                safety.requireManagementJobOwner(connection, jobId);
                try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status='RUNNING',updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND job_status='PREPARING' AND execution_id=?")) {
                    update.setString(1, jobId); update.setString(2, executionId);
                    if (update.executeUpdate() != 1) throw new SQLException("Preparing job changed before running");
                }
            }
        });
        executionPhase = "TICK";
    }

    /** Serializes cancellation acceptance and finalization at a durable tick boundary. */
    public boolean stopAtBoundary(boolean horizonReached) {
        requireHeld();
        if (jobId == null) {
            if (horizonReached) beginFinalization();
            return false;
        }
        boolean[] cancelled = {false};
        transact(() -> {
            safety.requireManagementJobOwner(connection, jobId);
            try (var query = connection.prepareStatement("SELECT j.job_status,e.completed_loops,e.requested_loops FROM sandbox_management_job j JOIN sandbox_execution e ON e.execution_id=j.execution_id WHERE j.job_id=? AND j.execution_id=? FOR UPDATE")) {
                query.setString(1, jobId); query.setString(2, executionId);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Job execution association disappeared");
                    String status = rows.getString(1);
                    if (horizonReached && rows.getInt(2) != rows.getInt(3)) throw new SQLException("Finalization boundary precedes durable horizon completion");
                    cancelled[0] = "CANCEL_REQUESTED".equals(status);
                    if (!cancelled[0] && !"RUNNING".equals(status)) throw new SQLException("Job is not executing");
                }
            }
            if (cancelled[0] || horizonReached) {
                try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status='FINALIZING',updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=?")) {
                    update.setString(1, jobId); update.executeUpdate();
                }
            }
        });
        if (cancelled[0] || horizonReached) beginFinalization();
        return cancelled[0];
    }

    /** Called only after the executor has stopped at a complete durable boundary. */
    public void beginFinalization() {
        requireHeld();
        executionPhase = "FINALIZATION";
    }

    public void finishCancelled(int completedLoops) {
        finish(completedLoops, null, true);
    }

    /** Business failure wins over cancellation; close acceptance before failure finalization. */
    public void beginFailureFinalization() {
        requireHeld();
        if (jobId == null) return;
        transact(() -> {
            safety.requireManagementJobOwner(connection, jobId);
            try (var update = connection.prepareStatement("UPDATE sandbox_management_job SET job_status='FINALIZING',updated_at=CURRENT_TIMESTAMP(6) WHERE job_id=? AND execution_id=? AND job_status IN('PREPARING','RUNNING','CANCEL_REQUESTED','FINALIZING')")) {
                update.setString(1, jobId); update.setString(2, executionId);
                if (update.executeUpdate() != 1) throw new SQLException("Cannot begin failure finalization");
            }
        });
    }

    public void finish(int completedLoops, Throwable failure) {
        finish(completedLoops, failure, false);
    }

    private void finish(int completedLoops, Throwable failure, boolean cancelled) {
        if (!created) return;
        requireHeld();
        boolean success = failure == null && !cancelled;
        if (cancelled && jobId == null) throw new IllegalStateException("Cancellation requires a management job");
        if (success && completedLoops != revision.specification().simulationClock().totalLoops()) {
            throw new IllegalArgumentException("A successful execution must complete its entire horizon");
        }
        String code = cancelled ? "USER_CANCELLED" : failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode()
                : failure == null ? null : "SANDBOX_EXECUTION_FAILED";
        String message = failure == null ? null : abbreviate(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        transact(() -> {
            String previousStatus;
            try (var query = connection.prepareStatement("SELECT completed_loops,execution_status FROM sandbox_execution WHERE execution_id=? FOR UPDATE")) {
                query.setString(1, executionId);
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != completedLoops) throw new SQLException("Reported completion differs from durable journal");
                    previousStatus = rows.getString(2);
                }
            }
            String phase = cancelled || success ? null : "CREATED".equals(previousStatus) ? "STARTUP" : executionPhase;
            try (var update = connection.prepareStatement("""
                    UPDATE sandbox_execution SET execution_status=?,completed_loops=?,failed_loop_index=?,
                      failure_code=?,failure_message=?,failure_phase=?,finished_at=CURRENT_TIMESTAMP(6)
                    WHERE execution_id=? AND execution_status IN ('CREATED','RUNNING')
                    """)) {
                update.setString(1, cancelled ? "CANCELLED" : success ? "COMPLETED" : "FAILED"); update.setInt(2, completedLoops);
                if ("TICK".equals(phase)) update.setInt(3, completedLoops); else update.setNull(3, Types.INTEGER);
                update.setString(4, code); update.setString(5, message); update.setString(6, phase); update.setString(7, executionId);
                if (update.executeUpdate() != 1) throw new SQLException("Execution already terminal or missing");
            }
            try (var update = connection.prepareStatement("""
                    UPDATE sandbox_workspace_marker SET workspace_state=?,active_execution_id=?,failure_code=?,
                      failure_message=?,failure_phase=? WHERE marker_id=1
                      AND (active_execution_id=? OR (active_execution_id IS NULL AND workspace_state='RUN_SPEC_READY'))
                    """)) {
                update.setString(1, cancelled ? "EXECUTION_CANCELLED" : success ? "EXECUTION_COMPLETED" : "FAILED"); update.setString(2, executionId);
                update.setString(3, code); update.setString(4, message); update.setString(5, phase); update.setString(6, executionId);
                if (update.executeUpdate() != 1) throw new SQLException("Execution marker missing");
            }
            if (jobId != null) {
                org.example.roadsimulation.sandbox.management.SandboxManagementJobStore.finishWithLease(
                        connection, jobId, executionId, cancelled ? "CANCELLED" : success ? "SUCCEEDED" : "FAILED", code);
            }
        });
    }

    private void transact(SqlAction action) {
        try {
            connection.setAutoCommit(false);
            try { action.run(); connection.commit(); }
            catch (Exception failure) { connection.rollback(); throw failure; }
            finally { connection.setAutoCommit(true); }
        } catch (Exception failure) {
            if (failure instanceof SandboxWorkspaceException workspace) throw workspace;
            throw new SandboxWorkspaceException("EXECUTION_STATE_WRITE_FAILED", "Cannot persist execution state", failure);
        }
    }

    private static String abbreviate(String value) { return value.length() <= 1000 ? value : value.substring(0, 1000); }
    private void requirePacketCapacity(String... payloads) throws SQLException {
        long bytes = 0;
        for (String value : payloads) bytes += value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        // Client-side prepared statements can escape every input byte. Reserve the
        // worst case, rather than let a too-large packet kill the held lock connection.
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT @@max_allowed_packet")) {
            if (!rows.next() || bytes * 2 + 8192 > rows.getLong(1)) {
                throw new SandboxWorkspaceException("EXECUTION_PACKET_LIMIT", "Journal payload has " + bytes
                        + " UTF-8 bytes; max_allowed_packet must cover escaped payload plus SQL overhead");
            }
        }
    }
    @FunctionalInterface private interface SqlAction { void run() throws Exception; }

    @Override public void close() throws SQLException {
        safety.releasePreparationLock(connection);
        connection.close();
    }
}
