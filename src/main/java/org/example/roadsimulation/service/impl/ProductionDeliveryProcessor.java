package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 将一个已经完成的运输交付事实投影到生产执行域。
 *
 * <p>每次调用使用独立事务：生产投影失败只能回滚本次生产更新，不能反向回滚
 * 已经提交的运单、任务或车辆状态。</p>
 */
@Service
public class ProductionDeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProductionDeliveryProcessor.class);

    private final ProcessingStageExecutionRepository executionRepository;
    private final ProcessingExecutionFlowRepository executionFlowRepository;
    private final ProductionBatchRepository batchRepository;

    public ProductionDeliveryProcessor(
            ProcessingStageExecutionRepository executionRepository,
            ProcessingExecutionFlowRepository executionFlowRepository,
            ProductionBatchRepository batchRepository
    ) {
        this.executionRepository = executionRepository;
        this.executionFlowRepository = executionFlowRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processShipment(Long shipmentId, LocalDateTime deliveredAt) {
        if (shipmentId == null) {
            return;
        }
        executionFlowRepository.findByShipmentId(shipmentId)
                .ifPresent(flow -> applyDelivery(flow, deliveredAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFlow(Long flowId, LocalDateTime deliveredAt) {
        if (flowId == null) {
            return;
        }
        executionFlowRepository.findById(flowId)
                .ifPresent(flow -> applyDelivery(flow, deliveredAt));
    }

    private void applyDelivery(ProcessingExecutionFlow flow, LocalDateTime deliveredAt) {
        if (deliveredAt == null) {
            throw new IllegalArgumentException("生产物流交付时间不能为空");
        }
        if (flow.getStatus() == ProcessingExecutionFlow.FlowStatus.FAILED
                || flow.getStatus() == ProcessingExecutionFlow.FlowStatus.CANCELLED) {
            return;
        }

        Shipment shipment = flow.getShipment();
        if (shipment == null || shipment.getStatus() != Shipment.ShipmentStatus.DELIVERED) {
            return;
        }

        if (flow.getStatus() != ProcessingExecutionFlow.FlowStatus.DELIVERED) {
            Double deliveredWeight = shipment.getTotalWeight();
            if (!isValidWeight(deliveredWeight)) {
                // 不可恢复的数据事实必须持久标记失败，不能通过抛异常把 FAILED 一并回滚。
                flow.setStatus(ProcessingExecutionFlow.FlowStatus.FAILED);
                executionFlowRepository.save(flow);
                log.error(
                        "Production flow marked FAILED because delivered weight is invalid: flowId={}, shipmentId={}, weight={}",
                        flow.getId(),
                        shipment.getId(),
                        deliveredWeight
                );
                return;
            }
            flow.setActualWeight(deliveredWeight);
            flow.setStatus(ProcessingExecutionFlow.FlowStatus.DELIVERED);
            executionFlowRepository.save(flow);
        }

        ProcessingStageExecution target = flow.getToExecution();
        if (target != null) {
            startExecutionIfInputsReady(target.getId(), deliveredAt);
        }
    }

    private void startExecutionIfInputsReady(Long executionId, LocalDateTime deliveredAt) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            if (execution.getStatus() != ProcessingStageExecution.ExecutionStatus.WAITING_INPUT
                    && execution.getStatus() != ProcessingStageExecution.ExecutionStatus.READY_TO_PROCESS) {
                return;
            }

            List<ProcessingExecutionFlow> inboundFlows =
                    executionFlowRepository.findByToExecutionId(executionId);
            if (inboundFlows.isEmpty()
                    || inboundFlows.stream().anyMatch(flow ->
                    flow.getStatus() != ProcessingExecutionFlow.FlowStatus.DELIVERED)) {
                return;
            }

            List<ProcessingExecutionFlow> invalidFlows = inboundFlows.stream()
                    .filter(flow -> !isValidWeight(flow.getActualWeight()))
                    .toList();
            if (!invalidFlows.isEmpty()) {
                invalidFlows.forEach(flow -> flow.setStatus(ProcessingExecutionFlow.FlowStatus.FAILED));
                executionFlowRepository.saveAll(invalidFlows);
                log.error(
                        "Production inbound flows marked FAILED because actual input weight is invalid: executionId={}, flowIds={}",
                        executionId,
                        invalidFlows.stream().map(ProcessingExecutionFlow::getId).toList()
                );
                return;
            }

            double actualInput = inboundFlows.stream()
                    .mapToDouble(ProcessingExecutionFlow::getActualWeight)
                    .sum();
            if (!isValidWeight(actualInput)) {
                throw new IllegalStateException("工序实际输入重量汇总无效: " + execution.getStage().getStageName());
            }

            double roundedInput = round(actualInput);
            execution.setActualInputWeight(roundedInput);

            if (execution.getPlanNode().getNodeRole() == ProductionPlanNode.NodeRole.SINK) {
                completeSinkDelivery(execution, roundedInput, deliveredAt);
                return;
            }

            execution.setStatus(ProcessingStageExecution.ExecutionStatus.PROCESSING);
            execution.setProgressPercent(0);
            execution.setStartedAt(deliveredAt);
            execution.getPlanNode().setStatus(ProductionPlanNode.NodeStatus.PROCESSING);

            ProductionBatch batch = execution.getBatch();
            batch.setStatus(ProductionBatch.BatchStatus.PROCESSING);
            if (batch.getStartedAt() == null) {
                batch.setStartedAt(deliveredAt);
            }
            executionRepository.save(execution);
            batchRepository.save(batch);
        });
    }

    /** 末端节点只接收最终货物，不再执行一轮虚构加工。 */
    private void completeSinkDelivery(
            ProcessingStageExecution execution,
            double deliveredWeight,
            LocalDateTime deliveredAt
    ) {
        execution.setActualOutputWeight(deliveredWeight);
        execution.setStatus(ProcessingStageExecution.ExecutionStatus.COMPLETED);
        execution.setProgressPercent(100);
        execution.setStartedAt(deliveredAt);
        execution.setCompletedAt(deliveredAt);
        execution.getPlanNode().setStatus(ProductionPlanNode.NodeStatus.COMPLETED);

        ProductionBatch batch = execution.getBatch();
        batch.setActualFinalOutputWeight(deliveredWeight);
        batch.setStatus(ProductionBatch.BatchStatus.COMPLETED);
        if (batch.getStartedAt() == null) {
            batch.setStartedAt(deliveredAt);
        }
        batch.setCompletedAt(deliveredAt);
        executionRepository.save(execution);
        batchRepository.save(batch);
    }

    private boolean isValidWeight(Double weight) {
        return weight != null && weight > 0 && Double.isFinite(weight);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
