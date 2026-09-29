package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Row-locked driver ownership shared by normal dispatch and replacement handoff. */
@Service
@Transactional
public class DriverResourceService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final DriverRepository drivers;
    private final AssignmentRepository assignments;
    private final AssignmentDriverHistoryRepository history;
    private final DriverPreferenceScorer scorer;
    private static final List<Assignment.AssignmentStatus> OPEN = List.of(
            Assignment.AssignmentStatus.ASSIGNED, Assignment.AssignmentStatus.IN_PROGRESS,
            Assignment.AssignmentStatus.WAITING);

    public DriverResourceService(DriverRepository drivers, AssignmentRepository assignments,
            AssignmentDriverHistoryRepository history, DriverPreferenceScorer scorer) {
        this.drivers=drivers; this.assignments=assignments; this.history=history; this.scorer=scorer;
    }

    public Driver claimForAssignment(Assignment assignment, Vehicle vehicle, LocalDateTime at, String actor) {
        if (assignment==null || vehicle==null || vehicle.getId()==null)
            throw new IllegalStateException("Assignment requires a vehicle and a driver");
        Driver preset=assignment.getAssignedDriver();
        List<Driver> ordered=preset==null?ranked(vehicle,assignment):List.of(preset);
        for (Driver listed:ordered) {
            Driver driver=locked(listed.getId());
            if (driver==null || !belongs(driver,vehicle) || driver.getReservedReplacementEventId()!=null) continue;
            boolean sameOwner=driver.getCurrentStatus()==Driver.DriverStatus.ASSIGNED
                    && Objects.equals(assignment.getAssignedDriver()==null?null:assignment.getAssignedDriver().getId(),driver.getId())
                    && assignments.findDriverCurrentAssignments(driver.getId(),OPEN).stream()
                    .anyMatch(a->Objects.equals(a.getId(),assignment.getId()))
                    && assignments.findDriverCurrentAssignments(driver.getId(),OPEN).stream()
                    .allMatch(a->Objects.equals(a.getId(),assignment.getId()));
            if (!sameOwner && !idle(driver)) continue;
            var previous=driver.getCurrentStatus();
            driver.setCurrentStatus(Driver.DriverStatus.ASSIGNED);
            driver.addAssignment(assignment); driver.setUpdatedBy(actor); driver.setUpdatedTime(at);
            drivers.save(driver);
            if (!sameOwner) record(assignment,driver,AssignmentDriverHistory.Action.BIND,previous,
                    Driver.DriverStatus.ASSIGNED,at,actor);
            return driver;
        }
        throw new IllegalStateException("Vehicle "+vehicle.getId()+" has no available associated driver");
    }

    public Optional<Driver> reserveReplacement(Vehicle vehicle, Assignment assignment, Long eventId, LocalDateTime at) {
        if(eventId==null) throw new IllegalArgumentException("Replacement event must be persisted");
        for(Driver listed:ranked(vehicle,assignment)) {
            Driver driver=locked(listed.getId());
            if(driver==null || !belongs(driver,vehicle) || !idle(driver)) continue;
            driver.setCurrentStatus(Driver.DriverStatus.ASSIGNED);
            driver.setReservedReplacementEventId(eventId);
            driver.setUpdatedBy("Replacement reservation"); driver.setUpdatedTime(at);
            drivers.save(driver); return Optional.of(driver);
        }
        return Optional.empty();
    }

    public boolean reservationValid(Long driverId, Long eventId, Vehicle vehicle) {
        if(driverId==null) return false;
        Driver driver=locked(driverId);
        return driver!=null && driver.getCurrentStatus()==Driver.DriverStatus.ASSIGNED
                && Objects.equals(driver.getReservedReplacementEventId(),eventId) && belongs(driver,vehicle)
                && assignments.findDriverCurrentAssignments(driver.getId(),OPEN).isEmpty();
    }

    public void handoff(Assignment assignment, Vehicle originalVehicle, Vehicle replacementVehicle,
            Long originalId, Long replacementId, Long eventId, LocalDateTime at) {
        if(originalId==null || replacementId==null || originalId.equals(replacementId))
            throw new IllegalStateException("Invalid replacement driver ownership");
        Map<Long,Driver> locked=new HashMap<>();
        for(Long id:java.util.stream.Stream.of(originalId,replacementId).sorted().toList())
            locked.put(id,java.util.Optional.ofNullable(locked(id)).orElseThrow(()->new IllegalStateException("Driver missing: "+id)));
        Driver old=locked.get(originalId), next=locked.get(replacementId);
        if(assignment.getAssignedDriver()==null || !Objects.equals(assignment.getAssignedDriver().getId(),originalId)
                || old.getCurrentStatus()!=Driver.DriverStatus.ASSIGNED || !belongs(old,originalVehicle)
                || !reservationValid(replacementId,eventId,replacementVehicle))
            throw new IllegalStateException("Replacement driver reservation or original ownership changed");
        old.removeAssignment(assignment); next.addAssignment(assignment);
        next.setReservedReplacementEventId(null);
        // Original driver remains occupied until this successful handoff, not at failure onset.
        if(assignments.findDriverCurrentAssignments(old.getId(),OPEN).stream()
                .noneMatch(a->!Objects.equals(a.getId(),assignment.getId()))) old.setCurrentStatus(Driver.DriverStatus.IDLE);
        old.setUpdatedTime(at); next.setUpdatedTime(at);
        drivers.save(old); drivers.save(next);
        record(assignment,old,AssignmentDriverHistory.Action.RELEASE,Driver.DriverStatus.ASSIGNED,old.getCurrentStatus(),at,"Replacement handoff");
        record(assignment,next,AssignmentDriverHistory.Action.BIND,Driver.DriverStatus.ASSIGNED,Driver.DriverStatus.ASSIGNED,at,"Replacement handoff");
    }

    public void cancelReservation(Long driverId, Long eventId, LocalDateTime at) {
        if(driverId==null) return;
        Driver driver=locked(driverId);
        if(driver!=null && Objects.equals(driver.getReservedReplacementEventId(),eventId)) {
            driver.setReservedReplacementEventId(null);
            if(assignments.findDriverCurrentAssignments(driverId,OPEN).isEmpty()) driver.setCurrentStatus(Driver.DriverStatus.IDLE);
            driver.setUpdatedTime(at); drivers.save(driver);
        }
    }

    public void requireExecutionDriver(Assignment assignment) {
        Driver driver=assignment.getAssignedDriver();
        if(driver==null || driver.getId()==null || driver.getCurrentStatus()!=Driver.DriverStatus.ASSIGNED
                || driver.getReservedReplacementEventId()!=null || !belongs(driver,assignment.getAssignedVehicle()))
            throw new IllegalStateException("Active task has no valid execution driver: "+assignment.getId());
    }

    private boolean idle(Driver driver) {
        return driver.getCurrentStatus()==Driver.DriverStatus.IDLE && driver.getReservedReplacementEventId()==null
                && assignments.findDriverCurrentAssignments(driver.getId(),OPEN).isEmpty();
    }
    private Driver locked(Long id) {
        Driver driver=drivers.findByIdForUpdate(id).orElse(null);
        // A ranking query may already have loaded this row before another transaction
        // committed a claim. A database lock alone does not refresh the JPA first-level cache.
        if(driver!=null && entityManager!=null) entityManager.refresh(driver,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return driver;
    }
    private boolean belongs(Driver driver, Vehicle vehicle) {
        return vehicle!=null && driver.getVehicles()!=null && driver.getVehicles().stream()
                .anyMatch(v->Objects.equals(v.getId(),vehicle.getId()));
    }
    private List<Driver> ranked(Vehicle vehicle, Assignment assignment) {
        ShipmentItem item=assignment.getShipmentItems()==null?null:assignment.getShipmentItems().stream()
                .filter(Objects::nonNull).sorted(Comparator.comparing(ShipmentItem::getId,Comparator.nullsLast(Long::compareTo)))
                .findFirst().orElse(null);
        return drivers.findDriversByVehicleId(vehicle.getId()).stream().filter(d->d.getId()!=null)
                .sorted(Comparator.comparingDouble((Driver d)->scorer.scoreFor(d,item)).reversed()
                        .thenComparing(Driver::getId)).toList();
    }
    private void record(Assignment assignment, Driver driver, AssignmentDriverHistory.Action action,
            Driver.DriverStatus before, Driver.DriverStatus after, LocalDateTime at, String actor) {
        if(assignment.getId()==null) return;
        AssignmentDriverHistory row=new AssignmentDriverHistory();
        row.setAssignmentId(assignment.getId()); row.setDriverId(driver.getId()); row.setDriverName(driver.getDriverName());
        row.setAction(action); row.setReason(actor); row.setActor(actor); row.setSimTime(at); row.setCreatedTime(at);
        row.setFromStatus(before.name()); row.setToStatus(after.name()); history.save(row);
    }
}
