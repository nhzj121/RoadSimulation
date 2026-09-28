package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.DriverRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverBehaviorServiceTest {

    private static final LocalDateTime SIM_NOW = LocalDateTime.of(2026, 9, 18, 10, 0);

    private final DriverRepository driverRepository = mock(DriverRepository.class);

    private DriverBehaviorService service(FixedRandom random) {
        return new DriverBehaviorService(driverRepository, random);
    }

    @Test
    void idleTransitionsToMaintenanceWhenRollBelowP2() {
        Driver d = driver(Driver.DriverStatus.IDLE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.005)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.MAINTENANCE, d.getCurrentStatus());
        assertEquals("DriverBehaviorService", d.getUpdatedBy());
        assertEquals(SIM_NOW, d.getUpdatedTime());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void idleTransitionsToRejectingWhenRollBetweenP2AndP2PlusP1() {
        Driver d = driver(Driver.DriverStatus.IDLE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.02)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.REJECTING, d.getCurrentStatus());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void idleStaysIdleWhenRollAboveP2PlusP1() {
        Driver d = driver(Driver.DriverStatus.IDLE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.5)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.IDLE, d.getCurrentStatus());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectingReturnsToIdleBelowQ1() {
        Driver d = driver(Driver.DriverStatus.REJECTING);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.2)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.IDLE, d.getCurrentStatus());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void rejectingStaysRejectingAboveQ1() {
        Driver d = driver(Driver.DriverStatus.REJECTING);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.5)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.REJECTING, d.getCurrentStatus());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void maintenanceReturnsToIdleBelowQ2() {
        Driver d = driver(Driver.DriverStatus.MAINTENANCE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.1)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.IDLE, d.getCurrentStatus());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void maintenanceStaysMaintenanceAboveQ2() {
        Driver d = driver(Driver.DriverStatus.MAINTENANCE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRandom(0.5)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.MAINTENANCE, d.getCurrentStatus());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void tickQueriesOnlyBehavioralStatuses() {
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of());

        service(new FixedRandom(0.5)).tick(SIM_NOW);

        verify(driverRepository).findByCurrentStatusIn(List.of(
                Driver.DriverStatus.IDLE,
                Driver.DriverStatus.REJECTING,
                Driver.DriverStatus.MAINTENANCE));
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void filterMaintenanceVehiclesRemovesVehiclesWithMaintenanceDriver() {
        Vehicle maintenanceVehicle = vehicleWithDriver(Driver.DriverStatus.MAINTENANCE);
        Vehicle idleVehicle = vehicleWithDriver(Driver.DriverStatus.IDLE);
        Vehicle rejectingVehicle = vehicleWithDriver(Driver.DriverStatus.REJECTING);
        Vehicle noDriverVehicle = new Vehicle();

        List<Vehicle> filtered = service(new FixedRandom(0.5)).filterMaintenanceVehicles(
                List.of(maintenanceVehicle, idleVehicle, rejectingVehicle, noDriverVehicle));

        assertEquals(List.of(idleVehicle, rejectingVehicle, noDriverVehicle), filtered);
    }

    @Test
    void filterMaintenanceVehiclesHandlesNullAndEmpty() {
        DriverBehaviorService service = service(new FixedRandom(0.5));
        List<Vehicle> empty = List.of();
        assertSame(empty, service.filterMaintenanceVehicles(empty));
        assertNull(service.filterMaintenanceVehicles(null));
    }

    private Vehicle vehicleWithDriver(Driver.DriverStatus status) {
        Vehicle vehicle = new Vehicle();
        driver(status).addVehicle(vehicle);
        return vehicle;
    }

    private Driver driver(Driver.DriverStatus status) {
        Driver d = new Driver();
        d.setCurrentStatus(status);
        return d;
    }

    /** 固定返回值序列的 Random：超过序列长度时重复最后一个值。 */
    private static final class FixedRandom extends Random {
        private final double[] values;
        private int index;

        FixedRandom(double... values) {
            this.values = values;
        }

        @Override
        public double nextDouble() {
            double value = values[Math.min(index, values.length - 1)];
            index++;
            return value;
        }
    }
}
