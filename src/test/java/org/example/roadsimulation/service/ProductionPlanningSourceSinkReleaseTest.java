package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanFlowRepository;
import org.example.roadsimulation.repository.ProductionPlanNodeRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.impl.ProductionPlanningServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionPlanningSourceSinkReleaseTest {

    @Test
    void releaseCreatesOnlySourceToSecondNodeTransportAndNeverExternalInbound() {
        ProcessingChainRepository chainRepository = mock(ProcessingChainRepository.class);
        ProductionPlanRepository planRepository = mock(ProductionPlanRepository.class);
        ProductionPlanNodeRepository nodeRepository = mock(ProductionPlanNodeRepository.class);
        ProductionPlanFlowRepository planFlowRepository = mock(ProductionPlanFlowRepository.class);
        ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
        ProcessingStageExecutionRepository executionRepository =
                mock(ProcessingStageExecutionRepository.class);
        ProcessingExecutionFlowRepository executionFlowRepository =
                mock(ProcessingExecutionFlowRepository.class);
        TransportDemandService transportDemandService = mock(TransportDemandService.class);
        ProductionPlanPoiSelector poiSelector = mock(ProductionPlanPoiSelector.class);
        ProductionPlanningServiceImpl service = new ProductionPlanningServiceImpl(
                chainRepository, planRepository, nodeRepository, planFlowRepository,
                batchRepository, executionRepository, executionFlowRepository,
                transportDemandService, poiSelector
        );

        ProcessingChain chain = new ProcessingChain();
        chain.setId(10L);
        chain.setChainCode("LINEAR");
        chain.setChainName("Linear chain");
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);

        ProductionPlan plan = new ProductionPlan();
        plan.setId(100L);
        plan.setPlanNo("PP-100");
        plan.setChain(chain);
        plan.setFinalDemandWeight(63.0);
        plan.setStatus(ProductionPlan.PlanStatus.CALCULATED);

        ProductionPlanNode source = node(201L, plan, stage(1L, 1, "Source"),
                ProductionPlanNode.NodeRole.SOURCE, 0.0, 72.0);
        ProductionPlanNode processing = node(202L, plan, stage(2L, 2, "Processing"),
                ProductionPlanNode.NodeRole.PROCESSING, 72.0, 63.0);
        ProductionPlanNode sink = node(203L, plan, stage(3L, 3, "Sink"),
                ProductionPlanNode.NodeRole.SINK, 63.0, 63.0);

        ProductionPlanFlow first = flow(301L, plan, source, processing, "RAW", 72.0);
        ProductionPlanFlow last = flow(302L, plan, processing, sink, "FINAL", 63.0);

        when(planRepository.updateStatusIfCurrent(
                100L, ProductionPlan.PlanStatus.CALCULATED, ProductionPlan.PlanStatus.RELEASED
        )).thenReturn(1);
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(batchRepository.existsByPlanId(100L)).thenReturn(false);
        when(nodeRepository.findByPlanIdOrderByStageOrderAsc(100L))
                .thenReturn(List.of(source, processing, sink));
        when(planFlowRepository.findByPlanId(100L)).thenReturn(List.of(first, last));
        when(batchRepository.save(any(ProductionBatch.class))).thenAnswer(invocation -> {
            ProductionBatch batch = invocation.getArgument(0);
            batch.setId(400L);
            return batch;
        });
        AtomicLong executionId = new AtomicLong(500L);
        when(executionRepository.save(any(ProcessingStageExecution.class))).thenAnswer(invocation -> {
            ProcessingStageExecution execution = invocation.getArgument(0);
            execution.setId(executionId.getAndIncrement());
            return execution;
        });
        AtomicLong flowId = new AtomicLong(600L);
        when(executionFlowRepository.save(any(ProcessingExecutionFlow.class))).thenAnswer(invocation -> {
            ProcessingExecutionFlow flow = invocation.getArgument(0);
            if (flow.getId() == null) {
                flow.setId(flowId.getAndIncrement());
            }
            return flow;
        });

        ProductionBatchResponse response = service.releasePlan(100L, "test");

        assertThat(response.plannedFinalOutputWeight()).isEqualTo(63.0);
        assertThat(response.executions()).extracting(ProductionBatchResponse.ExecutionResponse::status)
                .containsExactly("COMPLETED", "WAITING_INPUT", "WAITING_INPUT");
        assertThat(response.executions().get(0).actualInputWeight()).isZero();
        assertThat(response.executions().get(0).actualOutputWeight()).isEqualTo(72.0);
        assertThat(response.flows()).allSatisfy(flowResponse ->
                assertThat(flowResponse.fromExecutionId()).isNotNull());

        ArgumentCaptor<ProcessingExecutionFlow> initialFlow =
                ArgumentCaptor.forClass(ProcessingExecutionFlow.class);
        verify(transportDemandService, times(1))
                .createTransport(initialFlow.capture(), org.mockito.ArgumentMatchers.isNull(), any());
        assertThat(initialFlow.getValue().getFromExecution().getPlanNode().getNodeRole())
                .isEqualTo(ProductionPlanNode.NodeRole.SOURCE);
        assertThat(initialFlow.getValue().getToExecution().getPlanNode().getNodeRole())
                .isEqualTo(ProductionPlanNode.NodeRole.PROCESSING);
        assertThat(initialFlow.getValue().getActualWeight()).isEqualTo(72.0);
    }

    private ProductionPlanNode node(
            Long id,
            ProductionPlan plan,
            ProcessingStage stage,
            ProductionPlanNode.NodeRole role,
            double input,
            double output
    ) {
        ProductionPlanNode node = new ProductionPlanNode();
        node.setId(id);
        node.setPlan(plan);
        node.setStage(stage);
        node.setSelectedPOI(stage.getProcessingPOI());
        node.setStageOrder(stage.getStageOrder());
        node.setNodeRole(role);
        node.setPlannedInputWeight(input);
        node.setPlannedOutputWeight(output);
        return node;
    }

    private ProductionPlanFlow flow(
            Long id,
            ProductionPlan plan,
            ProductionPlanNode from,
            ProductionPlanNode to,
            String sku,
            double weight
    ) {
        ProductionPlanFlow flow = new ProductionPlanFlow();
        flow.setId(id);
        flow.setPlan(plan);
        // A separately loaded plan flow may contain different Long instances for the same IDs.
        flow.setFromNode(nodeReference(from.getId()));
        flow.setToNode(nodeReference(to.getId()));
        flow.setInputKey("input");
        flow.setSku(sku);
        flow.setPlannedWeight(weight);
        return flow;
    }

    private ProductionPlanNode nodeReference(Long id) {
        ProductionPlanNode reference = new ProductionPlanNode();
        reference.setId(Long.valueOf(id.toString()));
        return reference;
    }

    private ProcessingStage stage(Long id, int order, String name) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setStageName(name);
        stage.setProcessingPOI(poi(id + 100L, name + " POI"));
        return stage;
    }

    private POI poi(Long id, String name) {
        POI poi = new POI(name, BigDecimal.ZERO, BigDecimal.ZERO, POI.POIType.WAREHOUSE);
        poi.setId(id);
        return poi;
    }
}
