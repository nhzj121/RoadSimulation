package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.ShipmentDemandSource;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.impl.TransportDemandServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransportDemandServiceSimulationTimeTest {

    @Test
    void productionDemandUsesAuthoritativeSimulationCreationTime() {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipmentItemRepository shipmentItemRepository = mock(ShipmentItemRepository.class);
        GoodsRepository goodsRepository = mock(GoodsRepository.class);
        ProcessingExecutionFlowRepository flowRepository = mock(ProcessingExecutionFlowRepository.class);
        SimulationContext simulationContext = new SimulationContext();
        simulationContext.incrementLoop();
        simulationContext.incrementLoop();
        LocalDateTime expectedTime = simulationContext.getCurrentSimTime();

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Goods goods = goods("RAW", 0.5, 0.2);
        when(goodsRepository.findBySku("RAW")).thenReturn(Optional.of(goods));
        when(flowRepository.save(any(ProcessingExecutionFlow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductionTransportLoadSplitter loadSplitter = new ProductionTransportLoadSplitter(
                mockVehicleRepository(vehicle(20.0, 20.0))
        );
        TransportDemandServiceImpl service = new TransportDemandServiceImpl(
                shipmentRepository,
                shipmentItemRepository,
                goodsRepository,
                flowRepository,
                simulationContext,
                loadSplitter
        );
        ProcessingExecutionFlow flow = flowFixture();

        Shipment shipment = service.createTransport(flow, flow.getFromExecution().getProcessingPOI(), "test");

        ArgumentCaptor<ShipmentItem> itemCaptor = ArgumentCaptor.forClass(ShipmentItem.class);
        verify(shipmentItemRepository).save(itemCaptor.capture());
        assertThat(shipment.getCreatedAt()).isEqualTo(expectedTime);
        assertThat(shipment.getDemandSource()).isEqualTo(ShipmentDemandSource.PRODUCTION);
        assertThat(itemCaptor.getValue().getCreatedTime()).isEqualTo(expectedTime);
        assertThat(flow.getStatus()).isEqualTo(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT);
    }

    @Test
    void productionDemandPersistsAllCapacitySplitItemsAndPhysicalTotals() {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipmentItemRepository shipmentItemRepository = mock(ShipmentItemRepository.class);
        GoodsRepository goodsRepository = mock(GoodsRepository.class);
        ProcessingExecutionFlowRepository flowRepository = mock(ProcessingExecutionFlowRepository.class);
        SimulationContext simulationContext = new SimulationContext();
        Goods goods = goods("RAW", 0.5, 1.0);

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(goodsRepository.findBySku("RAW")).thenReturn(Optional.of(goods));
        when(flowRepository.save(any(ProcessingExecutionFlow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransportDemandServiceImpl service = new TransportDemandServiceImpl(
                shipmentRepository,
                shipmentItemRepository,
                goodsRepository,
                flowRepository,
                simulationContext,
                new ProductionTransportLoadSplitter(mockVehicleRepository(vehicle(5.0, 8.0)))
        );

        ProcessingExecutionFlow flow = flowFixture();
        Shipment shipment = service.createTransport(
                flow, flow.getFromExecution().getProcessingPOI(), "test");

        ArgumentCaptor<ShipmentItem> items = ArgumentCaptor.forClass(ShipmentItem.class);
        verify(shipmentItemRepository, times(4)).save(items.capture());
        assertThat(items.getAllValues()).hasSize(4);
        assertThat(items.getAllValues()).allSatisfy(item -> {
            assertThat(item.getWeightTonnes()).isLessThanOrEqualTo(5.0);
            assertThat(item.getVolume()).isLessThanOrEqualTo(8.0);
            assertThat(item.getGoods()).isSameAs(goods);
        });
        assertThat(items.getAllValues().stream().mapToDouble(ShipmentItem::getWeightTonnes).sum())
                .isCloseTo(12.5, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(items.getAllValues().stream().mapToDouble(ShipmentItem::getVolume).sum())
                .isCloseTo(25.0, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(shipment.getTotalWeight()).isEqualTo(12.5);
        assertThat(shipment.getTotalVolume())
                .isCloseTo(25.0, org.assertj.core.data.Offset.offset(1.0e-9));
    }

    private org.example.roadsimulation.repository.VehicleRepository mockVehicleRepository(Vehicle... vehicles) {
        org.example.roadsimulation.repository.VehicleRepository repository =
                mock(org.example.roadsimulation.repository.VehicleRepository.class);
        when(repository.findAll()).thenReturn(List.of(vehicles));
        return repository;
    }

    private Goods goods(String sku, double unitWeight, double unitVolume) {
        Goods goods = new Goods("Goods " + sku, sku);
        goods.setWeightPerUnit(unitWeight);
        goods.setVolumePerUnit(unitVolume);
        return goods;
    }

    private Vehicle vehicle(double maxWeight, double maxVolume) {
        Vehicle vehicle = new Vehicle();
        vehicle.setMaxLoadCapacityTonnes(maxWeight);
        vehicle.setCargoVolume(maxVolume);
        return vehicle;
    }

    private ProcessingExecutionFlow flowFixture() {
        POI origin = poi(1L, "Origin");
        POI destination = poi(2L, "Destination");

        ProcessingStage sourceStage = stage(10L, "Source", origin);
        ProcessingStage targetStage = stage(11L, "Target", destination);
        ProcessingStageExecution sourceExecution = execution(20L, sourceStage, origin);
        ProcessingStageExecution targetExecution = execution(21L, targetStage, destination);

        ProductionBatch batch = new ProductionBatch();
        batch.setId(30L);
        ProductionPlanFlow planFlow = new ProductionPlanFlow();
        planFlow.setId(40L);

        ProcessingExecutionFlow flow = new ProcessingExecutionFlow();
        flow.setId(50L);
        flow.setBatch(batch);
        flow.setPlanFlow(planFlow);
        flow.setFromExecution(sourceExecution);
        flow.setToExecution(targetExecution);
        flow.setInputKey("raw-input");
        flow.setSku("RAW");
        flow.setPlannedWeight(12.5);
        flow.setActualWeight(12.5);
        return flow;
    }

    private ProcessingStage stage(Long id, String name, POI poi) {
        ProcessingStage stage = new ProcessingStage();
        stage.setId(id);
        stage.setStageName(name);
        stage.setProcessingPOI(poi);
        return stage;
    }

    private ProcessingStageExecution execution(Long id, ProcessingStage stage, POI poi) {
        ProductionPlanNode node = new ProductionPlanNode();
        node.setId(id + 100L);
        node.setStage(stage);
        node.setStageOrder(1);

        ProcessingStageExecution execution = new ProcessingStageExecution();
        execution.setId(id);
        execution.setStage(stage);
        execution.setPlanNode(node);
        execution.setStageOrder(1);
        execution.setProcessingPOI(poi);
        execution.setStatus(ProcessingStageExecution.ExecutionStatus.READY_TO_PROCESS);
        return execution;
    }

    private POI poi(Long id, String name) {
        POI poi = new POI();
        poi.setId(id);
        poi.setName(name);
        return poi;
    }
}
