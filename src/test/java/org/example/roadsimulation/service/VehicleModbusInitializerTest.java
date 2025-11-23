package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleModbusInitializerTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleModbusInitializer initializer;

    private Vehicle unconfiguredVehicle;
    private Vehicle configuredVehicle;

    @BeforeEach
    void setUp() {
        unconfiguredVehicle = new Vehicle();
        unconfiguredVehicle.setId(1L);
        unconfiguredVehicle.setLicensePlate("京A11111");
        unconfiguredVehicle.setModbusSlaveId(null);

        configuredVehicle = new Vehicle();
        configuredVehicle.setId(2L);
        configuredVehicle.setLicensePlate("京A22222");
        configuredVehicle.setModbusSlaveId(1);
    }

    @Test
    void testInitModbusConfig_WithUnconfiguredVehicles() {
        // Given
        List<Vehicle> vehicles = Arrays.asList(unconfiguredVehicle);
        when(vehicleRepository.findAll()).thenReturn(vehicles);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(unconfiguredVehicle);

        // When
        initializer.initModbusConfig();

        // Then
        verify(vehicleRepository).save(argThat(vehicle ->
                vehicle.getModbusSlaveId() != null &&
                        vehicle.getModbusSlaveId() == 1 &&
                        vehicle.getIsOnline() &&
                        vehicle.getCurrentLongitude() != null &&
                        vehicle.getCurrentLatitude() != null
        ));
    }

    @Test
    void testInitModbusConfig_WithConfiguredVehicles() {
        // Given
        List<Vehicle> vehicles = Arrays.asList(configuredVehicle);
        when(vehicleRepository.findAll()).thenReturn(vehicles);

        // When
        initializer.initModbusConfig();

        // Then - 已配置的车辆不应该被修改
        verify(vehicleRepository, never()).save(configuredVehicle);
    }

    @Test
    void testInitModbusConfig_MixedVehicles() {
        // Given
        List<Vehicle> vehicles = Arrays.asList(unconfiguredVehicle, configuredVehicle);
        when(vehicleRepository.findAll()).thenReturn(vehicles);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(unconfiguredVehicle);

        // When
        initializer.initModbusConfig();

        // Then - 只为未配置的车辆分配Slave ID
        verify(vehicleRepository, times(1)).save(argThat(vehicle ->
                vehicle.getModbusSlaveId() != null
        ));
    }

    @Test
    void testResetModbusConfig() {
        // Given
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(unconfiguredVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(unconfiguredVehicle);

        // When
        initializer.resetModbusConfig(1L);

        // Then
        verify(vehicleRepository).save(argThat(vehicle ->
                vehicle.getModbusSlaveId() == null &&
                        !vehicle.getIsOnline()
        ));
    }

    @Test
    void testResetModbusConfig_VehicleNotFound() {
        // Given
        when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        initializer.resetModbusConfig(999L);

        // Then - 不应该尝试保存
        verify(vehicleRepository, never()).save(any());
    }
}