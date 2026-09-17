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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Runtime orchestration for demand-driven production batches. */
@Service
@Transactional
public class ProductionExecutionServiceImpl implements ProductionExecutionService {

    private final ProcessingStageExecutionRepository executionRepository;
    private final ProcessingExecutionFlowRepository executionFlowRepository;
    private final ProductionBatchRepository batchRepository;
    private final TransportDemandService transportDemandService;

    public ProductionExecutionServiceImpl(
            ProcessingStageExecutionRepository executionRepository,
            ProcessingExecutionFlowRepository executionFlowRepository,
            ProductionBatchRepository batchRepository,
            TransportDemandService transportDemandService
    ) {
        this.executionRepository = executionRepository;
        this.executionFlowRepository = executionFlowRepository;
        this.batchRepository = batchRepository;
        this.transportDemandService = transportDemandService;
    }

    @Override
    @EventListener
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        if (event == null || event.shipmentIds().isEmpty()) {
            return;
        }

        Set<Long> affectedExecutions = new HashSet<>();
        for (Long shipmentId : event.shipmentIds()) {
            Optional<ProcessingExecutionFlow> optional =
                    executionFlowRepository.findByShipmentId(shipmentId);
            if (optional.isEmpty()) {
                continue;
            }

            ProcessingExecutionFlow flow = optional.get();
            if (flow.getStatus() == ProcessingExecutionFlow.FlowStatus.DELIVERED) {
                affectedExecutions.add(flow.getToExecution().getId());
                continue;
            }

            Shipment shipment = flow.getShipment();
            if (shipment == null || shipment.getTotalWeight() == null
                    || shipment.getTotalWeight() <= 0 || !Double.isFinite(shipment.getTotalWeight())) {
                flow.setStatus(ProcessingExecutionFlow.FlowStatus.FAILED);
                executionFlowRepository.save(flow);
                throw new IllegalStateException("生产物料流交付重量无效: " + shipmentId);
            }

            flow.setActualWeight(shipment.getTotalWeight());
            flow.setStatus(ProcessingExecutionFlow.FlowStatus.DELIVERED);
            executionFlowRepository.save(flow);
            affectedExecutions.add(flow.getToExecution().getId());
        }

        for (Long executionId : affectedExecutions) {
            startExecutionIfInputsReady(executionId, event.deliveredAt());
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

            double actualInput = inboundFlows.stream()
                    .mapToDouble(ProcessingExecutionFlow::getActualWeight)
                    .sum();
            if (actualInput <= 0 || !Double.isFinite(actualInput)) {
                throw new IllegalStateException("工序实际输入重量无效: " + execution.getStage().getStageName());
            }

            execution.setActualInputWeight(round(actualInput));
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
        });
    }

    @Override
    public void updateProgress(LocalDateTime simNow, int minutesPerLoop) {
        if (simNow == null) {
            return;
        }
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
