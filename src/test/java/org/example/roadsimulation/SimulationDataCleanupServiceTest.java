package org.example.roadsimulation;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationDataCleanupServiceTest {

    @Mock
    private POIRepository poiRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @InjectMocks
    private SimulationDataCleanupService cleanupService;

    @Test
    void resetAllVehiclesToRandomInitializationPOIsDoesNotUseFixedChengduPOI() {
        POI warehouse = poi(10L, "Warehouse", POI.POIType.WAREHOUSE, "104.100000", "30.100000");
        Vehicle vehicle1 = vehicle(1L, "V-1");
        Vehicle vehicle2 = vehicle(2L, "V-2");

        when(poiRepository.findByPoiType(POI.POIType.WAREHOUSE)).thenReturn(List.of(warehouse));
        when(poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER)).thenReturn(List.of());
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle1, vehicle2));

        cleanupService.resetAllVehiclesToRandomInitializationPOIs();

        assertResetAtPOI(vehicle1, warehouse);
        assertResetAtPOI(vehicle2, warehouse);
        verify(poiRepository, never()).findById(3466L);
        verify(vehicleRepository).save(vehicle1);
        verify(vehicleRepository).save(vehicle2);
    }

    @Test
    void resetAllVehiclesToRandomInitializationPOIsFailsWhenNoCandidateExists() {
        when(poiRepository.findByPoiType(POI.POIType.WAREHOUSE)).thenReturn(List.of());
        when(poiRepository.findByPoiType(POI.POIType.DISTRIBUTION_CENTER)).thenReturn(List.of());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> cleanupService.resetAllVehiclesToRandomInitializationPOIs()
        );

        assertTrue(exception.getMessage().contains("随机重置车辆失败"));
        verify(vehicleRepository, never()).findAll();
        verify(vehicleRepository, never()).save(any());
    }

    private static void assertResetAtPOI(Vehicle vehicle, POI poi) {
        assertEquals(Vehicle.VehicleStatus.IDLE, vehicle.getCurrentStatus());
        assertNull(vehicle.getPreviousStatus());
        assertEquals(0L, vehicle.getStatusDurationSeconds());
        assertEquals(0, vehicle.getLoopCount());
        assertEquals(0.0, vehicle.getCurrentLoad());
        assertEquals(poi, vehicle.getCurrentPOI());
        assertEquals(poi.getLongitude(), vehicle.getCurrentLongitude());
        assertEquals(poi.getLatitude(), vehicle.getCurrentLatitude());
        assertEquals("System - Vehicle Reset", vehicle.getUpdatedBy());
        assertNotNull(vehicle.getUpdatedTime());
    }

    private static Vehicle vehicle(Long id, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setLicensePlate(plate);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING);
        vehicle.setPreviousStatus(Vehicle.VehicleStatus.ORDER_DRIVING);
        vehicle.setStatusDurationSeconds(99L);
        vehicle.setCurrentLoad(12.0);
        return vehicle;
    }

    private static POI poi(Long id, String name, POI.POIType type, String longitude, String latitude) {
        POI poi = new POI(name, new BigDecimal(longitude), new BigDecimal(latitude), type);
        poi.setId(id);
        return poi;
    }
}
