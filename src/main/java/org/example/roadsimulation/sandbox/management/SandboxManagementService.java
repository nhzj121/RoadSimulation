package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.*;
import org.example.roadsimulation.sandbox.baseline.*;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.run.*;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.*;
import java.sql.*;
import java.util.*;

/** Control plane only. Never obtains the ordinary application's DataSource or runtime services. */
public final class SandboxManagementService {
    private final SandboxManagementSettings settings;
    private final ObjectMapper json;
    private final SandboxManagementJobStore jobs;
    private final SandboxScenarioStore scenarios;
    private final SandboxRunSpecificationStore runs;
    private final LoadedSandboxBaseline baseline;
    private final EffectiveBaseData eligible;
    private final SandboxResultReader results;
    private static final java.util.concurrent.atomic.AtomicBoolean VERIFY_BUSY = new java.util.concurrent.atomic.AtomicBoolean();
    public SandboxManagementService(SandboxManagementSettings settings, ObjectMapper json) {
        // Existing baseline hashes authenticate their original JSON number representation.
        // Do not inherit MVC display/numeric preferences into this versioned protocol.
        this.settings = settings; this.json = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        jobs = new SandboxManagementJobStore(settings);
        results = new SandboxResultReader(jobs, this.json);
        scenarios = new SandboxScenarioStore(settings.url(), settings.user(), settings.password(), this.json);
        runs = new SandboxRunSpecificationStore(settings.url(), settings.user(), settings.password(), this.json);
        var loader = new SandboxBaselineLoader(this.json); baseline = loader.load(settings.baseline()); eligible = loader.selectAllEligible(baseline);
    }
    public Map<String, Object> baselineInfo() {
        return Map.of("baselineId", baseline.baseline().baselineId(), "fingerprints", baseline.baseline().fingerprints(),
                "effectiveBaseDataSha256", eligible.effectiveBaseDataSha256(), "eligibilityPolicy", baseline.baseline().eligibilityPolicy(),
                "catalogFilters", Map.of(
                        "vehicleTypes", eligible.data().vehicles().stream().map(SandboxBaselinePackageV1.Vehicle::vehicleType).filter(Objects::nonNull).distinct().sorted().toList(),
                        "modelTypes", eligible.data().vehicles().stream().map(SandboxBaselinePackageV1.Vehicle::modelType).filter(Objects::nonNull).distinct().sorted().toList(),
                        "poiTypes", eligible.data().pois().stream().map(SandboxBaselinePackageV1.Poi::poiType).filter(Objects::nonNull).distinct().sorted().toList(),
                        "categories", eligible.data().goods().stream().map(SandboxBaselinePackageV1.Goods::category).filter(Objects::nonNull).distinct().sorted().toList()));
    }
    public Map<String, Object> catalog(String type, int offset, int limit) {
        page(offset, limit);
        List<?> values = switch (type) {
            case "vehicles" -> eligible.data().vehicles(); case "pois" -> eligible.data().pois();
            case "goods" -> eligible.data().goods(); case "processing-chains" -> eligible.data().processingChains();
            default -> throw new SandboxWorkspaceException("INVALID_CATALOG", "Unsupported baseline catalog");
        };
        int start = Math.min(offset, values.size());
        return Map.of("baselineId", baseline.baseline().baselineId(), "total", values.size(), "offset", offset,
                "limit", limit, "items", values.subList(start, Math.min(start + limit, values.size())));
    }
    public JsonNode scenarioTemplate() { return read(new ClassPathResource("sandbox/scenarios/default-all-eligible-v1.json")); }
    public SandboxScenarioCompilationReport compileScenario(JsonNode definition) { return scenarios.compile(settings.baseline(), resource(definition)); }
    public SandboxScenarioCompilationReport saveScenario(JsonNode definition) { return scenarios.saveDraft(settings.baseline(), resource(definition)); }
    public SandboxScenarioRevisionV1 publishScenario(String key) { SandboxManagementJobStore.key(key); return scenarios.publish(settings.baseline(), key); }
    public SandboxScenarioRevisionV1 scenarioRevision(String key, int revision) { SandboxManagementJobStore.key(key); SandboxManagementJobStore.positive(revision); return scenarios.loadRevision(key, revision); }
    public SandboxRunSpecificationV2 runTemplate(String key, int revision) {
        SandboxManagementJobStore.key(key); SandboxManagementJobStore.positive(revision);
        var template = json.valueToTree(runs.resolveScenarioV2(settings.baseline(),
                new ClassPathResource("sandbox/runs/default-production-original-v2.json"), key, revision));
        // Automatic events are opt-in in the published v2 template; never mutable mid-execution.
        return json.convertValue(template, SandboxRunSpecificationV2.class);
    }
    public CompiledSandboxRunSpecificationV2 compileRun(JsonNode definition) { requireTextSeed(definition); return runs.compileV2(settings.baseline(), resource(definition)); }
    public CompiledSandboxRunSpecificationV2 saveRun(JsonNode definition) { requireTextSeed(definition); return runs.saveDraftV2(settings.baseline(), resource(definition)); }
    public SandboxRunSpecificationRevisionV2 publishRun(String key) { SandboxManagementJobStore.key(key); return runs.publishV2(settings.baseline(), key); }
    public SandboxRunSpecificationRevisionV2 runRevision(String key, int revision) { SandboxManagementJobStore.key(key); SandboxManagementJobStore.positive(revision); return runs.loadRevisionV2(key, revision); }
    public Map<String, Object> workspace() { return one("SELECT workspace_state,control_schema_version,baseline_id,scenario_key,scenario_revision,run_spec_key,run_spec_revision,active_job_id,active_execution_id,failure_code,failure_phase FROM sandbox_workspace_marker WHERE marker_id=1"); }

    /** One read-only statement, no full ledger reads, locks, process mutation or source DataSource. */
    public NavigationObservation navigationObservation() {
        var row = one("""
                SELECT m.workspace_state,
                  (SELECT COUNT(*) FROM sandbox_management_job j
                   WHERE j.job_status IN ('ACCEPTED','PREPARING','RUNNING','CANCEL_REQUESTED','FINALIZING','INTERRUPTED')) AS unfinished_jobs,
                  (SELECT COUNT(*) FROM sandbox_execution e
                   WHERE e.execution_status IN ('CREATED','RUNNING','INTERRUPTED')) AS unfinished_executions
                FROM sandbox_workspace_marker m WHERE m.marker_id=1
                """);
        String state = Objects.toString(row.get("workspace_state"), "");
        boolean known = Arrays.stream(org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceState.values())
                .anyMatch(value -> value.name().equals(state));
        if (!known) throw new SandboxWorkspaceException("NAVIGATION_STATE_UNCONFIRMED", "Workspace state is unknown");
        boolean preparing = Set.of("PREPARING", "SCENARIO_PREPARING", "RUN_SPEC_PREPARING", "EXECUTION_RUNNING").contains(state);
        boolean unfinished = ((Number) row.get("unfinished_jobs")).longValue() > 0
                || ((Number) row.get("unfinished_executions")).longValue() > 0;
        return new NavigationObservation(preparing || unfinished, state);
    }
    public record NavigationObservation(boolean busy, String workspaceState) {}

    public Map<String, Object> families(String kind, int offset, int limit) {
        String prefix = prefix(kind); String key = kind.equals("scenarios") ? "scenario_key" : "run_spec_key";
        return pageQuery("SELECT " + key + ",display_name,description,row_version,archived,draft_updated_at FROM " + prefix + " ORDER BY " + key + " LIMIT ? OFFSET ?", offset, limit);
    }
    public JsonNode draft(String kind, String key) {
        SandboxManagementJobStore.key(key); String column = kind.equals("scenarios") ? "scenario_key" : "run_spec_key";
        return parse(one("SELECT draft_json FROM " + prefix(kind) + " WHERE " + column + "=?", key).get("draft_json").toString());
    }
    public Map<String, Object> draftView(String kind, String key) {
        SandboxManagementJobStore.key(key);
        String column = kind.equals("scenarios") ? "scenario_key" : "run_spec_key";
        var row = one("SELECT draft_json,row_version FROM " + prefix(kind)
                + " WHERE " + column + "=? AND archived=b'0'", key);
        return Map.of("definition", parse(row.get("draft_json").toString()), "rowVersion", row.get("row_version"));
    }
    public Object guardedSave(String kind, JsonNode request) {
        if (request.size() != 2 || !request.path("definition").isObject()
                || !request.path("expectedRowVersion").isIntegralNumber()
                || !request.path("expectedRowVersion").canConvertToLong()
                || request.path("expectedRowVersion").asLong() < -1)
            throw new SandboxWorkspaceException("INVALID_DRAFT_GUARD", "Definition and draft version required");
        long version = request.path("expectedRowVersion").asLong();
        if ("runs".equals(kind)) requireTextSeed(request.get("definition"));
        return switch (kind) {
            case "scenarios" -> scenarios.saveDraft(settings.baseline(), resource(request.get("definition")), version);
            case "runs" -> runs.saveDraftV2(settings.baseline(), resource(request.get("definition")), version);
            default -> throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Unknown family");
        };
    }
    public Object guardedPublish(String kind, String key, JsonNode request) {
        SandboxManagementJobStore.key(key);
        if (request.size() != 2 || !request.path("expectedRowVersion").isIntegralNumber()
                || !request.path("expectedRowVersion").canConvertToLong()
                || request.path("expectedRowVersion").asLong() < 0
                || !request.path("expectedDefinitionSha256").isTextual()
                || !request.path("expectedDefinitionSha256").asText().matches("[0-9a-f]{64}"))
            throw new SandboxWorkspaceException("INVALID_DRAFT_GUARD", "Saved draft version and compiled hash required");
        long version = request.path("expectedRowVersion").asLong();
        String hash = request.path("expectedDefinitionSha256").asText();
        return switch (kind) {
            case "scenarios" -> scenarios.publish(settings.baseline(), key, version, hash);
            case "runs" -> runs.publishV2(settings.baseline(), key, version, hash);
            default -> throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Unknown family");
        };
    }
    private void requireTextSeed(JsonNode definition) {
        if (definition == null || !definition.path("random").path("rootSeed").isTextual())
            throw new SandboxWorkspaceException("INVALID_RANDOM_SEED", "Root seed must be integer text, not a JSON number");
    }
    public Map<String, Object> revisions(String kind, String key, int offset, int limit) {
        SandboxManagementJobStore.key(key); String column = kind.equals("scenarios") ? "scenario_key" : "run_spec_key";
        return pageQuery("SELECT " + column + ",revision_no,artifact_version,published_at,archived FROM " + prefix(kind) + "_revision WHERE " + column + "=? ORDER BY revision_no DESC LIMIT ? OFFSET ?", offset, limit, key);
    }
    public Map<String, Object> jobs(int offset, int limit) {
        return pageQuery("SELECT job_id,run_spec_key,run_spec_revision,job_status,execution_id,worker_pid,created_at,updated_at,finished_at,failure_code FROM sandbox_management_job ORDER BY created_at DESC,job_id DESC LIMIT ? OFFSET ?", offset, limit);
    }
    public Map<String, Object> job(String id) { return jobs.get(id); }
    public SandboxPageViews.JobView jobView(String id) { return SandboxPageViews.job(jobs.get(id)); }
    public SandboxManagementJobStore.StartReceipt startRequest(String id) { return jobs.findRequest(id); }
    public Map<String, Object> cancel(String id) { return jobs.cancel(id); }
    public Map<String, Object> executions(int offset, int limit) {
        return pageQuery("SELECT execution_id,run_spec_key,run_spec_revision,execution_status,requested_loops,completed_loops,created_at,finished_at,failure_phase,failure_code FROM sandbox_execution ORDER BY created_at DESC,execution_id DESC LIMIT ? OFFSET ?", offset, limit);
    }
    public Map<String, Object> execution(String id) {
        SandboxManagementJobStore.uuid(id);
        results.requireAccessible(id);
        return one("SELECT execution_id,run_spec_key,run_spec_revision,baseline_id,execution_status,requested_loops,completed_loops,failed_loop_index,failure_phase,failure_code,created_at,finished_at,manifest_sha256 FROM sandbox_execution WHERE execution_id=?", id);
    }
    public Map<String, Object> ticks(String id, int offset, int limit) {
        SandboxManagementJobStore.uuid(id); execution(id);
        return pageQuery("SELECT loop_index,tick_start,tick_end,tick_sha256,evaluation_sha256 FROM sandbox_execution_tick WHERE execution_id=? ORDER BY loop_index LIMIT ? OFFSET ?", offset, limit, id);
    }
    public JsonNode tick(String id, int index) {
        SandboxManagementJobStore.uuid(id);
        results.requireAccessible(id);
        if (index < 0) throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Tick index must be nonnegative");
        var row = one("SELECT facts_json,business_facts_json,evaluation_json,facts_sha256,business_facts_sha256,evaluation_sha256,tick_sha256,previous_tick_sha256 FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?", id, index);
        var canonical = new SandboxExecutionJson(json); var result = json.createObjectNode();
        for (var pair : Map.of("facts", "facts", "businessFacts", "business_facts", "evaluation", "evaluation").entrySet()) {
            JsonNode payload = parse(row.get(pair.getValue() + "_json").toString());
            if (!canonical.hash(payload).equals(row.get(pair.getValue() + "_sha256"))) throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Tick payload hash differs");
            result.set(pair.getKey(), payload);
        }
        result.put("executionId", id); result.put("loopIndex", index); result.put("tickSha256", row.get("tick_sha256").toString());
        result.put("integrityScope", "PAYLOAD_ONLY"); // Full manifest/chain verification is a separate explicit operation.
        return result;
    }
    public SandboxResultReader.SandboxExecutionConfigurationV1 executionConfiguration(String id) { return results.configuration(id); }
    public JsonNode mapContext(String id) { return new SandboxMapReader(results).context(id); }
    public JsonNode mapSnapshot(String id,int index) { return new SandboxMapReader(results).snapshot(id,index); }
    public SandboxResultExporter.PreparedExport exportResult(String id,String format) { return new SandboxResultExporter(results).prepare(id,format); }
    public Map<String, Object> verifyExecution(String id) {
        if (!VERIFY_BUSY.compareAndSet(false,true)) throw new SandboxWorkspaceException("VERIFICATION_BUSY","Another manual verification is active");
        try {
            return new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json).verify(id);
        } finally { VERIFY_BUSY.set(false); }
    }
    /** Public result-page operation, distinct from existing internal pre-finalization verification. */
    public Map<String,Object> verifyResult(String id) {
        results.snapshot(c->{SandboxResultReader.requireClosed(results.context(c,id)); return null;});
        return verifyExecution(id);
    }

    public SandboxPageViews.ExecutionSummary executionSummary(String id) {
        SandboxManagementJobStore.uuid(id);
        results.requireAccessible(id);
        // A single SQL statement sees the metadata and the last DURABLE tick together.
        // Do not load the full manifest/business facts or run full verification for polling.
        var row = one("""
                SELECT e.execution_id,e.run_spec_key,e.run_spec_revision,e.baseline_id,e.execution_status,
                  e.requested_loops,e.completed_loops,e.failed_loop_index,e.failure_phase,e.failure_code,
                  e.manifest_sha256,e.deterministic_run_id,t.tick_sha256,t.evaluation_json,t.evaluation_sha256,t.tick_start,t.tick_end,t.loop_index
                FROM sandbox_execution e LEFT JOIN sandbox_execution_tick t
                  ON t.execution_id=e.execution_id AND t.loop_index=e.completed_loops-1
                WHERE e.execution_id=?
                """, id);
        int recorded = ((Number) row.get("completed_loops")).intValue();
        JsonNode evaluation = recorded == 0 ? null : results.checkedEvaluation(row, recorded - 1,row.get("deterministic_run_id").toString());
        return SandboxPageViews.summary(row, evaluation);
    }

    public Map<String, Object> evaluations(String id, int offset, int limit) {
        return results.evaluations(id,offset,limit,null,null);
    }
    public Map<String,Object> evaluations(String id,int offset,int limit,Integer from,Integer to) { return results.evaluations(id,offset,limit,from,to); }

    private JsonNode checkedEvaluation(Map<String, Object> row, int loopIndex) {
        Object payload = row.get("evaluation_json");
        if (payload == null) throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Recorded evaluation is missing");
        JsonNode evaluation;
        try { evaluation = parse(payload.toString()); }
        catch (SandboxWorkspaceException failure) {
            throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Stored evaluation JSON is malformed", failure);
        }
        if (evaluation == null || !evaluation.isObject()
                || !new SandboxExecutionJson(json).hash(evaluation).equals(row.get("evaluation_sha256"))
                || !evaluation.path("loopIndex").isIntegralNumber()
                || evaluation.path("loopIndex").asInt() != loopIndex)
            throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT", "Evaluation payload differs");
        return evaluation;
    }

    private String prefix(String kind) {
        return switch (kind) { case "scenarios" -> "sandbox_scenario"; case "runs" -> "sandbox_run_spec";
            default -> throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Unsupported control catalog"); };
    }
    private Map<String, Object> one(String sql, Object... parameters) {
        var values = query(sql, parameters);
        if (values.isEmpty()) throw new SandboxWorkspaceException("RECORD_NOT_FOUND", "Sandbox record not found");
        return values.get(0);
    }
    private Map<String, Object> pageQuery(String sql, int offset, int limit, Object... parameters) {
        page(offset, limit); List<Object> bindings = new ArrayList<>(Arrays.asList(parameters));
        bindings.add(limit + 1); bindings.add(offset); var rows = query(sql, bindings.toArray());
        boolean more = rows.size() > limit;
        return Map.of("offset", offset, "limit", limit, "hasMore", more, "items", more ? rows.subList(0, limit) : rows);
    }
    private List<Map<String, Object>> query(String sql, Object... parameters) {
        try (var connection = jobs.open(); var statement = connection.prepareStatement(sql)) {
            connection.setReadOnly(true);
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            List<Map<String, Object>> result = new ArrayList<>();
            try (var rows = statement.executeQuery()) { while (rows.next()) result.add(SandboxManagementJobStore.row(rows)); }
            return result;
        } catch (SQLException failure) { throw new SandboxWorkspaceException("MANAGEMENT_READ_FAILED", "Cannot read sandbox management records", failure); }
    }
    public static void page(int offset, int limit) {
        if (offset < 0 || offset > 1_000_000 || limit < 1 || limit > 100) throw new SandboxWorkspaceException("INVALID_PAGE", "offset must be 0..1000000; limit must be 1..100");
    }
    private Resource resource(JsonNode node) {
        if (node == null || !node.isObject()) throw new SandboxWorkspaceException("INVALID_JSON", "Expected a definition object");
        try { return new ByteArrayResource(json.writeValueAsBytes(node)); }
        catch (Exception failure) { throw new SandboxWorkspaceException("INVALID_JSON", "Cannot read definition"); }
    }
    private JsonNode read(Resource resource) { try (var stream = resource.getInputStream()) { return json.readTree(stream); } catch (Exception failure) { throw new SandboxWorkspaceException("TEMPLATE_READ_FAILED", "Cannot read sandbox template", failure); } }
    public JsonNode definition(String text) {
        if (text == null || text.length() > 1_048_576) throw new SandboxWorkspaceException("INVALID_JSON", "Definition exceeds the request limit");
        var value = parse(text);
        if (value == null || !value.isObject()) throw new SandboxWorkspaceException("INVALID_JSON", "Definition must be an object");
        return value;
    }
    private JsonNode parse(String text) {
        try { return json.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true)).readTree(text); }
        catch (Exception failure) { throw new SandboxWorkspaceException("INVALID_JSON", "Malformed JSON", failure); }
    }
}
