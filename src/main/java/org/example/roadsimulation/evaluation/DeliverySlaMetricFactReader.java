package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.repository.DeliverySlaFactRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 9B-3：按评价运行读取交付 SLA 事实，并校验账本模型没有跨配置混用。
 *
 * <p>该组件不查询路线、不读取预约字段，也不修改业务或评价实体。</p>
 */
@Component
public class DeliverySlaMetricFactReader {

    private static final double EPSILON = 1.0e-9;

    private final DeliverySlaFactRepository repository;
    private final DeliverySlaLedgerHealth health;
    private final DeliverySlaPolicy slaPolicy;
    private final EvaluationMetricPolicy metricPolicy;

    public DeliverySlaMetricFactReader(
            DeliverySlaFactRepository repository,
            DeliverySlaLedgerHealth health,
            DeliverySlaPolicy slaPolicy,
            EvaluationMetricPolicy metricPolicy
    ) {
        this.repository = repository;
        this.health = health;
        this.slaPolicy = slaPolicy;
        this.metricPolicy = metricPolicy;
    }

    public DeliveryFacts read(String simulationRunId) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId must not be blank");
        }
        List<DeliverySlaFact> facts = List.copyOf(repository.findAllBySimulationRunId(simulationRunId));
        for (DeliverySlaFact fact : facts) {
            validateFrozenModel(simulationRunId, fact);
        }
        return new DeliveryFacts(
                facts,
                health.hasProjectionFailures(),
                health.hasProjectionFailures() ? health.describeFirstFailure() : null
        );
    }

    private void validateFrozenModel(String runId, DeliverySlaFact fact) {
        if (fact == null || !runId.equals(fact.getSimulationRunId())
                || fact.getShipmentItemId() == null || fact.getShipmentItemId() <= 0L
                || fact.getStatus() == null
                || fact.getDemandCreatedSimTime() == null
                || fact.getDeliveryDeadlineSimTime() == null
                || fact.getSlaSeconds() == null || fact.getSlaSeconds() <= 0L
                || fact.getHaversineDistanceKm() == null
                || !finiteNonNegative(fact.getHaversineDistanceKm())
                || fact.getReferenceRoadDistanceKm() == null
                || !finiteNonNegative(fact.getReferenceRoadDistanceKm())
                || !slaPolicy.getModelId().equals(fact.getSlaModelId())
                || !same(slaPolicy.getRoadDistanceFactor(), fact.getRoadDistanceFactor())
                || !same(slaPolicy.getReferenceSpeedKph(), fact.getReferenceSpeedKph())
                || fact.getMaxServiceWaitSeconds() == null
                || fact.getMaxServiceWaitSeconds() != metricPolicy.getMaxServiceWaitSeconds()) {
            throw new IllegalStateException("delivery SLA fact model or identity is inconsistent");
        }
        LocalDateTime expectedDeadline = fact.getDemandCreatedSimTime().plusSeconds(fact.getSlaSeconds());
        double expectedReferenceDistance = fact.getHaversineDistanceKm() * fact.getRoadDistanceFactor();
        if (!expectedDeadline.equals(fact.getDeliveryDeadlineSimTime())
                || !same(expectedReferenceDistance, fact.getReferenceRoadDistanceKm())) {
            throw new IllegalStateException("delivery SLA fact deadline or distance is inconsistent");
        }
    }

    private boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private boolean same(double expected, Double actual) {
        if (actual == null || !Double.isFinite(actual)) return false;
        double scale = Math.max(1.0, Math.max(Math.abs(expected), Math.abs(actual)));
        return Math.abs(expected - actual) <= EPSILON * scale;
    }

    /** Phase 9B-3：向计算器提供不可变的当前运行事实与失败状态。 */
    public record DeliveryFacts(
            List<DeliverySlaFact> facts,
            boolean projectionFailed,
            String failureReason
    ) { }
}
