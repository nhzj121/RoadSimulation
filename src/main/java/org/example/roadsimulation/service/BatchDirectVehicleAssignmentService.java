package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class BatchDirectVehicleAssignmentService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MIN_LOAD_FACTOR = 0.60;
    private static final double LOAD_FACTOR_TIE_BREAK_WEIGHT = 0.001;
    private static final double UNMATCHED_COST = 1_000_000.0;
    private static final double INFEASIBLE_COST = 10_000_000.0;
    private static final double EPS = 1e-9;

    public BatchMatchResult match(List<DirectAssignmentRequest> requests, List<Vehicle> vehicles) {
        List<DirectAssignmentRequest> cleanRequests = requests == null
                ? Collections.emptyList()
                : requests.stream()
                .filter(Objects::nonNull)
                .filter(request -> request.getShipmentItem() != null)
                .toList();

        List<Vehicle> cleanVehicles = vehicles == null
                ? Collections.emptyList()
                : vehicles.stream()
                .filter(Objects::nonNull)
                .toList();

        if (cleanRequests.isEmpty()) {
            return new BatchMatchResult(List.of(), List.of());
        }

        if (cleanVehicles.isEmpty()) {
            return new BatchMatchResult(List.of(), cleanRequests);
        }

        int requestCount = cleanRequests.size();
        int vehicleCount = cleanVehicles.size();
        int columnCount = vehicleCount + requestCount;
        int size = Math.max(requestCount, columnCount);

        double[][] cost = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                cost[row][col] = row < requestCount && col < columnCount
                        ? INFEASIBLE_COST
                        : 0.0;
            }
        }

        for (int row = 0; row < requestCount; row++) {
            DirectAssignmentRequest request = cleanRequests.get(row);
            for (int col = 0; col < vehicleCount; col++) {
                Vehicle vehicle = cleanVehicles.get(col);
                PairCost pairCost = calculatePairCost(request, vehicle);
                if (pairCost.feasible) {
                    cost[row][col] = pairCost.cost;
                }
            }
            for (int col = vehicleCount; col < columnCount; col++) {
                cost[row][col] = UNMATCHED_COST;
            }
        }

        int[] assignment = solveHungarian(cost);

        List<VehicleAssignment> matched = new ArrayList<>();
        List<DirectAssignmentRequest> unmatched = new ArrayList<>();
        Set<Long> usedVehicleIds = new LinkedHashSet<>();

        for (int row = 0; row < requestCount; row++) {
            int col = assignment[row];
            DirectAssignmentRequest request = cleanRequests.get(row);
            if (col >= 0 && col < vehicleCount && cost[row][col] < UNMATCHED_COST - EPS) {
                Vehicle vehicle = cleanVehicles.get(col);
                if (vehicle.getId() == null || usedVehicleIds.add(vehicle.getId())) {
                    matched.add(new VehicleAssignment(vehicle, request, cost[row][col]));
                    continue;
                }
            }
            unmatched.add(request);
        }

        matched.sort(Comparator.comparing(assignmentItem -> assignmentItem.getRequest().getOriginalOrder()));
        unmatched.sort(Comparator.comparing(DirectAssignmentRequest::getOriginalOrder));

        return new BatchMatchResult(matched, unmatched);
    }

    private PairCost calculatePairCost(DirectAssignmentRequest request, Vehicle vehicle) {
        ShipmentItem item = request.getShipmentItem();
        if (item == null || vehicle == null || request.getStartPOI() == null) {
            return PairCost.infeasible();
        }

        double itemWeight = safe(item.getWeight());
        double itemVolume = safe(item.getVolume());
        double maxLoad = safe(vehicle.getMaxLoadCapacity());
        double maxVolume = safe(vehicle.getCargoVolume());

        if (itemWeight <= 0.0 || itemVolume <= 0.0 || maxLoad <= 0.0 || maxVolume <= 0.0) {
            return PairCost.infeasible();
        }

        if (maxLoad + EPS < itemWeight || maxVolume + EPS < itemVolume) {
            return PairCost.infeasible();
        }

        double loadFactor = Math.max(itemWeight / maxLoad, itemVolume / maxVolume);
        if (loadFactor + EPS < MIN_LOAD_FACTOR) {
            return PairCost.infeasible();
        }

        Point vehiclePoint = pointFromVehicle(vehicle);
        Point startPoint = pointFromPoi(request.getStartPOI());
        if (vehiclePoint == null || startPoint == null) {
            return PairCost.infeasible();
        }

        double distanceKm = haversineKm(vehiclePoint.lat, vehiclePoint.lon, startPoint.lat, startPoint.lon);
        double cost = distanceKm + (1.0 - loadFactor) * LOAD_FACTOR_TIE_BREAK_WEIGHT;
        return PairCost.feasible(cost);
    }

    private int[] solveHungarian(double[][] matrix) {
        int n = matrix.length;
        double[] u = new double[n + 1];
        double[] v = new double[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            boolean[] used = new boolean[n + 1];
            for (int j = 1; j <= n; j++) {
                minv[j] = Double.POSITIVE_INFINITY;
            }

            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (used[j]) {
                        continue;
                    }
                    double cur = matrix[i0 - 1][j - 1] - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);

            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        int[] assignment = new int[n];
        for (int j = 1; j <= n; j++) {
            if (p[j] != 0) {
                assignment[p[j] - 1] = j - 1;
            }
        }
        return assignment;
    }

    private Point pointFromVehicle(Vehicle vehicle) {
        if (vehicle.getCurrentPOI() != null) {
            return pointFromPoi(vehicle.getCurrentPOI());
        }
        Double lat = toDouble(vehicle.getCurrentLatitude());
        Double lon = toDouble(vehicle.getCurrentLongitude());
        if (lat == null || lon == null) {
            return null;
        }
        return new Point(lat, lon);
    }

    private Point pointFromPoi(POI poi) {
        if (poi == null) {
            return null;
        }
        Double lat = toDouble(poi.getLatitude());
        Double lon = toDouble(poi.getLongitude());
        if (lat == null || lon == null) {
            return null;
        }
        return new Point(lat, lon);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
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

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private double safe(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? 0.0 : value;
    }

    private record Point(double lat, double lon) {
    }

    private record PairCost(boolean feasible, double cost) {
        private static PairCost feasible(double cost) {
            return new PairCost(true, cost);
        }

        private static PairCost infeasible() {
            return new PairCost(false, INFEASIBLE_COST);
        }
    }

    public static class DirectAssignmentRequest {
        private final ShipmentItem shipmentItem;
        private final POI startPOI;
        private final POI endPOI;
        private final Route route;
        private final int originalOrder;

        public DirectAssignmentRequest(
                ShipmentItem shipmentItem,
                POI startPOI,
                POI endPOI,
                Route route,
                int originalOrder
        ) {
            this.shipmentItem = shipmentItem;
            this.startPOI = startPOI;
            this.endPOI = endPOI;
            this.route = route;
            this.originalOrder = originalOrder;
        }

        public ShipmentItem getShipmentItem() {
            return shipmentItem;
        }

        public POI getStartPOI() {
            return startPOI;
        }

        public POI getEndPOI() {
            return endPOI;
        }

        public Route getRoute() {
            return route;
        }

        public int getOriginalOrder() {
            return originalOrder;
        }
    }

    public static class VehicleAssignment {
        private final Vehicle vehicle;
        private final DirectAssignmentRequest request;
        private final double cost;

        private VehicleAssignment(Vehicle vehicle, DirectAssignmentRequest request, double cost) {
            this.vehicle = vehicle;
            this.request = request;
            this.cost = cost;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        public DirectAssignmentRequest getRequest() {
            return request;
        }

        public double getCost() {
            return cost;
        }
    }

    public static class BatchMatchResult {
        private final List<VehicleAssignment> assignments;
        private final List<DirectAssignmentRequest> unmatchedRequests;

        private BatchMatchResult(
                List<VehicleAssignment> assignments,
                List<DirectAssignmentRequest> unmatchedRequests
        ) {
            this.assignments = assignments == null ? List.of() : List.copyOf(assignments);
            this.unmatchedRequests = unmatchedRequests == null ? List.of() : List.copyOf(unmatchedRequests);
        }

        public List<VehicleAssignment> getAssignments() {
            return assignments;
        }

        public List<DirectAssignmentRequest> getUnmatchedRequests() {
            return unmatchedRequests;
        }

        public Map<Vehicle, ShipmentItem> toVehicleShipmentItemMap() {
            Map<Vehicle, ShipmentItem> map = new LinkedHashMap<>();
            for (VehicleAssignment assignment : assignments) {
                map.put(assignment.getVehicle(), assignment.getRequest().getShipmentItem());
            }
            return map;
        }
    }
}
