package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** Read-only, snapshot-consistent verification/export of preserved execution artifacts. */
public final class SandboxExecutionStore {
    private static final int VERIFICATION_PAGE_SIZE = 4;

    public record VerifiedTick(SimulationTick tick, JsonNode facts, JsonNode evaluation) {}
    public record VerifiedJournal(ObjectNode metadata, int recordedLoops, int failedAssignments, VerifiedTick lastTick) {}

    private final String url, user, password;
    private final ObjectMapper json;
    private final ObjectMapper artifactJson;
    private final SandboxExecutionJson canonical;

    public SandboxExecutionStore(String url, String user, String password, ObjectMapper json) {
        this.url = url; this.user = user; this.password = password; this.json = json;
        artifactJson = json.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        canonical = new SandboxExecutionJson(json);
    }

    public ObjectNode export(String executionId) {
        return readSnapshot(executionId, true, null).metadata();
    }

    /** Verifies every payload and chain link while retaining only the final tick. */
    public VerifiedJournal verifyJournal(String executionId) {
        return verifyJournal(executionId, null);
    }

    /** Observations are provisional until verification of the entire journal returns successfully. */
    public VerifiedJournal verifyJournal(String executionId, Consumer<VerifiedTick> observer) {
        return readSnapshot(executionId, false, observer);
    }

    private VerifiedJournal readSnapshot(String executionId, boolean includeTicks, Consumer<VerifiedTick> observer) {
        try { UUID.fromString(executionId); }
        catch (Exception failure) { throw new SandboxWorkspaceException("INVALID_EXECUTION_ID", "execution-id must be a UUID"); }
        var safety = new SandboxWorkspaceSafety();
        if (!SandboxWorkspaceSafety.DATABASE_NAME.equals(safety.databaseName(url))) {
            throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Execution history requires the sandbox database");
        }
        try (var connection = DriverManager.getConnection(url, user, password)) {
            safety.requireSafeTarget(url, connection);
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var result = read(connection, executionId, includeTicks, observer);
                connection.commit(); return result;
            } catch (Exception failure) { connection.rollback(); throw failure; }
        } catch (SandboxWorkspaceException failure) { throw failure; }
        catch (Exception failure) {
            throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Cannot verify execution history", failure);
        }
    }

    public Map<String, Object> verify(String executionId) {
        return verificationReport(verifyJournal(executionId));
    }

    /** Discovery only. A matching ID is authenticated by journal verification before its facts are used. */
    public List<String> findHeuristicComparisons(String commonInputFingerprint, String profileId) {
        var safety = new SandboxWorkspaceSafety();
        if (!SandboxWorkspaceSafety.DATABASE_NAME.equals(safety.databaseName(url))) {
            throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Execution history requires the sandbox database");
        }
        try (var connection = DriverManager.getConnection(url, user, password)) {
            safety.requireSafeTarget(url, connection);
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement("""
                    SELECT execution_id FROM sandbox_execution
                    WHERE execution_status='COMPLETED' AND completed_loops=requested_loops
                      AND JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(manifest_json),manifest_json,'{}'),'$.executionMode'))=?
                      AND JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(manifest_json),manifest_json,'{}'),'$.sphericalComparison.commonInputFingerprint'))=?
                      AND JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(manifest_json),manifest_json,'{}'),'$.runRevision.algorithmProfile.profileId'))=?
                    ORDER BY finished_at DESC, execution_id
                    """)) {
                statement.setString(1, org.example.roadsimulation.sandbox.optimization.SphericalComparisonRun.MODE);
                statement.setString(2, commonInputFingerprint);
                statement.setString(3, profileId);
                try (var rows = statement.executeQuery()) {
                    var result = new ArrayList<String>();
                    while (rows.next()) result.add(rows.getString(1));
                    return List.copyOf(result);
                }
            }
        } catch (SQLException failure) {
            throw new SandboxWorkspaceException("EXECUTION_HISTORY_READ_FAILED", "Cannot find compatible HEURISTIC executions", failure);
        }
    }

    /** Used by inspected closure under the caller's authenticated lock and transaction. */
    public Map<String, Object> verify(Connection connection, String executionId) throws Exception {
        new SandboxWorkspaceSafety().requirePreparationLockHeld(connection);
        return verificationReport(read(connection, executionId, false, null));
    }

    private Map<String, Object> verificationReport(VerifiedJournal journal) {
        var result = journal.metadata();
        return Map.of("executionId", result.path("executionId").asText(), "status", result.path("status").asText(),
                "requestedLoops", result.path("requestedLoops").asInt(), "recordedLoops", journal.recordedLoops(),
                "manifestSha256", result.path("manifestSha256").asText(),
                "lastTickSha256", result.path("lastTickSha256").asText(), "integrity", "VERIFIED");
    }

    private VerifiedJournal read(Connection connection, String executionId, boolean includeTicks,
                                 Consumer<VerifiedTick> observer) throws Exception {
        ObjectNode result = json.createObjectNode();
        result.put("artifactVersion", "sandbox-execution-export/v1"); result.put("executionId", executionId);
        result.put("canonicalization", SandboxExecutionJson.VERSION);
        JsonNode manifest;
        int completed, requested;
        String status, previous;
        try (var query = connection.prepareStatement("SELECT * FROM sandbox_execution WHERE execution_id=?")) {
            query.setString(1, executionId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new SandboxWorkspaceException("EXECUTION_NOT_FOUND", "No execution with the requested ID");
                String manifestText = rows.getString("manifest_json");
                manifest = artifactJson.readTree(manifestText); previous = rows.getString("manifest_sha256");
                if (previous == null) throw new SandboxWorkspaceException("EXECUTION_UNJOURNALED", "Legacy v5 execution has no permanent tick journal");
                require(previous.equals(SandboxExecutionJson.hashManifestText(manifestText)), "Manifest fingerprint mismatch");
                verifyExecutionMode(manifest);
                requested = rows.getInt("requested_loops"); completed = rows.getInt("completed_loops"); status = rows.getString("execution_status");
                require(manifest.path("runRevision").path("specification").path("simulationClock").path("totalLoops").asInt() == requested,
                        "Requested horizon differs from manifest");
                require(manifest.path("preparation").path("runSpecKey").asText().equals(rows.getString("run_spec_key"))
                        && manifest.path("preparation").path("runSpecRevision").asInt() == rows.getInt("run_spec_revision")
                        && manifest.path("preparation").path("baselineId").asText().equals(rows.getString("baseline_id"))
                        && manifest.path("preparation").path("deterministicSimulationRunId").asText().equals(rows.getString("deterministic_run_id"))
                        && manifest.path("preparation").path("preparedRunFactsSha256").asText().equals(rows.getString("prepared_run_facts_sha256")),
                        "Execution identity differs from manifest");
                result.put("status", status); result.put("requestedLoops", requested); result.put("completedLoops", completed);
                result.set("failedLoopIndex", json.valueToTree(rows.getObject("failed_loop_index")));
                result.put("failureCode", rows.getString("failure_code")); result.put("failureMessage", rows.getString("failure_message"));
                result.put("failurePhase", rows.getString("failure_phase"));
                result.put("manifestSha256", previous); result.set("manifest", manifest);
                result.put("manifestJson", manifestText);
            }
        }
        var clock = manifest.path("runRevision").path("specification").path("simulationClock");
        LocalDateTime start = LocalDateTime.parse(clock.path("startLocalDateTime").asText());
        long seconds = clock.path("tickDurationSeconds").asLong();
        var ticks = includeTicks ? result.putArray("ticks") : null;
        int recorded = 0, failedAssignments = 0;
        VerifiedTick lastTick = null;
        var capture = new SandboxBusinessFactCapture(json);
        // Bound JDBC buffering as well as the decoded JSON retained by the verifier.
        try (var query = connection.prepareStatement("""
                SELECT * FROM sandbox_execution_tick WHERE execution_id=? AND loop_index>=?
                ORDER BY loop_index LIMIT ?
                """)) {
            query.setString(1, executionId);
            query.setInt(3, VERIFICATION_PAGE_SIZE);
            int pageSize;
            do {
                query.setInt(2, recorded == 0 ? Integer.MIN_VALUE : recorded);
                pageSize = 0;
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        int index = rows.getInt("loop_index");
                        require(index == recorded && recorded < completed, "Tick gap, duplicate, or wrong order");
                        var expected = SimulationTick.of(index, start.plusSeconds(Math.multiplyExact((long) index, seconds)), Duration.ofSeconds(seconds));
                        require(expected.tickStart().equals(rows.getTimestamp("tick_start").toLocalDateTime())
                                && expected.tickEnd().equals(rows.getTimestamp("tick_end").toLocalDateTime()), "Tick window differs from manifest");
                        JsonNode facts = artifactJson.readTree(rows.getString("facts_json"));
                        JsonNode business = artifactJson.readTree(rows.getString("business_facts_json"));
                        JsonNode evaluation = artifactJson.readTree(rows.getString("evaluation_json"));
                        if ("GLOBAL_PLAN".equals(manifest.path("executionMode").asText())) {
                            var planFacts=facts.path("runtime").path("planExecution");
                            require(manifest.path("planExecutionTaskSha256").asText().equals(planFacts.path("state").path("taskFingerprint").asText()),
                                    "Plan tick belongs to a different task");
                            require(manifest.path("planExecutionTask").path("compiledProblem").path("dependencies").path("catalogFingerprint").asText()
                                    .equals(planFacts.path("mapping").path("catalogFingerprint").asText()), "Plan mapping catalog mismatch");
                        } else if (org.example.roadsimulation.sandbox.optimization.SphericalComparisonRun.MODE
                                .equals(manifest.path("executionMode").asText())) {
                            var runtime = facts.path("runtime");
                            require(manifest.path("sphericalComparisonSha256").asText().equals(runtime.path("sphericalComparisonSha256").asText())
                                    && runtime.path("comparisonMapRequests").isIntegralNumber()
                                    && runtime.path("comparisonMapRequests").intValue() == 0,
                                    "Tick does not certify the frozen spherical comparison");
                        }
                        String factsHash = canonical.hash(facts), businessHash = canonical.hash(business), evaluationHash = canonical.hash(evaluation);
                        require(factsHash.equals(rows.getString("facts_sha256")) && businessHash.equals(rows.getString("business_facts_sha256"))
                                && evaluationHash.equals(rows.getString("evaluation_sha256")), "Tick payload fingerprint mismatch");
                        require(canonical.hash(capture.projectFacts((ObjectNode) facts)).equals(businessHash), "Business projection differs from raw facts");
                        require(expected.equals(json.treeToValue(facts.path("tick"), SimulationTick.class)), "Raw fact tick differs from journal");
                        require(evaluation.path("loopIndex").asInt() == index
                                && expected.tickEnd().equals(LocalDateTime.parse(evaluation.path("simTime").asText()))
                                && evaluation.path("simulationRunId").asText().equals(manifest.path("preparation").path("deterministicSimulationRunId").asText()),
                                "Evaluation belongs to a different tick or run");
                        var typedEvaluation = json.treeToValue(evaluation, org.example.roadsimulation.evaluation.EvaluationSnapshot.class);
                        var failureCount = evaluation.path("failedAssignmentCount");
                        require(failureCount.isIntegralNumber() && failureCount.canConvertToInt()
                                && typedEvaluation.failedAssignmentCount() == 0
                                && typedEvaluation.snapshotStatus() != org.example.roadsimulation.evaluation.EvaluationSnapshotStatus.FAILED
                                && !typedEvaluation.errorCodes().contains(org.example.roadsimulation.evaluation.EvaluationSnapshotService.HISTORY_PERSIST_FAILED),
                                "Recorded tick contains a failed authoritative evaluation");
                        require(previous.equals(rows.getString("previous_tick_sha256")), "Tick integrity chain is broken");
                        String tickHash = SandboxTickIntegrity.hash(json, expected, factsHash, businessHash, evaluationHash, previous);
                        require(tickHash.equals(rows.getString("tick_sha256")), "Tick chain fingerprint mismatch");
                        if (ticks != null) {
                            ObjectNode tick = ticks.addObject(); tick.set("tick", json.valueToTree(expected));
                            tick.set("facts", facts); tick.set("businessFacts", business); tick.set("evaluation", evaluation);
                            tick.put("factsSha256", factsHash); tick.put("businessFactsSha256", businessHash);
                            tick.put("evaluationSha256", evaluationHash); tick.put("previousTickSha256", previous); tick.put("tickSha256", tickHash);
                        }
                        if (observer != null || recorded == completed - 1) {
                            var verified = new VerifiedTick(expected, facts, evaluation);
                            if (recorded == completed - 1) lastTick = verified;
                            if (observer != null) observer.accept(verified);
                        }
                        failedAssignments = Math.addExact(failedAssignments, typedEvaluation.failedAssignmentCount());
                        previous = tickHash;
                        recorded++;
                        pageSize++;
                    }
                }
            } while (pageSize == VERIFICATION_PAGE_SIZE);
        }
        require(recorded == completed && completed <= requested, "Completion count differs from journal");
        if ("COMPLETED".equals(status)) require(completed == requested && result.path("failedLoopIndex").isNull(), "Incomplete run marked COMPLETED");
        if ("CANCELLED".equals(status)) require(result.path("failedLoopIndex").isNull()
                && "USER_CANCELLED".equals(result.path("failureCode").asText())
                && result.path("failurePhase").isNull(), "Invalid cancellation record");
        if ("INTERRUPTED".equals(status)) require(result.path("failedLoopIndex").isNull()
                && "INTERRUPTION".equals(result.path("failurePhase").asText()), "Invalid inspected interruption record");
        if ("FAILED".equals(status)) {
            String phase = result.path("failurePhase").asText();
            if ("TICK".equals(phase)) require(completed < requested && result.path("failedLoopIndex").isIntegralNumber()
                    && result.path("failedLoopIndex").asInt() == completed, "Failure index differs from recorded prefix");
            else if ("STARTUP".equals(phase)) require(completed == 0 && result.path("failedLoopIndex").isNull(), "Invalid startup failure");
            // Cancellation can enter finalization with any complete prefix, including
            // zero ticks. It is not a failed attempt to execute the next tick.
            else if ("FINALIZATION".equals(phase)) require(result.path("failedLoopIndex").isNull(), "Invalid finalization failure");
            else require(false, "Unknown failure phase");
        }
        result.put("integrity", "VERIFIED"); result.put("lastTickSha256", previous);
        return new VerifiedJournal(result, recorded, failedAssignments, lastTick);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", message);
    }

    private void verifyExecutionMode(JsonNode manifest) {
        String version = manifest.path("artifactVersion").asText();
        String mode = manifest.path("executionMode").asText();
        var comparisonMode = org.example.roadsimulation.sandbox.optimization.SphericalComparisonRun.MODE;
        boolean spherical = "sandbox-execution-manifest/v4".equals(version);
        require(spherical == comparisonMode.equals(mode), "Spherical comparison mode/version mismatch");
        if (spherical) {
            try {
                var comparison = json.treeToValue(manifest.path("sphericalComparison"),
                        org.example.roadsimulation.sandbox.optimization.SphericalComparisonRun.class);
                require(comparison.fingerprint().equals(manifest.path("sphericalComparisonSha256").asText()),
                        "Spherical comparison fingerprint mismatch");
                var baseline = new org.springframework.core.io.ByteArrayResource(json.writeValueAsBytes(manifest.path("baselinePackage")));
                var scene = json.treeToValue(manifest.path("scenarioRevision"),
                        org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1.class);
                var run = json.treeToValue(manifest.path("runRevision"),
                        org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2.class);
                comparison.verify(baseline, scene, run, json);
            } catch (Exception failure) {
                throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Cannot authenticate spherical comparison", failure);
            }
            return;
        }
        boolean plan="sandbox-execution-manifest/v3".equals(manifest.path("artifactVersion").asText());
        require(plan=="GLOBAL_PLAN".equals(manifest.path("executionMode").asText()),"Plan mode/version mismatch");
        if(!plan)return;
        var task=org.example.roadsimulation.sandbox.optimization.OptimizationJson.read(manifest.path("planExecutionTask").toString(),
                org.example.roadsimulation.sandbox.optimization.PlanExecutionTask.class);
        require(task.fingerprint().equals(manifest.path("planExecutionTaskSha256").asText()),"Plan task fingerprint mismatch");
        var baseline=new org.springframework.core.io.ByteArrayResource(manifest.path("baselinePackage").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            var scene=json.treeToValue(manifest.path("scenarioRevision"),org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1.class);
            var run=json.treeToValue(manifest.path("runRevision"),org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2.class);
            org.example.roadsimulation.sandbox.optimization.PlanExecutionAuthentication.verify(task,baseline,scene,run);
        }catch(Exception failure){throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT","Cannot authenticate frozen plan input",failure);}
    }
}
