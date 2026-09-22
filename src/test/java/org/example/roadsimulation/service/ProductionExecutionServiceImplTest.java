package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.event.ShipmentDeliveredEvent;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.service.impl.ProductionExecutionServiceImpl;
import org.example.roadsimulation.service.impl.ProductionDeliveryProcessor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionExecutionServiceImplTest {

    @Test
    void listenerFailureDoesNotEscapeIntoTransportCompletion() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionDeliveryProcessor deliveryProcessor = mock(ProductionDeliveryProcessor.class);
        ProductionExecutionServiceImpl service = new ProductionExecutionServiceImpl(
                executionRepository,
                executionFlowRepository,
                batchRepository,
                transportDemandService,
                deliveryProcessor
        );
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 1, 1, 8, 0);
        doThrow(new IllegalStateException("temporary production failure"))
                .when(deliveryProcessor).processShipment(90L, deliveredAt);

        assertThatCode(() -> service.onShipmentDelivered(
                new ShipmentDeliveredEvent(List.of(90L), deliveredAt)
        )).doesNotThrowAnyException();
    }

    @Test
    void nextTickRecoversDeliveredShipmentStillWaitingForProduction() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionDeliveryProcessor deliveryProcessor = mock(ProductionDeliveryProcessor.class);
        ProductionExecutionServiceImpl service = new ProductionExecutionServiceImpl(
                executionRepository,
                executionFlowRepository,
                batchRepository,
                transportDemandService,
                deliveryProcessor
        );
        ProcessingExecutionFlow flow = new ProcessingExecutionFlow();
        flow.setId(41L);
        flow.setStatus(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT);
        Shipment shipment = new Shipment();
        shipment.setId(90L);
        shipment.setStatus(Shipment.ShipmentStatus.DELIVERED);
        flow.setShipment(shipment);
        when(executionFlowRepository.findByStatus(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT))
                .thenReturn(List.of(flow));
        when(executionRepository.findByStatus(ProcessingStageExecution.ExecutionStatus.PROCESSING))
                .thenReturn(List.of());
        LocalDateTime simNow = LocalDateTime.of(2026, 1, 1, 9, 0);

        service.updateProgress(simNow, 30);

        verify(deliveryProcessor).processFlow(41L, simNow);
    }

    @Test
    void invalidDeliveredWeightMarksFlowFailedWithoutThrowing() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        ProductionDeliveryProcessor deliveryProcessor = new ProductionDeliveryProcessor(
                executionRepository,
                executionFlowRepository,
                batchRepository
        );
        ProcessingExecutionFlow flow = new ProcessingExecutionFlow();
        flow.setId(41L);
        flow.setStatus(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT);
        Shipment shipment = new Shipment();
        shipment.setId(90L);
        shipment.setStatus(Shipment.ShipmentStatus.DELIVERED);
        shipment.setTotalWeight(0.0);
        flow.setShipment(shipment);
        when(executionFlowRepository.findById(41L)).thenReturn(Optional.of(flow));

        assertThatCode(() -> deliveryProcessor.processFlow(
                41L,
                LocalDateTime.of(2026, 1, 1, 9, 0)
        )).doesNotThrowAnyException();

        assertThat(flow.getStatus()).isEqualTo(ProcessingExecutionFlow.FlowStatus.FAILED);
        verify(executionFlowRepository).save(flow);
    }

    @Test
    void updateProgressCompletesStageAndCreatesOutboundTransportDemand() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionDeliveryProcessor deliveryProcessor = new ProductionDeliveryProcessor(
                executionRepository,
                executionFlowRepository,
                batchRepository
        );
        ProductionExecutionServiceImpl service = new ProductionExecutionServiceImpl(
                executionRepository,
                executionFlowRepository,
                batchRepository,
                transportDemandService,
                deliveryProcessor
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

        ProductionPlanFlow planFlow = new ProductionPlanFlow();
        planFlow.setId(40L);
        ProcessingExecutionFlow flow = new ProcessingExecutionFlow();
        flow.setId(41L);
        flow.setBatch(batch);
        flow.setPlanFlow(planFlow);
        flow.setFromExecution(current);
        flow.setToExecution(next);
        flow.setInputKey("input");
        flow.setSku("SEMI");
        flow.setPlannedWeight(80.0);
        flow.setActualWeight(80.0);
        flow.setStatus(ProcessingExecutionFlow.FlowStatus.PLANNED);

        when(executionRepository.findByStatus(ProcessingStageExecution.ExecutionStatus.PROCESSING))
                .thenReturn(List.of(current));
        when(executionFlowRepository.findByFromExecutionId(30L)).thenReturn(List.of(flow));

        service.updateProgress(LocalDateTime.of(2026, 1, 1, 9, 0), 30);

        assertThat(current.getStatus()).isEqualTo(ProcessingStageExecution.ExecutionStatus.COMPLETED);
        assertThat(current.getActualOutputWeight()).isEqualTo(80.0);
        assertThat(current.getProgressPercent()).isEqualTo(100);
        verify(transportDemandService).createTransport(flow, null, "production-system");
        verify(batchRepository, never()).save(any(ProductionBatch.class));
    }

    @Test
    void mergeExecutionStartsOnlyAfterAllInputsAreDelivered() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionDeliveryProcessor deliveryProcessor = new ProductionDeliveryProcessor(
                executionRepository,
                executionFlowRepository,
                batchRepository
        );
        ProductionExecutionServiceImpl service = new ProductionExecutionServiceImpl(
                executionRepository,
                executionFlowRepository,
                batchRepository,
                transportDemandService,
                deliveryProcessor
        );

        ProductionBatch batch = new ProductionBatch();
        batch.setId(1L);
        batch.setStatus(ProductionBatch.BatchStatus.INTER_STAGE_TRANSPORT);
        ProcessingStageExecution merge = execution(30L, batch, 3);
        ProcessingExecutionFlow steel = inboundFlow(
                40L, batch, merge, "steel", 80.0, 90L
        );
        ProcessingExecutionFlow wood = inboundFlow(
                41L, batch, merge, "wood", 20.0, 91L
        );

        when(executionFlowRepository.findByShipmentId(90L)).thenReturn(Optional.of(steel));
        when(executionFlowRepository.findByShipmentId(91L)).thenReturn(Optional.of(wood));
        when(executionFlowRepository.findByToExecutionId(30L)).thenReturn(List.of(steel, wood));
        when(executionRepository.findById(30L)).thenReturn(Optional.of(merge));

        service.onShipmentDelivered(new ShipmentDeliveredEvent(
                List.of(90L),
                LocalDateTime.of(2026, 1, 1, 8, 0)
        ));
        assertThat(merge.getStatus()).isEqualTo(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);

        service.onShipmentDelivered(new ShipmentDeliveredEvent(
                List.of(91L),
                LocalDateTime.of(2026, 1, 1, 9, 0)
        ));
        assertThat(merge.getStatus()).isEqualTo(ProcessingStageExecution.ExecutionStatus.PROCESSING);
        assertThat(merge.getActualInputWeight()).isEqualTo(100.0);
    }

    @Test
    void finalDeliveryCompletesSinkAndBatchWithoutRunningSinkProcessing() {
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        ProductionDeliveryProcessor deliveryProcessor = new ProductionDeliveryProcessor(
                executionRepository,
                executionFlowRepository,
                batchRepository
        );

        ProductionBatch batch = new ProductionBatch();
        batch.setId(1L);
        batch.setStatus(ProductionBatch.BatchStatus.INTER_STAGE_TRANSPORT);
        ProcessingStageExecution sink = execution(30L, batch, 4);
        sink.getPlanNode().setNodeRole(ProductionPlanNode.NodeRole.SINK);
        ProcessingExecutionFlow finalFlow = inboundFlow(
                40L, batch, sink, "final", 63.0, 90L
        );
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 1, 1, 10, 0);

        when(executionFlowRepository.findByShipmentId(90L)).thenReturn(Optional.of(finalFlow));
        when(executionFlowRepository.findByToExecutionId(30L)).thenReturn(List.of(finalFlow));
        when(executionRepository.findById(30L)).thenReturn(Optional.of(sink));

        deliveryProcessor.processShipment(90L, deliveredAt);

        assertThat(sink.getStatus()).isEqualTo(ProcessingStageExecution.ExecutionStatus.COMPLETED);
        assertThat(sink.getActualInputWeight()).isEqualTo(63.0);
        assertThat(sink.getActualOutputWeight()).isEqualTo(63.0);
        assertThat(sink.getStartedAt()).isEqualTo(deliveredAt);
        assertThat(sink.getCompletedAt()).isEqualTo(deliveredAt);
        assertThat(batch.getStatus()).isEqualTo(ProductionBatch.BatchStatus.COMPLETED);
        assertThat(batch.getActualFinalOutputWeight()).isEqualTo(63.0);
        assertThat(batch.getCompletedAt()).isEqualTo(deliveredAt);
        verify(executionRepository).save(sink);
        verify(batchRepository).save(batch);
    }

    private ProcessingStageExecution execution(Long id, ProductionBatch batch, int order) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(10L);
        stage.setStageOrder(order);
        stage.setStageName("Merge");
        stage.setProcessingTimeMinutes(60);
        stage.setOutputWeightRatio(1.0);
        stage.setProcessingPOI(new POI("Factory", BigDecimal.ZERO, BigDecimal.ZERO, POI.POIType.WAREHOUSE));

        ProductionPlanNode node = new ProductionPlanNode();
        node.setId(20L);
        node.setStage(stage);
        node.setStageOrder(order);
        node.setPlannedInputWeight(100.0);
        node.setPlannedOutputWeight(100.0);

        ProcessingStageExecution execution = new ProcessingStageExecution();
        execution.setId(id);
        execution.setBatch(batch);
        execution.setPlanNode(node);
        execution.setStage(stage);
        execution.setStageOrder(order);
        execution.setProcessingPOI(stage.getProcessingPOI());
        execution.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
        return execution;
    }

    private ProcessingExecutionFlow inboundFlow(
            Long id,
            ProductionBatch batch,
            ProcessingStageExecution target,
            String inputKey,
            Double weight,
            Long shipmentId
    ) {
        Shipment shipment = new Shipment();
        shipment.setId(shipmentId);
        shipment.setTotalWeight(weight);
        shipment.setStatus(Shipment.ShipmentStatus.DELIVERED);

        ProductionPlanFlow planFlow = new ProductionPlanFlow();
        planFlow.setId(100L + id);
        ProcessingExecutionFlow flow = new ProcessingExecutionFlow();
        flow.setId(id);
        flow.setBatch(batch);
        flow.setPlanFlow(planFlow);
        flow.setToExecution(target);
        flow.setInputKey(inputKey);
        flow.setSku(inputKey.toUpperCase());
        flow.setPlannedWeight(weight);
        flow.setShipment(shipment);
        flow.setStatus(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT);
        return flow;
    }
}
