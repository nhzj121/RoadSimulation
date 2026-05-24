package org.example.roadsimulation.optimizer.multi.insertion;

import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 节点序列的轻量级距离估算器。
 *
 * 第一版使用 Haversine 直线距离，避免在插入枚举阶段频繁调用外部路径规划服务。
 */
@Component
public class RouteSequenceCostEstimator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double EPS = 1e-6;

    /**
     * 估算车辆执行当前节点序列的总距离。
     *
     * 距离组成：
     * 1. 车辆当前位置 -> 第一个节点 POI
     * 2. 节点之间顺序距离
     */
    public double estimateTotalDistanceKm(Vehicle vehicle, List<AssignmentNode> nodes) {
        if (vehicle == null || nodes == null || nodes.isEmpty()) {
            return 0.0;
        }

        List<AssignmentNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));

        double total = 0.0;

        POI firstPoi = ordered.get(0).getPoi();
        if (firstPoi != null) {
            total += distanceFromVehicleToPoi(vehicle, firstPoi);
        }

        for (int i = 0; i < ordered.size() - 1; i++) {
            POI from = ordered.get(i).getPoi();
            POI to = ordered.get(i + 1).getPoi();
            total += distanceBetweenPois(from, to);
        }

        return total;
    }

    /**
     * 插入候选的局部评分。
     *
     * 第一版：以新增里程 / 新增货重 作为评分，越小越好。
     * 这体现“新增运输货物占比 / 新增运输里程占比”的思想：
     * 同样新增一票货，新增里程越少越优。
     */
    public double estimateInsertionScore(
            Vehicle vehicle,
            ShipmentItem item,
            List<AssignmentNode> beforeNodes,
            List<AssignmentNode> afterNodes
    ) {
        double beforeDistance = estimateTotalDistanceKm(vehicle, beforeNodes);
        double afterDistance = estimateTotalDistanceKm(vehicle, afterNodes);
        double deltaDistance = Math.max(0.0, afterDistance - beforeDistance);

        double addedWeight = item != null && item.getWeight() != null
                ? Math.max(item.getWeight(), EPS)
                : EPS;

        return deltaDistance / addedWeight;
    }

    public double distanceFromVehicleToPoi(Vehicle vehicle, POI poi) {
        if (vehicle == null || poi == null) {
            return 0.0;
        }

        if (vehicle.getCurrentPOI() != null) {
            return distanceBetweenPois(vehicle.getCurrentPOI(), poi);
        }

        if (vehicle.getCurrentLatitude() != null
                && vehicle.getCurrentLongitude() != null
                && poi.getLatitude() != null
                && poi.getLongitude() != null) {
            return haversine(
                    toDouble(vehicle.getCurrentLatitude()),
                    toDouble(vehicle.getCurrentLongitude()),
                    toDouble(poi.getLatitude()),
                    toDouble(poi.getLongitude())
            );
        }

        return 0.0;
    }

    public double distanceBetweenPois(POI from, POI to) {
        if (from == null || to == null) {
            return 0.0;
        }

        if (from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null || to.getLongitude() == null) {
            return 0.0;
        }

        return haversine(
                toDouble(from.getLatitude()),
                toDouble(from.getLongitude()),
                toDouble(to.getLatitude()),
                toDouble(to.getLongitude())
        );
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}