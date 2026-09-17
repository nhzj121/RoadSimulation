package org.example.roadsimulation.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase 9B-2：一件货物在一次评价运行内冻结的交付 SLA 与后端交付完成事实。
 *
 * <p>本表只保存业务对象的标量标识，不建立 JPA 外键，也不映射或改写
 * {@code Shipment.deliveryAppoint}。评价账本的生命周期由仿真 reset 管理。</p>
 */
@Entity
@Table(
        name = "delivery_sla_fact",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_sla_run_item",
                columnNames = {"simulation_run_id", "shipment_item_id"}
        ),
        indexes = {
                @Index(name = "idx_delivery_sla_run", columnList = "simulation_run_id"),
                @Index(name = "idx_delivery_sla_run_status", columnList = "simulation_run_id,status"),
                @Index(name = "idx_delivery_sla_assignment", columnList = "delivery_assignment_id")
        }
)
public class DeliverySlaFact {

    public enum Status { OPEN, DELIVERED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_run_id", nullable = false, length = 100)
    private String simulationRunId;

    @Column(name = "shipment_item_id", nullable = false)
    private Long shipmentItemId;

    @Column(name = "delivery_assignment_id")
    private Long deliveryAssignmentId;

    @Column(name = "demand_created_sim_time", nullable = false)
    private LocalDateTime demandCreatedSimTime;

    @Column(name = "delivery_deadline_sim_time", nullable = false)
    private LocalDateTime deliveryDeadlineSimTime;

    @Column(name = "delivered_sim_time")
    private LocalDateTime deliveredSimTime;

    @Column(name = "haversine_distance_km", nullable = false)
    private Double haversineDistanceKm;

    @Column(name = "reference_road_distance_km", nullable = false)
    private Double referenceRoadDistanceKm;

    @Column(name = "sla_seconds", nullable = false)
    private Long slaSeconds;

    @Column(name = "sla_model_id", nullable = false, length = 80)
    private String slaModelId;

    @Column(name = "road_distance_factor", nullable = false)
    private Double roadDistanceFactor;

    @Column(name = "reference_speed_kph", nullable = false)
    private Double referenceSpeedKph;

    @Column(name = "max_service_wait_seconds", nullable = false)
    private Long maxServiceWaitSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Version
    private Long version;

    protected DeliverySlaFact() {
        // Phase 9B-2：JPA 专用；业务代码必须通过 freeze(...) 创建不可变截止基线。
    }

    public static DeliverySlaFact freeze(
            String simulationRunId,
            Long shipmentItemId,
            LocalDateTime demandCreatedSimTime,
            double haversineDistanceKm,
            DeliverySlaPolicy slaPolicy,
            long maxServiceWaitSeconds
    ) {
        Objects.requireNonNull(slaPolicy, "slaPolicy is required");
        if (!Double.isFinite(haversineDistanceKm) || haversineDistanceKm < 0.0) {
            throw new IllegalArgumentException("haversineDistanceKm must be non-negative and finite");
        }
        if (maxServiceWaitSeconds <= 0L) {
            throw new IllegalArgumentException("maxServiceWaitSeconds must be positive");
        }

        DeliverySlaFact fact = new DeliverySlaFact();
        fact.simulationRunId = requireText(simulationRunId, "simulationRunId");
        fact.shipmentItemId = requirePositive(shipmentItemId, "shipmentItemId");
        fact.demandCreatedSimTime = Objects.requireNonNull(
                demandCreatedSimTime, "demandCreatedSimTime is required");
        fact.haversineDistanceKm = haversineDistanceKm;
        fact.roadDistanceFactor = slaPolicy.getRoadDistanceFactor();
        fact.referenceRoadDistanceKm = haversineDistanceKm * fact.roadDistanceFactor;
        fact.referenceSpeedKph = slaPolicy.getReferenceSpeedKph();
        fact.maxServiceWaitSeconds = maxServiceWaitSeconds;
        fact.slaModelId = requireText(slaPolicy.getModelId(), "slaModelId");
        fact.slaSeconds = slaPolicy.calculateSlaSeconds(haversineDistanceKm, maxServiceWaitSeconds);
        fact.deliveryDeadlineSimTime = demandCreatedSimTime.plusSeconds(fact.slaSeconds);
        fact.status = Status.OPEN;
        return fact;
    }

    /** Phase 9B-2：只接受现有后端 UNLOAD 服务完成时刻，不接受前端到达回调。 */
    public void markDelivered(Long assignmentId, LocalDateTime deliveredAt) {
        Long resolvedAssignmentId = requirePositive(assignmentId, "deliveryAssignmentId");
        LocalDateTime resolvedTime = Objects.requireNonNull(deliveredAt, "deliveredAt is required");
        if (resolvedTime.isBefore(demandCreatedSimTime)) {
            throw new IllegalArgumentException("delivery cannot precede demand creation");
        }
        // Phase 9B-2：首次交付事实一经冻结不得被重复观察覆盖。
        if (status == Status.DELIVERED) {
            if (!Objects.equals(deliveryAssignmentId, resolvedAssignmentId)
                    || !Objects.equals(deliveredSimTime, resolvedTime)) {
                throw new IllegalStateException("conflicting delivery completion fact");
            }
            return;
        }
        if (status == Status.CANCELLED) {
            throw new IllegalStateException("cancelled delivery fact cannot become delivered");
        }
        deliveryAssignmentId = resolvedAssignmentId;
        deliveredSimTime = resolvedTime;
        status = Status.DELIVERED;
    }

    /** Phase 9B-2：取消项保留截止基线，但不会进入后续任务准时率判断。 */
    public void markCancelled() {
        if (status == Status.DELIVERED) {
            throw new IllegalStateException("delivered delivery fact cannot become cancelled");
        }
        status = Status.CANCELLED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    public Long getId() { return id; }
    public String getSimulationRunId() { return simulationRunId; }
    public Long getShipmentItemId() { return shipmentItemId; }
    public Long getDeliveryAssignmentId() { return deliveryAssignmentId; }
    public LocalDateTime getDemandCreatedSimTime() { return demandCreatedSimTime; }
    public LocalDateTime getDeliveryDeadlineSimTime() { return deliveryDeadlineSimTime; }
    public LocalDateTime getDeliveredSimTime() { return deliveredSimTime; }
    public Double getHaversineDistanceKm() { return haversineDistanceKm; }
    public Double getReferenceRoadDistanceKm() { return referenceRoadDistanceKm; }
    public Long getSlaSeconds() { return slaSeconds; }
    public String getSlaModelId() { return slaModelId; }
    public Double getRoadDistanceFactor() { return roadDistanceFactor; }
    public Double getReferenceSpeedKph() { return referenceSpeedKph; }
    public Long getMaxServiceWaitSeconds() { return maxServiceWaitSeconds; }
    public Status getStatus() { return status; }
    public Long getVersion() { return version; }
}
