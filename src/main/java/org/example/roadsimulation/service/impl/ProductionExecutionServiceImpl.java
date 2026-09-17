package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.event.ShipmentDeliveredEvent;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.service.ProductionExecutionService;
import org.example.roadsimulation.service.TransportDemandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Runtime orchestration for demand-driven production batches. */
@Service
@Transactional
public class ProductionExecutionServiceImpl implements ProductionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ProductionExecutionServiceImpl.class);

    private final ProcessingStageExecutionRepository executionRepository;
    private final ProcessingExecutionFlowRepository executionFlowRepository;
    private final ProductionBatchRepository batchRepository;
    private final TransportDemandService transportDemandService;
    private final ProductionDeliveryProcessor deliveryProcessor;

    public ProductionExecutionServiceImpl(
            ProcessingStageExecutionRepository executionRepository,
            ProcessingExecutionFlowRepository executionFlowRepository,
            ProductionBatchRepository batchRepository,
            TransportDemandService transportDemandService,
            ProductionDeliveryProcessor deliveryProcessor
    ) {
        this.executionRepository = executionRepository;
        this.executionFlowRepository = executionFlowRepository;
        this.batchRepository = batchRepository;
        this.transportDemandService = transportDemandService;
        this.deliveryProcessor = deliveryProcessor;
    }

    @Override
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        if (event == null || event.shipmentIds().isEmpty()) {
            return;
        }

        for (Long shipmentId : event.shipmentIds()) {
            try {
                // 合并修复：实际生产投影在 ProductionDeliveryProcessor 的 REQUIRES_NEW 事务中完成。
                deliveryProcessor.processShipment(shipmentId, event.deliveredAt());
            } catch (RuntimeException productionFailure) {
                // 临时异常保留 WAITING_TRANSPORT，下一轮恢复扫描会重试；不得传播到运输主链。
                log.error(
                        "Production delivery projection failed and will be retried: shipmentId={}",
                        shipmentId,
                        productionFailure
                );
            }
        }
    }

    @Override
    public void updateProgress(LocalDateTime simNow, int minutesPerLoop) {
        if (simNow == null) {
            return;
        }
        reconcileDeliveredTransportFlows(simNow);

        List<ProcessingStageExecution> executions = executionRepository.findByStatus(
                ProcessingStageExecution.ExecutionStatus.PROCESSING
        );
        for (ProcessingStageExecution execution : executions) {
            if (execution.getStartedAt() == null) {
                continue;
            }

            ProcessingStage stage = execution.getStage();
            int processingMinutes = stage.getProcessingTimeMinutes() == null
                    ? 0
                    : stage.getProcessingTimeMinutes();
            long elapsedMinutes = Duration.between(execution.getStartedAt(), simNow).toMinutes();
            int progress = processingMinutes <= 0
                    ? 100
                    : Math.min(100, (int) (elapsedMinutes * 100 / processingMinutes));
            execution.setProgressPercent(progress);

            if (progress >= 100) {
                completeExecution(execution, simNow);
            } else {
                executionRepository.save(execution);
            }
        }
    }

    private void reconcileDeliveredTransportFlows(LocalDateTime simNow) {
        List<ProcessingExecutionFlow> waitingFlows = executionFlowRepository.findByStatus(
                ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT
        );
        for (ProcessingExecutionFlow flow : waitingFlows) {
            Shipment shipment = flow.getShipment();
            if (shipment == null || shipment.getStatus() != Shipment.ShipmentStatus.DELIVERED) {
                continue;
            }
            try {
                // 恢复路径使用当前权威仿真时间启动下游工序，不使用系统墙钟时间。
                deliveryProcessor.processFlow(flow.getId(), simNow);
            } catch (RuntimeException productionFailure) {
                // 普通临时错误保持 WAITING_TRANSPORT，避免阻塞同一轮其它生产流与运输主循环。
                log.error(
                        "Production delivery recovery failed and will be retried: flowId={}, shipmentId={}",
                        flow.getId(),
                        shipment.getId(),
                        productionFailure
                );
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionBatchResponse getBatch(Long batchId) {
        ProductionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("生产批次不存在: " + batchId));
        List<ProcessingStageExecution> executions =
                executionRepository.findByBatchIdOrderByStageOrderAsc(batch.getId());
        List<ProcessingExecutionFlow> flows = executionFlowRepository.findByBatchId(batch.getId());
        return mapBatch(batch, executions, flows);
    }

    private void completeExecution(ProcessingStageExecution execution, LocalDateTime simNow) {
        double input = execution.getActualInputWeight() == null
                ? execution.getPlanNode().getPlannedInputWeight()
                : execution.getActualInputWeight();
        double ratio = execution.getStage().getOutputWeightRatio() == null
                ? 1.0
                : execution.getStage().getOutputWeightRatio();
        double output = round(input * ratio);

        execution.setActualOutputWeight(output);
        execution.setProgressPercent(100);
        execution.setStatus(ProcessingStageExecution.ExecutionStatus.COMPLETED);
        execution.setCompletedAt(simNow);
        execution.getPlanNode().setStatus(ProductionPlanNode.NodeStatus.COMPLETED);

        List<ProcessingExecutionFlow> outgoingFlows =
                executionFlowRepository.findByFromExecutionId(execution.getId());
        if (outgoingFlows.isEmpty()) {
            ProductionBatch batch = execution.getBatch();
            batch.setActualFinalOutputWeight(output);
            batch.setStatus(ProductionBatch.BatchStatus.COMPLETED);
            batch.setCompletedAt(simNow);
            batchRepository.save(batch);
        } else {
            for (ProcessingExecutionFlow flow : outgoingFlows) {
                flow.setActualWeight(output);
                executionFlowRepository.save(flow);
                transportDemandService.createTransport(flow, null, "production-system");
            }
        }
        executionRepository.save(execution);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private ProductionBatchResponse mapBatch(
            ProductionBatch batch,
            List<ProcessingStageExecution> executions,
            List<ProcessingExecutionFlow> flows
    ) {
        List<ProductionBatchResponse.ExecutionResponse> executionResponses = executions.stream()
                .map(execution -> new ProductionBatchResponse.ExecutionResponse(
                        execution.getId(),
                        execution.getPlanNode().getId(),
                        execution.getStage().getId(),
                        execution.getStageOrder(),
                        execution.getStage().getStageName(),
                        execution.getProcessingPOI().getId(),
                        execution.getProcessingPOI().getName(),
                        execution.getStatus().name(),
                        execution.getActualInputWeight(),
                        execution.getActualOutputWeight(),
                        execution.getProgressPercent(),
                        execution.getStartedAt(),
                        execution.getCompletedAt(),
                        firstInboundShipmentId(flows, execution.getId()),
                        firstOutboundShipmentId(flows, execution.getId())
                ))
                .toList();

        List<ProductionBatchResponse.FlowResponse> flowResponses = flows.stream()
                .map(flow -> new ProductionBatchResponse.FlowResponse(
                        flow.getId(),
                        flow.getPlanFlow().getId(),
                        flow.getFromExecution() == null ? null : flow.getFromExecution().getId(),
                        flow.getToExecution().getId(),
                        flow.getInputKey(),
                        flow.getSku(),
                        flow.getPlannedWeight(),
                        flow.getActualWeight(),
                        flow.getShipment() == null ? null : flow.getShipment().getId(),
                        flow.getStatus().name()
                ))
                .toList();

        return new ProductionBatchResponse(
                batch.getId(),
                batch.getBatchNo(),
                batch.getPlan().getId(),
                batch.getPlan().getPlanNo(),
                batch.getChain().getId(),
                batch.getChain().getChainCode(),
                batch.getStatus().name(),
                batch.getPlannedFinalOutputWeight(),
                batch.getActualFinalOutputWeight(),
                batch.getStartedAt(),
                batch.getCompletedAt(),
                executionResponses,
                flowResponses
        );
    }

    private Long firstInboundShipmentId(List<ProcessingExecutionFlow> flows, Long executionId) {
        return flows.stream()
                .filter(flow -> executionId.equals(flow.getToExecution().getId()))
                .map(flow -> flow.getShipment() == null ? null : flow.getShipment().getId())
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    private Long firstOutboundShipmentId(List<ProcessingExecutionFlow> flows, Long executionId) {
        return flows.stream()
                .filter(flow -> flow.getFromExecution() != null
                        && executionId.equals(flow.getFromExecution().getId()))
                .map(flow -> flow.getShipment() == null ? null : flow.getShipment().getId())
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }
}
