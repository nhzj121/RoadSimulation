package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.TransportDemandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates one transport demand for one production material flow.
 * Vehicle assignment remains the responsibility of the existing dispatch system.
 */
@Service
@Transactional
public class TransportDemandServiceImpl implements TransportDemandService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final GoodsRepository goodsRepository;
    private final ProcessingExecutionFlowRepository executionFlowRepository;

    public TransportDemandServiceImpl(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            GoodsRepository goodsRepository,
            ProcessingExecutionFlowRepository executionFlowRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.goodsRepository = goodsRepository;
        this.executionFlowRepository = executionFlowRepository;
    }

    @Override
    public Shipment createTransport(
            ProcessingExecutionFlow flow,
            POI sourcePOI,
            String actor
    ) {
        validateFlow(flow);
        POI origin = sourcePOI != null
                ? sourcePOI
                : flow.getFromExecution().getProcessingPOI();
        ProcessingStageExecution target = flow.getToExecution();
        double weight = flow.getActualWeight() == null
                ? flow.getPlannedWeight()
                : flow.getActualWeight();

        Shipment shipment = createTransport(
                origin,
                target.getProcessingPOI(),
                flow.getSku(),
                weight,
                flow,
                actor
        );

        flow.setShipment(shipment);
        flow.setStatus(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT);
        target.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
        target.getPlanNode().setStatus(ProductionPlanNode.NodeStatus.WAITING_TRANSPORT);

        ProductionBatch batch = flow.getBatch();
        batch.setStatus(flow.getFromExecution() == null
                ? ProductionBatch.BatchStatus.INBOUND_TRANSPORT
                : ProductionBatch.BatchStatus.INTER_STAGE_TRANSPORT);
        return executionFlowRepository.save(flow).getShipment();
    }

    private Shipment createTransport(
            POI origin,
            POI destination,
            String sku,
            Double weight,
            ProcessingExecutionFlow flow,
            String actor
    ) {
        if (origin == null || destination == null || origin.getId() == null || destination.getId() == null) {
            throw new IllegalArgumentException("运输需求必须包含有效起点和终点 POI");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("运输需求必须包含货物 SKU");
        }
        if (weight == null || weight <= 0 || !Double.isFinite(weight)) {
            throw new IllegalArgumentException("运输重量必须大于 0");
        }

        String refNo = "PROD-" + flow.getBatch().getId()
                + "-" + flow.getToExecution().getStageOrder()
                + "-" + flow.getInputKey()
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        String safeActor = actor == null || actor.isBlank() ? "production-system" : actor;

        Shipment shipment = new Shipment(refNo, origin, destination, weight, 0.0);
        shipment.setCargoType(sku);
        shipment.setStatus(Shipment.ShipmentStatus.CREATED);
        shipment.setUpdatedBy(safeActor);
        Shipment savedShipment = shipmentRepository.save(shipment);

        Optional<Goods> goods = goodsRepository.findBySku(sku);
        ShipmentItem item = new ShipmentItem(
                savedShipment,
                flow.getToExecution().getStage().getStageName() + "-" + flow.getInputKey() + "运输",
                1,
                sku,
                weight,
                0.0
        );
        goods.ifPresent(item::setGoods);
        item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
        item.setUpdatedBy(safeActor);
        shipmentItemRepository.save(item);
        return savedShipment;
    }

    private void validateFlow(ProcessingExecutionFlow flow) {
        if (flow == null || flow.getId() == null) {
            throw new IllegalArgumentException("生产物料流不存在");
        }
        if (flow.getBatch() == null || flow.getPlanFlow() == null || flow.getToExecution() == null) {
            throw new IllegalArgumentException("生产物料流缺少执行关联");
        }
        if (flow.getFromExecution() == null && (flow.getPlanFlow().getSourcePOI() == null
                || flow.getPlanFlow().getSourcePOI().getId() == null)) {
            throw new IllegalArgumentException("外部原材料流缺少来源 POI");
        }
        if (flow.getSku() == null || flow.getSku().isBlank()) {
            throw new IllegalArgumentException("生产物料流缺少 SKU");
        }
    }
}
