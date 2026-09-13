package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.AssignmentNodeRepository;
import org.example.roadsimulation.repository.CargoWaitEpisodeRepository;
import org.example.roadsimulation.repository.DeliverySlaFactRepository;
import org.example.roadsimulation.repository.NodeServiceEpisodeRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 9B-2：在业务 tick 完成后，把现有需求和后端 UNLOAD 完成记录投影为交付 SLA 事实。
 *
 * <p>该类只读取业务实体和既有评价账本，只保存 {@link DeliverySlaFact}。禁止调用分配、
 * 路径规划、运输推进或状态转换服务，也禁止读取或修改 {@code Shipment.deliveryAppoint}。</p>
 */
@Component
public class DeliverySlaLedgerWriter {

    private static final double EARTH_RADIUS_KM = 6_371.0;

    private final DeliverySlaFactRepository deliveryFactRepository;
    private final CargoWaitEpisodeRepository cargoWaitRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final AssignmentNodeRepository assignmentNodeRepository;
    private final NodeServiceEpisodeRepository nodeServiceEpisodeRepository;
    private final DeliverySlaPolicy slaPolicy;
    private final EvaluationMetricPolicy metricPolicy;

    public DeliverySlaLedgerWriter(
            DeliverySlaFactRepository deliveryFactRepository,
            CargoWaitEpisodeRepository cargoWaitRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentNodeRepository assignmentNodeRepository,
            NodeServiceEpisodeRepository nodeServiceEpisodeRepository,
            DeliverySlaPolicy slaPolicy,
            EvaluationMetricPolicy metricPolicy
    ) {
        this.deliveryFactRepository = deliveryFactRepository;
        this.cargoWaitRepository = cargoWaitRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.assignmentNodeRepository = assignmentNodeRepository;
        this.nodeServiceEpisodeRepository = nodeServiceEpisodeRepository;
        this.slaPolicy = slaPolicy;
        this.metricPolicy = metricPolicy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void project(String simulationRunId, SimulationTick tick) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId must not be blank");
        }
        Objects.requireNonNull(tick, "simulation tick is required");

        Map<Long, DeliverySlaFact> facts = uniqueDeliveryFacts(simulationRunId);
        Map<Long, CargoWaitEpisode> cargoFacts = uniqueCargoFacts(simulationRunId);
        List<ShipmentItem> items = shipmentItemRepository.findAll();
        Map<Long, List<ShipmentItem>> itemsByAssignment = indexItemsByAssignment(items);
        Map<Long, AssignmentNode> nodesById = indexNodesById(assignmentNodeRepository.findAll());
        Map<Long, DeliveryCompletion> completions = deliveryCompletions(
                nodeServiceEpisodeRepository.findAll(), nodesById, itemsByAssignment,
                runStartOf(tick), tick.tickEnd());

        for (ShipmentItem item : items) {
            Long itemId = requirePositive(item == null ? null : item.getId(), "shipmentItemId");
            CargoWaitEpisode cargoFact = cargoFacts.get(itemId);
            if (cargoFact == null) {
                // Phase 9B-2：需求起点必须复用 Phase 9A 已规范化时间，禁止自行猜测墙钟时间。
                throw new IllegalStateException("shipment item " + itemId + " has no current-run cargo wait fact");
            }

            DeliverySlaFact fact = facts.get(itemId);
            if (fact == null) {
                double distanceKm = haversineDistanceKm(item);
                fact = DeliverySlaFact.freeze(
                        simulationRunId,
                        itemId,
                        cargoFact.getStartedSimTime(),
                        distanceKm,
                        slaPolicy,
                        metricPolicy.getMaxServiceWaitSeconds());
                facts.put(itemId, fact);
            }

            DeliveryCompletion completion = completions.get(itemId);
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                if (completion != null) {
                    throw new IllegalStateException("cancelled shipment item " + itemId + " has UNLOAD completion");
                }
                fact.markCancelled();
            } else if (item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                if (completion == null) {
                    // Phase 9B-2：不得用 Assignment.endTime、更新时间或前端到达时间伪造卸货完成事实。
                    throw new IllegalStateException("delivered shipment item " + itemId + " has no UNLOAD completion fact");
                }
                fact.markDelivered(completion.assignmentId(), completion.deliveredAt());
            } else if (completion != null) {
                throw new IllegalStateException(
                        "UNLOAD completion exists before shipment item " + itemId + " is DELIVERED");
            }
            deliveryFactRepository.save(fact);
        }
    }

    private Map<Long, DeliverySlaFact> uniqueDeliveryFacts(String runId) {
        Map<Long, DeliverySlaFact> result = new HashMap<>();
        for (DeliverySlaFact fact : deliveryFactRepository.findAllBySimulationRunId(runId)) {
            if (result.put(fact.getShipmentItemId(), fact) != null) {
                throw new IllegalStateException(
                        "duplicate delivery SLA fact for item " + fact.getShipmentItemId());
            }
        }
        return result;
    }

    private Map<Long, CargoWaitEpisode> uniqueCargoFacts(String runId) {
        Map<Long, CargoWaitEpisode> result = new HashMap<>();
        for (CargoWaitEpisode fact : cargoWaitRepository.findAllBySimulationRunId(runId)) {
            if (result.put(fact.getShipmentItemId(), fact) != null) {
                throw new IllegalStateException(
                        "duplicate cargo wait fact for item " + fact.getShipmentItemId());
            }
        }
        return result;
    }

    private Map<Long, List<ShipmentItem>> indexItemsByAssignment(List<ShipmentItem> items) {
        Map<Long, List<ShipmentItem>> result = new HashMap<>();
        for (ShipmentItem item : items) {
            if (item != null && item.getAssignment() != null && item.getAssignment().getId() != null) {
                result.computeIfAbsent(item.getAssignment().getId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        return result;
    }

    private Map<Long, AssignmentNode> indexNodesById(List<AssignmentNode> nodes) {
        Map<Long, AssignmentNode> result = new HashMap<>();
        for (AssignmentNode node : nodes) {
            if (node != null && node.getId() != null && result.put(node.getId(), node) != null) {
                throw new IllegalStateException("duplicate assignment node id " + node.getId());
            }
        }
        return result;
    }

    private Map<Long, DeliveryCompletion> deliveryCompletions(
            List<NodeServiceEpisode> episodes,
            Map<Long, AssignmentNode> nodesById,
            Map<Long, List<ShipmentItem>> itemsByAssignment,
            LocalDateTime runStart,
            LocalDateTime tickEnd
    ) {
        Map<Long, DeliveryCompletion> result = new HashMap<>();
        for (NodeServiceEpisode episode : episodes) {
            if (episode == null || episode.getActionType() != NodeServiceEpisode.ActionType.UNLOAD
                    || episode.getStatus() != NodeServiceEpisode.Status.COMPLETED) {
                continue;
            }
            LocalDateTime deliveredAt = episode.getServiceCompletedAt();
            if (deliveredAt == null || deliveredAt.isBefore(runStart) || deliveredAt.isAfter(tickEnd)) {
                continue;
            }
            Long assignmentId = requirePositive(episode.getAssignmentId(), "UNLOAD assignmentId");
            if (episode.getAssignmentNodeId() != null) {
                // Phase 9B-2：VRP 的 UNLOAD 节点精确对应一件 ShipmentItem。
                AssignmentNode node = nodesById.get(episode.getAssignmentNodeId());
                if (node == null || node.getActionType() != AssignmentNode.NodeActionType.UNLOAD
                        || node.getShipmentItem() == null) {
                    throw new IllegalStateException(
                            "UNLOAD episode " + episode.getId() + " has no valid shipment item node");
                }
                mergeCompletion(result, requirePositive(node.getShipmentItem().getId(), "shipmentItemId"),
                        new DeliveryCompletion(assignmentId, deliveredAt));
            } else {
                // Phase 9B-2：普通任务没有 AssignmentNode，其唯一 UNLOAD 完成同时交付任务内非取消货物。
                List<ShipmentItem> assignmentItems = itemsByAssignment.getOrDefault(assignmentId, List.of());
                if (assignmentItems.isEmpty()) {
                    throw new IllegalStateException(
                            "ordinary UNLOAD episode " + episode.getId() + " has no assignment items");
                }
                for (ShipmentItem item : assignmentItems) {
                    if (item.getStatus() != ShipmentItem.ShipmentItemStatus.CANCELLED) {
                        mergeCompletion(result, requirePositive(item.getId(), "shipmentItemId"),
                                new DeliveryCompletion(assignmentId, deliveredAt));
                    }
                }
            }
        }
        return result;
    }

    private void mergeCompletion(
            Map<Long, DeliveryCompletion> result,
            Long itemId,
            DeliveryCompletion candidate
    ) {
        DeliveryCompletion previous = result.get(itemId);
        if (previous == null) {
            result.put(itemId, candidate);
            return;
        }
        // Phase 9B-2：一次货物只能存在一个权威卸货完成边界；不同任务或不同时刻均视为事实冲突。
        if (!Objects.equals(candidate.assignmentId(), previous.assignmentId())
                || !Objects.equals(candidate.deliveredAt(), previous.deliveredAt())) {
            throw new IllegalStateException("shipment item " + itemId + " has conflicting UNLOAD completions");
        }
    }

    private double haversineDistanceKm(ShipmentItem item) {
        Shipment shipment = item.getShipment();
        if (shipment == null) {
            throw new IllegalStateException("shipment item " + item.getId() + " has no shipment");
        }
        POI origin = shipment.getOriginPOI();
        POI destination = shipment.getDestPOI();
        double lat1 = coordinate(origin == null ? null : origin.getLatitude(), -90.0, 90.0, "origin latitude");
        double lon1 = coordinate(origin == null ? null : origin.getLongitude(), -180.0, 180.0, "origin longitude");
        double lat2 = coordinate(destination == null ? null : destination.getLatitude(), -90.0, 90.0,
                "destination latitude");
        double lon2 = coordinate(destination == null ? null : destination.getLongitude(), -180.0, 180.0,
                "destination longitude");

        double latRadians = Math.toRadians(lat2 - lat1);
        double lonRadians = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latRadians / 2.0) * Math.sin(latRadians / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonRadians / 2.0) * Math.sin(lonRadians / 2.0);
        // Phase 9B-2：限制舍入噪声，确保反三角函数不会产生 NaN。
        double bounded = Math.max(0.0, Math.min(1.0, a));
        return EARTH_RADIUS_KM * 2.0 * Math.atan2(Math.sqrt(bounded), Math.sqrt(1.0 - bounded));
    }

    private double coordinate(BigDecimal value, double min, double max, String field) {
        if (value == null) throw new IllegalStateException(field + " is required");
        double coordinate = value.doubleValue();
        if (!Double.isFinite(coordinate) || coordinate < min || coordinate > max) {
            throw new IllegalStateException(field + " is outside [" + min + ", " + max + "]");
        }
        return coordinate;
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) throw new IllegalStateException(field + " must be positive");
        return value;
    }

    private LocalDateTime runStartOf(SimulationTick tick) {
        long elapsedSeconds = Math.multiplyExact((long) tick.loopIndex(), tick.availableSeconds());
        return tick.tickStart().minusSeconds(elapsedSeconds);
    }

    private record DeliveryCompletion(Long assignmentId, LocalDateTime deliveredAt) { }
}
