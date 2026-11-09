package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.*;

@Service @Profile("sandbox-runtime")
public class PlanTripMaterializer {
    public record Result(Long assignmentId,String waitingReason) {}
    private final PlanExecutionRuntime runtime;private final SimulationContext clock;
    private final VehicleRepository vehicles;private final ShipmentItemRepository items;private final AssignmentRepository assignments;
    private final DriverRepository drivers;private final TransportMetricsService metrics;private final TransportLifecycleService lifecycle;
    public PlanTripMaterializer(PlanExecutionRuntime runtime,SimulationContext clock,VehicleRepository vehicles,ShipmentItemRepository items,
            AssignmentRepository assignments,DriverRepository drivers,TransportMetricsService metrics,TransportLifecycleService lifecycle) {
        this.runtime=runtime;this.clock=clock;this.vehicles=vehicles;this.items=items;this.assignments=assignments;this.drivers=drivers;this.metrics=metrics;this.lifecycle=lifecycle;
    }
    @Transactional
    public Result materialize(TripKey key,GlobalPlan.Trip trip,Map<GlobalPlan.TaskKey,Long> mapping) {
        var state=runtime.state();var existing=state.assignment(key);if(existing!=null)return new Result(existing,null);
        if(!key.equals(state.head(key.logicalVehicleId())))throw new IllegalStateException("PLAN_NOT_QUEUE_HEAD");
        var vehicle=vehicles.findByIdForUpdate(state.physical(key.logicalVehicleId())).orElseThrow();
        if(vehicle.getCurrentStatus()!=Vehicle.VehicleStatus.IDLE || vehicle.getReplacementReservationEventId()!=null
                ||assignments.findActiveAssignmentByVehicleForUpdate(vehicle.getId()).isPresent())return new Result(null,"VEHICLE_BUSY");
        if((vehicle.getCurrentLoadTonnes()!=null&&vehicle.getCurrentLoadTonnes()>1e-6)
                ||(vehicle.getCurrentVolumn()!=null&&vehicle.getCurrentVolumn()>1e-6))
            throw new IllegalStateException("PLAN_IDLE_VEHICLE_HAS_CARGO");
        var peak=runtime.validation().peaks().get(key);
        if(vehicle.getMaxLoadCapacityTonnes()==null||vehicle.getCargoVolume()==null
                ||vehicle.getMaxLoadCapacityTonnes()+1e-6<peak.weight().doubleValue()||vehicle.getCargoVolume()+1e-6<peak.volume().doubleValue())
            throw new IllegalStateException("PLAN_RUNTIME_CAPACITY_MISMATCH");
        boolean driverAvailable=drivers.findDriversByVehicleId(vehicle.getId()).stream().anyMatch(d ->
                d.getCurrentStatus()==Driver.DriverStatus.IDLE && d.getReservedReplacementEventId()==null
                        &&assignments.findDriverCurrentAssignments(d.getId(),List.of(Assignment.AssignmentStatus.ASSIGNED,
                        Assignment.AssignmentStatus.IN_PROGRESS,Assignment.AssignmentStatus.WAITING)).isEmpty());
        if(!driverAvailable)return new Result(null,"DRIVER_UNAVAILABLE");
        var cargo=new HashMap<GlobalPlan.TaskKey,ShipmentItem>();
        for(var task:trip.nodes().stream().filter(n -> n.action()==GlobalPlan.Action.LOAD).map(GlobalPlan.Node::task).toList()) {
            Long id=mapping.get(task);if(id==null)return new Result(null,"NOT_RELEASED");
            var item=items.findByIdForUpdate(id).orElseThrow();
            if(item.getStatus()==ShipmentItem.ShipmentItemStatus.CANCELLED || item.getShipment().getStatus()==Shipment.ShipmentStatus.CANCELLED)
                return new Result(null,"TASK_CANCELLED");
            if(item.getStatus()!=ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED || item.getAssignment()!=null)
                throw new IllegalStateException("PLAN_CARGO_ALREADY_OWNED");
            var catalog=state.task().compiledProblem().catalog();
            var expected=catalog.tasks().stream().filter(t -> t.key().equals(task)).findFirst().orElseThrow();
            var flow=catalog.flows().stream().filter(f -> f.key().equals(expected.flow())).findFirst().orElseThrow();
            if(!flow.sku().equals(item.getSku()) || item.getQty()!=expected.quantity()
                    ||java.math.BigDecimal.valueOf(item.getWeightTonnes()).compareTo(expected.weightTonnes())!=0
                    ||java.math.BigDecimal.valueOf(item.getVolume()).compareTo(expected.volumeCubicMeters())!=0
                    ||item.getShipment().getOriginPOI().getId()!=flow.originPoiId()
                    ||item.getShipment().getDestPOI().getId()!=flow.destinationPoiId())
                throw new IllegalStateException("PLAN_LOCKED_CARGO_MISMATCH");
            cargo.put(task,item);
        }
        var assignment=new Assignment();assignment.setAssignedVehicle(vehicle);assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
        assignment.setCreatedTime(clock.getCurrentSimTime());assignment.setUpdatedTime(clock.getCurrentSimTime());assignment.setUpdatedBy("GlobalPlan/v1");assignment.setCurrentActionIndex(0);
        cargo.values().stream().sorted(Comparator.comparing(ShipmentItem::getId)).forEach(assignment::addShipmentItem);
        int index=0;
        for(var planned:trip.nodes()) {
            var item=cargo.get(planned.task());var node=new AssignmentNode();node.setSequenceIndex(index++);
            boolean load=planned.action()==GlobalPlan.Action.LOAD;node.setActionType(load?AssignmentNode.NodeActionType.LOAD:AssignmentNode.NodeActionType.UNLOAD);
            node.setShipmentItem(item);node.setPoi(load?item.getShipment().getOriginPOI():item.getShipment().getDestPOI());
            node.setWeightDelta((load?1:-1)*item.getWeightTonnes());node.setVolumeDelta((load?1:-1)*item.getVolume());node.setCompleted(false);assignment.addNode(node);
        }
        assignment.setOriginPOI(assignment.getNodes().get(0).getPoi());assignment.setDestPOI(assignment.getNodes().get(assignment.getNodes().size()-1).getPoi());
        assignment.setActionLine(assignment.getNodes().stream().map(n -> n.getPoi().getId()).toList());
        var saved=assignments.saveAndFlush(assignment);
        if(!metrics.rebuildMetricsForAssignmentStrict(saved.getId()))throw new IllegalStateException("PLAN_ROUTE_BUILD_FAILED");
        lifecycle.startAssignmentExecution(saved,vehicle,clock.getCurrentSimTime(),"GlobalPlan/v1");
        long id=saved.getId();int loop=clock.getLoopCount();var at=clock.getCurrentSimTime();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){state.dispatched(key,id,loop,at);}
        });
        return new Result(id,null);
    }
}
