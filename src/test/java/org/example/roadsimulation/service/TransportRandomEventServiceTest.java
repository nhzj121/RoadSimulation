package org.example.roadsimulation.service;

import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.TransportRandomEventRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransportRandomEventServiceTest {

    @Mock
    private TransportRandomEventRepository eventRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private AssignmentRepository assignmentRepository;

    private TransportRandomEventService service;
    private Vehicle vehicle;
    private Assignment assignment;
    private LocalDateTime simNow;

    @BeforeEach
    void setUp() {
        RandomEventProperties properties = new RandomEventProperties();
        service = new TransportRandomEventService(
                eventRepository,
                vehicleRepository,
                assignmentRepository,
                new RandomEventDecisionPolicy(),
                properties
        );
        simNow = LocalDateTime.of(2026, 1, 1, 8, 0);
        vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setLicensePlate("川A-0012");
        vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING, simNow.minusMinutes(30), Duration.ofMinutes(120));
        assignment = new Assignment();
        assignment.setId(88L);
        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        assignment.setAssignedVehicle(vehicle);

        when(vehicleRepository.findById(12L)).thenReturn(Optional.of(vehicle));
        when(assignmentRepository.findActiveAssignmentByVehicle(12L)).thenReturn(Optional.of(assignment));
        when(eventRepository.findFirstByVehicleIdAndStatus(12L, TransportRandomEvent.EventStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void manualCongestionBlocksProgressWithoutReplacingDrivingStatus() {
        TransportRandomEvent event = service.triggerManually(
                TransportRandomEvent.EventType.TRAFFIC_CONGESTION,
                12L,
                60,
                simNow
        );
        when(eventRepository.findFirstByVehicleIdAndStatus(12L, TransportRandomEvent.EventStatus.ACTIVE))
                .thenReturn(Optional.of(event));

        assertEquals(TransportRandomEvent.EventStatus.ACTIVE, event.getStatus());
        assertEquals(simNow.plusMinutes(60), event.getPlannedEndTime());
        assertEquals(0.4, event.getSpeedFactor(), 1e-9);
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING, vehicle.getCurrentStatus());
        assertTrue(service.isTransitionBlocked(12L, simNow.plusMinutes(30)));
    }

    @Test
    void breakdownRestoresInterruptedStateAndRemainingDurationWhenResolved() {
        TransportRandomEvent event = service.triggerManually(
                TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L,
                60,
                simNow
        );
        assertEquals(Vehicle.VehicleStatus.BREAKDOWN, vehicle.getCurrentStatus());
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING, event.getPreviousVehicleStatus());
        assertEquals(5400L, event.getRemainingStatusSeconds());

        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));

        service.tick(simNow.plusMinutes(60), 30, 4);

        assertEquals(TransportRandomEvent.EventStatus.RESOLVED, event.getStatus());
        assertEquals(3600L, event.getDelaySeconds());
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING, vehicle.getCurrentStatus());
        assertEquals(Duration.ofSeconds(5400), vehicle.getStatusDuration());
    }
}
