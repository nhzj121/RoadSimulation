package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import org.example.roadsimulation.sandbox.execution.SandboxReplayFactComparator;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskMapper.*;

/** Evidence from snapshot-consistent, verified execution exports. */
final class SphericalComparisonEvidence {
    record Side(String strategy, String executionId, String manifestSha256, String lastTickSha256,
                Map<String, CandidateAssessment.MetricReading> metrics, FulfillmentComparison.Side delivery,
                long elapsedNanos) {}

    private SphericalComparisonEvidence() {}

    static Side heuristic(JsonNode export, CompiledOptimizationProblem problem, SphericalComparisonRun comparison,
                          long elapsedNanos, ObjectMapper json) {
        var manifest = export.path("manifest");
        int loops = problem.problem().totalLoops();
        if (!"VERIFIED".equals(export.path("integrity").asText()) || !"COMPLETED".equals(export.path("status").asText())
                || !SphericalComparisonRun.MODE.equals(manifest.path("executionMode").asText())
                || !comparison.fingerprint().equals(manifest.path("sphericalComparisonSha256").asText())
                || !problem.sourceRunSha256().equals(manifest.path("preparation").path("runSpecificationSha256").asText())
                || export.path("completedLoops").asInt() != loops || export.path("ticks").size() != loops)
            throw new IllegalArgumentException("Invalid HEURISTIC comparison execution identity or horizon");
        var last = export.path("ticks").get(loops - 1);
        var snapshot = json.convertValue(last.path("evaluation"), EvaluationSnapshot.class);
        var assessment = EvaluationMetricAdapter.fromSnapshot(problem.problem(), comparison.fingerprint(), true,
                CandidateAssessment.ExecutionStatus.COMPLETED, loops, 0, snapshot);
        if (!assessment.executionIssues().isEmpty()
                || !assessment.auditData().keySet().containsAll(Set.of("cargoDeliveredTonnes", "cargoRequiredTonnes")))
            throw new IllegalArgumentException("Incomplete HEURISTIC evaluation evidence");
        String run = manifest.path("preparation").path("deterministicSimulationRunId").asText();
        var mapping = mapping(problem.catalog(), last.path("facts").path("database"), run);
        var facts = FulfillmentComparison.facts(problem.catalog(), export, problem.problem().problemFingerprint(), mapping,
                assessment.auditData(), assessment.metrics().get("unmetDemandRatio"));
        return side("HEURISTIC", export, assessment, FulfillmentComparison.totals(problem.catalog(), facts), elapsedNanos);
    }

    static Side plan(String strategy, JsonNode export, PlanExecutionTask task, long elapsedNanos, ObjectMapper json) {
        var assessment = SandboxCandidateEvaluator.assessmentFromVerifiedExport(task, export, json);
        var facts = FulfillmentComparison.facts(task, export, assessment, json);
        return side(strategy, export, assessment, FulfillmentComparison.totals(task.compiledProblem().catalog(), facts), elapsedNanos);
    }

    private static Side side(String strategy, JsonNode export, CandidateAssessment assessment,
                             FulfillmentComparison.Side delivery, long elapsedNanos) {
        var metrics = new TreeMap<String, CandidateAssessment.MetricReading>();
        for (var definition : ObjectiveRegistry.throughput().definitions()) {
            var reading = assessment.metrics().get(definition.metricId());
            if (reading == null || reading.value() == null || !definition.unit().equals(reading.unit()))
                throw new IllegalArgumentException("Missing comparison objective " + definition.metricId());
            definition.validate(reading.value());
            metrics.put(definition.metricId(), reading);
        }
        return new Side(strategy, export.path("executionId").asText(), export.path("manifestSha256").asText(),
                export.path("lastTickSha256").asText(), metrics, delivery, elapsedNanos);
    }

    static Mapping mapping(ProductionTaskCatalog catalog, JsonNode database, String run) {
        var plans = rows(database, "production_plan");
        var batches = rows(database, "production_batch");
        var stages = rows(database, "processing_stage_execution");
        var shipments = rows(database, "shipment");
        var items = rows(database, "shipment_item").values().stream()
                .collect(Collectors.groupingBy(item -> id(item, "shipment_id")));
        var frozenBatches = catalog.batches().stream().collect(Collectors.toMap(Batch::key, batch -> batch));
        var batchKeys = new HashMap<Long, BatchKey>();
        var seenBatches = new HashSet<BatchKey>();
        var seenPlans = new HashSet<Long>();
        batches.forEach((batchId, batch) -> {
            long planId = id(batch, "plan_id");
            var plan = requireRow(plans, planId);
            var key = new BatchKey(plan.path("generation_round").asInt(-1), id(batch, "chain_id"));
            var frozen = frozenBatches.get(key);
            if (!seenBatches.add(key) || !seenPlans.add(planId) || frozen == null
                    || !run.equals(plan.path("simulation_run_id").asText())
                    || id(plan, "chain_id") != key.chainId()
                    || !"RELEASED".equals(plan.path("status").asText())
                    || frozen.finalDemandWeight().compareTo(number(plan, "final_demand_weight")) != 0)
                throw new IllegalArgumentException("Conflicting frozen production batch evidence");
            batchKeys.put(batchId, key);
        });
        if (!seenBatches.equals(frozenBatches.keySet()) || seenPlans.size() != plans.size())
            throw new IllegalArgumentException("Incomplete production batch evidence");
        var expected = new HashSet<FlowKey>();
        var structural = new HashSet<FlowKey>();
        var observed = new ArrayList<ObservedFlow>();
        var seenShipments = new HashSet<Long>();
        for (var flow : rows(database, "processing_execution_flow").values()) {
            long batchId = id(flow, "batch_id");
            var batch = requireRow(batchKeys, batchId);
            var from = requireRow(stages, id(flow, "from_execution_id"));
            var to = requireRow(stages, id(flow, "to_execution_id"));
            var key = new FlowKey(batch, id(from, "stage_id"), id(to, "stage_id"), flow.path("input_key").asText());
            if (id(from, "batch_id") != batchId || id(to, "batch_id") != batchId || !structural.add(key))
                throw new IllegalArgumentException("Conflicting production flow identity");
            if ("COMPLETED".equals(from.path("status").asText())) expected.add(key);
            if (!flow.path("shipment_id").isNull()) {
                long shipmentId = id(flow, "shipment_id");
                if (!seenShipments.add(shipmentId)) throw new IllegalArgumentException("Duplicate released shipment");
                var shipment = requireRow(shipments, shipmentId);
                var observedItems = items.getOrDefault(shipmentId, List.of()).stream().map(item -> new ObservedItem(
                        id(item, "id"), item.path("sku").asText(), item.path("qty").asInt(), number(item, "weight"),
                        number(item, "volume"))).toList();
                observed.add(new ObservedFlow(key, shipmentId, flow.path("sku").asText(),
                        id(shipment, "origin_poi_id"), id(shipment, "dest_poi_id"),
                        number(shipment, "total_weight"), number(shipment, "total_volume"), observedItems));
            }
        }
        if (!structural.equals(catalog.flows().stream().map(Flow::key).collect(Collectors.toSet()))
                || !seenShipments.equals(shipments.keySet()))
            throw new IllegalArgumentException("Incomplete production flow or shipment evidence");
        return new ProductionTaskMapper().map(run, catalog, expected, observed);
    }

    static void requireReplay(JsonNode expected, JsonNode actual, ObjectMapper json) {
        if (!completed(expected) || !completed(actual) || expected.path("executionId").equals(actual.path("executionId"))
                || !expected.path("manifest").path("planExecutionTaskSha256")
                        .equals(actual.path("manifest").path("planExecutionTaskSha256")))
            throw new IllegalArgumentException("Replay execution identity mismatch");
        var left = expected.path("ticks");
        var right = actual.path("ticks");
        if (left.size() != right.size()) throw new IllegalArgumentException("Replay horizon mismatch");
        var comparator = new SandboxReplayFactComparator();
        var expectedRounds = new HashMap<GlobalPlan.TaskKey, Integer>();
        var actualRounds = new HashMap<GlobalPlan.TaskKey, Integer>();
        for (int index = 0; index < left.size(); index++) {
            var a = left.get(index);
            var b = right.get(index);
            var compared = comparator.compare(a.path("businessFacts"), b.path("businessFacts"),
                    a.path("evaluation"), b.path("evaluation"));
            if (!compared.factsMatch())
                throw new IllegalArgumentException("Replay differs at loop " + index + ": " + compared.differences());
            collectDelivered(a, index, expectedRounds, json);
            collectDelivered(b, index, actualRounds, json);
        }
        if (!expectedRounds.equals(actualRounds)) throw new IllegalArgumentException("Replay delivery rounds differ");
    }

    private static boolean completed(JsonNode export) {
        var ticks = export.path("ticks");
        return "VERIFIED".equals(export.path("integrity").asText())
                && "COMPLETED".equals(export.path("status").asText())
                && ticks.isArray() && !ticks.isEmpty() && ticks.size() == export.path("requestedLoops").asInt();
    }

    private static void collectDelivered(JsonNode tick, int index, Map<GlobalPlan.TaskKey, Integer> rounds, ObjectMapper json) {
        var facts = tick.path("facts");
        var mapping = json.convertValue(facts.path("runtime").path("planExecution").path("mapping"), Mapping.class);
        var items = rows(facts.path("database"), "shipment_item");
        for (var binding : mapping.tasks()) {
            if ("DELIVERED".equals(requireRow(items, binding.shipmentItemId()).path("status").asText()))
                rounds.putIfAbsent(binding.key(), index);
        }
    }

    static Map<Long, JsonNode> rows(JsonNode database, String table) {
        var values = database.path(table);
        if (!values.isArray()) throw new IllegalArgumentException("Missing journal table " + table);
        var result = new HashMap<Long, JsonNode>();
        for (var row : values)
            if (result.putIfAbsent(id(row, "id"), row) != null) throw new IllegalArgumentException("Duplicate row in " + table);
        return result;
    }

    private static <T> T requireRow(Map<Long, T> rows, long id) {
        T result = rows.get(id);
        if (result == null) throw new IllegalArgumentException("Missing journal row " + id);
        return result;
    }

    static long id(JsonNode row, String field) {
        var value = row.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IllegalArgumentException("Invalid journal ID " + field);
        return value.longValue();
    }

    private static BigDecimal number(JsonNode row, String field) {
        var value = row.path(field);
        if (!value.isNumber()) throw new IllegalArgumentException("Invalid journal number " + field);
        return value.decimalValue();
    }
}
