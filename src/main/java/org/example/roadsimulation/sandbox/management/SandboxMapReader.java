package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.time.Duration;
import java.util.Objects;

/** Authenticated single-tick display reader. No live business SQL, mutation or complete-ledger claim. */
public final class SandboxMapReader {
    private final SandboxResultReader reader;
    private final SandboxMapProjection projection;
    public SandboxMapReader(SandboxResultReader reader) { this.reader=reader;projection=new SandboxMapProjection(reader.mapper()); }
    public ObjectNode context(String id) {
        return reader.snapshot(c->{var ctx=reader.context(c,id);var result=identity("sandbox-map-context/v1",ctx);
            result.set("pois",projection.pois(ctx.manifest().path("initialDatabaseFacts")));
            result.set("baseline",reader.mapper().valueToTree(ctx.configuration().baseline()));
            result.put("scenarioKey",ctx.configuration().scenarioRevision().path("scenarioKey").asText());
            result.put("scenarioRevision",ctx.configuration().scenarioRevision().path("revision").asInt());
            result.put("integrityScope","MANIFEST_PAYLOAD");return result;});
    }
    public ObjectNode snapshot(String id,int index) {
        if(index<0) throw new SandboxWorkspaceException("INVALID_RANGE","Nonnegative loop required");
        return reader.snapshot(c->{var ctx=reader.context(c,id);
            if(index>=ctx.recorded()) throw new SandboxWorkspaceException("TICK_NOT_FOUND","No complete recorded tick");
            try(var q=c.prepareStatement("SELECT * FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
                q.setString(1,id);q.setInt(2,index);try(var r=q.executeQuery()) {
                    SandboxResultReader.require(r.next(),"EXECUTION_RECORD_CORRUPT");
                    var evaluation=reader.evaluationItem(ctx,r,index);var facts=reader.parse(r.getString("facts_json"));
                    var canonical=new SandboxExecutionJson(reader.mapper());String factsHash=canonical.hash(facts);
                    SandboxResultReader.require(factsHash.equals(r.getString("facts_sha256")),"EXECUTION_RECORD_CORRUPT");
                    String version=facts.path("artifactVersion").asText();
                    if(!java.util.Set.of(SandboxBusinessFactCapture.LEGACY_VERSION,SandboxBusinessFactCapture.VERSION).contains(version))
                        throw new SandboxWorkspaceException("UNSUPPORTED_FACT_VERSION","Map cannot interpret unknown recorded fact version");
                    var tick=SimulationTick.of(index,ctx.start().plusSeconds(Math.multiplyExact((long)index,ctx.durationSeconds())),Duration.ofSeconds(ctx.durationSeconds()));
                    SandboxResultReader.require(facts.isObject()&&facts.path("database").isObject()
                        &&tick.equals(reader.mapper().treeToValue(facts.path("tick"),SimulationTick.class)),"EXECUTION_RECORD_CORRUPT");
                    String previous=ctx.configuration().manifestSha256();
                    if(index>0)try(var prev=c.prepareStatement("SELECT tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")){
                        prev.setString(1,id);prev.setInt(2,index-1);try(var row=prev.executeQuery()){SandboxResultReader.require(row.next(),"EXECUTION_RECORD_CORRUPT");previous=row.getString(1);}}
                    SandboxResultReader.require(Objects.equals(previous,r.getString("previous_tick_sha256"))
                        &&SandboxTickIntegrity.hash(reader.mapper(),tick,factsHash,r.getString("business_facts_sha256"),r.getString("evaluation_sha256"),previous).equals(r.getString("tick_sha256")),"EXECUTION_RECORD_CORRUPT");
                    var projected=projection.project(facts.path("database"));
                    SandboxResultReader.require(projected.path("pois").equals(projection.pois(ctx.manifest().path("initialDatabaseFacts"))),"EXECUTION_RECORD_CORRUPT");
                    var result=identity("sandbox-map-snapshot/v1",ctx);result.put("loopIndex",index);result.put("simTime",tick.tickEnd().toString());
                    result.put("tickSha256",r.getString("tick_sha256"));result.put("factsSha256",factsHash);result.put("evaluationSha256",r.getString("evaluation_sha256"));
                    result.put("factVersion",version);result.put("integrityScope","MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK");
                    result.put("ledgerIntegrity","NOT_CHECKED");result.set("projection",projected);result.set("evaluation",reader.mapper().valueToTree(evaluation.get("evaluation")));return result;
                }
            }
        });
    }
    private ObjectNode identity(String version,SandboxResultReader.Context ctx) {
        var result=reader.mapper().createObjectNode();result.put("artifactVersion",version);result.put("executionId",ctx.id());
        result.put("manifestSha256",ctx.configuration().manifestSha256());result.put("positionPolicy",SandboxMapProjection.POSITION_POLICY);return result;
    }
}
