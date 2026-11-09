package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.evaluation.*;
import java.math.BigDecimal;
import java.util.*;

/** Explicit registry; extensions never silently change the existing evaluation enum. */
public record ObjectiveRegistry(List<ObjectiveDefinition> definitions) {
    public static final String PRIMARY = "emptyMileageRatio";

    public ObjectiveRegistry {
        definitions = ContractChecks.list(definitions, "definitions");
        var indexed = new TreeMap<String, ObjectiveDefinition>();
        for (var definition : definitions) {
            if (indexed.putIfAbsent(definition.metricId(), definition) != null)
                throw new IllegalArgumentException("Duplicate objective: " + definition.metricId());
        }
        for (var required : standardDefinitions()) {
            if (!required.equals(indexed.get(required.metricId())))
                throw new IllegalArgumentException("Missing or redefined standard objective: " + required.metricId());
        }
        definitions = List.copyOf(indexed.values());
    }

    public static ObjectiveRegistry standard() { return new ObjectiveRegistry(standardDefinitions()); }

    private static List<ObjectiveDefinition> standardDefinitions() {
        var catalog = new EvaluationMetricCatalog();
        return List.of(EvaluationMetricId.GLOBAL_EMPTY_MILEAGE_RATIO,
                        EvaluationMetricId.GLOBAL_CAPACITY_WASTE_RATIO,
                        EvaluationMetricId.GLOBAL_UNMET_DEMAND_RATIO,
                        EvaluationMetricId.GLOBAL_CARBON_INTENSITY).stream()
                .map(id -> new ObjectiveDefinition(id.getMetricId(), catalog.require(id).unit(),
                        ObjectiveDefinition.Direction.MINIMIZE, BigDecimal.ZERO,
                        id == EvaluationMetricId.GLOBAL_CARBON_INTENSITY ? null : BigDecimal.ONE)).toList();
    }

    public Set<String> metricIds() {
        var keys = new TreeSet<String>();
        definitions.forEach(d -> keys.add(d.metricId()));
        return Collections.unmodifiableSet(keys);
    }

    public ObjectiveDefinition require(String id) {
        return definitions.stream().filter(d -> d.metricId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown objective: " + id));
    }
}
