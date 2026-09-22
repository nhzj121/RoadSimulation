package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentDemandSource;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.ProductionTransportLoadSplitter;
import org.example.roadsimulation.service.TransportDemandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    private final SimulationContext simulationContext;
    private final ProductionTransportLoadSplitter loadSplitter;

    public TransportDemandServiceImpl(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            GoodsRepository goodsRepository,
            ProcessingExecutionFlowRepository executionFlowRepository,
            SimulationContext simulationContext,
            ProductionTransportLoadSplitter loadSplitter
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.goodsRepository = goodsRepository;
        this.executionFlowRepository = executionFlowRepository;
        this.simulationContext = simulationContext;
        this.loadSplitter = loadSplitter;
    }

    @Override
    public Shipment createTransport(
            ProcessingExecutionFlow flow,
            POI sourcePOI,
            String actor
    ) {
        validateFlow(flow);
        POI origin = flow.getFromExecution().getProcessingPOI();
        if (sourcePOI != null && !sourcePOI.getId().equals(origin.getId())) {
            throw new IllegalArgumentException("运输起点必须是上游加工链节点 POI");
        }
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
        batch.setStatus(ProductionBatch.BatchStatus.INTER_STAGE_TRANSPORT);
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
        LocalDateTime createdSimTime = simulationContext.getCurrentSimTime();

        Goods goods = goodsRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalStateException("生产运输货物缺少 Goods 主数据: " + sku));
        List<ProductionTransportLoadSplitter.LoadPart> loadParts = loadSplitter.split(goods, weight);
        double totalVolume = loadParts.stream()
                .mapToDouble(ProductionTransportLoadSplitter.LoadPart::volume)
                .sum();

        Shipment shipment = new Shipment(refNo, origin, destination, weight, totalVolume);
        shipment.setDemandSource(ShipmentDemandSource.PRODUCTION);
        shipment.setCargoType(sku);
        shipment.setStatus(Shipment.ShipmentStatus.CREATED);
        // 合并修复：生产运输需求与普通需求共享评价口径，创建时间必须来自权威仿真时钟。
        shipment.setCreatedAt(createdSimTime);
        shipment.setUpdatedBy(safeActor);
        Shipment savedShipment = shipmentRepository.save(shipment);

        for (int index = 0; index < loadParts.size(); index++) {
            ProductionTransportLoadSplitter.LoadPart part = loadParts.get(index);
            ShipmentItem item = new ShipmentItem();
            savedShipment.addItem(item);
            item.setName(flow.getToExecution().getStage().getStageName()
                    + "-" + flow.getInputKey() + "运输"
                    + (loadParts.size() == 1 ? "" : "-" + (index + 1) + "/" + loadParts.size()));
            item.setQty(part.quantity());
            item.setSku(sku);
            item.setWeightTonnes(part.weight());
            item.setVolume(part.volume());
            item.setGoods(goods);
            item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            item.setCreatedTime(createdSimTime);
            item.setUpdatedBy(safeActor);
            shipmentItemRepository.save(item);
        }
        return savedShipment;
    }

    private void validateFlow(ProcessingExecutionFlow flow) {
        if (flow == null || flow.getId() == null) {
            throw new IllegalArgumentException("生产物料流不存在");
        }
        if (flow.getBatch() == null || flow.getPlanFlow() == null || flow.getToExecution() == null) {
            throw new IllegalArgumentException("生产物料流缺少执行关联");
        }
        if (flow.getFromExecution() == null
                || flow.getFromExecution().getProcessingPOI() == null
                || flow.getFromExecution().getProcessingPOI().getId() == null) {
            throw new IllegalArgumentException("生产运输必须来自加工链上游节点");
        }
        if (flow.getSku() == null || flow.getSku().isBlank()) {
            throw new IllegalArgumentException("生产物料流缺少 SKU");
        }
    }
}
