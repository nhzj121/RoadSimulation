package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.entity.ShipmentItem.ShipmentItemStatus;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The final comparison is an audit over a pair of ledgers, independent of the search score. */
public final class FulfillmentComparison {
    public static final String VERSION = "sandbox-fulfillment-comparison/v1";
    private static final Comparator<GlobalPlan.TaskKey> TASK_ORDER = Comparator.comparing(OptimizationJson::write);

    record Facts(String executionId, String problemFingerprint, String catalogFingerprint,
                 Set<GlobalPlan.TaskKey> released, Set<GlobalPlan.TaskKey> delivered,
                 BigDecimal operationalUnmetRatio) {
        Facts {
            released = Set.copyOf(released);
            delivered = Set.copyOf(delivered);
            if (!released.containsAll(delivered)) throw new IllegalArgumentException("Unreleased task marked delivered");
        }
    }

    public record Side(String executionId, int releasedTaskCount, BigDecimal releasedTonnes, BigDecimal deliveredTonnes,
                       BigDecimal terminalDeliveredTonnes, BigDecimal operationalUnmetRatio, BigDecimal commonUnmetRatio) {}
    public record Report(String artifactVersion, String problemFingerprint, String catalogFingerprint,
                         List<GlobalPlan.TaskKey> commonTasks, List<GlobalPlan.TaskKey> unreleasedByBoth,
                         BigDecimal commonRequiredTonnes, BigDecimal commonUnmetImprovementPercentagePoints,
                         Side baseline, Side selected) {}

    private FulfillmentComparison() {}

    static Facts facts(PlanExecutionTask task, JsonNode export, CandidateAssessment assessment, ObjectMapper json) {
        var last = export.path("ticks").get(task.plan().problem().totalLoops() - 1).path("facts");
        return factsFromFinalTick(task, export.path("executionId").asText(), export.path("manifest"), last, assessment, json);
    }

    static Facts factsFromFinalTick(PlanExecutionTask task, String executionId, JsonNode manifest, JsonNode last,
                                    CandidateAssessment assessment, ObjectMapper json) {
        var catalog = task.compiledProblem().catalog();
        var rawMapping = last.path("runtime").path("planExecution").path("mapping");
        if (!rawMapping.isObject()) throw new IllegalArgumentException("Missing fulfillment task mapping");
        var mapping = json.convertValue(rawMapping, ProductionTaskMapper.Mapping.class);
        if (!catalog.fingerprint().equals(mapping.catalogFingerprint())
                || !mapping.runId().equals(manifest.path("preparation").path("deterministicSimulationRunId").asText())
                || !task.plan().fingerprint().equals(assessment.planFingerprint())
                || !task.plan().problem().equals(assessment.problem()))
            throw new IllegalArgumentException("Fulfillment mapping identity mismatch");

        return factsFromFinalTick(catalog, executionId, last, assessment.problem().problemFingerprint(), mapping,
                assessment.auditData(), assessment.metrics().get("unmetDemandRatio"));
    }

    static Facts facts(ProductionTaskCatalog catalog, JsonNode export, String problemFingerprint,
                       ProductionTaskMapper.Mapping mapping, Map<String, BigDecimal> audit,
                       CandidateAssessment.MetricReading operational) {
        var last = export.path("ticks").get(export.path("ticks").size() - 1).path("facts");
        return factsFromFinalTick(catalog, export.path("executionId").asText(), last, problemFingerprint, mapping, audit, operational);
    }

    private static Facts factsFromFinalTick(ProductionTaskCatalog catalog, String executionId, JsonNode last, String problemFingerprint,
                                           ProductionTaskMapper.Mapping mapping, Map<String, BigDecimal> audit,
                                           CandidateAssessment.MetricReading operational) {
        var items = new HashMap<Long, JsonNode>();
        var rawItems = last.path("database").path("shipment_item");
        if (!rawItems.isArray()) throw new IllegalArgumentException("Missing shipment item facts");
        for (var item : rawItems) {
            var id = item.path("id");
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0 || items.putIfAbsent(id.longValue(), item) != null)
                throw new IllegalArgumentException("Invalid or duplicate shipment item fact");
        }
        var tasks = catalog.tasks().stream().collect(Collectors.toMap(ProductionTaskCatalog.Task::key, Function.identity()));
        var released = new HashSet<GlobalPlan.TaskKey>();
        var delivered = new HashSet<GlobalPlan.TaskKey>();
        for (var binding : mapping.tasks()) {
            var item = items.remove(binding.shipmentItemId());
            var frozen = tasks.get(binding.key());
            if (item == null || frozen == null || !released.add(binding.key()))
                throw new IllegalArgumentException("Unknown, missing or duplicate fulfillment mapping");
            var weight = item.path("weight");
            if (!weight.isNumber() || !tonnesMatch(frozen.weightTonnes(), weight.decimalValue()))
                throw new IllegalArgumentException("Fulfillment item weight differs from frozen task");
            var status = ShipmentItemStatus.valueOf(item.path("status").asText());
            if (status == ShipmentItemStatus.DELIVERED) delivered.add(binding.key());
        }
        if (!items.isEmpty()) throw new IllegalArgumentException("Shipment item lacks a stable task binding");
        requireMatchingAudit(audit.get("cargoRequiredTonnes"), weight(released, tasks));
        requireMatchingAudit(audit.get("cargoDeliveredTonnes"), weight(delivered, tasks));
        return new Facts(executionId, problemFingerprint,
                catalog.fingerprint(), released, delivered, operational == null ? null : operational.value());
    }

    static Report compare(ProductionTaskCatalog catalog, Facts baseline, Facts selected) {
        String fingerprint = catalog.fingerprint();
        if (!fingerprint.equals(baseline.catalogFingerprint) || !fingerprint.equals(selected.catalogFingerprint)
                || !baseline.problemFingerprint.equals(selected.problemFingerprint))
            throw new IllegalArgumentException("Cannot compare different frozen fulfillment problems");
        var tasks = catalog.tasks().stream().collect(Collectors.toMap(ProductionTaskCatalog.Task::key, Function.identity()));
        var union = new TreeSet<>(TASK_ORDER);
        union.addAll(baseline.released);
        union.addAll(selected.released);
        if (!tasks.keySet().containsAll(union)) throw new IllegalArgumentException("Released task missing from catalog");
        var denominator = weight(union, tasks);
        var sinks = sinks(catalog);
        var left = side(baseline, denominator, tasks, sinks);
        var right = side(selected, denominator, tasks, sinks);
        var improvement = denominator.signum() == 0 ? null : right.deliveredTonnes.subtract(left.deliveredTonnes)
                .movePointRight(2).divide(denominator, PreferenceScorer.PRECISION);
        return new Report(VERSION, baseline.problemFingerprint, fingerprint, List.copyOf(union),
                catalog.taskKeys().stream().filter(k -> !union.contains(k)).toList(), denominator, improvement, left, right);
    }

    static Side totals(ProductionTaskCatalog catalog, Facts facts) {
        if (!catalog.fingerprint().equals(facts.catalogFingerprint()))
            throw new IllegalArgumentException("Delivery evidence belongs to another task catalog");
        var tasks = catalog.tasks().stream().collect(Collectors.toMap(ProductionTaskCatalog.Task::key, Function.identity()));
        return side(facts, BigDecimal.ZERO, tasks, sinks(catalog));
    }

    private static Set<ProductionTaskCatalog.StageKey> sinks(ProductionTaskCatalog catalog) {
        return catalog.batches().stream().flatMap(b -> b.stages().stream())
                .filter(s -> s.role() == ProductionTaskCatalog.Role.SINK)
                .map(ProductionTaskCatalog.Stage::key).collect(Collectors.toSet());
    }

    private static Side side(Facts facts, BigDecimal denominator, Map<GlobalPlan.TaskKey, ProductionTaskCatalog.Task> tasks,
                             Set<ProductionTaskCatalog.StageKey> sinks) {
        var delivered = weight(facts.delivered, tasks);
        var terminal = facts.delivered.stream().filter(key -> sinks.contains(new ProductionTaskCatalog.StageKey(
                        new ProductionTaskCatalog.BatchKey(key.generationLoop(), key.chainId()), key.toStageId())))
                .map(key -> tasks.get(key).weightTonnes()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var unmet = denominator.signum() == 0 ? null
                : denominator.subtract(delivered).divide(denominator, PreferenceScorer.PRECISION);
        return new Side(facts.executionId, facts.released.size(), weight(facts.released, tasks), delivered,
                terminal, facts.operationalUnmetRatio, unmet);
    }

    private static BigDecimal weight(Collection<GlobalPlan.TaskKey> keys,
                                     Map<GlobalPlan.TaskKey, ProductionTaskCatalog.Task> tasks) {
        return keys.stream().map(key -> tasks.get(key).weightTonnes()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void requireMatchingAudit(BigDecimal observed, BigDecimal actual) {
        // Missing metrics remain unavailable at scoring; a populated snapshot must reconcile with ledger facts.
        if (observed != null && !tonnesMatch(observed, actual))
            throw new IllegalArgumentException("Fulfillment evidence disagrees with evaluated tonnage");
    }

    private static boolean tonnesMatch(BigDecimal a, BigDecimal b) {
        var tolerance = new BigDecimal("1e-9");
        return a.subtract(b).abs().compareTo(tolerance.max(a.abs().max(b.abs()).multiply(tolerance))) <= 0;
    }
}
