package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.core.*;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real master legs, action windows, cargo lifecycle and final inventory settlement. */
class WeatherCargoDeliveryTest {
    @Test void rainyMultiStopDeliveryKeepsLegsCargoAndInventorySeparate() throws Exception {
        var f=new Fixture();
        var first=f.item(1L,10);var second=f.item(2L,20);
        f.node(first,AssignmentNode.NodeActionType.LOAD,10,true);
        f.node(second,AssignmentNode.NodeActionType.LOAD,20,true);
        f.node(first,AssignmentNode.NodeActionType.UNLOAD,-10,false);
        f.node(second,AssignmentNode.NodeActionType.UNLOAD,-20,false);
        f.assignment.setCurrentActionIndex(2);f.vehicle.setCurrentLoad(30.0);
        f.leg(0,2,30);f.leg(1,3,20);
        f.vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,f.start,Duration.ZERO);
        f.tick(0);f.tick(30);f.tick(60);
        assertEquals(f.start.plusMinutes(75),f.legFacts.get(0).getCompletedSimTime());
        assertEquals(Vehicle.VehicleStatus.UNLOADING,f.vehicle.getCurrentStatus());
        assertEquals(f.start.plusMinutes(90),f.vehicle.getStatusStartTime());
        assertEquals(ShipmentItem.ShipmentItemStatus.IN_TRANSIT,first.getStatus());
        f.tick(90);f.tick(120);
        assertEquals(ShipmentItem.ShipmentItemStatus.DELIVERED,first.getStatus());
        assertEquals(ShipmentItem.ShipmentItemStatus.IN_TRANSIT,second.getStatus());
        assertEquals(20.0,f.vehicle.getCurrentLoad());
        assertEquals(3,f.assignment.getCurrentActionIndex());
        assertEquals(10,f.inventory.getQuantity());
        f.tick(120); // duplicate loop must not skip the second leg or double debit
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING,f.vehicle.getCurrentStatus());
        assertFalse(f.assignment.getNodes().get(3).isCompleted());
        for(int minute=150;minute<=300 && f.assignment.getStatus()!=Assignment.AssignmentStatus.COMPLETED;minute+=30) f.tick(minute);
        assertEquals(Assignment.AssignmentStatus.COMPLETED,f.assignment.getStatus());
        assertEquals(ShipmentItem.ShipmentItemStatus.DELIVERED,second.getStatus());
        assertEquals(0.0,f.vehicle.getCurrentLoad());
        assertEquals(8,f.inventory.getQuantity());
        assertEquals(2,f.assignment.getCurrentLegIndex());
        f.delivery.settleAfterBackendUnloading(f.assignment,f.vehicle,f.destination,f.start.plusMinutes(300),"duplicate");
        assertEquals(8,f.inventory.getQuantity());
        verify(f.enrollments,times(1)).save(f.inventory);
    }

    @Test void cancelledCargoDoesNotConsumeInventoryAtFinalSettlement() throws Exception {
        var f=new Fixture();f.item(1L,10);f.item(2L,20).setStatus(ShipmentItem.ShipmentItemStatus.CANCELLED);
        f.delivery.settleAfterBackendUnloading(f.assignment,f.vehicle,f.destination,f.start,"test");
        assertEquals(9,f.inventory.getQuantity());
    }

    private static class Fixture {
        final LocalDateTime start=LocalDateTime.of(2026,1,1,0,0);
        final VehicleRepository vehicles=mock(VehicleRepository.class);
        final AssignmentRepository assignments=mock(AssignmentRepository.class);
        final AssignmentLegRepository legs=mock(AssignmentLegRepository.class);
        final ShipmentRepository shipments=mock(ShipmentRepository.class);
        final ShipmentItemRepository items=mock(ShipmentItemRepository.class);
        final EnrollmentRepository enrollments=mock(EnrollmentRepository.class);
        final SimulationContext clock=new SimulationContext();
        final Vehicle vehicle=new Vehicle();
        final Assignment assignment=new Assignment();
        final POI origin=new POI(),destination=new POI();
        final Goods goods=new Goods();
        final Enrollment inventory=new Enrollment();
        final List<AssignmentLeg> legFacts=new ArrayList<>();
        final StateTransitionServiceImpl state=new StateTransitionServiceImpl();
        final TransportProgressService progress;
        final TransportDeliverySettlementService delivery;
        final WeatherEnvironmentService weather=mock(WeatherEnvironmentService.class);

        Fixture() throws Exception {
            vehicle.setId(1L);vehicle.setMaxLoadCapacity(50.0);assignment.setId(2L);
            assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);vehicle.addAssignment(assignment);
            origin.setId(3L);destination.setId(4L);goods.setId(5L);assignment.setDestPOI(destination);
            inventory.setQuantity(10);
            when(enrollments.findByPoiAndGoods(origin,goods)).thenReturn(Optional.of(inventory));
            when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle));
            when(vehicles.findById(1L)).thenReturn(Optional.of(vehicle));
            when(assignments.findById(2L)).thenReturn(Optional.of(assignment));
            when(legs.findByAssignmentIdOrderBySequenceIndexAsc(2L)).thenReturn(legFacts);
            when(legs.findByAssignmentIdAndSequenceIndex(eq(2L),anyInt())).thenAnswer(i->
                    legFacts.stream().filter(l->l.getSequenceIndex()==i.<Integer>getArgument(1)).findFirst());
            var lifecycle=new TransportLifecycleService(shipments,items,assignments,vehicles);
            var constructor=DataInitializer.class.getConstructors()[0];
            Object[] deps=Arrays.stream(constructor.getParameterTypes()).map(t->mock(t)).toArray();
            var initializer=(DataInitializer)constructor.newInstance(deps);
            ReflectionTestUtils.setField(initializer,"enrollmentRepository",enrollments);
            ReflectionTestUtils.setField(initializer,"transportLifecycleService",lifecycle);
            ReflectionTestUtils.setField(initializer,"simulationContext",clock);
            delivery=new TransportDeliverySettlementService(initializer,assignments,lifecycle);
            progress=new TransportProgressService(assignments,legs,lifecycle,mock(PlatformTransactionManager.class));
            progress.setWeatherEnvironmentService(weather);
            when(weather.runId()).thenReturn("cargo-run");
            ReflectionTestUtils.setField(state,"vehicleRepository",vehicles);
            ReflectionTestUtils.setField(state,"assignmentRepository",assignments);
            ReflectionTestUtils.setField(state,"assignmentLegRepository",legs);
            ReflectionTestUtils.setField(state,"transportRandomEventService",mock(TransportRandomEventService.class));
            ReflectionTestUtils.setField(state,"transportLifecycleService",lifecycle);
            ReflectionTestUtils.setField(state,"transportDeliverySettlementService",delivery);
        }
        ShipmentItem item(long id,double weight) {
            var shipment=new Shipment();shipment.setId(id);shipment.setOriginPOI(origin);
            when(shipments.findById(id)).thenReturn(Optional.of(shipment));
            var item=new ShipmentItem();item.setId(id);item.setShipment(shipment);item.setGoods(goods);item.setQty(1);item.setWeight(weight);
            assignment.addShipmentItem(item);item.setStatus(ShipmentItem.ShipmentItemStatus.IN_TRANSIT);
            when(items.findByShipmentId(id)).thenReturn(List.of(item));
            return item;
        }
        void node(ShipmentItem item,AssignmentNode.NodeActionType action,double weight,boolean completed) {
            var node=new AssignmentNode(assignment,assignment.getNodes().size(),destination,action,item,weight,0.0);
            node.setCompleted(completed);assignment.addNode(node);
        }
        void leg(int index,int node,double load) {
            var leg=new AssignmentLeg();leg.setId(10L+index);leg.setAssignment(assignment);leg.setVehicle(vehicle);
            leg.setSequenceIndex(index);leg.setToNode(assignment.getNodes().get(node));leg.setToPOI(destination);
            leg.setLoadState(AssignmentLeg.LoadState.LOADED);leg.setCurrentLoadTonnes(load);
            leg.setPlannedDistanceMeters(3600.0);leg.setPlannedDrivingSeconds(3600L);legFacts.add(leg);
        }
        void tick(int minute) {
            var from=start.plusMinutes(minute);var tick=new SimulationTick(minute/30,from,from.plusMinutes(30),1800);
            when(weather.averageSpeedFactor(from,tick.tickEnd())).thenReturn(.8);
            when(weather.windows(from,tick.tickEnd())).thenReturn(List.of(new WeatherDrivingIntegrator.Window(from,tick.tickEnd(),.8)));
            state.updateVehicleStateWithContext(vehicle,from,30);progress.advanceAssignment(2L,tick);
        }
    }
}
