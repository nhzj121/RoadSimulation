package org.example.roadsimulation.optimizer.multi.insertion;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个合法插入候选：
 * 把某个 ShipmentItem 插入某辆车当前节点序列后的结果。
 */
public class InsertionCandidate {

    private final Vehicle vehicle;
    private final ShipmentItem shipmentItem;

    /**
     * LOAD 节点插入位置。
     */
    private final int loadInsertIndex;

    /**
     * UNLOAD 节点插入位置。
     * 注意：这是在 LOAD 已经插入后的序列位置。
     */
    private final int unloadInsertIndex;

    /**
     * 插入后的完整节点序列。
     */
    private final List<AssignmentNode> nodesAfterInsertion;

    /**
     * 插入前路线距离，km。
     */
    private final double beforeDistanceKm;

    /**
     * 插入后路线距离，km。
     */
    private final double afterDistanceKm;

    /**
     * 新增里程，km。
     */
    private final double deltaDistanceKm;

    /**
     * 局部评分。默认越小越好。
     */
    private final double score;

    public InsertionCandidate(
            Vehicle vehicle,
            ShipmentItem shipmentItem,
            int loadInsertIndex,
            int unloadInsertIndex,
            List<AssignmentNode> nodesAfterInsertion,
            double beforeDistanceKm,
            double afterDistanceKm,
            double score
    ) {
        this.vehicle = vehicle;
        this.shipmentItem = shipmentItem;
        this.loadInsertIndex = loadInsertIndex;
        this.unloadInsertIndex = unloadInsertIndex;
        this.nodesAfterInsertion = nodesAfterInsertion != null
                ? new ArrayList<>(nodesAfterInsertion)
                : new ArrayList<>();
        this.beforeDistanceKm = beforeDistanceKm;
        this.afterDistanceKm = afterDistanceKm;
        this.deltaDistanceKm = Math.max(0.0, afterDistanceKm - beforeDistanceKm);
        this.score = score;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ShipmentItem getShipmentItem() {
        return shipmentItem;
    }

    public int getLoadInsertIndex() {
        return loadInsertIndex;
    }

    public int getUnloadInsertIndex() {
        return unloadInsertIndex;
    }

    public List<AssignmentNode> getNodesAfterInsertion() {
        return new ArrayList<>(nodesAfterInsertion);
    }

    public double getBeforeDistanceKm() {
        return beforeDistanceKm;
    }

    public double getAfterDistanceKm() {
        return afterDistanceKm;
    }

    public double getDeltaDistanceKm() {
        return deltaDistanceKm;
    }

    public double getScore() {
        return score;
    }
}