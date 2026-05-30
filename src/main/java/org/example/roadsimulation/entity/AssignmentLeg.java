package org.example.roadsimulation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "assignment_leg",
        indexes = {
                @Index(name = "idx_assignment_leg_assignment", columnList = "assignment_id"),
                @Index(name = "idx_assignment_leg_vehicle", columnList = "vehicle_id"),
                @Index(name = "idx_assignment_leg_load_state", columnList = "load_state")
        }
)
public class AssignmentLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_poi_id")
    private POI fromPOI;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_poi_id", nullable = false)
    private POI toPOI;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id")
    private AssignmentNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id")
    private AssignmentNode toNode;

    @Column(name = "sequence_index", nullable = false)
    private Integer sequenceIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_state", nullable = false, length = 20)
    private LoadState loadState = LoadState.EMPTY;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "driving_seconds")
    private Long drivingSeconds;

    @Column(name = "carried_shipment_item_ids", columnDefinition = "TEXT")
    private String carriedShipmentItemIds;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum LoadState {
        EMPTY,
        LOADED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public POI getFromPOI() { return fromPOI; }
    public void setFromPOI(POI fromPOI) { this.fromPOI = fromPOI; }

    public POI getToPOI() { return toPOI; }
    public void setToPOI(POI toPOI) { this.toPOI = toPOI; }

    public AssignmentNode getFromNode() { return fromNode; }
    public void setFromNode(AssignmentNode fromNode) { this.fromNode = fromNode; }

    public AssignmentNode getToNode() { return toNode; }
    public void setToNode(AssignmentNode toNode) { this.toNode = toNode; }

    public Integer getSequenceIndex() { return sequenceIndex; }
    public void setSequenceIndex(Integer sequenceIndex) { this.sequenceIndex = sequenceIndex; }

    public LoadState getLoadState() { return loadState; }
    public void setLoadState(LoadState loadState) { this.loadState = loadState; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public Long getDrivingSeconds() { return drivingSeconds; }
    public void setDrivingSeconds(Long drivingSeconds) { this.drivingSeconds = drivingSeconds; }

    public String getCarriedShipmentItemIds() { return carriedShipmentItemIds; }
    public void setCarriedShipmentItemIds(String carriedShipmentItemIds) { this.carriedShipmentItemIds = carriedShipmentItemIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Long> getCarriedShipmentItemIdList() {
        List<Long> ids = new ArrayList<>();
        if (carriedShipmentItemIds == null || carriedShipmentItemIds.isBlank()) {
            return ids;
        }
        String[] parts = carriedShipmentItemIds.split(",");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy values.
            }
        }
        return ids;
    }

    public void setCarriedShipmentItemIdList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            this.carriedShipmentItemIds = "";
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        this.carriedShipmentItemIds = builder.toString();
    }
}
