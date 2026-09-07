package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlanNode;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.TransportDemandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates transport demands for the production domain.
 *
 * <p>This service deliberately creates only Shipment and ShipmentItem. Vehicle
 * assignment remains the responsibility of the existing dispatch system.</p>
 */
@Service
@Transactional
public class TransportDemandServiceImpl implements TransportDemandService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final GoodsRepository goodsRepository;
    private final ProcessingStageExecutionRepository executionRepository;

    public TransportDemandServiceImpl(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            GoodsRepository goodsRepository,
            ProcessingStageExecutionRepository executionRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.goodsRepository = goodsRepository;
        this.executionRepository = executionRepository;
    }

    @Override
    public Shipment createInboundTransport(
            ProcessingStageExecution execution,
            POI sourcePOI,
            String actor
    ) {
        validateExecution(execution);
        if (sourcePOI == null || sourcePOI.getId() == null) {
            throw new IllegalArgumentException("生产计划缺少原材料来源 POI");
        }

        ProductionPlanNode node = execution.getPlanNode();
        Shipment shipment = createTransport(
                sourcePOI,
                execution.getProcessingPOI(),
                node.getInputSku(),
                node.getPlannedInputWeight(),
                execution,
                actor
        );
        execution.setInboundShipment(shipment);
        execution.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
        node.setStatus(ProductionPlanNode.NodeStatus.WAITING_TRANSPORT);
        execution.getBatch().setStatus(ProductionBatch.BatchStatus.INBOUND_TRANSPORT);
        executionRepository.save(execution);
        return shipment;
    }

    @Override
    public Shipment createOutboundTransport(
            ProcessingStageExecution currentExecution,
            ProcessingStageExecution nextExecution,
            String actor
    ) {
        validateExecution(currentExecution);
        validateExecution(nextExecution);
        if (currentExecution.getActualOutputWeight() == null
                || currentExecution.getActualOutputWeight() <= 0) {
            throw new IllegalStateException("当前工序没有有效产出，无法创建运输需求");
        }

        ProductionPlanNode currentNode = currentExecution.getPlanNode();
        Shipment shipment = createTransport(
                currentExecution.getProcessingPOI(),
                nextExecution.getProcessingPOI(),
                currentNode.getOutputSku(),
                currentExecution.getActualOutputWeight(),
                nextExecution,
                actor
        );

        currentExecution.setOutboundShipment(shipment);
        nextExecution.setInboundShipment(shipment);
        nextExecution.setStatus(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
        currentNode.setStatus(ProductionPlanNode.NodeStatus.COMPLETED);
        nextExecution.getPlanNode().setStatus(ProductionPlanNode.NodeStatus.WAITING_TRANSPORT);
        currentExecution.getBatch().setStatus(ProductionBatch.BatchStatus.INTER_STAGE_TRANSPORT);

        executionRepository.save(currentExecution);
        executionRepository.save(nextExecution);
        return shipment;
    }

    private Shipment createTransport(
            POI origin,
            POI destination,
            String sku,
            Double weight,
            ProcessingStageExecution execution,
            String actor
    ) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("运输需求必须包含起点和终点 POI");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("运输需求必须包含货物 SKU");
        }
        if (weight == null || weight <= 0 || !Double.isFinite(weight)) {
            throw new IllegalArgumentException("运输重量必须大于 0");
        }

        String refNo = "PROD-" + execution.getBatch().getId()
                + "-" + execution.getStageOrder()
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
                execution.getStage().getStageName() + "运输",
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

    private void validateExecution(ProcessingStageExecution execution) {
        if (execution == null || execution.getId() == null) {
            throw new IllegalArgumentException("工序执行记录不存在");
        }
        if (execution.getBatch() == null || execution.getPlanNode() == null || execution.getStage() == null) {
            throw new IllegalArgumentException("工序执行记录缺少生产计划关联");
        }
    }
}
