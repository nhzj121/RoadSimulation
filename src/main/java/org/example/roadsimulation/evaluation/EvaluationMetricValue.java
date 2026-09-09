package org.example.roadsimulation.evaluation;

import java.util.Objects;

/** Phase 6B：单个指标在同一快照版本中的不可变值投影。 */
public record EvaluationMetricValue(
        String metricId,
        String displayName,
        EvaluationMetricCategory category,
        String unit,
        EvaluationMetricValueStatus status,
        Double value,
        String reason
) {
    public EvaluationMetricValue {
        metricId = requireText(metricId, "metricId");
        displayName = requireText(displayName, "displayName");
        Objects.requireNonNull(category, "category is required");
        unit = requireText(unit, "unit");
        Objects.requireNonNull(status, "status is required");

        // Phase 6B：不可用值必须使用 null；禁止用 0 冒充缺失、无语义或异常事实。
        if (status == EvaluationMetricValueStatus.AVAILABLE) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("AVAILABLE metric must contain a finite value: " + metricId);
            }
            reason = null;
        } else {
            if (value != null) {
                throw new IllegalArgumentException("Non-AVAILABLE metric must contain null value: " + metricId);
            }
            reason = requireText(reason, "reason");
        }
    }

    /** Phase 6B：按 Phase 6A 定义创建可用数值，避免调用方遗漏元数据。 */
    public static EvaluationMetricValue available(EvaluationMetricDefinition definition, double value) {
        return new EvaluationMetricValue(
                definition.id().getMetricId(),
                definition.displayName(),
                definition.category(),
                definition.unit(),
                EvaluationMetricValueStatus.AVAILABLE,
                value,
                null
        );
    }

    /** Phase 6B：按 Phase 6A 定义创建明确的不可用状态。 */
    public static EvaluationMetricValue unavailable(
            EvaluationMetricDefinition definition,
            EvaluationMetricValueStatus status,
            String reason
    ) {
        if (status == EvaluationMetricValueStatus.AVAILABLE) {
            throw new IllegalArgumentException("unavailable factory cannot create AVAILABLE metric");
        }
        return new EvaluationMetricValue(
                definition.id().getMetricId(),
                definition.displayName(),
                definition.category(),
                definition.unit(),
                status,
                null,
                reason
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
