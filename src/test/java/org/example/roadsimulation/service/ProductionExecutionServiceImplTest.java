package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.service.impl.ProductionExecutionServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionExecutionServiceImplTest {

    @Test
    void updateProgressCompletesStageAndCreatesOutboundTransportDemand() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionExecutionServiceImpl service = new ProductionExecutionServiceImpl(
                executionRepository,
                batchRepository,
                transportDemandService
        );

        ProductionBatch batch = new ProductionBatch();
        batch.setId(1L);
        batch.setStatus(ProductionBatch.BatchStatus.PROCESSING);

        ProcessingStage stage = new ProcessingStage();
        stage.setId(10L);
        stage.setStageOrder(1);
        stage.setStageName("Stage 1");
        stage.setProcessingTimeMinutes(60);
        stage.setOutputWeightRatio(0.8);

        ProductionPlanNode node = new ProductionPlanNode();
        node.setId(20L);
        node.setStage(stage);
        node.setStageOrder(1);
        node.setPlannedInputWeight(100.0);
        node.setPlannedOutputWeight(80.0);

        ProcessingStageExecution current = new ProcessingStageExecution();
        current.setId(30L);
        current.setBatch(batch);
        current.setPlanNode(node);
        current.setStage(stage);
        current.setStageOrder(1);
        current.setStatus(ProcessingStageExecution.ExecutionStatus.PROCESSING);
        current.setActualInputWeight(100.0);
        current.setStartedAt(LocalDateTime.of(2026, 1, 1, 8, 0));

        ProcessingStage nextStage = new ProcessingStage();
        nextStage.setId(11L);
        nextStage.setStageOrder(2);
        nextStage.setStageName("Stage 2");
        nextStage.setProcessingTimeMinutes(60);
        nextStage.setOutputWeightRatio(1.0);

        ProductionPlanNode nextNode = new ProductionPlanNode();
        nextNode.setId(21L);
        nextNode.setStage(nextStage);
        nextNode.setStageOrder(2);
        nextNode.setPlannedInputWeight(80.0);
        nextNode.setPlannedOutputWeight(80.0);

        ProcessingStageExecution next = new ProcessingStageExecution();
        next.setId(31L);
        next.setBatch(batch);
        next.setPlanNode(nextNode);
        next.setStage(nextStage);
        next.setStageOrder(2);
        next.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);

        when(executionRepository.findByStatus(ProcessingStageExecution.ExecutionStatus.PROCESSING))
                .thenReturn(List.of(current));
        when(executionRepository.findByBatchIdAndStageOrder(1L, 2))
                .thenReturn(Optional.of(next));

        service.updateProgress(LocalDateTime.of(2026, 1, 1, 9, 0), 30);

        assertThat(current.getStatus()).isEqualTo(ProcessingStageExecution.ExecutionStatus.COMPLETED);
        assertThat(current.getActualOutputWeight()).isEqualTo(80.0);
        assertThat(current.getProgressPercent()).isEqualTo(100);
        verify(transportDemandService).createOutboundTransport(
                current,
                next,
                "production-system"
        );
        verify(batchRepository, never()).save(any(ProductionBatch.class));
    }
}
