package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.time.Duration;
import java.util.*;

/** One frozen end-tick contains cumulative episodes. No N-tick JSON download or business SQL. */
public final class SandboxServiceActivityReader {
    private final SandboxResultReader reader;
    public SandboxServiceActivityReader(SandboxResultReader reader) { this.reader = reader; }
    public ObjectNode read(String id, int index, int windowLoops) {
        if (index < 0 || !Set.of(1, 5, 10, 20).contains(windowLoops))
            throw new SandboxWorkspaceException("INVALID_RANGE", "Allowed window loops: 1,5,10,20; nonnegative index required");
        return reader.snapshot(c -> {
            var ctx = reader.context(c, id);
            if (index >= ctx.recorded()) throw new SandboxWorkspaceException("TICK_NOT_FOUND", "No complete recorded tick");
            // Never transfer the duplicate business_facts_json for a read that
            // only needs raw episode facts and integrity metadata.
            try (var q = c.prepareStatement("SELECT execution_id,loop_index,tick_start,tick_end,facts_json,facts_sha256,business_facts_sha256,evaluation_json,evaluation_sha256,previous_tick_sha256,tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
                q.setString(1, id); q.setInt(2, index);
                try (var r = q.executeQuery()) {
                    SandboxResultReader.require(r.next(), "EXECUTION_RECORD_CORRUPT");
                    var evaluation = reader.evaluationItem(ctx, r, index);
                    var facts = reader.parse(r.getString("facts_json")); var canonical = new SandboxExecutionJson(reader.mapper());
                    String factsHash = canonical.hash(facts), version = facts.path("artifactVersion").asText();
                    SandboxResultReader.require(factsHash.equals(r.getString("facts_sha256")), "EXECUTION_RECORD_CORRUPT");
                    if (!Set.of(SandboxBusinessFactCapture.LEGACY_VERSION, SandboxBusinessFactCapture.VERSION).contains(version))
                        throw new SandboxWorkspaceException("UNSUPPORTED_FACT_VERSION", "Unknown recorded fact version");
                    var tick = SimulationTick.of(index, ctx.start().plusSeconds(Math.multiplyExact((long) index, ctx.durationSeconds())), Duration.ofSeconds(ctx.durationSeconds()));
                    SandboxResultReader.require(facts.isObject() && facts.path("database").isObject()
                            && tick.equals(reader.mapper().treeToValue(facts.path("tick"), SimulationTick.class)), "EXECUTION_RECORD_CORRUPT");
                    String previous = ctx.configuration().manifestSha256();
                    if (index > 0) try (var prev = c.prepareStatement("SELECT tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
                        prev.setString(1,id); prev.setInt(2,index-1);
                        try (var row = prev.executeQuery()) { SandboxResultReader.require(row.next(), "EXECUTION_RECORD_CORRUPT"); previous = row.getString(1); }
                    }
                    SandboxResultReader.require(Objects.equals(previous, r.getString("previous_tick_sha256"))
                            && SandboxTickIntegrity.hash(reader.mapper(), tick, factsHash, r.getString("business_facts_sha256"), r.getString("evaluation_sha256"), previous).equals(r.getString("tick_sha256")), "EXECUTION_RECORD_CORRUPT");
                    int from = Math.max(0, index-windowLoops+1);
                    var lower = ctx.start().plusSeconds(Math.multiplyExact((long) from, ctx.durationSeconds()));
                    var result = new SandboxServiceActivityProjection(reader.mapper()).project(facts.path("database"),
                            reader.mapper().valueToTree(evaluation.get("evaluation")), ctx.start(), lower, tick.tickEnd(), from==0);
                    result.put("artifactVersion", "sandbox-service-activity/v1"); result.put("executionId", id);
                    result.put("manifestSha256", ctx.configuration().manifestSha256()); result.put("loopIndex", index);
                    result.put("simTime", ((com.fasterxml.jackson.databind.JsonNode)evaluation.get("evaluation")).path("simTime").asText());
                    result.put("windowLoops", windowLoops); result.put("fromLoopIndex", from); result.put("toLoopIndex", index);
                    result.put("windowStart", lower.toString()); result.put("windowEnd", tick.tickEnd().toString());
                    result.put("tickSha256", r.getString("tick_sha256")); result.put("factsSha256", factsHash);
                    result.put("evaluationSha256", r.getString("evaluation_sha256")); result.put("factVersion", version);
                    result.put("integrityScope", "MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK"); result.put("ledgerIntegrity", "NOT_CHECKED");
                    return result;
                }
            }
        });
    }
}
