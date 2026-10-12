package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.roadsimulation.evaluation.EvaluationMetricCatalog;

import java.math.BigDecimal;
import java.util.*;

/** Reexecution acceptance, not an algorithm comparison or a replacement for journal integrity checks. */
public final class SandboxReplayFactComparator {
    public static final String VERSION = "sandbox-replay-tolerances/v1";
    private record Tolerance(BigDecimal absolute, BigDecimal relative) {
        boolean accepts(BigDecimal a, BigDecimal b) {
            var bound = absolute.max(relative.multiply(a.abs().max(b.abs())));
            return a.subtract(b).abs().compareTo(bound) <= 0;
        }
    }
    public record Difference(String path, String expected, String actual, String rule) {}
    public record Result(boolean factsMatch, String toleranceVersion, List<Difference> differences) {
        public Result { differences = List.copyOf(differences); }
    }
    private final Map<String, Tolerance> rules = new HashMap<>();

    public SandboxReplayFactComparator() {
        // Explicit qualified fields only. Baseline configuration, IDs, counts and integer time stay exact.
        var meters = tolerance("0.001", "1e-9");
        var km = tolerance("0.000001", "1e-9");
        var tonnes = tolerance("1e-9", "1e-9");
        var volume = tolerance("1e-9", "1e-9");
        var energy = tolerance("0.000001", "1e-9");
        var cost = tolerance("0.000001", "1e-9");
        var ratio = tolerance("1e-9", "0");
        var seconds = tolerance("0.000001", "0");
        var hours = tolerance("1e-9", "0");
        for (String table : List.of("assignment", "shipment", "vehicle")) {
            database(table, meters, "empty_distance_meters", "loaded_distance_meters", "total_distance_meters");
            database(table, km, "total_driving_distance", "empty_driving_distance_km", "empty_driving_distance");
        }
        database("assignment", tonnes, "actual_output_weight", "expected_output_weight");
        database("assignment", ratio, "expected_yield_rate");
        database("assignment_leg", meters, "distance_meters", "executed_distance_meters");
        database("assignment_leg", tonnes, "current_load_tonnes");
        database("assignment_leg", energy, "executed_energy_liters", "executed_emission_kg");
        database("assignment_leg", tolerance("1e-7", "0"), "from_latitude", "from_longitude", "to_latitude", "to_longitude");
        database("assignment_nodes", tonnes, "weight_delta"); database("assignment_nodes", volume, "volume_delta");
        database("shipment", tonnes, "total_weight", "actual_output_weight", "expected_output_weight");
        database("shipment", volume, "total_volume"); database("shipment", ratio, "expected_yield_rate");
        database("shipment", meters, "allocated_distance_meters");
        database("shipment_item", tonnes, "weight", "processed_weight"); database("shipment_item", volume, "volume");
        database("shipment_item", meters, "allocated_distance_meters");
        database("vehicle", tonnes, "current_load"); database("vehicle", volume, "current-volumn");
        database("processing_execution_flow", tonnes, "actual_weight", "planned_weight");
        database("processing_stage_execution", tonnes, "actual_input_weight", "actual_output_weight");
        database("production_batch", tonnes, "actual_final_output_weight", "planned_final_output_weight");
        database("production_plan", tonnes, "final_demand_weight");
        database("production_plan_flow", tonnes, "planned_weight");
        database("production_plan_node", tonnes, "planned_input_weight", "planned_output_weight");
        database("processing_stage", meters, "transport_distance_meters");
        database("node_service_episode", tonnes, "processed_tonnes");
        database("cargo_wait_episode", tonnes, "weight_tonnes");
        database("driving_progress", seconds, "initial_work_seconds", "remaining_work_seconds", "affected_seconds", "lost_work_seconds");
        database("transport_execution_segment", meters, "distance_meters");
        database("transport_execution_segment", tonnes, "capacity_tonnes", "load_tonnes");
        database("transport_execution_segment", energy, "energy_liters", "emission_kg");
        database("transport_execution_segment", ratio, "travel_time_factor");
        runtime("costAccumulators", km, "totalMileage", "totalMileageWithoutThings");
        runtime("costAccumulators", hours, "totalTransportTime", "totalWaitingTime");
        runtime("costAccumulators", tolerance("0.000001", "1e-9"), "totalTheoryCapacity", "totalRealityCapacity", "worstTheoryRealityCapacity");
        runtime("costAccumulators", ratio, "worstWaitingTransportTime"); runtime("costAccumulators", cost, "worstLoss");
        runtime("costs", cost, "costA", "costB", "costC", "costD", "costE", "allCost");
        runtime("costs", ratio, "normalizedCostA", "normalizedCostB", "normalizedCostC", "normalizedCostD", "normalizedCostE", "normalizedAllCost");
        for (String component : List.of("A", "B", "C", "D", "E")) {
            runtime("normalizationWindow", cost, "startCost" + component, "endCost" + component, "unitCost" + component);
        }
        // The catalog fixes each metric's unit; counts, boolean and ordinal levels have no tolerance.
        for (var metric : new EvaluationMetricCatalog().all()) {
            Tolerance selected = switch (metric.unit()) {
                case "km" -> km;
                case "t", "t/tick" -> tonnes;
                case "t·km" -> tolerance("0.000001", "1e-9");
                case "s" -> seconds;
                case "ratio", "index" -> ratio;
                case "km/h" -> tolerance("0.000001", "1e-9");
                case "L(diesel-eq)", "kgCO2e", "kgCO2e/(t·km)" -> energy;
                default -> null;
            };
            if (selected != null) rules.put("/evaluation/metrics/" + metric.id().getMetricId() + "/value", selected);
        }
    }

    public Result compare(JsonNode expectedBusiness, JsonNode actualBusiness, JsonNode expectedEvaluation, JsonNode actualEvaluation) {
        List<Difference> differences = new ArrayList<>();
        walk(expectedBusiness, actualBusiness, "/business", "/business", differences);
        walk(expectedEvaluation, actualEvaluation, "/evaluation", "/evaluation", differences);
        return new Result(differences.isEmpty(), VERSION, differences);
    }

    private void walk(JsonNode a, JsonNode b, String path, String rulePath, List<Difference> differences) {
        if (differences.size() >= 20) return; // Bound diagnostics, never turn a mismatch into success.
        if (a == null || b == null) {
            if (a != b) difference(a, b, path, "EXACT_MISSING_FIELD", differences);
            return;
        }
        if (a.isNumber() && b.isNumber()) {
            SandboxBusinessFactCapture.requireFinite(a); SandboxBusinessFactCapture.requireFinite(b);
            BigDecimal left = a.decimalValue(), right = b.decimalValue();
            Tolerance rule = rules.get(rulePath);
            if (left.compareTo(right) != 0 && (rule == null || !rule.accepts(left, right))) {
                difference(a, b, path, rule == null ? "EXACT" : "abs=" + rule.absolute + ",rel=" + rule.relative, differences);
            }
        } else if (a.isObject() && b.isObject()) {
            Set<String> keys = new TreeSet<>(); a.fieldNames().forEachRemaining(keys::add); b.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                String escaped = key.replace("~", "~0").replace("/", "~1");
                walk(a.get(key), b.get(key), path + "/" + escaped, rulePath + "/" + escaped, differences);
            }
        } else if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) difference(a, b, path, "EXACT_ARRAY_SIZE", differences);
            else for (int i = 0; i < a.size(); i++) walk(a.get(i), b.get(i), path + "/" + i, rulePath + "/*", differences);
        } else if (!a.equals(b)) difference(a, b, path, "EXACT", differences);
    }

    private void difference(JsonNode a, JsonNode b, String path, String rule, List<Difference> result) {
        result.add(new Difference(path, describe(a), describe(b), rule));
    }
    private String describe(JsonNode value) {
        if (value == null) return "<missing>";
        if (value.isContainerNode()) return value.getNodeType() + "(size=" + value.size() + ")";
        return value.toString();
    }
    private static Tolerance tolerance(String absolute, String relative) {
        return new Tolerance(new BigDecimal(absolute), new BigDecimal(relative));
    }
    private void database(String table, Tolerance tolerance, String... fields) {
        for (String field : fields) rules.put("/business/database/" + table + "/*/" + field, tolerance);
    }
    private void runtime(String object, Tolerance tolerance, String... fields) {
        for (String field : fields) rules.put("/business/runtime/" + object + "/" + field, tolerance);
    }
}
