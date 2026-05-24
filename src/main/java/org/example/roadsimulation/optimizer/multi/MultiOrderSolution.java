package org.example.roadsimulation.optimizer.multi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 多运单-多车辆启发式算法解。
 *
 * 每辆车对应一条有序装卸节点序列。
 * 未分配运单单独记录，用于尾单保留。
 */
public class MultiOrderSolution {

    private List<VehicleRouteGene> vehicleRoutes = new ArrayList<>();
    private Set<Long> unassignedShipmentItemIds = new HashSet<>();

    private double cost = Double.MAX_VALUE;
    private boolean feasible = false;

    public MultiOrderSolution() {}

    public MultiOrderSolution(MultiOrderSolution other) {
        for (VehicleRouteGene route : other.vehicleRoutes) {
            this.vehicleRoutes.add(new VehicleRouteGene(route));
        }
        this.unassignedShipmentItemIds.addAll(other.unassignedShipmentItemIds);
        this.cost = other.cost;
        this.feasible = other.feasible;
    }

    public List<VehicleRouteGene> getVehicleRoutes() {
        return vehicleRoutes;
    }

    public void setVehicleRoutes(List<VehicleRouteGene> vehicleRoutes) {
        this.vehicleRoutes = vehicleRoutes != null ? vehicleRoutes : new ArrayList<>();
    }

    public Set<Long> getUnassignedShipmentItemIds() {
        return unassignedShipmentItemIds;
    }

    public void setUnassignedShipmentItemIds(Set<Long> unassignedShipmentItemIds) {
        this.unassignedShipmentItemIds =
                unassignedShipmentItemIds != null ? unassignedShipmentItemIds : new HashSet<>();
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public void setFeasible(boolean feasible) {
        this.feasible = feasible;
    }
}