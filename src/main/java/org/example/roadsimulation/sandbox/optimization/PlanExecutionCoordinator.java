package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.core.SimulationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @Profile("sandbox-runtime")
public class PlanExecutionCoordinator {
    private final PlanExecutionRuntime runtime;private final SimulationContext clock;
    private final PlanTaskObservationService observation;private final PlanTripMaterializer materializer;
    public PlanExecutionCoordinator(PlanExecutionRuntime runtime,SimulationContext clock,PlanTaskObservationService observation,PlanTripMaterializer materializer){
        this.runtime=runtime;this.clock=clock;this.observation=observation;this.materializer=materializer;
    }
    public boolean active(){return runtime.active();}
    public PlanTaskObservationService.Observation observe(){return observation.observe(clock.getLoopCount(),clock.getCurrentSimTime());}
    public PlanTaskObservationService.Observation afterCompletedTick(){
        var observed=observation.observe(clock.getLoopCount()-1,clock.getCurrentSimTime());
        var mapped=new HashSet<GlobalPlan.TaskKey>();observed.mapping().tasks().forEach(t -> mapped.add(t.key()));
        var state=runtime.state();
        for(var vehicle:state.task().plan().vehicles()){
            var key=state.head(vehicle.vehicleId());if(key.tripIndex()>=vehicle.trips().size()||state.assignment(key)!=null)continue;
            var trip=vehicle.trips().get(key.tripIndex());String reason;
            String previous=state.snapshot().queues().stream().filter(q -> q.logicalVehicleId()==vehicle.vehicleId()).findFirst().orElseThrow().reason();
            if(runtime.validation().upstreamDeferred().contains(key))reason="UPSTREAM_DEFERRED";
            else if(clock.getLoopCount()<trip.notBeforeLoop())reason="NOT_BEFORE";
            else if(trip.nodes().stream().anyMatch(n -> !mapped.contains(n.task())))reason="NOT_RELEASED";
            else if(Set.of("TASK_CANCELLED","DRIVER_UNAVAILABLE","VEHICLE_BUSY").contains(previous))reason=previous;
            else reason="WAIT_DISPATCH_SLOT";
            state.waitFor(key,reason,clock.getLoopCount()-1,clock.getCurrentSimTime());
        }
        return new PlanTaskObservationService.Observation(observed.mapping(),state.snapshot());
    }
    public void dispatch(){
        int loop=clock.getLoopCount();if(loop<=0||loop%3!=0)throw new IllegalStateException("PLAN_ILLEGAL_DISPATCH_TICK");
        var observed=observe();var mapping=new HashMap<GlobalPlan.TaskKey,Long>();observed.mapping().tasks().forEach(b -> mapping.put(b.key(),b.shipmentItemId()));
        var state=runtime.state();
        for(var vehicle:state.task().plan().vehicles()){
            var key=state.head(vehicle.vehicleId());if(key.tripIndex()>=vehicle.trips().size()||state.assignment(key)!=null)continue;
            var trip=vehicle.trips().get(key.tripIndex());String reason=null;
            if(runtime.validation().upstreamDeferred().contains(key))reason="UPSTREAM_DEFERRED";
            else if(loop<trip.notBeforeLoop())reason="NOT_BEFORE";
            else if(trip.nodes().stream().anyMatch(n -> !mapping.containsKey(n.task())))reason="NOT_RELEASED";
            if(reason==null)reason=materializer.materialize(key,trip,mapping).waitingReason();
            if(reason!=null)state.waitFor(key,reason,loop,clock.getCurrentSimTime());
        }
    }
}
