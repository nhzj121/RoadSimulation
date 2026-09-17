package org.example.roadsimulation.evaluation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 6A：一个指标的不可变代码级契约。
 *
 * <p>契约固定名称、单位、公式、事实来源、时间范围和缺失策略。Phase 6B 只能按该契约
 * 生成快照，不能在计算服务中临时改变指标含义。</p>
 */
public record EvaluationMetricDefinition(
        EvaluationMetricId id,
        String displayName,
        EvaluationMetricCategory category,
        String unit,
        EvaluationMetricTimeScope timeScope,
        String formula,
        String numerator,
        String denominator,
        List<String> factSources,
        String availabilityRule,
        String updateCadence,
        EvaluationMetricReadiness readiness,
        EvaluationMetricValueStatus unavailableStatus,
        Set<EvaluationMetricDependency> dependencies,
        String readinessReason
) {
    public EvaluationMetricDefinition {
        // Phase 6A：在应用启动时拒绝不完整契约，避免缺字段一直传播到前端。
        Objects.requireNonNull(id, "metric id is required");
        displayName = requireText(displayName, "displayName");
        Objects.requireNonNull(category, "metric category is required");
        unit = requireText(unit, "unit");
        Objects.requireNonNull(timeScope, "timeScope is required");
        formula = requireText(formula, "formula");
        numerator = requireText(numerator, "numerator");
        denominator = requireText(denominator, "denominator");
        factSources = List.copyOf(Objects.requireNonNull(factSources, "factSources are required"));
        if (factSources.isEmpty() || factSources.stream().anyMatch(source -> source == null || source.isBlank())) {
            throw new IllegalArgumentException("factSources must contain non-blank values: " + id);
        }
        availabilityRule = requireText(availabilityRule, "availabilityRule");
        updateCadence = requireText(updateCadence, "updateCadence");
        Objects.requireNonNull(readiness, "readiness is required");
        Objects.requireNonNull(unavailableStatus, "unavailableStatus is required");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies are required"));
        readinessReason = requireText(readinessReason, "readinessReason");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
