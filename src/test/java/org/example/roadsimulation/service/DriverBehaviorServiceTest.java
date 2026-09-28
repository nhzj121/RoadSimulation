package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.DriverRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.DoubleSupplier;

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

    private DriverBehaviorService service(FixedRolls rolls) {
        return new DriverBehaviorService(driverRepository, rolls);
    }

    @Test
    void idleTransitionsToMaintenanceWhenRollBelowP2() {
        Driver d = driver(Driver.DriverStatus.IDLE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.005)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.MAINTENANCE, d.getCurrentStatus());
        assertEquals("DriverBehaviorService", d.getUpdatedBy());
        assertEquals(SIM_NOW, d.getUpdatedTime());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void idleTransitionsToRejectingWhenRollBetweenP2AndP2PlusP1() {
        Driver d = driver(Driver.DriverStatus.IDLE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.02)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.REJECTING, d.getCurrentStatus());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void idleStaysIdleWhenRollAboveP2PlusP1() {
        Driver d = driver(Driver.DriverStatus.IDLE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.5)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.IDLE, d.getCurrentStatus());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectingReturnsToIdleBelowQ1() {
        Driver d = driver(Driver.DriverStatus.REJECTING);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.2)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.IDLE, d.getCurrentStatus());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void rejectingStaysRejectingAboveQ1() {
        Driver d = driver(Driver.DriverStatus.REJECTING);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.5)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.REJECTING, d.getCurrentStatus());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void maintenanceReturnsToIdleBelowQ2() {
        Driver d = driver(Driver.DriverStatus.MAINTENANCE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.1)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.IDLE, d.getCurrentStatus());
        verify(driverRepository).saveAll(anyList());
    }

    @Test
    void maintenanceStaysMaintenanceAboveQ2() {
        Driver d = driver(Driver.DriverStatus.MAINTENANCE);
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of(d));

        service(new FixedRolls(0.5)).tick(SIM_NOW);

        assertEquals(Driver.DriverStatus.MAINTENANCE, d.getCurrentStatus());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void tickQueriesOnlyBehavioralStatuses() {
        when(driverRepository.findByCurrentStatusIn(anyList())).thenReturn(List.of());

        service(new FixedRolls(0.5)).tick(SIM_NOW);

        verify(driverRepository).findByCurrentStatusIn(List.of(
                Driver.DriverStatus.IDLE,
                Driver.DriverStatus.REJECTING,
                Driver.DriverStatus.MAINTENANCE));
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void deterministicProtocolIsStableForRootSeedLoopAndDriverIdRegardlessOfRepositoryOrder() {
        DriverRepository firstRepository = mock(DriverRepository.class);
        Driver firstId11 = persistedDriver(11L, Driver.DriverStatus.IDLE);
        Driver firstId22 = persistedDriver(22L, Driver.DriverStatus.IDLE);
        when(firstRepository.findByCurrentStatusIn(anyList()))
                .thenReturn(List.of(firstId22, firstId11));

        DriverRepository repeatedRepository = mock(DriverRepository.class);
        Driver repeatedId11 = persistedDriver(11L, Driver.DriverStatus.IDLE);
        Driver repeatedId22 = persistedDriver(22L, Driver.DriverStatus.IDLE);
        when(repeatedRepository.findByCurrentStatusIn(anyList()))
                .thenReturn(List.of(repeatedId11, repeatedId22));

        deterministicService(firstRepository, "20260928", 7).tick(SIM_NOW);
        deterministicService(repeatedRepository, "20260928", 7).tick(SIM_NOW);

        assertEquals(firstId11.getCurrentStatus(), repeatedId11.getCurrentStatus());
        assertEquals(firstId22.getCurrentStatus(), repeatedId22.getCurrentStatus());
    }

    @Test
    void disabledBehaviorDoesNotReadOrMutateDrivers() {
        SimulationContext context = mock(SimulationContext.class);
        DriverBehaviorService service = new DriverBehaviorService(
                driverRepository, context, new ObjectMapper().findAndRegisterModules());
        service.setEnabled(false);

        service.tick(SIM_NOW);

        verify(driverRepository, never()).findByCurrentStatusIn(anyList());
        verify(driverRepository, never()).saveAll(anyList());
    }

    @Test
    void filterVehiclesWithIdleDriverExcludesVehiclesWithoutAnIdleDriver() {
        Vehicle maintenanceVehicle = vehicleWithDriver(Driver.DriverStatus.MAINTENANCE);
        Vehicle idleVehicle = vehicleWithDriver(Driver.DriverStatus.IDLE);
        Vehicle rejectingVehicle = vehicleWithDriver(Driver.DriverStatus.REJECTING);
        Vehicle noDriverVehicle = new Vehicle();

        List<Vehicle> filtered = service(new FixedRolls(0.5)).filterVehiclesWithIdleDriver(
                List.of(maintenanceVehicle, idleVehicle, rejectingVehicle, noDriverVehicle));

        assertEquals(List.of(idleVehicle), filtered);
    }

    @Test
    void filterVehiclesWithIdleDriverHandlesNullAndEmpty() {
        DriverBehaviorService service = service(new FixedRolls(0.5));
        List<Vehicle> empty = List.of();
        assertSame(empty, service.filterVehiclesWithIdleDriver(empty));
        assertNull(service.filterVehiclesWithIdleDriver(null));
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

    private Driver persistedDriver(long id, Driver.DriverStatus status) {
        Driver driver = driver(status);
        driver.setId(id);
        return driver;
    }

    private DriverBehaviorService deterministicService(
            DriverRepository repository,
            String rootSeed,
            int loopIndex
    ) {
        SimulationContext context = mock(SimulationContext.class);
        when(context.getLoopCount()).thenReturn(loopIndex);
        DriverBehaviorService service = new DriverBehaviorService(
                repository, context, new ObjectMapper().findAndRegisterModules());
        service.setEnabled(true);
        service.setOrdinaryRootSeed(rootSeed);
        service.setIdleToMaintenance(0.5);
        service.setIdleToRejecting(0.5);
        return service;
    }

    /** 固定返回值序列：超过序列长度时重复最后一个值。 */
    private static final class FixedRolls implements DoubleSupplier {
        private final double[] values;
        private int index;

        FixedRolls(double... values) {
            this.values = values;
        }

        @Override
        public double getAsDouble() {
            double value = values[Math.min(index, values.length - 1)];
            index++;
            return value;
        }
    }
}
