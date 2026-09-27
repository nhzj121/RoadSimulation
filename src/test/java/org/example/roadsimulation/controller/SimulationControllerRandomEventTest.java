package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.TransportRandomEventService;
import org.example.roadsimulation.service.TransportLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SimulationControllerRandomEventTest {

    @Test
    void persistedOrdinaryReplacementCannotBypassReadinessWithMissingOrWrongEventId() {
        SimulationController controller = new SimulationController();
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        VehicleRepository vehicles = mock(VehicleRepository.class);
        TransportRandomEventService events = mock(TransportRandomEventService.class);
        org.example.roadsimulation.DataInitializer dataInitializer = mock(org.example.roadsimulation.DataInitializer.class);
        ReflectionTestUtils.setField(controller,"assignmentRepository",assignments);
        ReflectionTestUtils.setField(controller,"vehicleRepository",vehicles);
        ReflectionTestUtils.setField(controller,"transportRandomEventService",events);
        ReflectionTestUtils.setField(controller,"dataInitializer",dataInitializer);
        Vehicle vehicle=new Vehicle();vehicle.setId(21L);
        Assignment assignment=new Assignment();assignment.setId(88L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);vehicle.addAssignment(assignment);
        when(assignments.findById(88L)).thenReturn(Optional.of(assignment));
        when(vehicles.findByIdForUpdate(21L)).thenReturn(Optional.of(vehicle));
        when(events.replacementRecoveryState(assignment)).thenReturn(
                new TransportRandomEventService.ReplacementRecoveryState(true,101L,12L,21L,true));
        SimulationController.VehicleArrivedRequest request=new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L);request.setVehicleId(21L);request.setEndPOIId(20L);

        assertEquals(HttpStatus.CONFLICT,controller.handleVehicleArrived(request).getStatusCode());
        request.setReplacementEventId(999L);
        assertEquals(HttpStatus.CONFLICT,controller.handleVehicleArrived(request).getStatusCode());
        verifyNoInteractions(dataInitializer);
    }

    @Test
    void legacyOrdinaryArrivalWithoutReplacementHistoryStillNeedsNoEventId() {
        SimulationController controller = new SimulationController();
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        VehicleRepository vehicles = mock(VehicleRepository.class);
        POIRepository pois = mock(POIRepository.class);
        TransportRandomEventService events = mock(TransportRandomEventService.class);
        SimulationMainLoop loop = mock(SimulationMainLoop.class);
        org.example.roadsimulation.DataInitializer dataInitializer = mock(org.example.roadsimulation.DataInitializer.class);
        ReflectionTestUtils.setField(controller,"assignmentRepository",assignments);
        ReflectionTestUtils.setField(controller,"vehicleRepository",vehicles);
        ReflectionTestUtils.setField(controller,"poiRepository",pois);
        ReflectionTestUtils.setField(controller,"transportRandomEventService",events);
        ReflectionTestUtils.setField(controller,"simulationMainLoop",loop);
        ReflectionTestUtils.setField(controller,"dataInitializer",dataInitializer);
        Vehicle vehicle=new Vehicle();vehicle.setId(21L);
        POI start=new POI();start.setId(10L);POI end=new POI();end.setId(20L);
        Route route=new Route();route.setStartPOI(start);route.setEndPOI(end);
        Assignment assignment=new Assignment();assignment.setId(88L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);assignment.setRoute(route);vehicle.addAssignment(assignment);
        when(assignments.findById(88L)).thenReturn(Optional.of(assignment));
        when(vehicles.findByIdForUpdate(21L)).thenReturn(Optional.of(vehicle));
        when(pois.findById(20L)).thenReturn(Optional.of(end));
        when(events.replacementRecoveryState(assignment)).thenReturn(
                new TransportRandomEventService.ReplacementRecoveryState(false,null,null,null,false));
        SimulationController.VehicleArrivedRequest request=new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L);request.setVehicleId(21L);request.setEndPOIId(20L);

        assertEquals(HttpStatus.OK,controller.handleVehicleArrived(request).getStatusCode());
        verify(dataInitializer).processVehicleDelivery(start,vehicle,end);
    }

    @Test
    void replacementArrivalReturns409UntilDurableBackendStateIsReady() {
        SimulationController controller = new SimulationController();
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        TransportRandomEventService eventService = mock(TransportRandomEventService.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        org.example.roadsimulation.DataInitializer dataInitializer = mock(org.example.roadsimulation.DataInitializer.class);
        ReflectionTestUtils.setField(controller, "assignmentRepository", assignmentRepository);
        ReflectionTestUtils.setField(controller, "transportRandomEventService", eventService);
        ReflectionTestUtils.setField(controller, "vehicleRepository", vehicleRepository);
        ReflectionTestUtils.setField(controller, "dataInitializer", dataInitializer);
        Vehicle vehicle = new Vehicle(); vehicle.setId(21L);
        Assignment assignment = new Assignment(); assignment.setId(88L); assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        vehicle.addAssignment(assignment);
        when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(vehicle));
        when(eventService.replacementRecoveryState(assignment)).thenReturn(
                new TransportRandomEventService.ReplacementRecoveryState(true,101L,12L,21L,false));

        SimulationController.VehicleArrivedRequest request = new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L); request.setVehicleId(21L); request.setEndPOIId(20L); request.setReplacementEventId(101L);

        assertEquals(HttpStatus.CONFLICT, controller.handleVehicleArrived(request).getStatusCode());
        verify(eventService).replacementRecoveryState(assignment);
        verifyNoInteractions(dataInitializer);
    }

    @Test
    void backendReadyReplacementArrivalCompletesOrdinaryAssignmentOnce() throws Exception {
        SimulationController controller = new SimulationController();
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        TransportRandomEventService eventService = mock(TransportRandomEventService.class);
        SimulationMainLoop simulationMainLoop = mock(SimulationMainLoop.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        POIRepository poiRepository = mock(POIRepository.class);
        org.example.roadsimulation.repository.ShipmentRepository shipmentRepository = mock(org.example.roadsimulation.repository.ShipmentRepository.class);
        org.example.roadsimulation.repository.ShipmentItemRepository itemRepository = mock(org.example.roadsimulation.repository.ShipmentItemRepository.class);
        org.example.roadsimulation.repository.EnrollmentRepository enrollmentRepository = mock(org.example.roadsimulation.repository.EnrollmentRepository.class);
        org.example.roadsimulation.repository.GoodsRepository goodsRepository = mock(org.example.roadsimulation.repository.GoodsRepository.class);
        TransportLifecycleService lifecycle = new TransportLifecycleService(
                shipmentRepository,itemRepository,assignmentRepository,vehicleRepository,eventService);
        var initializerConstructor = org.example.roadsimulation.DataInitializer.class.getConstructors()[0];
        Object[] initializerDependencies = java.util.Arrays.stream(initializerConstructor.getParameterTypes())
                .map(type -> mock(type)).toArray();
        org.example.roadsimulation.DataInitializer dataInitializer =
                (org.example.roadsimulation.DataInitializer) initializerConstructor.newInstance(initializerDependencies);
        ReflectionTestUtils.setField(dataInitializer,"poiRepository",poiRepository);
        ReflectionTestUtils.setField(dataInitializer,"shipmentRepository",shipmentRepository);
        ReflectionTestUtils.setField(dataInitializer,"shipmentItemRepository",itemRepository);
        ReflectionTestUtils.setField(dataInitializer,"enrollmentRepository",enrollmentRepository);
        ReflectionTestUtils.setField(dataInitializer,"goodsRepository",goodsRepository);
        ReflectionTestUtils.setField(dataInitializer,"transportLifecycleService",lifecycle);
        ReflectionTestUtils.setField(controller, "assignmentRepository", assignmentRepository);
        ReflectionTestUtils.setField(controller, "transportRandomEventService", eventService);
        ReflectionTestUtils.setField(controller, "simulationMainLoop", simulationMainLoop);
        ReflectionTestUtils.setField(controller, "vehicleRepository", vehicleRepository);
        ReflectionTestUtils.setField(controller, "poiRepository", poiRepository);
        ReflectionTestUtils.setField(controller, "dataInitializer", dataInitializer);

        Vehicle vehicle = new Vehicle(); vehicle.setId(21L);
        vehicle.transitionToStatus(Vehicle.VehicleStatus.UNLOADING, LocalDateTime.of(2026,1,1,9,0), java.time.Duration.ofMinutes(30));
        POI start = new POI(); start.setId(10L);
        POI end = new POI(); end.setId(20L);
        Route route = new Route(); route.setStartPOI(start); route.setEndPOI(end);
        Assignment assignment = new Assignment(); assignment.setId(88L); assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignment.setRoute(route); vehicle.addAssignment(assignment);
        var goods = new org.example.roadsimulation.entity.Goods();
        var inventory = new org.example.roadsimulation.entity.Enrollment(start,goods,2);
        start.addGoodsEnrollment(inventory); goods.addPOIEnrollment(inventory);
        var shipment = new org.example.roadsimulation.entity.Shipment(); shipment.setId(31L); shipment.setOriginPOI(start); shipment.setDestPOI(end);
        var item = new org.example.roadsimulation.entity.ShipmentItem(); item.setId(41L); item.setShipment(shipment);
        item.setGoods(goods); item.setStatus(org.example.roadsimulation.entity.ShipmentItem.ShipmentItemStatus.IN_TRANSIT);
        assignment.addShipmentItem(item);
        when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findById(21L)).thenReturn(Optional.of(vehicle));
        when(poiRepository.findById(10L)).thenReturn(Optional.of(start));
        when(poiRepository.findById(20L)).thenReturn(Optional.of(end));
        when(shipmentRepository.findById(31L)).thenReturn(Optional.of(shipment));
        when(itemRepository.findByShipmentId(31L)).thenReturn(java.util.List.of(item));
        when(eventService.replacementRecoveryState(assignment)).thenReturn(
                new TransportRandomEventService.ReplacementRecoveryState(true,101L,12L,21L,true));
        @SuppressWarnings("unchecked")
        java.util.Map<String,org.example.roadsimulation.entity.Shipment> pairMap =
                (java.util.Map<String,org.example.roadsimulation.entity.Shipment>) ReflectionTestUtils.getField(dataInitializer,"poiPairShipmentMapping");
        String pairKey=ReflectionTestUtils.invokeMethod(dataInitializer,"generatePoiPairKey",start,end);
        pairMap.put(pairKey,shipment);

        SimulationController.VehicleArrivedRequest request = new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L); request.setVehicleId(21L); request.setEndPOIId(20L); request.setReplacementEventId(101L);

        assertEquals(HttpStatus.OK, controller.handleVehicleArrived(request).getStatusCode());
        assertEquals(HttpStatus.OK, controller.handleVehicleArrived(request).getStatusCode());
        assertEquals(Assignment.AssignmentStatus.COMPLETED,assignment.getStatus());
        assertEquals(org.example.roadsimulation.entity.ShipmentItem.ShipmentItemStatus.DELIVERED,item.getStatus());
        assertEquals(Vehicle.VehicleStatus.IDLE,vehicle.getCurrentStatus());
        assertEquals(1,inventory.getQuantity());
        verify(enrollmentRepository,times(1)).save(inventory);
    }

    @Test
    void vehicleArrivalReturns409WhileEventBlocksTransition() {
        SimulationController controller = new SimulationController();
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        TransportRandomEventService eventService = mock(TransportRandomEventService.class);
        SimulationMainLoop simulationMainLoop = mock(SimulationMainLoop.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        ReflectionTestUtils.setField(controller, "assignmentRepository", assignmentRepository);
        ReflectionTestUtils.setField(controller, "transportRandomEventService", eventService);
        ReflectionTestUtils.setField(controller, "simulationMainLoop", simulationMainLoop);
        ReflectionTestUtils.setField(controller, "vehicleRepository", vehicleRepository);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        Assignment assignment = new Assignment();
        assignment.setId(88L);
        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignment.setAssignedVehicle(vehicle);
        LocalDateTime simNow = LocalDateTime.of(2026, 1, 1, 8, 30);
        when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        when(vehicleRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(simulationMainLoop.getCurrentSimTime()).thenReturn(simNow);
        when(eventService.isTransitionBlocked(12L, simNow)).thenReturn(true);

        SimulationController.VehicleArrivedRequest request = new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L);
        request.setVehicleId(12L);
        ResponseEntity<Void> response = controller.handleVehicleArrived(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(assignmentRepository).findById(88L);
        verify(vehicleRepository).findByIdForUpdate(12L);
        verify(simulationMainLoop).getCurrentSimTime();
        verify(eventService).replacementRecoveryState(assignment);
        verify(eventService).isTransitionBlocked(12L, simNow);
        verifyNoMoreInteractions(assignmentRepository, vehicleRepository, eventService, simulationMainLoop);
    }

    @Test
    void assignmentLoadedReturns409WhenLifecycleDetectsActiveEvent() {
        SimulationController controller = new SimulationController();
        TransportLifecycleService lifecycleService = mock(TransportLifecycleService.class);
        SimulationMainLoop simulationMainLoop = mock(SimulationMainLoop.class);
        ReflectionTestUtils.setField(controller, "transportLifecycleService", lifecycleService);
        ReflectionTestUtils.setField(controller, "simulationMainLoop", simulationMainLoop);
        LocalDateTime simNow = LocalDateTime.of(2026, 1, 1, 8, 30);
        when(simulationMainLoop.getCurrentSimTime()).thenReturn(simNow);
        when(lifecycleService.markFrontendLoadingCompleted(88L, 12L, simNow, "Frontend assignment-loaded"))
                .thenThrow(new TransportRandomEventService.TransitionBlockedException(12L));

        SimulationController.AssignmentLoadedRequest request = new SimulationController.AssignmentLoadedRequest();
        request.setAssignmentId(88L);
        request.setVehicleId(12L);

        assertEquals(HttpStatus.CONFLICT, controller.handleAssignmentLoaded(request).getStatusCode());
    }
}
