package org.example.roadsimulation;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DataInitializerOriginalVrpDispatchTest {

    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final GoodsRepository goodsRepository = mock(GoodsRepository.class);
    private final POIRepository poiRepository = mock(POIRepository.class);
    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
    private final ShipmentItemRepository shipmentItemRepository = mock(ShipmentItemRepository.class);
    private final ProcessingChainRepository processingChainRepository = mock(ProcessingChainRepository.class);
    private final SimulationDataCleanupService cleanupService = mock(SimulationDataCleanupService.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final ShipmentItemService shipmentItemService = mock(ShipmentItemService.class);
    private final ShipmentProgressService shipmentProgressService = mock(ShipmentProgressService.class);
    private final RoutePlanningService routePlanningService = mock(RoutePlanningService.class);
    private final GetCostService getCostService = mock(GetCostService.class);
    private final CargoChunkService cargoChunkService = mock(CargoChunkService.class);
    private final POIShipmentManager poiShipmentManager = mock(POIShipmentManager.class);
    private final TransportMetricsService transportMetricsService = mock(TransportMetricsService.class);
    private final SimulationContext simulationContext = mock(SimulationContext.class);
    private final BatchDirectVehicleAssignmentService batchDirectVehicleAssignmentService =
            new BatchDirectVehicleAssignmentService();
    private final TransportLifecycleService transportLifecycleService = mock(TransportLifecycleService.class);

    @Test
    void keepsItemsUnassignedWhenFinalLoadFactorIsBelowThreshold() {
        Vehicle vehicle = vehicle(1L, 2.0, 17.2);
        ShipmentItem lowLoadItem = item(101L, 0.3, 0.2, poi(1L, 0.0, 0.0), poi(2L, 0.0, 0.1));

        when(poiShipmentManager.sweepExpiredShipments(120)).thenReturn(List.of());
        when(shipmentItemRepository.findAll()).thenReturn(List.of(lowLoadItem));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(vehicle));
        when(getCostService.estimateMarginalCost(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble()
        )).thenReturn(0.0);

        dataInitializer().vrpDispatchingCycle();

        assertEquals(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED, lowLoadItem.getStatus());
        verify(assignmentRepository, never()).save(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void skipsAddedCandidateWhenTonsPerExtraKmIsBelowThreshold() {
        Vehicle vehicle = vehicle(1L, 2.0, 17.2);
        ShipmentItem firstItem = item(101L, 0.3, 0.2, poi(1L, 0.0, 0.0), poi(2L, 0.0, 0.1));
        ShipmentItem lowWorthAddedItem = item(102L, 0.1, 0.2, poi(3L, 0.0, 0.01), poi(4L, 0.0, 0.2));

        when(poiShipmentManager.sweepExpiredShipments(120)).thenReturn(List.of());
        when(shipmentItemRepository.findAll()).thenReturn(List.of(firstItem, lowWorthAddedItem));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(vehicle));
        when(getCostService.estimateMarginalCost(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble()
        )).thenReturn(0.0);

        dataInitializer().vrpDispatchingCycle();

        verify(getCostService, times(1)).estimateMarginalCost(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble()
        );
        assertEquals(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED, firstItem.getStatus());
        assertEquals(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED, lowWorthAddedItem.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void doesNotUseWaitingVehiclesForOriginalVrpDispatch() {
        Vehicle waitingVehicle = vehicle(1L, 2.0, 17.2);
        waitingVehicle.setCurrentStatus(Vehicle.VehicleStatus.WAITING);
        ShipmentItem item = item(101L, 1.7, 0.2, poi(1L, 0.0, 0.0), poi(2L, 0.0, 0.1));

        when(poiShipmentManager.sweepExpiredShipments(120)).thenReturn(List.of());
        when(shipmentItemRepository.findAll()).thenReturn(List.of(item));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of());
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.WAITING)).thenReturn(List.of(waitingVehicle));

        dataInitializer().vrpDispatchingCycle();

        assertEquals(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED, item.getStatus());
        verify(vehicleRepository).findByCurrentStatus(Vehicle.VehicleStatus.IDLE);
        verify(vehicleRepository, never()).findByCurrentStatus(Vehicle.VehicleStatus.WAITING);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void experimentRubberShipmentsStayUnassignedWithManyIdleLargeVehiclesBelowOriginalLoadThreshold() {
        List<ShipmentItem> uploadedExperimentRubberItems = List.of(
                item(7915L, 2.88, 2.16, poi(4573L, 30.0, 120.0), poi(4764L, 30.1, 120.1)),
                item(7917L, 1.92, 1.44, poi(4573L, 30.0, 120.0), poi(4762L, 30.1, 120.1)),
                item(7920L, 1.28, 0.96, poi(4569L, 30.0, 120.0), poi(4762L, 30.1, 120.1)),
                item(7928L, 1.60, 1.20, poi(4572L, 30.0, 120.0), poi(4764L, 30.1, 120.1))
        );
        List<Vehicle> manyIdleLargeVehicles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyIdleLargeVehicles.add(vehicle((long) i + 1, 15.7, 55.0));
        }

        when(poiShipmentManager.sweepExpiredShipments(120)).thenReturn(List.of());
        when(shipmentItemRepository.findAll()).thenReturn(uploadedExperimentRubberItems);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(manyIdleLargeVehicles);
        when(getCostService.estimateMarginalCost(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble()
        )).thenReturn(0.0);

        dataInitializer().vrpDispatchingCycle();

        for (ShipmentItem item : uploadedExperimentRubberItems) {
            assertEquals(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED, item.getStatus());
        }
        verify(assignmentRepository, never()).save(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void tailFallbackDoesNotDispatchBeforeWaitThreshold() {
        LocalDateTime simStart = LocalDateTime.of(2026, 1, 1, 0, 0);
        ShipmentItem tailItem = item(7925L, 0.8, 1.6, poi(1L, 0.0, 0.0), poi(2L, 0.0, 0.1));
        tailItem.setCreatedTime(simStart);
        Vehicle vehicle = vehicle(1L, 1.475, 22.8);

        when(simulationContext.getCurrentSimTime()).thenReturn(simStart.plusMinutes(150));
        when(simulationContext.getLoopCount()).thenReturn(5);
        when(simulationContext.getMinutesPerLoop()).thenReturn(30);
        when(shipmentItemRepository.findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED))
                .thenReturn(List.of(tailItem));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(vehicle));

        int dispatched = dataInitializer().dispatchOverdueTailItems("TAIL_FALLBACK_ORIGINAL");

        assertEquals(0, dispatched);
        verify(transportLifecycleService, never()).startAssignmentExecution(any(), any(), any(), anyString());
    }

    @Test
    void tailFallbackDispatchesOverdueItemAndPacksHelpfulOptionalItem() {
        LocalDateTime simStart = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime simNow = simStart.plusMinutes(180);
        ShipmentItem overdueItem = item(7925L, 0.8, 1.6, poi(1L, 0.0, 0.0), poi(2L, 0.0, 0.1));
        overdueItem.setCreatedTime(simStart);
        ShipmentItem optionalItem = item(7924L, 2.0, 4.0, poi(3L, 0.0, 0.01), poi(4L, 0.0, 0.2));
        optionalItem.setCreatedTime(simNow);
        Vehicle vehicle = vehicle(1L, 3.59, 42.0);
        Route route = new Route(overdueItem.getShipment().getOriginPOI(), optionalItem.getShipment().getDestPOI());

        when(simulationContext.getCurrentSimTime()).thenReturn(simNow);
        when(simulationContext.getLoopCount()).thenReturn(6);
        when(simulationContext.getMinutesPerLoop()).thenReturn(30);
        when(simulationContext.isRunning()).thenReturn(true);
        when(shipmentItemRepository.findByStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED))
                .thenReturn(List.of(overdueItem, optionalItem));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(vehicle));
        when(routeRepository.findByStartPOIIdAndEndPOIId(anyLong(), anyLong())).thenReturn(List.of(route));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment assignment = invocation.getArgument(0);
            if (assignment.getId() == null) {
                assignment.setId(900L);
            }
            return assignment;
        });
        when(transportLifecycleService.startAssignmentExecution(any(Assignment.class), eq(vehicle), eq(simNow), eq("TAIL_FALLBACK_ORIGINAL")))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transportMetricsService.rebuildMetricsForAssignmentStrict(anyLong())).thenReturn(true);

        int dispatched = dataInitializer().dispatchOverdueTailItems("TAIL_FALLBACK_ORIGINAL");

        assertEquals(1, dispatched);
        verify(transportLifecycleService).startAssignmentExecution(
                argThat(assignment -> assignment.getShipmentItems().contains(overdueItem)
                        && assignment.getShipmentItems().contains(optionalItem)),
                eq(vehicle),
                eq(simNow),
                eq("TAIL_FALLBACK_ORIGINAL")
        );
        assertEquals(10_800L, overdueItem.getWaitingAssignmentSeconds());
    }

    private DataInitializer dataInitializer() {
        return new DataInitializer(
                enrollmentRepository,
                goodsRepository,
                poiRepository,
                routeRepository,
                shipmentRepository,
                shipmentItemRepository,
                processingChainRepository,
                cleanupService,
                assignmentRepository,
                vehicleRepository,
                shipmentItemService,
                shipmentProgressService,
                routePlanningService,
                getCostService,
                cargoChunkService,
                poiShipmentManager,
                transportMetricsService,
                simulationContext,
                batchDirectVehicleAssignmentService,
                new OriginalVrpDispatchPolicy(0.80, 400.0, 4000.0, 0.02),
                transportLifecycleService
        );
    }

    private Vehicle vehicle(Long id, double maxLoad, double maxVolume) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setLicensePlate("V-" + id);
        vehicle.setMaxLoadCapacity(maxLoad);
        vehicle.setCargoVolume(maxVolume);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
        vehicle.setCurrentLatitude(BigDecimal.ZERO);
        vehicle.setCurrentLongitude(BigDecimal.ZERO);
        return vehicle;
    }

    private ShipmentItem item(Long id, double weight, double volume, POI origin, POI dest) {
        Shipment shipment = new Shipment();
        shipment.setOriginPOI(origin);
        shipment.setDestPOI(dest);

        ShipmentItem item = new ShipmentItem();
        item.setId(id);
        item.setShipment(shipment);
        item.setWeight(weight);
        item.setVolume(volume);
        item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
        return item;
    }

    private POI poi(Long id, double lat, double lon) {
        POI poi = new POI();
        poi.setId(id);
        poi.setName("poi-" + id);
        poi.setLatitude(BigDecimal.valueOf(lat));
        poi.setLongitude(BigDecimal.valueOf(lon));
        return poi;
    }
}
