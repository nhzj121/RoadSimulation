package org.example.roadsimulation.optimizer.multi;

import java.util.ArrayList;
import java.util.List;

public class VehicleRouteGene {

    private Long vehicleId;
    private List<NodeGene> nodes = new ArrayList<>();

    public VehicleRouteGene(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public VehicleRouteGene(VehicleRouteGene other) {
        this.vehicleId = other.vehicleId;
        this.nodes = new ArrayList<>(other.nodes);
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public List<NodeGene> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeGene> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public void addNode(NodeGene node) {
        this.nodes.add(node);
    }
}