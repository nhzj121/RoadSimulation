package org.example.roadsimulation.service;

import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
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
    private RandomEventProperties properties;
    private Vehicle vehicle;
    private Assignment assignment;
    private LocalDateTime simNow;

    @BeforeEach
    void setUp() {
        properties = new RandomEventProperties();
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

        lenient().when(vehicleRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        lenient().when(assignmentRepository.findActiveAssignmentByVehicle(12L)).thenReturn(Optional.of(assignment));
        lenient().when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        lenient().when(eventRepository.findFirstByVehicleIdAndStatus(12L, TransportRandomEvent.EventStatus.ACTIVE))
                .thenReturn(Optional.empty());
        lenient().when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void manualCongestionUsesDrivingWorkInsteadOfBlanketTransitionBlock() {
        DrivingProgressService progress = mock(DrivingProgressService.class);
        when(progress.enabled()).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "drivingProgressService", progress);
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
        assertFalse(service.isTransitionBlocked(12L, simNow.plusMinutes(30)));
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

    @Test
    void activeEventAccumulatesDelayOnEachSimulationTick() {
        TransportRandomEvent event = service.triggerManually(
                TransportRandomEvent.EventType.TRAFFIC_CONGESTION,
                12L,
                60,
                simNow
        );
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));

        service.tick(simNow.plusMinutes(30), 30, 2);

        assertEquals(TransportRandomEvent.EventStatus.ACTIVE, event.getStatus());
        assertEquals(1800L, event.getDelaySeconds());
        verify(eventRepository, atLeast(2)).save(event);
    }

    @Test void activeBreakdownStillBlocksAtPlannedEndUntilRecoveryTick() {
        TransportRandomEvent event = service.triggerManually(
                TransportRandomEvent.EventType.VEHICLE_BREAKDOWN, 12L, 60, simNow);
        when(eventRepository.findFirstByVehicleIdAndStatus(12L, TransportRandomEvent.EventStatus.ACTIVE))
                .thenReturn(Optional.of(event));
        assertTrue(service.isTransitionBlocked(12L, event.getPlannedEndTime()));

        event.setEventType(TransportRandomEvent.EventType.TRAFFIC_CONGESTION);
        assertFalse(service.isTransitionBlocked(12L, event.getPlannedEndTime()));
    }

    @Test
    void disablingNewEventsStillResolvesPersistedDueEvent() {
        TransportRandomEvent event = service.triggerManually(
                TransportRandomEvent.EventType.TRAFFIC_CONGESTION,
                12L,
                60,
                simNow
        );
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        properties.setEnabled(false);

        service.tick(simNow.plusMinutes(60), 30, 3);

        assertEquals(TransportRandomEvent.EventStatus.RESOLVED, event.getStatus());
        assertEquals(3600L, event.getDelaySeconds());
    }

    @Test void assistanceBreakdownAdvancesHalfOpenPhasesAndIsIdempotent() {
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, TransportRandomEvent.BreakdownLevel.ASSISTANCE_REQUIRED, 30, 90, simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        assertEquals(TransportRandomEvent.BreakdownPhase.WAITING_RESCUE, event.getBreakdownPhase());
        service.tick(simNow.plusMinutes(30), 30, 1);
        assertEquals(TransportRandomEvent.BreakdownPhase.REPAIRING, event.getBreakdownPhase());
        assertEquals(simNow.plusMinutes(30), event.getRepairStartTime());
        service.tick(simNow.plusMinutes(150), 150, 2);
        service.tick(simNow.plusMinutes(150), 150, 2);
        assertEquals(TransportRandomEvent.BreakdownPhase.RECOVERED, event.getBreakdownPhase());
        assertEquals(simNow.plusMinutes(150), event.getRecoveryProcessedTime());
        assertEquals(simNow.plusMinutes(120), event.getResolvedTime());
        assertEquals("RESTORED", event.getRecoveryOutcome());
    }

    @Test void recoveryDoesNotOverwriteChangedAssignmentStageOrVehicleState() {
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        assignment.setCurrentActionIndex(2);
        vehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, simNow.plusMinutes(10), Duration.ZERO);
        service.tick(simNow.plusMinutes(60), 60, 1);
        assertEquals(Vehicle.VehicleStatus.IDLE, vehicle.getCurrentStatus());
        assertEquals("VEHICLE_STATUS_CHANGED", event.getRecoveryOutcome());
    }

    @Test void recoveryDoesNotOverwriteChangedAssignmentStatus() {
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
        service.tick(simNow.plusMinutes(60), 60, 1);
        assertEquals(Vehicle.VehicleStatus.BREAKDOWN, vehicle.getCurrentStatus());
        assertEquals("ASSIGNMENT_STATUS_CHANGED", event.getRecoveryOutcome());
    }

    @Test void recoveryDoesNotOverwriteChangedAssignmentLeg() {
        assignment.setCurrentActionIndex(1);
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        assignment.setCurrentActionIndex(2);
        service.tick(simNow.plusMinutes(60), 60, 1);
        assertEquals(Vehicle.VehicleStatus.BREAKDOWN, vehicle.getCurrentStatus());
        assertEquals("ASSIGNMENT_STAGE_CHANGED", event.getRecoveryOutcome());
    }

    @Test void automaticBreakdownUsesV2PolicyWithoutChangingOccurrencePolicy() {
        properties.setAutoEnabled(true);
        properties.getBreakdown().setHourlyProbability(1);
        properties.getCongestion().setHourlyProbability(0);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of());
        when(assignmentRepository.findActiveAssignments()).thenReturn(List.of(assignment));
        WeatherEnvironmentService weather = mock(WeatherEnvironmentService.class);
        var policy = new WeatherScenarioDTO.BreakdownPolicy("breakdown-v2", 1, 30, 60, 30, 60, 60, 120);
        when(weather.breakdownPolicy()).thenReturn(policy);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "weatherEnvironmentService", weather);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "breakdownDecisionPolicy", new BreakdownDecisionPolicy());

        service.tick(simNow, 60, 4);

        var saved = org.mockito.ArgumentCaptor.forClass(TransportRandomEvent.class);
        verify(eventRepository, atLeastOnce()).save(saved.capture());
        TransportRandomEvent event = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals("breakdown-v2", event.getBreakdownRuleVersion());
        assertEquals(TransportRandomEvent.BreakdownLevel.MINOR, event.getBreakdownLevel());
        assertEquals(0, event.getRescueWaitMinutes());
        assertTrue(event.getRepairMinutes() >= 30 && event.getRepairMinutes() <= 60);
    }

    @Test void missingVehicleGetsExplicitRecoveryOutcome() {
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        when(vehicleRepository.findByIdForUpdate(12L)).thenReturn(Optional.empty());
        service.tick(simNow.plusMinutes(60), 60, 1);
        assertEquals("VEHICLE_MISSING", event.getRecoveryOutcome());
        assertEquals(TransportRandomEvent.BreakdownPhase.RECOVERED, event.getBreakdownPhase());
    }

    @Test void validatesNewBreakdownParametersAndLegacyCompatibility() {
        assertThrows(IllegalArgumentException.class, () -> service.triggerManually(
                TransportRandomEvent.EventType.VEHICLE_BREAKDOWN, 12L, TransportRandomEvent.BreakdownLevel.MINOR, 30, 60, simNow));
        assertThrows(IllegalArgumentException.class, () -> service.triggerManually(
                TransportRandomEvent.EventType.TRAFFIC_CONGESTION, 12L, TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow));
        TransportRandomEvent legacy = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN, 12L, 60, simNow);
        assertNull(legacy.getBreakdownRuleVersion());
        assertEquals(assignment.getStatus(), legacy.getOriginalAssignmentStatus());
    }

    @Test void malformedShapeIsRejectedBeforeVehicleBusinessConflict() {
        assertThrows(IllegalArgumentException.class, () -> service.triggerManually(
                TransportRandomEvent.EventType.VEHICLE_BREAKDOWN, 12L, 60,
                TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow));
        verify(vehicleRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test void completedOriginalAssignmentReportsStatusRatherThanGenericAssignmentChange() {
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, TransportRandomEvent.BreakdownLevel.MINOR, 0, 60, simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
        when(assignmentRepository.findById(88L)).thenReturn(Optional.of(assignment));
        service.tick(simNow.plusMinutes(60), 60, 1);
        assertEquals("ASSIGNMENT_STATUS_CHANGED", event.getRecoveryOutcome());
    }
}
