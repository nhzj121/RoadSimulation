package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;

/** Pure verification of observed released flows; never guesses a missing key from cargo weight. */
public final class ProductionTaskMapper {
    public record ObservedItem(long shipmentItemId, String sku, int quantity, BigDecimal weight, BigDecimal volume) {
        public ObservedItem {
            if (shipmentItemId <= 0 || quantity <= 0) throw new IllegalArgumentException("Invalid observed item");
            sku = ContractChecks.text(sku, "sku"); weight = ContractChecks.positive(weight, "weight");
            volume = ContractChecks.nonNegative(volume, "volume");
        }
    }
    public record ObservedFlow(FlowKey key, long shipmentId, String sku, long originPoiId, long destinationPoiId,
                               BigDecimal totalWeight, BigDecimal totalVolume, List<ObservedItem> items) {
        public ObservedFlow {
            Objects.requireNonNull(key);
            if (shipmentId <= 0 || originPoiId <= 0 || destinationPoiId <= 0) throw new IllegalArgumentException("Invalid shipment");
            sku = ContractChecks.text(sku, "sku"); totalWeight = ContractChecks.positive(totalWeight, "totalWeight");
            totalVolume = ContractChecks.nonNegative(totalVolume, "totalVolume");
            items = ContractChecks.list(items, "items").stream().sorted(Comparator.comparingLong(ObservedItem::shipmentItemId)).toList();
        }
    }
    public record FlowBinding(FlowKey key, long shipmentId) {}
    public record TaskBinding(GlobalPlan.TaskKey key, long shipmentItemId) {}
    public record Mapping(String artifactVersion, String runId, String catalogFingerprint, List<FlowBinding> flows, List<TaskBinding> tasks) {
        public static final String VERSION = "sandbox-production-task-mapping/v1";
        public Mapping {
            artifactVersion = ContractChecks.version(artifactVersion, VERSION);
            runId = ContractChecks.text(runId, "runId"); catalogFingerprint = ContractChecks.hash(catalogFingerprint, "catalogFingerprint");
            flows = sorted(flows, "flow bindings", FlowBinding::key);
            tasks = sorted(tasks, "task bindings", TaskBinding::key);
        }
    }
    public Mapping map(String runId, ProductionTaskCatalog catalog, Collection<FlowKey> expectedReleased,
                       List<ObservedFlow> observed) {
        var expected = new HashSet<>(ContractChecks.list(expectedReleased, "expectedReleased"));
        if (expected.size() != expectedReleased.size()) throw new IllegalArgumentException("Duplicate expected flow");
        var actual = sorted(observed, "observed flows", ObservedFlow::key);
        if (!expected.equals(new HashSet<>(actual.stream().map(ObservedFlow::key).toList())))
            throw new IllegalArgumentException("Missing or unexpected released flow");
        var bindings = new ArrayList<FlowBinding>(); var taskBindings = new ArrayList<TaskBinding>();
        var shipmentIds = new HashSet<Long>(); var itemIds = new HashSet<Long>();
        var flowsByKey = new HashMap<FlowKey, Flow>();
        var tasksByFlow = new HashMap<FlowKey, List<Task>>();
        catalog.flows().forEach(flow -> flowsByKey.put(flow.key(), flow));
        catalog.tasks().forEach(task -> tasksByFlow.computeIfAbsent(task.flow(), ignored -> new ArrayList<>()).add(task));
        for (var observation : actual) {
            var flow = flowsByKey.get(observation.key);
            if (flow == null) throw new IllegalArgumentException("Unknown observed flow");
            if (!shipmentIds.add(observation.shipmentId) || !flow.sku().equals(observation.sku)
                    || flow.originPoiId() != observation.originPoiId || flow.destinationPoiId() != observation.destinationPoiId
                    || flow.transportWeight().compareTo(observation.totalWeight) != 0
                    || flow.transportVolume().compareTo(observation.totalVolume) != 0)
                throw new IllegalArgumentException("Conflicting flow evidence: " + flow.key());
            var parts = tasksByFlow.get(flow.key()).stream()
                    .sorted(Comparator.comparingInt(t -> t.key().partIndex())).toList();
            if (parts.size() != observation.items.size()) throw new IllegalArgumentException("Part count mismatch");
            for (int i = 0; i < parts.size(); i++) {
                var task = parts.get(i); var item = observation.items.get(i);
                if (!itemIds.add(item.shipmentItemId) || !flow.sku().equals(item.sku) || task.quantity() != item.quantity
                        || task.weightTonnes().compareTo(item.weight) != 0 || task.volumeCubicMeters().compareTo(item.volume) != 0)
                    throw new IllegalArgumentException("Conflicting task evidence: " + task.key());
                taskBindings.add(new TaskBinding(task.key(), item.shipmentItemId));
            }
            bindings.add(new FlowBinding(flow.key(), observation.shipmentId));
        }
        return new Mapping(Mapping.VERSION, runId, catalog.fingerprint(), bindings, taskBindings);
    }
}
