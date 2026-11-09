package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.entity.*;
import java.math.BigDecimal;
import java.util.List;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskMapper.*;

/** Snapshots already-loaded facts. Caller supplies the complete item set; no repositories are accessed. */
public final class ProductionObservationAdapter {
    private ProductionObservationAdapter() {}
    public static ObservedFlow from(ProcessingExecutionFlow flow, List<ShipmentItem> items) {
        if (flow == null || flow.getBatch() == null || flow.getBatch().getPlan() == null
                || flow.getBatch().getChain() == null || flow.getFromExecution() == null || flow.getToExecution() == null)
            throw new IllegalArgumentException("Incomplete production-flow identity");
        items = ContractChecks.list(items, "shipment items");
        var plan = flow.getBatch().getPlan(); var shipment = flow.getShipment();
        if (shipment == null || plan.getGenerationRound() == null)
            throw new IllegalArgumentException("Automatic released flow evidence is required");
        var key = new FlowKey(new BatchKey(plan.getGenerationRound(), flow.getBatch().getChain().getId()),
                flow.getFromExecution().getStage().getId(), flow.getToExecution().getStage().getId(), flow.getInputKey());
        var readings = items.stream().map(item -> {
            if (item.getShipment() == null || !shipment.getId().equals(item.getShipment().getId()))
                throw new IllegalArgumentException("Item belongs to another shipment");
            return new ObservedItem(item.getId(), item.getSku(), item.getQty(),
                    BigDecimal.valueOf(item.getWeightTonnes()), BigDecimal.valueOf(item.getVolume()));
        }).toList();
        return new ObservedFlow(key, shipment.getId(), flow.getSku(), shipment.getOriginPOI().getId(),
                shipment.getDestPOI().getId(), BigDecimal.valueOf(shipment.getTotalWeight()),
                BigDecimal.valueOf(shipment.getTotalVolume()), readings);
    }
}
