package org.example.roadsimulation.optimizer.multi;

import org.example.roadsimulation.entity.AssignmentNode;

public class NodeGene {

    private Long shipmentItemId;
    private AssignmentNode.NodeActionType actionType;
    private Long poiId;

    public NodeGene(Long shipmentItemId,
                    AssignmentNode.NodeActionType actionType,
                    Long poiId) {
        this.shipmentItemId = shipmentItemId;
        this.actionType = actionType;
        this.poiId = poiId;
    }

    public Long getShipmentItemId() {
        return shipmentItemId;
    }

    public AssignmentNode.NodeActionType getActionType() {
        return actionType;
    }

    public Long getPoiId() {
        return poiId;
    }
}