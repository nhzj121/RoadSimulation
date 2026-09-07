package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.event.ShipmentDeliveredEvent;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.service.ProductionExecutionService;
import org.example.roadsimulation.service.TransportDemandService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Runtime orchestration for demand-driven production batches.
 */
@Service
@Transactional
public class ProductionExecutionServiceImpl implements ProductionExecutionService {

    private final ProcessingStageExecutionRepository executionRepository;
    private final ProductionBatchRepository batchRepository;
    private final TransportDemandService transportDemandService;

    public ProductionExecutionServiceImpl(
            ProcessingStageExecutionRepository executionRepository,
            ProductionBatchRepository batchRepository,
            TransportDemandService transportDemandService
    ) {
        this.executionRepository = executionRepository;
        this.batchRepository = batchRepository;
        this.transportDemandService = transportDemandService;
    }

    @Override
    @EventListener
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        if (event == null || event.shipmentIds().isEmpty()) {
            return;
        }
        LocalDateTime deliveredAt = event.deliveredAt();
        for (Long shipmentId : event.shipmentIds()) {
            Optional<ProcessingStageExecution> optional =
                    executionRepository.findByInboundShipmentId(shipmentId);
            if (optional.isEmpty()) {
                continue;
            }

            ProcessingStageExecution execution = optional.get();
            if (execution.getStatus() != ProcessingStageExecution.ExecutionStatus.WAITING_INPUT
                    && execution.getStatus() != ProcessingStageExecution.ExecutionStatus.READY_TO_PROCESS) {
                continue;
            }

            Shipment shipment = execution.getInboundShipment();
            execution.setActualInputWeight(shipment.getTotalWeight());
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
        }
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
        return mapBatch(batch, executions);
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

        Optional<ProcessingStageExecution> nextOptional = executionRepository.findByBatchIdAndStageOrder(
                execution.getBatch().getId(),
                execution.getStageOrder() + 1
        );

        if (nextOptional.isPresent()) {
            ProcessingStageExecution nextExecution = nextOptional.get();
            transportDemandService.createOutboundTransport(
                    execution,
                    nextExecution,
                    "production-system"
            );
        } else {
            execution.getPlanNode().setStatus(ProductionPlanNode.NodeStatus.COMPLETED);
            ProductionBatch batch = execution.getBatch();
            batch.setActualFinalOutputWeight(output);
            batch.setStatus(ProductionBatch.BatchStatus.COMPLETED);
            batch.setCompletedAt(simNow);
            batchRepository.save(batch);
        }

        executionRepository.save(execution);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private ProductionBatchResponse mapBatch(
            ProductionBatch batch,
            List<ProcessingStageExecution> executions
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
                        execution.getInboundShipment() == null ? null : execution.getInboundShipment().getId(),
                        execution.getOutboundShipment() == null ? null : execution.getOutboundShipment().getId()
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
                executionResponses
        );
    }
}
