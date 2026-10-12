package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionJson;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Read-only projection of authenticated, frozen execution artifacts. Never queries business tables. */
public final class SandboxResultReader {
    static final String EVALUATION_COLUMNS = "loop_index,tick_start,tick_end,evaluation_json,evaluation_sha256,tick_sha256";
    private final SandboxManagementJobStore jobs;
    private final ObjectMapper json;
    private final SandboxExecutionJson canonical;
    public SandboxResultReader(SandboxManagementJobStore jobs, ObjectMapper mapper) {
        this.jobs = jobs;
        json = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        canonical = new SandboxExecutionJson(json);
    }
    public record SandboxExecutionConfigurationV1(String artifactVersion, String executionId,
            String manifestSha256, String javaVersion, JsonNode baseline, JsonNode preparation,
            JsonNode scenarioRevision, JsonNode runRevision, String integrityScope) {}
    record Context(Map<String,Object> row, JsonNode manifest, SandboxExecutionConfigurationV1 configuration,
                   LocalDateTime start, long durationSeconds) {
        int recorded() { return ((Number)row.get("completed_loops")).intValue(); }
        String id() { return row.get("execution_id").toString(); }
    }
    @FunctionalInterface interface Read<T> { T apply(Connection connection) throws Exception; }
    <T> T snapshot(Read<T> action) {
        try (var connection = jobs.open()) {
            connection.setReadOnly(true); connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try { T result = action.apply(connection); connection.commit(); return result; }
            catch (Exception failure) { connection.rollback(); throw failure; }
        } catch (SandboxWorkspaceException failure) { throw failure; }
        catch (SQLException failure) { throw new SandboxWorkspaceException("MANAGEMENT_READ_FAILED", "Cannot read result", failure); }
        catch (Exception failure) { throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Invalid recorded result", failure); }
    }
    void accessible(Connection connection, String id) throws SQLException {
        SandboxManagementJobStore.uuid(id);
        try (var query = connection.prepareStatement("SELECT job_id FROM sandbox_management_job WHERE execution_id=?")) {
            query.setString(1,id);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    var job = jobs.get(rows.getString(1));
                    if (Boolean.TRUE.equals(job.get("attentionRequired")) || "INTERRUPTED".equals(job.get("job_status")))
                        throw new SandboxResultInspectionException(job.get("job_id").toString(),
                                Objects.toString(job.get("failure_code"), Objects.toString(job.get("workerObservation"), "INSPECTION_REQUIRED")));
                }
            }
        }
    }
    public void requireAccessible(String id) { snapshot(c -> { accessible(c,id); return null; }); }
    Context context(Connection c, String id) throws SQLException {
        accessible(c,id);
        Map<String,Object> row;
        try(var q=c.prepareStatement("SELECT * FROM sandbox_execution WHERE execution_id=?")) {
            q.setString(1,id); try(var r=q.executeQuery()) {
                if(!r.next()) throw new SandboxWorkspaceException("EXECUTION_NOT_FOUND","Unknown execution");
                row=SandboxManagementJobStore.row(r);
            }
        }
        String raw=Objects.toString(row.get("manifest_json"),null), hash=Objects.toString(row.get("manifest_sha256"),null);
        require(hash!=null,"EXECUTION_UNJOURNALED");
        require(raw!=null && hash.equals(SandboxExecutionJson.hashManifestText(raw)),"EXECUTION_RECORD_CORRUPT");
        JsonNode manifest=parse(raw), preparation=manifest.path("preparation"), run=manifest.path("runRevision"), scene=manifest.path("scenarioRevision");
        require(manifest.isObject() && preparation.isObject() && run.isObject() && scene.isObject(),"EXECUTION_RECORD_CORRUPT");
        require(textEquals(preparation,"runSpecKey",row.get("run_spec_key"))
                && integerEquals(preparation,"runSpecRevision",row.get("run_spec_revision"))
                && textEquals(preparation,"baselineId",row.get("baseline_id"))
                && textEquals(preparation,"deterministicSimulationRunId",row.get("deterministic_run_id"))
                && textEquals(preparation,"preparedRunFactsSha256",row.get("prepared_run_facts_sha256"))
                && textEquals(run,"runSpecKey",row.get("run_spec_key")) && integerEquals(run,"revision",row.get("run_spec_revision")),"EXECUTION_RECORD_CORRUPT");
        var binding=run.path("specification").path("scenario");
        require(textEquals(scene,"scenarioKey",preparation.path("scenarioKey").asText())
                && integerEquals(scene,"revision",preparation.path("scenarioRevision").numberValue())
                && textEquals(binding,"scenarioKey",scene.path("scenarioKey").asText())
                && integerEquals(binding,"revision",scene.path("revision").numberValue())
                && textEquals(binding,"scenarioDefinitionSha256",scene.path("fingerprints").path("scenarioDefinitionSha256").asText())
                && textEquals(binding,"effectiveScenarioDataSha256",scene.path("fingerprints").path("effectiveScenarioDataSha256").asText())
                && textEquals(preparation,"runSpecificationSha256",run.path("fingerprints").path("runSpecificationSha256").asText())
                && textEquals(scene.path("definition").path("baseline"),"baselineId",row.get("baseline_id")),"EXECUTION_RECORD_CORRUPT");
        var clock=run.path("specification").path("simulationClock");
        require(integerEquals(clock,"totalLoops",row.get("requested_loops")) && clock.path("tickDurationSeconds").isIntegralNumber()
                && clock.path("tickDurationSeconds").canConvertToLong() && clock.path("tickDurationSeconds").asLong()>0
                && run.path("specification").path("random").path("rootSeed").isTextual(),"EXECUTION_RECORD_CORRUPT");
        LocalDateTime start;
        try { start=LocalDateTime.parse(clock.path("startLocalDateTime").asText()); }
        catch(Exception failure) { throw corrupt(); }
        int recorded=((Number)row.get("completed_loops")).intValue(), requested=((Number)row.get("requested_loops")).intValue();
        require(recorded>=0 && recorded<=requested && (!"COMPLETED".equals(row.get("execution_status")) || recorded==requested),"EXECUTION_RECORD_CORRUPT");
        ObjectNode baseline=json.createObjectNode(); baseline.put("baselineId",row.get("baseline_id").toString());
        var pack=manifest.path("baselinePackage");
        if(pack.isObject()) {
            require(textEquals(pack,"baselineId",row.get("baseline_id")),"EXECUTION_RECORD_CORRUPT");
            baseline.set("artifactVersion",pack.path("artifactVersion")); baseline.set("fingerprints",pack.path("fingerprints"));
        }
        var config=new SandboxExecutionConfigurationV1("sandbox-execution-configuration/v1",id,hash,
                manifest.path("javaVersion").asText(),baseline,preparation,scene,run,"MANIFEST_PAYLOAD");
        return new Context(row,manifest,config,start,clock.path("tickDurationSeconds").asLong());
    }
    public SandboxExecutionConfigurationV1 configuration(String id) { return snapshot(c->context(c,id).configuration()); }
    public Map<String,Object> evaluations(String id,int offset,int limit,Integer from,Integer to) {
        SandboxManagementService.page(offset,limit);
        if((from==null)!=(to==null) || from!=null && (from<0 || to<from))
            throw new SandboxWorkspaceException("INVALID_RANGE","Both inclusive loop bounds required");
        return snapshot(c->{
            Context ctx=context(c,id); int lower=from==null?0:from, upper=to==null?ctx.recorded()-1:to;
            if(from!=null && upper>=ctx.recorded()) throw new SandboxWorkspaceException("INVALID_RANGE","Range exceeds durable prefix");
            List<Object> items=new ArrayList<>();
            try(var q=c.prepareStatement("SELECT "+EVALUATION_COLUMNS+" FROM sandbox_execution_tick WHERE execution_id=? AND loop_index BETWEEN ? AND ? ORDER BY loop_index LIMIT ? OFFSET ?")) {
                q.setString(1,id); q.setInt(2,lower); q.setInt(3,upper); q.setInt(4,limit+1); q.setInt(5,offset);
                try(var r=q.executeQuery()) {
                    while(r.next()) {
                        int expected=Math.addExact(lower,Math.addExact(offset,items.size()));
                        items.add(evaluationItem(ctx,r,expected));
                    }
                }
            }
            int expectedSize=(int)Math.min((long)limit+1,Math.max(0L,(long)upper-lower+1-offset));
            require(items.size()==expectedSize,"EXECUTION_RECORD_CORRUPT");
            boolean more=items.size()>limit;
            var result=new LinkedHashMap<String,Object>();
            result.put("artifactVersion","sandbox-evaluation-page/v1"); result.put("executionId",id);
            result.put("offset",offset); result.put("limit",limit); result.put("hasMore",more);
            result.put("items",more?items.subList(0,limit):items); result.put("integrityScope","PAYLOAD_ONLY");
            result.put("fromLoopIndex",lower); result.put("toLoopIndex",upper); result.put("recordedLoops",ctx.recorded());
            return result;
        });
    }
    Map<String,Object> evaluationItem(Context ctx,ResultSet r,int expected) throws SQLException {
        var row=SandboxManagementJobStore.row(r);
        JsonNode evaluation=checkedEvaluation(row,expected,ctx.row().get("deterministic_run_id").toString());
        LocalDateTime start=ctx.start().plusSeconds(Math.multiplyExact((long)expected,ctx.durationSeconds())),end=start.plusSeconds(ctx.durationSeconds());
        require(timeEquals(start,row.get("tick_start")) && timeEquals(end,row.get("tick_end")),"EXECUTION_RECORD_CORRUPT");
        return Map.of("loopIndex",expected,"tickStart",start.toString(),"tickEnd",end.toString(),
                "evaluation",evaluation,"evaluationSha256",row.get("evaluation_sha256"));
    }
    JsonNode checkedEvaluation(Map<String,Object> row,int index,String runId) {
        var e=parse(Objects.toString(row.get("evaluation_json"),""));
        require(e.isObject() && canonical.hash(e).equals(row.get("evaluation_sha256"))
                && integerEquals(e,"loopIndex",index) && textEquals(e,"simulationRunId",runId)
                && timeEquals(e.path("simTime").asText(),row.get("tick_end"))
                && (row.get("loop_index")==null || Objects.equals(((Number)row.get("loop_index")).intValue(),index))
                && e.path("contractVersion").isTextual() && e.path("metrics").isObject(),"EXECUTION_RECORD_CORRUPT");
        e.path("metrics").fields().forEachRemaining(metric->{
            var v=metric.getValue();
            require(v.isObject() && textEquals(v,"metricId",metric.getKey()) && v.path("status").isTextual()
                    && v.path("unit").isTextual() && v.path("displayName").isTextual(),"EXECUTION_RECORD_CORRUPT");
            if("AVAILABLE".equals(v.path("status").asText())) require(v.path("value").isNumber(),"EXECUTION_RECORD_CORRUPT");
        });
        return e;
    }
    static void requireClosed(Context ctx) {
        require(Set.of("COMPLETED","CANCELLED","FAILED").contains(ctx.row().get("execution_status")),"EXECUTION_STILL_RUNNING");
    }
    JsonNode parse(String raw) {
        try { var result=json.readTree(raw); if(result==null) throw corrupt(); return result; }
        catch(Exception failure) { throw corrupt(); }
    }
    ObjectMapper mapper() { return json; }
    static boolean textEquals(JsonNode node,String field,Object expected) { return expected!=null && node.path(field).isTextual() && node.path(field).asText().equals(expected.toString()); }
    static boolean timeEquals(String actual,Object expected) {
        try { return timeEquals(LocalDateTime.parse(actual),expected); }
        catch(RuntimeException invalid) { return false; }
    }
    static boolean timeEquals(LocalDateTime actual,Object expected) {
        if(actual==null || expected==null) return false;
        // DATETIME is a local simulation time, not an instant. JDBC drivers may return
        // LocalDateTime or Timestamp; compare temporal values without timezone conversion.
        try {
            LocalDateTime recorded=expected instanceof LocalDateTime time ? time
                    : expected instanceof Timestamp time ? time.toLocalDateTime()
                    : LocalDateTime.parse(expected.toString());
            return actual.equals(recorded);
        }
        catch(RuntimeException invalid) { return false; }
    }
    static boolean integerEquals(JsonNode node,String field,Object expected) { return expected instanceof Number n && node.path(field).isIntegralNumber() && node.path(field).canConvertToLong() && node.path(field).asLong()==n.longValue(); }
    static void require(boolean condition,String code) { if(!condition) throw new SandboxWorkspaceException(code,"Recorded result check failed"); }
    static SandboxWorkspaceException corrupt() { return new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT","Invalid recorded artifact"); }
}
