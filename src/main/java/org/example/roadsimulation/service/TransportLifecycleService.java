package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the backend state transitions shared by direct, ORIGINAL and HEURISTIC assignments.
 */
@Service
public class TransportLifecycleService {

    private static final Duration FRONTEND_ORDER_DRIVING_WINDOW = Duration.ofMinutes(30);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final AssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;

    public TransportLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public record LoadingCompletionResult(
            Long assignmentId,
            Long vehicleId,
            Double currentLoad,
            Double currentVolume
    ) {
    }

    @Transactional
    public Assignment startAssignmentExecution(
            Assignment assignment,
            Vehicle vehicle,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignment == null) {
            return null;
        }
        LocalDateTime now = resolveTime(simNow);
        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);

        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        if (assignment.getStartTime() == null) {
            assignment.setStartTime(now);
        }
        if (assignment.getCurrentActionIndex() == null) {
            assignment.setCurrentActionIndex(0);
        }
        assignment.setUpdatedBy(actor);
        assignment.setUpdatedTime(LocalDateTime.now());

        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || isTerminalItem(item)) {
                continue;
            }
            item.setAssignment(assignment);
            item.setStatus(ShipmentItem.ShipmentItemStatus.ASSIGNED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        if (managedVehicle != null) {
            managedVehicle.addAssignment(assignment);
            managedVehicle.transitionToStatus(
                    Vehicle.VehicleStatus.ORDER_DRIVING,
                    now,
                    FRONTEND_ORDER_DRIVING_WINDOW
            );
            managedVehicle.setCurrentLoad(0.0);
            managedVehicle.setCurrentVolumn(0.0);
            managedVehicle.setUpdatedBy(actor);
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        Assignment saved = assignmentRepository.save(assignment);
        refreshShipments(touchedShipments);
        return saved;
    }

    @Transactional
    public void markLoadingCompleted(Assignment assignment, LocalDateTime simNow, String actor) {
        if (!isActiveAssignment(assignment)) {
            return;
        }
        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || isTerminalItem(item)) {
                continue;
            }
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED) {
                item.setStatus(ShipmentItem.ShipmentItemStatus.LOADED);
                item.setUpdatedBy(actor);
                item.setUpdatedTime(LocalDateTime.now());
                shipmentItemRepository.save(item);
            }
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }
        refreshShipments(touchedShipments);
        syncVehicleRuntimeLoad(assignment, null, actor);
    }

    @Transactional
    public LoadingCompletionResult markFrontendLoadingCompleted(
            Long assignmentId,
            Long vehicleId,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignmentId == null) {
            throw new IllegalArgumentException("assignmentId is required");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("vehicleId is required");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        if (isClosedAssignment(assignment)) {
            throw new IllegalStateException("Assignment is closed: " + assignmentId);
        }
        if (hasNodes(assignment)) {
            throw new IllegalArgumentException("VRP assignments are not supported by assignment-loaded");
        }

        Vehicle managedVehicle = resolveVehicle(null, assignment);
        if (managedVehicle == null || managedVehicle.getId() == null) {
            throw new IllegalStateException("No vehicle assigned to assignment: " + assignmentId);
        }
        if (!vehicleId.equals(managedVehicle.getId())) {
            throw new IllegalArgumentException("Vehicle does not match assignment: " + vehicleId);
        }

        LocalDateTime now = resolveTime(simNow);
        String effectiveActor = actor != null ? actor : "Frontend loading completion";

        if (assignment.getStatus() == Assignment.AssignmentStatus.ASSIGNED) {
            assignment = startAssignmentExecution(assignment, managedVehicle, now, effectiveActor);
            managedVehicle = resolveVehicle(managedVehicle, assignment);
        }
        if (!isActiveAssignment(assignment)) {
            throw new IllegalStateException("Assignment is not active: " + assignment.getStatus());
        }

        Set<ShipmentItem> items = getAssignmentItems(assignment);
        if (items.isEmpty()) {
            throw new IllegalStateException("Assignment has no shipment items: " + assignmentId);
        }

        boolean shouldCompleteLoading = hasAssignedItems(items);
        if (shouldCompleteLoading) {
            markLoadingCompleted(assignment, now, effectiveActor);
            assignment.moveToNextAction(now);
            assignment.setUpdatedBy(effectiveActor);
            assignment.setUpdatedTime(LocalDateTime.now());
            assignmentRepository.save(assignment);
        } else {
            syncVehicleRuntimeLoad(assignment, managedVehicle, effectiveActor);
        }

        managedVehicle = resolveVehicle(managedVehicle, assignment);
        if (managedVehicle != null && shouldMarkTransportDriving(managedVehicle)) {
            managedVehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING, now, Duration.ofMinutes(30));
            managedVehicle.setUpdatedBy(effectiveActor);
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        return new LoadingCompletionResult(
                assignment.getId(),
                managedVehicle != null ? managedVehicle.getId() : vehicleId,
                managedVehicle != null ? safe(managedVehicle.getCurrentLoad()) : 0.0,
                managedVehicle != null ? safe(managedVehicle.getCurrentVolumn()) : 0.0
        );
    }

    @Transactional
    public void markTransportStarted(Assignment assignment, LocalDateTime simNow, String actor) {
        if (!isActiveAssignment(assignment)) {
            return;
        }
        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || isTerminalItem(item)) {
                continue;
            }
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED
                    || item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED) {
                item.setStatus(ShipmentItem.ShipmentItemStatus.IN_TRANSIT);
                item.setUpdatedBy(actor);
                item.setUpdatedTime(LocalDateTime.now());
                shipmentItemRepository.save(item);
            }
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }
        refreshShipments(touchedShipments);
        syncVehicleRuntimeLoad(assignment, null, actor);
    }

    @Transactional
    public void markCurrentNodeCompleted(
            Assignment assignment,
            Vehicle vehicle,
            LocalDateTime simNow,
            String actor
    ) {
        if (!isActiveAssignment(assignment) || !hasNodes(assignment)) {
            return;
        }

        LocalDateTime now = resolveTime(simNow);
        AssignmentNode node = resolveCurrentNode(assignment);
        if (node == null || node.isCompleted()) {
            return;
        }

        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        ShipmentItem item = node.getShipmentItem();
        if (item != null && item.getStatus() != ShipmentItem.ShipmentItemStatus.CANCELLED) {
            if (node.getActionType() == AssignmentNode.NodeActionType.LOAD) {
                item.setAssignment(assignment);
                item.setStatus(ShipmentItem.ShipmentItemStatus.LOADED);
            } else if (node.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
                item.setStatus(ShipmentItem.ShipmentItemStatus.DELIVERED);
            }
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        node.setCompleted(true);
        node.setActualArrivalTime(now);
        advanceNodeIndex(assignment);
        assignment.setUpdatedBy(actor);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        syncVehicleRuntimeLoad(assignment, vehicle, actor);
        refreshShipments(touchedShipments);
    }

    @Transactional
    public void syncVehicleRuntimeLoad(Assignment assignment, Vehicle vehicle, String actor) {
        if (assignment == null) {
            return;
        }
        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle == null) {
            return;
        }

        double[] loadAndVolume = hasNodes(assignment)
                ? calculateCompletedNodeLoad(assignment)
                : calculateLoadedItemLoad(assignment);

        managedVehicle.setCurrentLoad(loadAndVolume[0]);
        managedVehicle.setCurrentVolumn(loadAndVolume[1]);
        managedVehicle.setUpdatedBy(actor);
        managedVehicle.setUpdatedTime(LocalDateTime.now());
        vehicleRepository.save(managedVehicle);
    }

    public boolean hasPendingNodes(Assignment assignment) {
        return resolveCurrentNode(assignment) != null;
    }

    @Transactional
    public void completeDelivery(
            Assignment assignment,
            Vehicle vehicle,
            POI endPOI,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignment == null || isClosedAssignment(assignment)) {
            return;
        }
        LocalDateTime now = resolveTime(simNow);
        Set<Shipment> touchedShipments = new LinkedHashSet<>();

        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.DELIVERED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
        assignment.setEndTime(now);
        assignment.setUpdatedBy(actor);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle != null) {
            managedVehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, now, Duration.ZERO);
            if (endPOI != null) {
                managedVehicle.setCurrentPOI(endPOI);
                managedVehicle.setCurrentLongitude(endPOI.getLongitude());
                managedVehicle.setCurrentLatitude(endPOI.getLatitude());
            }
            managedVehicle.setCurrentLoad(0.0);
            managedVehicle.setCurrentVolumn(0.0);
            managedVehicle.setUpdatedBy(actor);
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        refreshShipments(touchedShipments);
    }

    @Transactional
    public void rollbackAssignmentForRetry(
            Assignment assignment,
            Vehicle vehicle,
            Collection<ShipmentItem> items,
            String reason
    ) {
        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        Collection<ShipmentItem> targetItems = items != null ? items : getAssignmentItems(assignment);
        for (ShipmentItem item : targetItems) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED
                    || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                continue;
            }
            item.setAssignment(null);
            item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            item.setUpdatedBy("Route planning rollback");
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle != null) {
            if (assignment != null) {
                managedVehicle.removeAssignment(assignment);
            }
            managedVehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, resolveTime(null), Duration.ZERO);
            managedVehicle.setCurrentLoad(0.0);
            managedVehicle.setCurrentVolumn(0.0);
            managedVehicle.setUpdatedBy("Route planning rollback");
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        if (assignment != null) {
            assignment.setAssignedVehicle(null);
            assignment.setStatus(Assignment.AssignmentStatus.FAILED);
            assignment.setUpdatedBy(reason);
            assignment.setUpdatedTime(LocalDateTime.now());
            assignmentRepository.save(assignment);
        }

        refreshShipments(touchedShipments);
    }

    @Transactional
    public void cancelAssignment(Assignment assignment, String reason, LocalDateTime simNow, String actor) {
        if (assignment == null || isClosedAssignment(assignment)) {
            return;
        }
        LocalDateTime now = resolveTime(simNow);
        Set<Shipment> touchedShipments = new LinkedHashSet<>();

        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.CANCELLED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        assignment.setStatus(Assignment.AssignmentStatus.CANCELLED);
        assignment.setEndTime(now);
        assignment.setUpdatedBy(reason != null ? reason : actor);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        Vehicle vehicle = resolveVehicle(null, assignment);
        if (vehicle != null) {
            vehicle.removeAssignment(assignment);
            vehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, now, Duration.ZERO);
            vehicle.setCurrentLoad(0.0);
            vehicle.setCurrentVolumn(0.0);
            vehicle.setUpdatedBy(actor);
            vehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(vehicle);
            assignmentRepository.save(assignment);
        }

        refreshShipments(touchedShipments);
    }

    @Transactional
    public void cancelShipment(Shipment shipment, String reason, LocalDateTime simNow, String actor) {
        if (shipment == null || shipment.getId() == null
                || shipment.getStatus() == Shipment.ShipmentStatus.DELIVERED
                || shipment.getStatus() == Shipment.ShipmentStatus.CANCELLED) {
            return;
        }

        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
        Set<Assignment> assignments = new LinkedHashSet<>();
        for (ShipmentItem item : items) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.CANCELLED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getAssignment() != null) {
                assignments.add(item.getAssignment());
            }
        }

        for (Assignment assignment : assignments) {
            cancelAssignment(assignment, reason, simNow, actor);
        }

        shipment.setStatus(Shipment.ShipmentStatus.CANCELLED);
        shipment.setUpdatedAt(LocalDateTime.now());
        shipment.setUpdatedBy(actor);
        shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment refreshShipmentStatus(Shipment shipment) {
        if (shipment == null || shipment.getId() == null) {
            return shipment;
        }

        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
        if (items.isEmpty()) {
            shipment.setStatus(Shipment.ShipmentStatus.CREATED);
            shipment.setUpdatedAt(LocalDateTime.now());
            return shipmentRepository.save(shipment);
        }

        long cancelled = items.stream()
                .filter(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED)
                .count();
        List<ShipmentItem> effectiveItems = items.stream()
                .filter(item -> item.getStatus() != ShipmentItem.ShipmentItemStatus.CANCELLED)
                .toList();

        Shipment.ShipmentStatus nextStatus;
        if (effectiveItems.isEmpty() && cancelled > 0) {
            nextStatus = Shipment.ShipmentStatus.CANCELLED;
        } else if (!effectiveItems.isEmpty()
                && effectiveItems.stream().allMatch(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED)) {
            nextStatus = Shipment.ShipmentStatus.DELIVERED;
        } else if (effectiveItems.stream().anyMatch(item ->
                item.getStatus() == ShipmentItem.ShipmentItemStatus.IN_TRANSIT
                        || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED)) {
            nextStatus = Shipment.ShipmentStatus.IN_TRANSIT;
        } else if (!effectiveItems.isEmpty()
                && effectiveItems.stream().allMatch(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED)) {
            nextStatus = Shipment.ShipmentStatus.PICKED_UP;
        } else if (effectiveItems.stream().anyMatch(item ->
                item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED
                        || item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED)) {
            nextStatus = Shipment.ShipmentStatus.PLANNED;
        } else {
            nextStatus = Shipment.ShipmentStatus.CREATED;
        }

        shipment.setStatus(nextStatus);
        shipment.setUpdatedAt(LocalDateTime.now());
        return shipmentRepository.save(shipment);
    }

    private void refreshShipments(Set<Shipment> shipments) {
        if (shipments == null) {
            return;
        }
        shipments.stream()
                .filter(Objects::nonNull)
                .forEach(this::refreshShipmentStatus);
    }

    private Set<ShipmentItem> getAssignmentItems(Assignment assignment) {
        if (assignment == null || assignment.getShipmentItems() == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(assignment.getShipmentItems());
    }

    private boolean isTerminalItem(ShipmentItem item) {
        return item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED
                || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED;
    }

    private boolean isActiveAssignment(Assignment assignment) {
        return assignment != null && assignment.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS;
    }

    private boolean hasAssignedItems(Set<ShipmentItem> items) {
        if (items == null) {
            return false;
        }
        return items.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED);
    }

    private boolean hasNodes(Assignment assignment) {
        return assignment != null && assignment.getNodes() != null && !assignment.getNodes().isEmpty();
    }

    private AssignmentNode resolveCurrentNode(Assignment assignment) {
        List<AssignmentNode> nodes = orderedNodes(assignment);
        if (nodes.isEmpty()) {
            return null;
        }

        Integer idx = assignment.getCurrentActionIndex();
        if (idx != null && idx >= 0 && idx < nodes.size()) {
            AssignmentNode node = nodes.get(idx);
            if (node != null && !node.isCompleted()) {
                return node;
            }
        }

        return nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> !node.isCompleted())
                .findFirst()
                .orElse(null);
    }

    private List<AssignmentNode> orderedNodes(Assignment assignment) {
        if (!hasNodes(assignment)) {
            return new ArrayList<>();
        }
        List<AssignmentNode> nodes = new ArrayList<>(assignment.getNodes());
        nodes.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));
        return nodes;
    }

    private void advanceNodeIndex(Assignment assignment) {
        List<AssignmentNode> nodes = orderedNodes(assignment);
        for (int i = 0; i < nodes.size(); i++) {
            AssignmentNode node = nodes.get(i);
            if (node != null && !node.isCompleted()) {
                assignment.setCurrentActionIndex(i);
                return;
            }
        }
        assignment.setCurrentActionIndex(nodes.size());
    }

    private double[] calculateCompletedNodeLoad(Assignment assignment) {
        double load = 0.0;
        double volume = 0.0;
        for (AssignmentNode node : orderedNodes(assignment)) {
            if (node == null || !node.isCompleted()) {
                continue;
            }
            load += safe(node.getWeightDelta());
            volume += safe(node.getVolumeDelta());
        }
        return new double[]{Math.max(0.0, load), Math.max(0.0, volume)};
    }

    private double[] calculateLoadedItemLoad(Assignment assignment) {
        double load = 0.0;
        double volume = 0.0;
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || item.getStatus() == null) {
                continue;
            }
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED
                    || item.getStatus() == ShipmentItem.ShipmentItemStatus.IN_TRANSIT) {
                load += safe(item.getWeight());
                volume += safe(item.getVolume());
            }
        }
        return new double[]{Math.max(0.0, load), Math.max(0.0, volume)};
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }

    private boolean shouldMarkTransportDriving(Vehicle vehicle) {
        if (vehicle == null || vehicle.getCurrentStatus() == null) {
            return true;
        }
        Vehicle.VehicleStatus status = vehicle.getCurrentStatus();
        return status == Vehicle.VehicleStatus.IDLE
                || status == Vehicle.VehicleStatus.ORDER_DRIVING
                || status == Vehicle.VehicleStatus.LOADING;
    }

    private boolean isClosedAssignment(Assignment assignment) {
        return assignment.getStatus() == Assignment.AssignmentStatus.COMPLETED
                || assignment.getStatus() == Assignment.AssignmentStatus.CANCELLED
                || assignment.getStatus() == Assignment.AssignmentStatus.FAILED;
    }

    private Vehicle resolveVehicle(Vehicle vehicle, Assignment assignment) {
        if (vehicle != null && vehicle.getId() != null) {
            return vehicleRepository.findById(vehicle.getId()).orElse(vehicle);
        }
        if (assignment != null && assignment.getAssignedVehicle() != null
                && assignment.getAssignedVehicle().getId() != null) {
            return vehicleRepository.findById(assignment.getAssignedVehicle().getId())
                    .orElse(assignment.getAssignedVehicle());
        }
        return null;
    }

    private LocalDateTime resolveTime(LocalDateTime simNow) {
        return simNow != null ? simNow : LocalDateTime.now();
    }
}
