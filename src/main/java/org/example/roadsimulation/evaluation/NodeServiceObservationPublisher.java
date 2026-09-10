package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Phase 7B：把既有运输状态边界翻译成只读观察事件。
 *
 * <p>本组件不保存运输对象，也不决定下一车辆状态。生命周期仍是唯一业务权威；发布器只复制
 * 已经发生的标量事实，供事务提交后的账本投影使用。</p>
 */
@Component
public class NodeServiceObservationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NodeServiceObservationPublisher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final NodeServiceLedgerHealth health;

    /** Phase 7B：保留单参数构造器供纯单元测试使用。 */
    public NodeServiceObservationPublisher(ApplicationEventPublisher eventPublisher) {
        this(eventPublisher, null);
    }

    @Autowired
    public NodeServiceObservationPublisher(
            ApplicationEventPublisher eventPublisher,
            NodeServiceLedgerHealth health
    ) {
        this.eventPublisher = eventPublisher;
        this.health = health;
    }

    public void serviceStarted(
            Assignment assignment,
            AssignmentLeg leg,
            Vehicle vehicle,
            Vehicle.VehicleStatus serviceStatus,
            LocalDateTime occurredAt
    ) {
        NodeServiceEpisode.ActionType actionType = actionType(serviceStatus);
        if (actionType == null) {
            // Phase 7B：PASS_BY 当前映射为 WAITING，但它不是装卸服务，不进入服务时长和吞吐量。
            return;
        }
        AssignmentNode node = leg == null ? null : leg.getToNode();
        POI poi = leg == null ? null : leg.getToPOI();
        Long assignmentId = idOf(assignment);
        Long vehicleId = idOf(vehicle);
        Long poiId = idOf(poi);
        Integer legIndex = leg == null ? null : leg.getSequenceIndex();
        Long nodeId = idOf(node);
        if (assignmentId == null || vehicleId == null || poiId == null || occurredAt == null
                || legIndex == null || legIndex < 0) {
            // Phase 7B：缺少身份的事件不能成为可信账本事实；业务推进本身不因此被拒绝。
            // Phase 7E：拒绝事件时保留事件类型和仿真时刻，供评价 reason 定位首错。
            reject(NodeServiceObservation.Type.SERVICE_STARTED,
                    "service start lacks stable identity", assignmentId, nodeId, occurredAt);
            return;
        }
        String eventKey = nodeId != null
                ? "node:" + nodeId
                : "assignment:" + assignmentId + ":leg:" + legIndex + ":" + actionType;
        publish(new NodeServiceObservation(
                NodeServiceObservation.Type.SERVICE_STARTED,
                eventKey,
                assignmentId,
                vehicleId,
                poiId,
                nodeId,
                legIndex,
                actionType,
                occurredAt,
                null
        ));
    }

    public void serviceCompleted(
            Assignment assignment,
            Vehicle vehicle,
            AssignmentNode node,
            NodeServiceEpisode.ActionType actionType,
            LocalDateTime occurredAt
    ) {
        Long assignmentId = idOf(assignment);
        if (assignmentId == null || actionType == null || occurredAt == null) {
            // Phase 7E：完成事件校验失败也记录其本来应具有的事件类型。
            reject(NodeServiceObservation.Type.SERVICE_COMPLETED,
                    "service completion lacks stable identity", assignmentId, idOf(node), occurredAt);
            return;
        }
        Double processedTonnes = processedTonnes(assignment, node);
        if (processedTonnes == null) {
            // Phase 7E：无效吨数不再只留下通用健康计数。
            reject(NodeServiceObservation.Type.SERVICE_COMPLETED,
                    "service completion lacks valid processed tonnes", assignmentId, idOf(node), occurredAt);
            return;
        }
        publish(new NodeServiceObservation(
                NodeServiceObservation.Type.SERVICE_COMPLETED,
                null,
                assignmentId,
                idOf(vehicle),
                null,
                idOf(node),
                null,
                actionType,
                occurredAt,
                processedTonnes
        ));
    }

    private void publish(NodeServiceObservation observation) {
        try {
            eventPublisher.publishEvent(observation);
        } catch (RuntimeException ex) {
            // Phase 7B：发布失败不回滚已存在的运输事实，但必须令本运行节点指标失败封闭。
            // Phase 7E：发布器把原始标量事件和异常摘要交给健康状态锁存。
            recordFailure(
                    "PUBLISH",
                    observation.type(),
                    observation.assignmentId(),
                    observation.assignmentNodeId(),
                    observation.occurredAt(),
                    failureReason(ex)
            );
            log.error(
                    "[Phase7B NodeServiceLedger] observation publish failed: type={}, assignmentId={}, nodeId={}",
                    observation.type(), observation.assignmentId(), observation.assignmentNodeId(), ex
            );
        }
    }

    private void reject(
            NodeServiceObservation.Type eventType,
            String reason,
            Long assignmentId,
            Long nodeId,
            LocalDateTime occurredAt
    ) {
        // Phase 7B：身份或吨数缺失代表账本可能少记，评价层必须失败封闭而不是显示偏低值。
        // Phase 7E：VALIDATION 与真正的数据库投影故障分开记录，避免误判排查方向。
        recordFailure("VALIDATION", eventType, assignmentId, nodeId, occurredAt, reason);
        log.error(
                "[Phase7B NodeServiceLedger] observation rejected: reason={}, assignmentId={}, nodeId={}",
                reason, assignmentId, nodeId
        );
    }

    private void recordFailure(
            String stage,
            NodeServiceObservation.Type eventType,
            Long assignmentId,
            Long nodeId,
            LocalDateTime occurredAt,
            String reason
    ) {
        if (health != null) {
            health.recordProjectionFailure(stage, eventType, assignmentId, nodeId, occurredAt, reason);
        }
    }

    /** Phase 7E：错误类型与消息足以排查，避免把完整堆栈复制进每轮评价快照。 */
    private String failureReason(RuntimeException ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private NodeServiceEpisode.ActionType actionType(Vehicle.VehicleStatus status) {
        if (status == Vehicle.VehicleStatus.LOADING) {
            return NodeServiceEpisode.ActionType.LOAD;
        }
        if (status == Vehicle.VehicleStatus.UNLOADING) {
            return NodeServiceEpisode.ActionType.UNLOAD;
        }
        return null;
    }

    private Double processedTonnes(Assignment assignment, AssignmentNode node) {
        if (node != null) {
            Double delta = node.getWeightDeltaTonnes();
            if (delta != null && Double.isFinite(delta)) {
                return Math.abs(delta);
            }
            ShipmentItem item = node.getShipmentItem();
            return validTonnes(item == null ? null : item.getWeightTonnes());
        }
        if (assignment == null || assignment.getShipmentItems() == null) {
            return null;
        }
        // Phase 7B：普通任务没有 AssignmentNode，按未取消货物项总吨数形成一次装/卸服务工作量。
        double total = 0.0;
        boolean found = false;
        for (ShipmentItem item : assignment.getShipmentItems()) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                continue;
            }
            Double tonnes = validTonnes(item.getWeightTonnes());
            if (tonnes == null) {
                return null;
            }
            total += tonnes;
            found = true;
        }
        return found && Double.isFinite(total) ? total : null;
    }

    private Double validTonnes(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 ? value : null;
    }

    private Long idOf(Assignment value) { return value == null ? null : value.getId(); }
    private Long idOf(Vehicle value) { return value == null ? null : value.getId(); }
    private Long idOf(POI value) { return value == null ? null : value.getId(); }
    private Long idOf(AssignmentNode value) { return value == null ? null : value.getId(); }
}
