package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.sql.Connection;
import java.sql.ResultSet;
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
        return readTick(id,index,false);
    }
    public ObjectNode replayTick(String id,int index) {
        return readTick(id,index,true);
    }
    public ObjectNode replayAssignments(String id,int from,int to,int offset,int limit) {
        SandboxManagementService.page(offset,limit);
        if(from<0 || to<from) throw new SandboxWorkspaceException("INVALID_RANGE","Invalid assignment replay range");
        return reader.snapshot(c->{
            var ctx=reader.context(c,id);
            if(to>=ctx.recorded()) throw new SandboxWorkspaceException("TICK_NOT_FOUND","No complete recorded tick");
            var result=identity("sandbox-road-replay-assignments/v1",ctx);
            result.put("fromLoopIndex",from);result.put("toLoopIndex",to);result.put("offset",offset);
            long start=(long)from+offset;
            if(start==0) result.set("initialAssignments",projection.replayAssignments(ctx.manifest().path("initialDatabaseFacts")));
            var items=result.putArray("items");
            int count=(int)Math.max(0,Math.min(limit,(long)to-start+1));
            if(count>0) try(var q=c.prepareStatement("""
                    SELECT * FROM sandbox_execution_tick
                    WHERE execution_id=? AND loop_index BETWEEN ? AND ? ORDER BY loop_index
                    """)) {
                q.setString(1,id);q.setInt(2,(int)start);q.setInt(3,(int)(start+count-1));
                try(var rows=q.executeQuery()){
                    for(int i=0;i<count;i++){
                        int index=(int)start+i;
                        SandboxResultReader.require(rows.next()&&rows.getInt("loop_index")==index,"EXECUTION_RECORD_CORRUPT");
                        var record=validateTick(c,ctx,index,rows);
                        var item=items.addObject();
                        item.put("loopIndex",index);item.put("tickSha256",rows.getString("tick_sha256"));
                        item.put("tickStart",record.tick().tickStart().toString());
                        item.put("tickEnd",record.tick().tickEnd().toString());
                        item.set("assignments",projection.replayAssignments(record.facts().path("database")));
                    }
                }
            }
            result.put("hasMore",start+count<=to);
            return result;
        });
    }

    private record ValidatedTick(SimulationTick tick,JsonNode facts,String factsHash,String version,java.util.Map<String,Object> evaluation) {}

    private ValidatedTick validateTick(Connection c,SandboxResultReader.Context ctx,int index,ResultSet row) throws Exception {
        var evaluation=reader.evaluationItem(ctx,row,index);
        var facts=reader.parse(row.getString("facts_json"));
        String factsHash=new SandboxExecutionJson(reader.mapper()).hash(facts);
        SandboxResultReader.require(factsHash.equals(row.getString("facts_sha256")),"EXECUTION_RECORD_CORRUPT");
        String version=facts.path("artifactVersion").asText();
        if(!java.util.Set.of(SandboxBusinessFactCapture.LEGACY_VERSION,SandboxBusinessFactCapture.VERSION).contains(version))
            throw new SandboxWorkspaceException("UNSUPPORTED_FACT_VERSION","Map cannot interpret unknown recorded fact version");
        var tick=SimulationTick.of(index,ctx.start().plusSeconds(Math.multiplyExact((long)index,ctx.durationSeconds())),Duration.ofSeconds(ctx.durationSeconds()));
        SandboxResultReader.require(facts.isObject()&&facts.path("database").isObject()
            &&tick.equals(reader.mapper().treeToValue(facts.path("tick"),SimulationTick.class)),"EXECUTION_RECORD_CORRUPT");
        String previous=ctx.configuration().manifestSha256();
        if(index>0)try(var query=c.prepareStatement("SELECT tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")){
            query.setString(1,ctx.id());query.setInt(2,index-1);
            try(var rows=query.executeQuery()){
                SandboxResultReader.require(rows.next(),"EXECUTION_RECORD_CORRUPT");previous=rows.getString(1);
            }
        }
        SandboxResultReader.require(Objects.equals(previous,row.getString("previous_tick_sha256"))
            &&SandboxTickIntegrity.hash(reader.mapper(),tick,factsHash,row.getString("business_facts_sha256"),row.getString("evaluation_sha256"),previous).equals(row.getString("tick_sha256")),"EXECUTION_RECORD_CORRUPT");
        return new ValidatedTick(tick,facts,factsHash,version,evaluation);
    }
    private ObjectNode readTick(String id,int index,boolean replay) {
        if(index<0) throw new SandboxWorkspaceException("INVALID_RANGE","Nonnegative loop required");
        return reader.snapshot(c->{var ctx=reader.context(c,id);
            if(index>=ctx.recorded()) throw new SandboxWorkspaceException("TICK_NOT_FOUND","No complete recorded tick");
            try(var q=c.prepareStatement("SELECT * FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
                q.setString(1,id);q.setInt(2,index);try(var r=q.executeQuery()) {
                    SandboxResultReader.require(r.next(),"EXECUTION_RECORD_CORRUPT");
                    var record=validateTick(c,ctx,index,r);
                    var facts=record.facts();var tick=record.tick();
                    var projected=projection.project(facts.path("database"));
                    SandboxResultReader.require(projected.path("pois").equals(projection.pois(ctx.manifest().path("initialDatabaseFacts"))),"EXECUTION_RECORD_CORRUPT");
                    if(replay) {
                        var result=identity("sandbox-road-replay-tick/v1",ctx);
                        result.put("loopIndex",index);
                        result.put("tickSha256",r.getString("tick_sha256"));
                        result.put("tickStart",tick.tickStart().toString());
                        result.put("tickEnd",tick.tickEnd().toString());
                        result.set("facts",projection.replayFacts(facts.path("database"),projected));
                        if(index==0) {
                            var initial=ctx.manifest().path("initialDatabaseFacts");
                            result.set("initialVehicles",projection.project(initial).path("vehicles"));
                        }
                        return result;
                    }
                    var result=identity("sandbox-map-snapshot/v1",ctx);result.put("loopIndex",index);result.put("simTime",tick.tickEnd().toString());
                    result.put("tickSha256",r.getString("tick_sha256"));result.put("factsSha256",record.factsHash());result.put("evaluationSha256",r.getString("evaluation_sha256"));
                    result.put("factVersion",record.version());result.put("integrityScope","MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK");
                    result.put("ledgerIntegrity","NOT_CHECKED");result.set("projection",projected);result.set("evaluation",reader.mapper().valueToTree(record.evaluation().get("evaluation")));return result;
                }
            }
        });
    }
    private ObjectNode identity(String version,SandboxResultReader.Context ctx) {
        var result=reader.mapper().createObjectNode();result.put("artifactVersion",version);result.put("executionId",ctx.id());
        result.put("manifestSha256",ctx.configuration().manifestSha256());result.put("positionPolicy",SandboxMapProjection.POSITION_POLICY);return result;
    }
}
