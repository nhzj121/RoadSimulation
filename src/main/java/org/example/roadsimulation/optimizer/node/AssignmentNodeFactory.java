package org.example.roadsimulation.optimizer.node;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.springframework.stereotype.Component;

@Component
public class AssignmentNodeFactory {

    public AssignmentNode createLoadNode(
            Assignment assignment,
            int sequenceIndex,
            ShipmentItem item
    ) {
        Shipment shipment = requireShipment(item);
        POI origin = shipment.getOriginPOI();

        return new AssignmentNode(
                assignment,
                sequenceIndex,
                origin,
                AssignmentNode.NodeActionType.LOAD,
                item,
                safe(item.getWeight()),
                safe(item.getVolume())
        );
    }

    public AssignmentNode createUnloadNode(
            Assignment assignment,
            int sequenceIndex,
            ShipmentItem item
    ) {
        Shipment shipment = requireShipment(item);
        POI dest = shipment.getDestPOI();

        return new AssignmentNode(
                assignment,
                sequenceIndex,
                dest,
                AssignmentNode.NodeActionType.UNLOAD,
                item,
                -safe(item.getWeight()),
                -safe(item.getVolume())
        );
    }

    private Shipment requireShipment(ShipmentItem item) {
        if (item == null) {
            throw new IllegalArgumentException("ShipmentItem 不能为空");
        }
        if (item.getShipment() == null) {
            throw new IllegalArgumentException("ShipmentItem 缺少 Shipment");
        }
        return item.getShipment();
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }
}