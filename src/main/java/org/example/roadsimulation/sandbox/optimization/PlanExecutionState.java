package org.example.roadsimulation.sandbox.optimization;

import java.time.LocalDateTime;
import java.util.*;

/** Single-execution state; never reused to resume an abandoned process. */
public final class PlanExecutionState {
    public record Binding(TripKey trip, long assignmentId) {}
    public record Event(int loopIndex, LocalDateTime simTime, String type, TripKey trip,
                        Long fromVehicle, Long toVehicle, String reason) {}
    public record Queue(long logicalVehicleId, long physicalVehicleId, int nextTripIndex, String reason) {}
    public record Snapshot(String artifactVersion, String taskFingerprint, List<Queue> queues,
                           List<Binding> bindings, List<TripKey> completed, List<Event> events) {
        public static final String VERSION="sandbox-plan-execution-state/v1";
        public Snapshot {
            artifactVersion=ContractChecks.version(artifactVersion,VERSION);taskFingerprint=ContractChecks.hash(taskFingerprint,"taskFingerprint");
            queues=List.copyOf(queues); bindings=List.copyOf(bindings); completed=List.copyOf(completed); events=List.copyOf(events);
        }
    }
    private final PlanExecutionTask task;
    private final Map<Long,Long> physical = new TreeMap<>();
    private final Map<Long,Integer> heads = new TreeMap<>();
    private final Map<Long,String> reasons = new TreeMap<>();
    private final Map<TripKey,Long> assignments = new TreeMap<>();
    private final Set<TripKey> completed = new TreeSet<>();
    private final List<Event> events = new ArrayList<>();
    public PlanExecutionState(PlanExecutionTask task) {
        this.task=Objects.requireNonNull(task);
        task.plan().vehicles().forEach(v -> { physical.put(v.vehicleId(),v.vehicleId()); heads.put(v.vehicleId(),0); reasons.put(v.vehicleId(),"NOT_RELEASED"); });
    }
    public PlanExecutionTask task() { return task; }
    public synchronized long physical(long logical) { return physical.get(logical); }
    public synchronized TripKey head(long logical) { return new TripKey(logical,heads.get(logical)); }
    public synchronized Long assignment(TripKey trip) { return assignments.get(trip); }
    public synchronized boolean completed(TripKey trip) { return completed.contains(trip); }
    public synchronized void dispatched(TripKey trip,long assignment,int loop,LocalDateTime at) {
        if (assignments.putIfAbsent(trip,assignment)!=null) throw new IllegalStateException("Duplicate trip dispatch");
        reasons.put(trip.logicalVehicleId(),"IN_PROGRESS"); events.add(new Event(loop,at,"DISPATCH",trip,null,physical(trip.logicalVehicleId()),null));
    }
    public synchronized void complete(TripKey trip,int loop,LocalDateTime at) {
        if (completed.add(trip)) {
            heads.put(trip.logicalVehicleId(),trip.tripIndex()+1); reasons.put(trip.logicalVehicleId(),"READY_CHECK");
            events.add(new Event(loop,at,"COMPLETED",trip,null,physical(trip.logicalVehicleId()),null));
        }
    }
    public synchronized void waitFor(TripKey trip,String reason,int loop,LocalDateTime at) {
        if (!Objects.equals(reasons.put(trip.logicalVehicleId(),reason),reason))
            events.add(new Event(loop,at,"WAIT",trip,null,physical(trip.logicalVehicleId()),reason));
    }
    public synchronized Optional<TripKey> forAssignment(long assignment) {
        return assignments.entrySet().stream().filter(e -> e.getValue()==assignment).map(Map.Entry::getKey).findFirst();
    }
    public synchronized boolean hasRemaining(long vehicle) {
        return task.plan().vehicles().stream().anyMatch(v -> physical(v.vehicleId())==vehicle && heads.get(v.vehicleId())<v.trips().size());
    }
    public synchronized void handoff(long assignment,long original,long spare,int loop,LocalDateTime at) {
        var trip=forAssignment(assignment).orElseThrow(() -> new IllegalStateException("Untracked handoff"));
        if (physical(trip.logicalVehicleId())!=original || hasRemaining(spare)) throw new IllegalStateException("Invalid spare ownership");
        physical.put(trip.logicalVehicleId(),spare);
        events.add(new Event(loop,at,"HANDOFF",trip,original,spare,"CURRENT_AND_REMAINING_QUEUE"));
    }
    public synchronized Snapshot snapshot() {
        var queues=physical.entrySet().stream().map(e -> new Queue(e.getKey(),e.getValue(),heads.get(e.getKey()),reasons.get(e.getKey()))).toList();
        return new Snapshot("sandbox-plan-execution-state/v1",task.fingerprint(),queues,
                assignments.entrySet().stream().map(e -> new Binding(e.getKey(),e.getValue())).toList(),List.copyOf(completed),events);
    }
}
