package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransportMetricsService {

    private static final Logger log = LoggerFactory.getLogger(TransportMetricsService.class);
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double FALLBACK_SPEED_METERS_PER_SECOND = 40_000.0 / 3600.0;
    private static final int STRICT_ROUTE_METRIC_MAX_ATTEMPTS = 3;
    private static final long STRICT_ROUTE_METRIC_RETRY_DELAY_MS = 300L;

    private final AssignmentLegRepository assignmentLegRepository;
    private final AssignmentRepository assignmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final VehicleRepository vehicleRepository;
    private final ProcessingStageRepository processingStageRepository;
    private final ProcessingChainRepository processingChainRepository;
    private final GaodeRoutePlanningQueueService routePlanningQueueService;

    public TransportMetricsService(
            AssignmentLegRepository assignmentLegRepository,
            AssignmentRepository assignmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            ShipmentRepository shipmentRepository,
            VehicleRepository vehicleRepository,
            ProcessingStageRepository processingStageRepository,
            ProcessingChainRepository processingChainRepository,
            GaodeRoutePlanningQueueService routePlanningQueueService
    ) {
        this.assignmentLegRepository = assignmentLegRepository;
        this.assignmentRepository = assignmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.shipmentRepository = shipmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.processingStageRepository = processingStageRepository;
        this.processingChainRepository = processingChainRepository;
        this.routePlanningQueueService = routePlanningQueueService;
    }

    @Transactional
    public void rebuildMetricsForAssignment(Long assignmentId) {
        rebuildMetricsForAssignment(assignmentId, true);
    }

    @Transactional
    public boolean rebuildMetricsForAssignmentStrict(Long assignmentId) {
        try {
            rebuildMetricsForAssignment(assignmentId, true);
            return true;
        } catch (RouteMetricPlanningException e) {
            log.warn("Strict route metric rebuild failed. assignmentId={}, reason={}", assignmentId, e.getMessage());
            return false;
        }
    }

    private void rebuildMetricsForAssignment(Long assignmentId, boolean allowFallback) {
        if (assignmentId == null) {
            return;
        }

        Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return;
        }

        Vehicle vehicle = assignment.getAssignedVehicle();
        Set<Long> affectedShipmentIds = assignment.getShipmentItems().stream()
                .filter(Objects::nonNull)
                .map(ShipmentItem::getShipment)
                .filter(Objects::nonNull)
                .map(Shipment::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> affectedStageIds = assignment.getShipmentItems().stream()
                .filter(Objects::nonNull)
                .map(ShipmentItem::getStage)
                .filter(Objects::nonNull)
                .map(ProcessingStage::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<AssignmentLeg> legs = buildLegs(assignment, allowFallback);
        assignmentLegRepository.deleteByAssignmentId(assignmentId);
        assignmentLegRepository.saveAll(legs);

        aggregateAssignment(assignment, legs);
        assignmentRepository.save(assignment);

        if (vehicle != null && vehicle.getId() != null) {
            aggregateVehicle(vehicle.getId());
        }

        allocateShipmentItems(assignment);

        for (Long shipmentId : affectedShipmentIds) {
            aggregateShipment(shipmentId);
        }
        for (Long stageId : affectedStageIds) {
            aggregateProcessingStage(stageId);
        }

        Set<Long> affectedChainIds = affectedStageIds.stream()
                .map(id -> processingStageRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(ProcessingStage::getProcessingChain)
                .filter(Objects::nonNull)
                .map(ProcessingChain::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Long chainId : affectedChainIds) {
            aggregateProcessingChain(chainId);
        }
    }

    private List<AssignmentLeg> buildLegs(Assignment assignment, boolean allowFallback) {
        if (assignment.getNodes() != null && !assignment.getNodes().isEmpty()) {
            return buildNodeBasedLegs(assignment, allowFallback);
        }
        return buildRouteBasedLegs(assignment, allowFallback);
    }

    private List<AssignmentLeg> buildNodeBasedLegs(Assignment assignment, boolean allowFallback) {
        List<AssignmentNode> ordered = new ArrayList<>(assignment.getNodes());
        ordered.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));

        List<AssignmentLeg> legs = new ArrayList<>();
        Vehicle vehicle = assignment.getAssignedVehicle();
        RoutePoint previous = RoutePoint.fromVehicle(vehicle);
        AssignmentNode previousNode = null;
        LinkedHashMap<Long, ShipmentItem> carriedItems = new LinkedHashMap<>();
        int sequence = 0;

        for (AssignmentNode node : ordered) {
            if (node == null || node.getPoi() == null) {
                continue;
            }

            AssignmentLeg leg = createLeg(
                    assignment,
                    vehicle,
                    previous,
                    RoutePoint.fromPOI(node.getPoi()),
                    previousNode,
                    node,
                    sequence++,
                    carriedItems,
                    allowFallback
            );
            legs.add(leg);

            applyNodeAction(carriedItems, node);
            previous = RoutePoint.fromPOI(node.getPoi());
            previousNode = node;
        }

        return legs;
    }

    private List<AssignmentLeg> buildRouteBasedLegs(Assignment assignment, boolean allowFallback) {
        List<AssignmentLeg> legs = new ArrayList<>();
        Vehicle vehicle = assignment.getAssignedVehicle();
        Route route = assignment.getRoute();
        POI origin = assignment.getOriginPOI();
        POI destination = assignment.getDestPOI();

        if (route != null) {
            if (origin == null) {
                origin = route.getStartPOI();
            }
            if (destination == null) {
                destination = route.getEndPOI();
            }
        }

        if (origin == null || destination == null) {
            return legs;
        }

        LinkedHashMap<Long, ShipmentItem> emptyItems = new LinkedHashMap<>();
        legs.add(createLeg(
                assignment,
                vehicle,
                RoutePoint.fromVehicle(vehicle),
                RoutePoint.fromPOI(origin),
                null,
                null,
                0,
                emptyItems,
                allowFallback
        ));

        LinkedHashMap<Long, ShipmentItem> loadedItems = new LinkedHashMap<>();
        for (ShipmentItem item : assignment.getShipmentItems()) {
            if (item != null && item.getId() != null) {
                loadedItems.put(item.getId(), item);
            }
        }

        legs.add(createLeg(
                assignment,
                vehicle,
                RoutePoint.fromPOI(origin),
                RoutePoint.fromPOI(destination),
                null,
                null,
                1,
                loadedItems,
                allowFallback
        ));

        return legs;
    }

    private AssignmentLeg createLeg(
            Assignment assignment,
            Vehicle vehicle,
            RoutePoint from,
            RoutePoint to,
            AssignmentNode fromNode,
            AssignmentNode toNode,
            int sequenceIndex,
            LinkedHashMap<Long, ShipmentItem> carriedItems,
            boolean allowFallback
    ) {
        RouteMetric routeMetric = calculateRouteMetric(from, to, allowFallback);

        AssignmentLeg leg = new AssignmentLeg();
        leg.setAssignment(assignment);
        leg.setVehicle(vehicle);
        leg.setFromPOI(from == null ? null : from.poi);
        leg.setToPOI(to == null ? null : to.poi);
        leg.setFromNode(fromNode);
        leg.setToNode(toNode);
        leg.setSequenceIndex(sequenceIndex);
        leg.setDistanceMeters(routeMetric.distanceMeters);
        leg.setDrivingSeconds(routeMetric.drivingSeconds);
        leg.setLoadState(carriedItems == null || carriedItems.isEmpty()
                ? AssignmentLeg.LoadState.EMPTY
                : AssignmentLeg.LoadState.LOADED);
        leg.setCarriedShipmentItemIdList(carriedItems == null
                ? List.of()
                : new ArrayList<>(carriedItems.keySet()));
        return leg;
    }

    private void applyNodeAction(LinkedHashMap<Long, ShipmentItem> carriedItems, AssignmentNode node) {
        ShipmentItem item = node.getShipmentItem();
        if (item == null || item.getId() == null || node.getActionType() == null) {
            return;
        }

        if (node.getActionType() == AssignmentNode.NodeActionType.LOAD) {
            carriedItems.put(item.getId(), item);
        } else if (node.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
            carriedItems.remove(item.getId());
        }
    }

    private RouteMetric calculateRouteMetric(RoutePoint from, RoutePoint to, boolean allowFallback) {
        if (from == null || to == null || !from.hasCoordinates() || !to.hasCoordinates()) {
            if (!allowFallback) {
                throw new RouteMetricPlanningException("Route point coordinates are missing");
            }
            return new RouteMetric(0.0, 0L);
        }

        if (from.sameCoordinates(to)) {
            return new RouteMetric(0.0, 0L);
        }

        try {
            GaodeRouteRequest request = new GaodeRouteRequest(from.toLocation(), to.toLocation());
            int maxAttempts = allowFallback ? 1 : STRICT_ROUTE_METRIC_MAX_ATTEMPTS;
            String lastMessage = "unknown error";

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                GaodeRouteResponse response = routePlanningQueueService.submitAndWait(request);
                if (response != null && response.isSuccess() && response.getData() != null) {
                    Double distance = response.getData().getTotalDistance();
                    Double duration = response.getData().getTotalDuration();
                    if (distance != null && duration != null) {
                        return new RouteMetric(Math.max(0.0, distance), Math.max(0L, Math.round(duration)));
                    }
                    lastMessage = "missing distance or duration";
                } else {
                    lastMessage = response == null ? "empty response" : response.getMessage();
                }

                if (isSimulationLifecycleRouteFailure(lastMessage)) {
                    throw new RouteMetricPlanningException(
                            "Route planning cancelled by simulation lifecycle: " + lastMessage
                    );
                }

                if (!allowFallback && attempt < maxAttempts) {
                    sleepBeforeStrictRetry(attempt);
                }
            }

            if (!allowFallback) {
                throw new RouteMetricPlanningException(
                        "Gaode route metric failed after " + maxAttempts + " attempts: " + lastMessage
                );
            }
        } catch (Exception e) {
            if (e instanceof RouteMetricPlanningException
                    && isSimulationLifecycleRouteFailure(e.getMessage())) {
                throw (RouteMetricPlanningException) e;
            }
            if (!allowFallback) {
                if (e instanceof RouteMetricPlanningException) {
                    throw (RouteMetricPlanningException) e;
                }
                throw new RouteMetricPlanningException("Gaode route metric failed: " + e.getMessage(), e);
            }
            log.warn("Gaode route metric failed, fallback to haversine. from={}, to={}, error={}",
                    from.toLocation(), to.toLocation(), e.getMessage());
        }

        double fallbackDistance = haversineMeters(from.lat, from.lon, to.lat, to.lon);
        long fallbackSeconds = Math.round(fallbackDistance / FALLBACK_SPEED_METERS_PER_SECOND);
        return new RouteMetric(fallbackDistance, fallbackSeconds);
    }

    private boolean isSimulationLifecycleRouteFailure(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("simulation pause")
                || normalized.contains("simulation reset")
                || normalized.contains("simulation stopped")
                || normalized.contains("interrupted")
                || normalized.contains("cancelled")
                || normalized.contains("stale")
                || normalized.contains("not accepting requests")
                || normalized.contains("discarded");
    }

    private void sleepBeforeStrictRetry(int attempt) {
        try {
            Thread.sleep(STRICT_ROUTE_METRIC_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RouteMetricPlanningException("Gaode route metric retry interrupted", e);
        }
    }

    private void aggregateAssignment(Assignment assignment, List<AssignmentLeg> legs) {
        double emptyDistance = 0.0;
        double loadedDistance = 0.0;
        long emptySeconds = 0L;
        long loadedSeconds = 0L;

        for (AssignmentLeg leg : legs) {
            double distance = safe(leg.getDistanceMeters());
            long seconds = safe(leg.getDrivingSeconds());
            if (AssignmentLeg.LoadState.LOADED.equals(leg.getLoadState())) {
                loadedDistance += distance;
                loadedSeconds += seconds;
            } else {
                emptyDistance += distance;
                emptySeconds += seconds;
            }
        }

        long waitingAssignmentSeconds = calculateAssignmentWaitingSeconds(assignment);
        long loadingWaitSeconds = safe(assignment.getLoadingWaitSeconds());
        long unloadingWaitSeconds = safe(assignment.getUnloadingWaitSeconds());

        assignment.setEmptyDistanceMeters(emptyDistance);
        assignment.setLoadedDistanceMeters(loadedDistance);
        assignment.setTotalDistanceMeters(emptyDistance + loadedDistance);
        assignment.setEmptyDrivingSeconds(emptySeconds);
        assignment.setLoadedDrivingSeconds(loadedSeconds);
        assignment.setTotalDrivingSeconds(emptySeconds + loadedSeconds);
        assignment.setWaitingAssignmentSeconds(waitingAssignmentSeconds);
        assignment.setLoadingWaitSeconds(loadingWaitSeconds);
        assignment.setUnloadingWaitSeconds(unloadingWaitSeconds);

        assignment.setEmptyDrivingDistance(emptyDistance);
        assignment.setTotalDrivingDistance(emptyDistance + loadedDistance);
        assignment.setEmptyDrivingTime(emptySeconds);
        assignment.setTotalDrivingTime(emptySeconds + loadedSeconds);
        assignment.setWaitingAssignmentTime(waitingAssignmentSeconds);
        assignment.setLoadingWaitTime(loadingWaitSeconds);
        assignment.setUnloadingWaitTime(unloadingWaitSeconds);
    }

    private void aggregateVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        if (vehicle == null) {
            return;
        }

        List<AssignmentLeg> legs = assignmentLegRepository.findByVehicleId(vehicleId);
        double emptyDistance = 0.0;
        double loadedDistance = 0.0;
        long emptySeconds = 0L;
        long loadedSeconds = 0L;

        for (AssignmentLeg leg : legs) {
            double distance = safe(leg.getDistanceMeters());
            long seconds = safe(leg.getDrivingSeconds());
            if (AssignmentLeg.LoadState.LOADED.equals(leg.getLoadState())) {
                loadedDistance += distance;
                loadedSeconds += seconds;
            } else {
                emptyDistance += distance;
                emptySeconds += seconds;
            }
        }

        long loadingWaitSeconds = safe(vehicle.getLoadingWaitSeconds());
        long unloadingWaitSeconds = safe(vehicle.getUnloadingWaitSeconds());
        long waitingAssignmentSeconds = safe(vehicle.getWaitingAssignmentSeconds());

        vehicle.setEmptyDistanceMeters(emptyDistance);
        vehicle.setLoadedDistanceMeters(loadedDistance);
        vehicle.setTotalDistanceMeters(emptyDistance + loadedDistance);
        vehicle.setEmptyDrivingSeconds(emptySeconds);
        vehicle.setLoadedDrivingSeconds(loadedSeconds);
        vehicle.setTotalDrivingSeconds(emptySeconds + loadedSeconds);

        vehicle.setEmptyDrivingDistance(emptyDistance);
        vehicle.setTotalDrivingDistance(emptyDistance + loadedDistance);
        vehicle.setEmptyDrivingTime(emptySeconds);
        vehicle.setTotalDrivingTime(emptySeconds + loadedSeconds);

        vehicle.setLoadingWaitTime(loadingWaitSeconds);
        vehicle.setUnloadingWaitTime(unloadingWaitSeconds);
        vehicle.setWaitingAssignmentTime(waitingAssignmentSeconds);
        vehicleRepository.save(vehicle);
    }

    private void allocateShipmentItems(Assignment assignment) {
        Map<Long, ShipmentItem> assignmentItems = assignment.getShipmentItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ShipmentItem::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        Map<Long, Double> allocatedDistance = new HashMap<>();
        Map<Long, Long> allocatedSeconds = new HashMap<>();

        List<AssignmentLeg> legs = assignmentLegRepository.findByAssignmentIdOrderBySequenceIndexAsc(assignment.getId());
        for (AssignmentLeg leg : legs) {
            if (!AssignmentLeg.LoadState.LOADED.equals(leg.getLoadState())) {
                continue;
            }

            List<Long> carriedIds = leg.getCarriedShipmentItemIdList();
            if (carriedIds.isEmpty()) {
                continue;
            }

            Map<Long, Double> weights = buildAllocationWeights(carriedIds, assignmentItems);
            double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (totalWeight <= 0.0) {
                continue;
            }

            for (Long itemId : carriedIds) {
                double ratio = weights.getOrDefault(itemId, 0.0) / totalWeight;
                allocatedDistance.merge(itemId, safe(leg.getDistanceMeters()) * ratio, Double::sum);
                allocatedSeconds.merge(itemId, Math.round(safe(leg.getDrivingSeconds()) * ratio), Long::sum);
            }
        }

        for (ShipmentItem item : assignmentItems.values()) {
            item.setAllocatedDistanceMeters(allocatedDistance.getOrDefault(item.getId(), 0.0));
            item.setAllocatedDrivingSeconds(allocatedSeconds.getOrDefault(item.getId(), 0L));
            item.setWaitingAssignmentSeconds(calculateItemWaitingSeconds(item, assignment));
            item.setLoadingWaitSeconds(item.getLoadingWaitSeconds() == null ? 0L : item.getLoadingWaitSeconds());
            item.setUnloadingWaitSeconds(item.getUnloadingWaitSeconds() == null ? 0L : item.getUnloadingWaitSeconds());
            shipmentItemRepository.save(item);
        }
    }

    private Map<Long, Double> buildAllocationWeights(List<Long> carriedIds, Map<Long, ShipmentItem> itemMap) {
        Map<Long, Double> weights = new LinkedHashMap<>();
        double totalWeight = 0.0;
        double totalVolume = 0.0;

        for (Long id : carriedIds) {
            ShipmentItem item = itemMap.get(id);
            totalWeight += item == null || item.getWeight() == null ? 0.0 : Math.max(0.0, item.getWeight());
            totalVolume += item == null || item.getVolume() == null ? 0.0 : Math.max(0.0, item.getVolume());
        }

        for (Long id : carriedIds) {
            ShipmentItem item = itemMap.get(id);
            double basis;
            if (totalWeight > 0.0) {
                basis = item == null || item.getWeight() == null ? 0.0 : Math.max(0.0, item.getWeight());
            } else if (totalVolume > 0.0) {
                basis = item == null || item.getVolume() == null ? 0.0 : Math.max(0.0, item.getVolume());
            } else {
                basis = 1.0;
            }
            weights.put(id, basis);
        }

        return weights;
    }

    private void aggregateShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElse(null);
        if (shipment == null) {
            return;
        }

        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipmentId);
        double allocatedDistance = 0.0;
        long allocatedSeconds = 0L;
        long loadingWait = 0L;
        long unloadingWait = 0L;
        long waitingAssignment = 0L;

        for (ShipmentItem item : items) {
            allocatedDistance += safe(item.getAllocatedDistanceMeters());
            allocatedSeconds += safe(item.getAllocatedDrivingSeconds());
            loadingWait += safe(item.getLoadingWaitSeconds());
            unloadingWait += safe(item.getUnloadingWaitSeconds());
            waitingAssignment += safe(item.getWaitingAssignmentSeconds());
        }

        shipment.setAllocatedDistanceMeters(allocatedDistance);
        shipment.setAllocatedDrivingSeconds(allocatedSeconds);
        shipment.setLoadedDistanceMeters(allocatedDistance);
        shipment.setLoadedDrivingSeconds(allocatedSeconds);
        shipment.setLoadingWaitSeconds(loadingWait);
        shipment.setUnloadingWaitSeconds(unloadingWait);
        shipment.setWaitingAssignmentSeconds(waitingAssignment);

        shipment.setTotalDrivingDistance(allocatedDistance);
        shipment.setTotalDrivingTime(allocatedSeconds);
        shipment.setLoadingWaitTime(loadingWait);
        shipment.setUnloadingWaitTime(unloadingWait);
        shipment.setWaitingAssignmentTime(waitingAssignment);
        shipmentRepository.save(shipment);
    }

    private void aggregateProcessingStage(Long stageId) {
        ProcessingStage stage = processingStageRepository.findById(stageId).orElse(null);
        if (stage == null) {
            return;
        }

        List<ShipmentItem> items = shipmentItemRepository.findByStageId(stageId);
        double transportDistance = 0.0;
        long transportSeconds = 0L;
        long waitingSeconds = 0L;
        long processingSeconds = 0L;

        for (ShipmentItem item : items) {
            transportDistance += safe(item.getAllocatedDistanceMeters());
            transportSeconds += safe(item.getAllocatedDrivingSeconds());
            waitingSeconds += safe(item.getWaitingAssignmentSeconds())
                    + safe(item.getLoadingWaitSeconds())
                    + safe(item.getUnloadingWaitSeconds());
            processingSeconds += calculateProcessingSeconds(stage, item);
        }

        stage.setTransportDistanceMeters(transportDistance);
        stage.setTransportDrivingSeconds(transportSeconds);
        stage.setWaitingSeconds(waitingSeconds);
        stage.setProcessingSeconds(processingSeconds);
        stage.setTotalElapsedSeconds(transportSeconds + waitingSeconds + processingSeconds);
        processingStageRepository.save(stage);
    }

    private void aggregateProcessingChain(Long chainId) {
        ProcessingChain chain = processingChainRepository.findById(chainId).orElse(null);
        if (chain == null) {
            return;
        }

        List<ProcessingStage> stages = processingStageRepository.findByProcessingChainIdOrderByStageOrderAsc(chainId);
        double transportDistance = 0.0;
        long transportSeconds = 0L;
        long waitingSeconds = 0L;
        long processingSeconds = 0L;

        for (ProcessingStage stage : stages) {
            transportDistance += safe(stage.getTransportDistanceMeters());
            transportSeconds += safe(stage.getTransportDrivingSeconds());
            waitingSeconds += safe(stage.getWaitingSeconds());
            processingSeconds += safe(stage.getProcessingSeconds());
        }

        chain.setTransportDistanceMeters(transportDistance);
        chain.setTransportDrivingSeconds(transportSeconds);
        chain.setWaitingSeconds(waitingSeconds);
        chain.setProcessingSeconds(processingSeconds);
        chain.setTotalElapsedSeconds(transportSeconds + waitingSeconds + processingSeconds);
        processingChainRepository.save(chain);
    }

    private long calculateAssignmentWaitingSeconds(Assignment assignment) {
        long total = 0L;
        for (ShipmentItem item : assignment.getShipmentItems()) {
            total += calculateItemWaitingSeconds(item, assignment);
        }
        return total;
    }

    private long calculateItemWaitingSeconds(ShipmentItem item, Assignment assignment) {
        if (item == null || item.getCreatedTime() == null || assignment == null || assignment.getCreatedTime() == null) {
            return 0L;
        }
        long seconds = Duration.between(item.getCreatedTime(), assignment.getCreatedTime()).getSeconds();
        return Math.max(0L, seconds);
    }

    private long calculateProcessingSeconds(ProcessingStage stage, ShipmentItem item) {
        if (item.getProcessingStartTime() != null && item.getProcessingEndTime() != null) {
            return Math.max(0L, Duration.between(item.getProcessingStartTime(), item.getProcessingEndTime()).getSeconds());
        }
        Integer minutes = stage.getProcessingTimeMinutes();
        return minutes == null ? 0L : Math.max(0L, minutes.longValue() * 60L);
    }

    private double haversineMeters(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0.0;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static class RouteMetricPlanningException extends RuntimeException {
        private RouteMetricPlanningException(String message) {
            super(message);
        }

        private RouteMetricPlanningException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private double safe(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? 0.0 : value;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private static class RouteMetric {
        private final double distanceMeters;
        private final long drivingSeconds;

        private RouteMetric(double distanceMeters, long drivingSeconds) {
            this.distanceMeters = distanceMeters;
            this.drivingSeconds = drivingSeconds;
        }
    }

    private static class RoutePoint {
        private final POI poi;
        private final Double lon;
        private final Double lat;

        private RoutePoint(POI poi, Double lon, Double lat) {
            this.poi = poi;
            this.lon = lon;
            this.lat = lat;
        }

        private static RoutePoint fromPOI(POI poi) {
            if (poi == null) {
                return new RoutePoint(null, null, null);
            }
            return new RoutePoint(poi, toDouble(poi.getLongitude()), toDouble(poi.getLatitude()));
        }

        private static RoutePoint fromVehicle(Vehicle vehicle) {
            if (vehicle == null) {
                return new RoutePoint(null, null, null);
            }
            if (vehicle.getCurrentPOI() != null) {
                return fromPOI(vehicle.getCurrentPOI());
            }
            return new RoutePoint(null, toDouble(vehicle.getCurrentLongitude()), toDouble(vehicle.getCurrentLatitude()));
        }

        private boolean hasCoordinates() {
            return lon != null && lat != null;
        }

        private boolean sameCoordinates(RoutePoint other) {
            if (other == null || !hasCoordinates() || !other.hasCoordinates()) {
                return false;
            }
            return Math.abs(lon - other.lon) < 0.000001 && Math.abs(lat - other.lat) < 0.000001;
        }

        private String toLocation() {
            return String.format(Locale.US, "%.6f,%.6f", lon, lat);
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }
    }
}
