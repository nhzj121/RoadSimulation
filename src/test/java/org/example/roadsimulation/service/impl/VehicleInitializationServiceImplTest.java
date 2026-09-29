package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
import org.example.roadsimulation.sandbox.run.SandboxVehicleInitialState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleInitializationServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private POIRepository poiRepository;

    @Test
    void initializeAllVehicleStatusRandomizesToWarehouseOrDistributionCenter() {
        Vehicle vehicle1 = vehicle(1L, "V-1");
        Vehicle vehicle2 = vehicle(2L, "V-2");
        POI warehouse = poi(10L, "Warehouse", POI.POIType.WAREHOUSE, "104.100000", "30.100000");
        POI distributionCenter = poi(11L, "Distribution", POI.POIType.DISTRIBUTION_CENTER, "104.200000", "30.200000");

        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle1, vehicle2));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle1));
        when(vehicleRepository.findById(2L)).thenReturn(Optional.of(vehicle2));
        when(poiRepository.findByPoiType(POI.POIType.WAREHOUSE)).thenReturn(List.of(warehouse));
        when(poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER)).thenReturn(List.of(distributionCenter));

        VehicleInitializationServiceImpl service =
                new VehicleInitializationServiceImpl(vehicleRepository, poiRepository);

        service.initializeAllVehicleStatus();

        Set<Long> allowedPoiIds = Set.of(warehouse.getId(), distributionCenter.getId());
        assertInitializedAtAllowedPOI(vehicle1, allowedPoiIds);
        assertInitializedAtAllowedPOI(vehicle2, allowedPoiIds);
        verify(vehicleRepository).saveAll(List.of(vehicle1, vehicle2));
    }

    @Test
    void initializeAllVehicleStatusFailsWhenNoInitializationPOIExists() {
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle(1L, "V-1")));
        when(poiRepository.findByPoiType(POI.POIType.WAREHOUSE)).thenReturn(List.of());
        when(poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER)).thenReturn(List.of());

        VehicleInitializationServiceImpl service =
                new VehicleInitializationServiceImpl(vehicleRepository, poiRepository);

        assertThrows(IllegalStateException.class, service::initializeAllVehicleStatus);
        verify(vehicleRepository, never()).saveAll(anyList());
    }

    @Test
    void sandboxInitializationReappliesPublishedPoiWithoutCopyingCoordinates() {
        Vehicle vehicle = vehicle(1L, "V-1");
        POI warehouse = poi(10L, "Warehouse", POI.POIType.WAREHOUSE, "104.100000", "30.100000");
        SandboxRunRuntimeContext runtime = mock(SandboxRunRuntimeContext.class);
        SandboxRunSpecificationV1.SimulationClock clock =
                new SandboxRunSpecificationV1.SimulationClock(
                        java.time.LocalDateTime.of(2026, 1, 1, 0, 0), 1800, 48);
        when(runtime.vehicleInitialStatesByVehicleId()).thenReturn(java.util.Map.of(
                1L, new SandboxVehicleInitialState(
                        1L, SandboxVehicleInitialState.RANDOM_DERIVED_POI, 10L,
                        "VEHICLE_INITIAL_POI/v1", "{\"vehicleId\":1}", "0123456789abcdef")));
        when(runtime.simulationClock()).thenReturn(clock);
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));
        when(poiRepository.findById(10L)).thenReturn(Optional.of(warehouse));

        VehicleInitializationServiceImpl service =
                new VehicleInitializationServiceImpl(vehicleRepository, poiRepository);
        service.setSandboxRunRuntimeContext(runtime);
        service.initializeAllVehicleStatus();

        assertEquals(warehouse, vehicle.getCurrentPOI());
        assertNull(vehicle.getCurrentLongitude());
        assertNull(vehicle.getCurrentLatitude());
        assertEquals(Vehicle.VehicleStatus.IDLE, vehicle.getCurrentStatus());
        assertEquals(0.0, vehicle.getCurrentLoad());
        verify(poiRepository, never()).findByPoiType(any());
        verify(vehicleRepository).saveAll(List.of(vehicle));
    }

    private static void assertInitializedAtAllowedPOI(Vehicle vehicle, Set<Long> allowedPoiIds) {
        assertEquals(Vehicle.VehicleStatus.IDLE, vehicle.getCurrentStatus());
        assertEquals(0.0, vehicle.getCurrentLoad());
        assertNotNull(vehicle.getCurrentPOI());
        assertTrue(allowedPoiIds.contains(vehicle.getCurrentPOI().getId()));
        assertEquals(vehicle.getCurrentPOI().getLongitude(), vehicle.getCurrentLongitude());
        assertEquals(vehicle.getCurrentPOI().getLatitude(), vehicle.getCurrentLatitude());
    }

    private static Vehicle vehicle(Long id, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setLicensePlate(plate);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.WAITING);
        vehicle.setCurrentLoad(12.0);
        return vehicle;
    }

    private static POI poi(Long id, String name, POI.POIType type, String longitude, String latitude) {
        POI poi = new POI(name, new BigDecimal(longitude), new BigDecimal(latitude), type);
        poi.setId(id);
        return poi;
    }
}
