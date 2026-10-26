package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.sql.Connection;
import java.util.*;
import java.math.MathContext;

/** A read-only comparison of frozen execution artifacts; never recalculates evaluation facts. */
public final class SandboxComparisonReader {
    private final SandboxResultReader reader;
    public SandboxComparisonReader(SandboxResultReader reader) { this.reader = reader; }

    public ObjectNode read(String leftId, String rightId) {
        if (Objects.equals(leftId, rightId)) throw new SandboxWorkspaceException("INVALID_ARGUMENT", "Different executions required");
        return reader.snapshot(c -> {
            var left = reader.context(c, leftId); var right = reader.context(c, rightId);
            requireCompleted(left); requireCompleted(right);
            if (!left.start().equals(right.start()) || left.durationSeconds() != right.durationSeconds()
                    || left.recorded() != right.recorded())
                throw new SandboxWorkspaceException("COMPARISON_TIME_MISMATCH", "Equal observation windows required");
            ObjectNode a = side(c, left), b = side(c, right), result = reader.mapper().createObjectNode();
            result.put("artifactVersion", "sandbox-execution-comparison/v1");
            result.set("left", a); result.set("right", b);
            result.set("metrics", compareMetrics(a.path("finalSnapshot"), b.path("finalSnapshot")));
            ArrayNode differences = result.putArray("differences");
            differences("baseline", left.configuration().baseline(), right.configuration().baseline(), differences);
            for (String field : List.of("selection", "overrides"))
                differences("scenario." + field, left.manifest().path("scenarioRevision").path("definition").path(field),
                        right.manifest().path("scenarioRevision").path("definition").path(field), differences);
            for (String field : List.of("demand", "dispatch", "random", "vehicleInitialization", "driverBehavior", "weather", "events"))
                differences("run." + field, left.manifest().path("runRevision").path("specification").path(field),
                        right.manifest().path("runRevision").path("specification").path(field), differences);
            for (String field : List.of("algorithmProfile", "vehicleInitialStates", "weatherTimeline", "eventConfiguration"))
                differences("resolved." + field, left.manifest().path("runRevision").path(field), right.manifest().path("runRevision").path(field), differences);
            differences("javaVersion", left.manifest().path("javaVersion"), right.manifest().path("javaVersion"), differences);
            differences("evaluation.thresholds", a.path("finalSnapshot").path("thresholds"), b.path("finalSnapshot").path("thresholds"), differences);
            differences("evaluation.energyEmissionModel", a.path("finalSnapshot").path("energyEmissionModel"), b.path("finalSnapshot").path("energyEmissionModel"), differences);
            result.put("integrityScope", "MANIFEST_AND_EVALUATION_PAYLOADS"); result.put("ledgerIntegrity", "NOT_CHECKED");
            return result;
        });
    }
    private void requireCompleted(SandboxResultReader.Context ctx) {
        if (!"COMPLETED".equals(ctx.row().get("execution_status")))
            throw new SandboxWorkspaceException("COMPARISON_REQUIRES_COMPLETED", "Completed executions required");
        if (ctx.recorded() == 0) throw new SandboxWorkspaceException("COMPARISON_NO_EVALUATION", "No recorded evaluation");
    }
    private ObjectNode side(Connection c, SandboxResultReader.Context ctx) throws Exception {
        var side = reader.mapper().createObjectNode();
        side.put("executionId", ctx.id()); side.put("runSpecKey", ctx.row().get("run_spec_key").toString());
        side.put("revision", ((Number)ctx.row().get("run_spec_revision")).intValue());
        var run = ctx.manifest().path("runRevision").path("specification");
        side.put("displayName", run.path("displayName").isTextual() ? run.path("displayName").asText() : side.path("runSpecKey").asText());
        var scene = ctx.manifest().path("scenarioRevision");
        side.put("scenarioName", scene.path("definition").path("displayName").isTextual()
                ? scene.path("definition").path("displayName").asText() : scene.path("scenarioKey").asText());
        side.put("baselineId", ctx.row().get("baseline_id").toString());
        side.put("strategy", run.path("dispatch").path("strategy").asText());
        side.put("algorithmProfileId", run.path("dispatch").path("algorithmProfileId").asText());
        side.set("clock", run.path("simulationClock")); side.put("manifestSha256", ctx.configuration().manifestSha256());
        try (var q = c.prepareStatement("SELECT " + SandboxResultReader.EVALUATION_COLUMNS + " FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
            q.setString(1, ctx.id()); q.setInt(2, ctx.recorded()-1);
            try (var rows = q.executeQuery()) {
                SandboxResultReader.require(rows.next(), "EXECUTION_RECORD_CORRUPT");
                var item = reader.evaluationItem(ctx, rows, ctx.recorded()-1);
                side.set("finalSnapshot", (JsonNode)item.get("evaluation"));
                side.put("finalEvaluationSha256", item.get("evaluationSha256").toString());
            }
        }
        return side;
    }

    static ArrayNode compareMetrics(JsonNode a, JsonNode b) {
        var result = JsonNodeFactory.instance.arrayNode(); var ids = new LinkedHashSet<String>();
        a.path("metrics").fieldNames().forEachRemaining(ids::add); b.path("metrics").fieldNames().forEachRemaining(ids::add);
        for (String id : ids) {
            JsonNode left = a.path("metrics").get(id), right = b.path("metrics").get(id), m = left == null ? right : left;
            var row = result.addObject(); row.put("metricId", id);
            row.put("displayName", m.path("displayName").asText(id)); row.put("category", m.path("category").asText()); row.put("unit", m.path("unit").asText());
            row.set("left", left == null ? NullNode.instance : left); row.set("right", right == null ? NullNode.instance : right);
            String reason = incompatibility(a, b, id);
            boolean comparable = reason == null; row.put("comparable", comparable);
            if (reason == null) row.putNull("reason"); else row.put("reason", reason);
            row.putNull("rightMinusLeft"); row.putNull("relativeChangeRatio");
            if (comparable && !"boolean".equals(m.path("unit").asText())) {
                var x = left.path("value").decimalValue(); var delta = right.path("value").decimalValue().subtract(x);
                row.put("rightMinusLeft", delta);
                if (x.signum() != 0) row.put("relativeChangeRatio", delta.divide(x.abs(), MathContext.DECIMAL128));
            }
        }
        return result;
    }
    static String incompatibility(JsonNode a, JsonNode b, String id) {
        if (!a.path("contractVersion").equals(b.path("contractVersion"))) return "CONTRACT_MISMATCH";
        if (!"1.9".equals(a.path("contractVersion").asText())) return "UNSUPPORTED_CONTRACT";
        JsonNode x = a.path("metrics").get(id), y = b.path("metrics").get(id);
        if (x == null || y == null) return "METRIC_MISSING";
        if (!x.path("unit").equals(y.path("unit")) || !x.path("category").equals(y.path("category"))) return "METRIC_SEMANTICS_MISMATCH";
        if (Set.of("carbonIntensity", "vehicleTotalEnergy", "vehicleTotalEmissionKg", "vehicleEmissionIntensity", "taskEmissionKg", "taskEmissionIntensity", "environmentEnergyFactor").contains(id)
                && (!a.path("energyEmissionModel").isObject() || !a.path("energyEmissionModel").equals(b.path("energyEmissionModel")))) return "MODEL_MISMATCH";
        String threshold = switch (id) {
            case "vehicleLowLoadRatio", "taskLowLoadCount" -> "lowLoadRatioThreshold";
            case "vehicleFullLoadRatio" -> "fullLoadRatioThreshold";
            case "waitingServiceCompliant" -> "maxServiceWaitSeconds";
            default -> null;
        };
        if (threshold != null && (!a.path("thresholds").path(threshold).isNumber()
                || !a.path("thresholds").path(threshold).equals(b.path("thresholds").path(threshold)))) return "THRESHOLD_MISMATCH";
        if (!"AVAILABLE".equals(x.path("status").asText()) || !"AVAILABLE".equals(y.path("status").asText())) return "VALUE_UNAVAILABLE";
        if (!x.path("value").isNumber() || !y.path("value").isNumber()
                || !Double.isFinite(x.path("value").asDouble()) || !Double.isFinite(y.path("value").asDouble())) return "INVALID_VALUE";
        if ("boolean".equals(x.path("unit").asText()) && (x.path("value").asDouble()!=0 && x.path("value").asDouble()!=1
                || y.path("value").asDouble()!=0 && y.path("value").asDouble()!=1)) return "INVALID_VALUE";
        return null;
    }
    private void differences(String path, JsonNode a, JsonNode b, ArrayNode out) {
        if (a.equals(b)) return;
        if (a.isObject() && b.isObject()) {
            var keys = new TreeSet<String>(); a.fieldNames().forEachRemaining(keys::add); b.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) differences(path + "." + key, a.path(key), b.path(key), out);
        } else {
            var row = out.addObject(); row.put("path", path);
            row.set("left", a.isMissingNode() ? NullNode.instance : a); row.set("right", b.isMissingNode() ? NullNode.instance : b);
        }
    }
}
