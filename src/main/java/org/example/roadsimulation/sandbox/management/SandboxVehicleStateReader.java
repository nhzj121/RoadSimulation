package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;

/** A bounded, read-only journal projection. No live vehicle query or full JSON response. */
public final class SandboxVehicleStateReader {
    private final SandboxResultReader reader;
    public SandboxVehicleStateReader(SandboxResultReader reader) { this.reader = reader; }
    private record Frozen(ObjectNode database, JsonNode evaluation, String factVersion, String tickHash,
                          String factsHash, String evaluationHash, String previousHash) {}
    public ObjectNode read(String id, int index) {
        return read(id, index, true);
    }
    public ObjectNode read(String id, int index, boolean includeWindow) {
        if (index < 0) throw new SandboxWorkspaceException("INVALID_RANGE", "Nonnegative loop required");
        return reader.snapshot(c -> {
            var ctx = reader.context(c, id);
            if (index >= ctx.recorded()) throw new SandboxWorkspaceException("TICK_NOT_FOUND", "No complete recorded tick");
            int from = Math.max(0, index - 19);
            var lower = ctx.start().plusSeconds(Math.multiplyExact((long) from, ctx.durationSeconds()));
            var upper = ctx.start().plusSeconds(Math.multiplyExact((long) index + 1, ctx.durationSeconds()));
            var projection = new SandboxVehicleStateProjection(reader.mapper(), ctx.manifest().path("initialDatabaseFacts"), lower, upper);
            String previous = ctx.configuration().manifestSha256();
            if (includeWindow && from > 0) {
                var boundary = tick(c, ctx, from - 1);
                checkPrevious(c, ctx, from - 1, boundary.previousHash());
                previous = boundary.tickHash(); projection.observe(boundary.database(), lower, true);
            }
            Frozen last = null;
            // Process one payload at a time; never buffer twenty large raw JSON
            // strings or send the frozen business tables to the browser.
            for (int loop = includeWindow ? from : index; loop <= index; loop++) {
                var current = tick(c, ctx, loop);
                if (includeWindow) {
                    SandboxResultReader.require(previous.equals(current.previousHash()), "EXECUTION_RECORD_CORRUPT");
                    projection.observe(current.database(), ctx.start().plusSeconds(Math.multiplyExact((long) loop + 1, ctx.durationSeconds())), false);
                } else checkPrevious(c, ctx, index, current.previousHash());
                previous = current.tickHash(); last = current;
            }
            var result = includeWindow ? projection.finish(last.database(), last.evaluation(), ctx.start()) : projection.current(last.database());
            result.put("artifactVersion", "sandbox-vehicle-states/v1"); result.put("executionId", id);
            result.put("manifestSha256", ctx.configuration().manifestSha256()); result.put("loopIndex", index);
            result.put("simTime", last.evaluation().path("simTime").asText()); result.put("windowLoops", 20); result.put("fromLoopIndex", from); result.put("toLoopIndex", index);
            result.put("windowStart", lower.toString()); result.put("windowEnd", upper.toString());
            result.put("tickSha256", last.tickHash()); result.put("factsSha256", last.factsHash()); result.put("evaluationSha256", last.evaluationHash());
            result.put("factVersion", last.factVersion()); result.put("source", "FROZEN_VEHICLE_SNAPSHOTS_AND_RECORDED_INTERVALS");
            result.put("boundaryPolicy", "INTERVAL_OVERLAP_WITH_WINDOW_END_INCLUDED");
            result.put("checkedFromLoopIndex", includeWindow ? Math.max(0, from - 1) : index);
            result.put("integrityScope", includeWindow ? "MANIFEST_WINDOW_FACTS_EVALUATIONS_AND_LOCAL_TICK_LINKS" : "MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK"); result.put("ledgerIntegrity", "NOT_CHECKED");
            return result;
        });
    }
    private void checkPrevious(Connection c, SandboxResultReader.Context ctx, int index, String hash) throws Exception {
        String expected = ctx.configuration().manifestSha256();
        if (index > 0) try (var q = c.prepareStatement("SELECT tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
            q.setString(1, ctx.id()); q.setInt(2, index - 1);
            try (var r = q.executeQuery()) { SandboxResultReader.require(r.next(), "EXECUTION_RECORD_CORRUPT"); expected = r.getString(1); }
        }
        SandboxResultReader.require(Objects.equals(expected, hash), "EXECUTION_RECORD_CORRUPT");
    }
    private Frozen tick(Connection c, SandboxResultReader.Context ctx, int index) throws Exception {
        try (var q = c.prepareStatement("SELECT execution_id,loop_index,tick_start,tick_end,facts_json,facts_sha256,business_facts_sha256,evaluation_json,evaluation_sha256,previous_tick_sha256,tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
            q.setString(1, ctx.id()); q.setInt(2, index);
            try (var r = q.executeQuery()) {
                SandboxResultReader.require(r.next() && ctx.id().equals(r.getString("execution_id")), "EXECUTION_RECORD_CORRUPT");
                var evaluation = (JsonNode) reader.evaluationItem(ctx, r, index).get("evaluation");
                var facts = reader.parse(r.getString("facts_json")); String version = facts.path("artifactVersion").asText();
                if (!Set.of(SandboxBusinessFactCapture.LEGACY_VERSION, SandboxBusinessFactCapture.VERSION).contains(version))
                    throw new SandboxWorkspaceException("UNSUPPORTED_FACT_VERSION", "Unknown recorded vehicle fact version");
                String hash = new SandboxExecutionJson(reader.mapper()).hash(facts);
                var tick = SimulationTick.of(index, ctx.start().plusSeconds(Math.multiplyExact((long) index, ctx.durationSeconds())), Duration.ofSeconds(ctx.durationSeconds()));
                SandboxResultReader.require(hash.equals(r.getString("facts_sha256")) && facts.path("database").isObject()
                    && tick.equals(reader.mapper().treeToValue(facts.path("tick"), SimulationTick.class))
                    && SandboxTickIntegrity.hash(reader.mapper(), tick, hash, r.getString("business_facts_sha256"), r.getString("evaluation_sha256"), r.getString("previous_tick_sha256")).equals(r.getString("tick_sha256")), "EXECUTION_RECORD_CORRUPT");
                return new Frozen((ObjectNode) facts.path("database"), evaluation, version, r.getString("tick_sha256"), hash, r.getString("evaluation_sha256"), r.getString("previous_tick_sha256"));
            }
        }
    }
}
