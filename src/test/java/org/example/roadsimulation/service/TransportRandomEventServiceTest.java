package org.example.roadsimulation.service;

import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.VehicleReplacementAttempt;
import org.example.roadsimulation.dto.WeatherScenarioDTO;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.TransportRandomEventRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.repository.VehicleReplacementAttemptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

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
    @Mock
    private VehicleReplacementAttemptRepository replacementAttemptRepository;
    @Mock
    private EntityManager entityManager;

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
        lenient().when(replacementAttemptRepository.save(any(VehicleReplacementAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(vehicleRepository.reserveReplacementIfIdle(anyLong(),anyLong(),any())).thenAnswer(i->{
            Vehicle candidate=vehicleRepository.findByIdForUpdate(i.getArgument(0,Long.class)).orElse(null);
            if(candidate==null||candidate.getCurrentStatus()!=Vehicle.VehicleStatus.IDLE||candidate.getReplacementReservationEventId()!=null)return 0;
            candidate.reserveAsReplacement(i.getArgument(1,Long.class),i.getArgument(2,LocalDateTime.class));return 1;
        });
        lenient().when(vehicleRepository.releaseReplacementIfOwned(anyLong(),anyLong(),any())).thenAnswer(i->{
            Vehicle candidate=vehicleRepository.findByIdForUpdate(i.getArgument(0,Long.class)).orElse(null);
            if(candidate==null||candidate.getCurrentStatus()!=Vehicle.VehicleStatus.RESERVED_REPLACEMENT
                    ||!java.util.Objects.equals(candidate.getReplacementReservationEventId(),i.getArgument(1,Long.class)))return 0;
            candidate.releaseReplacementReservation(i.getArgument(2,LocalDateTime.class));return 1;
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "replacementAttemptRepository", replacementAttemptRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"entityManager",entityManager);
    }

    @Test void reservationCasDoesNotClearTheTickPersistenceContext() throws Exception {
        Modifying reserve=VehicleRepository.class.getMethod("reserveReplacementIfIdle",Long.class,Long.class,LocalDateTime.class)
                .getAnnotation(Modifying.class);
        Modifying release=VehicleRepository.class.getMethod("releaseReplacementIfOwned",Long.class,Long.class,LocalDateTime.class)
                .getAnnotation(Modifying.class);
        assertFalse(reserve.clearAutomatically());
        assertFalse(release.clearAutomatically());
    }

    @Test void successfulReservationRefreshesAStaleManagedCandidateBeforeEligibilityCheck() {
        Vehicle stale=candidate(21L,"T",10,10);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(stale));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(stale));
        doReturn(1).when(vehicleRepository).reserveReplacementIfIdle(eq(21L),anyLong(),any());
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(21L,TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);if(e.getId()==null)e.setId(101L);return e;});
        doAnswer(i->{stale.reserveAsReplacement(101L,simNow);return null;}).when(entityManager).refresh(stale,LockModeType.PESSIMISTIC_WRITE);

        TransportRandomEvent event=service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,
                TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow);

        assertEquals(21L,event.getReplacementVehicleId());
        assertEquals(101L,stale.getReplacementReservationEventId());
    }

    @Test void oneAutomaticTickCanReserveSeparateCandidatesForTwoBufferedAssignments() {
        properties.setAutoEnabled(true);properties.getBreakdown().setHourlyProbability(1);properties.getCongestion().setHourlyProbability(0);
        Vehicle original2=candidate(13L,"O2",10,10);original2.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,simNow,Duration.ofHours(1));
        Assignment assignment2=new Assignment();assignment2.setId(89L);assignment2.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);assignment2.setAssignedVehicle(original2);
        assignment.setNodes(new java.util.ArrayList<>());assignment2.setNodes(new java.util.ArrayList<>());
        Vehicle candidate1=candidate(21L,"T1",10,10),candidate2=candidate(22L,"T2",10,10);
        when(assignmentRepository.findActiveAssignments()).thenReturn(List.of(assignment,assignment2));
        when(vehicleRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(original2));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(candidate1));
        when(vehicleRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(candidate2));
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(candidate1,candidate2));
        when(assignmentRepository.findActiveAssignmentByVehicle(anyLong())).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(anyLong(),eq(TransportRandomEvent.EventStatus.ACTIVE))).thenReturn(Optional.empty());
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of());
        AtomicLong ids=new AtomicLong(100);List<TransportRandomEvent> saved=new CopyOnWriteArrayList<>();
        when(eventRepository.save(any())).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);if(e.getId()==null)e.setId(ids.incrementAndGet());saved.add(e);return e;});
        WeatherEnvironmentService weather=mock(WeatherEnvironmentService.class);
        when(weather.breakdownPolicy()).thenReturn(new WeatherScenarioDTO.BreakdownPolicy("breakdown-v3",.6,30,60,30,60,60,120,.1,60,90));
        BreakdownDecisionPolicy breakdown=mock(BreakdownDecisionPolicy.class);
        when(breakdown.decide(anyLong(),anyInt(),anyLong(),any())).thenReturn(new BreakdownDecisionPolicy.Decision(
                TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,0,0,60));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"weatherEnvironmentService",weather);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"breakdownDecisionPolicy",breakdown);

        service.tick(simNow,60,4);

        List<TransportRandomEvent> reserved=saved.stream().filter(e->e.getReplacementVehicleId()!=null).distinct().toList();
        assertEquals(2,reserved.size());
        assertEquals(java.util.Set.of(21L,22L),reserved.stream().map(TransportRandomEvent::getReplacementVehicleId).collect(java.util.stream.Collectors.toSet()));
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
    @Test void replacementProjectsPeakCapacityAcrossIncompleteNodes() {
        vehicle.setCurrentLoad(4.0); vehicle.setCurrentVolumn(3.0);
        var unload = new org.example.roadsimulation.entity.AssignmentNode();
        unload.setSequenceIndex(1); unload.setWeightDelta(-2.0); unload.setVolumeDelta(-1.0);
        var load = new org.example.roadsimulation.entity.AssignmentNode();
        load.setSequenceIndex(2); load.setWeightDelta(7.0); load.setVolumeDelta(6.0);
        assignment.setNodes(new java.util.ArrayList<>(List.of(unload, load)));
        var required = TransportRandomEventService.requiredCapacity(vehicle, assignment);
        assertEquals(9.0, required.load(), 1e-9); assertEquals(8.0, required.volume(), 1e-9);
    }

    @Test void replacementWaitsWithoutCandidateThenReservesBestSlackCandidateOnRetry() {
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(i -> {TransportRandomEvent e=i.getArgument(0);if(e.getId()==null)e.setId(101L);return e;});
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of());
        TransportRandomEvent event = service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L, null, TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED, null, null, 60, simNow);
        assertEquals(Vehicle.VehicleStatus.SCRAPPED, vehicle.getCurrentStatus());
        assertEquals(TransportRandomEvent.BreakdownPhase.WAITING_REPLACEMENT, event.getBreakdownPhase());
        assertNull(event.getPlannedEndTime());
        Vehicle roomy = candidate(20L, "R", 15, 12); Vehicle tight = candidate(21L, "T", 10, 10);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(roomy, tight));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(tight));
        when(assignmentRepository.findActiveAssignmentByVehicle(20L)).thenReturn(Optional.empty());
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(20L, TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(21L, TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        service.tick(simNow.plusMinutes(30), 30, 1);
        assertEquals(21L, event.getReplacementVehicleId());
        assertEquals(Vehicle.VehicleStatus.RESERVED_REPLACEMENT, tight.getCurrentStatus());
        assertEquals(simNow.plusMinutes(90), event.getReplacementReadyTime());
        verify(assignmentRepository).findActiveAssignmentByVehicleForUpdate(21L);
        assertTrue(service.isTransitionBlocked(21L,simNow.plusMinutes(31)));
    }

    @Test void replacementHandoffTransfersOwnershipStateAndSettlesLargeTickAfterReady() {
        vehicle.setCurrentLoad(4.0); vehicle.setCurrentVolumn(3.0);
        vehicle.setCurrentLongitude(java.math.BigDecimal.ONE); vehicle.setCurrentLatitude(java.math.BigDecimal.TEN);
        vehicle.addAssignment(assignment);
        Vehicle replacement=candidate(21L,"T",10,10);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(replacement));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(replacement));
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(21L, TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);e.setId(101L);return e;});
        DrivingProgressService progress=mock(DrivingProgressService.class); when(progress.enabled()).thenReturn(true);
        var sourceProgress=new org.example.roadsimulation.entity.DrivingProgress();sourceProgress.setPhaseKey("captured");
        sourceProgress.setAssignmentId(88L);sourceProgress.setVehicleId(12L);
        when(progress.settle(vehicle,simNow)).thenReturn(sourceProgress);
        when(progress.isTransferSourceValid(12L,assignment,"captured")).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"drivingProgressService",progress);
        TransportRandomEvent event=service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L,null,TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        LocalDateTime observed=simNow.plusMinutes(90); service.tick(observed,90,1);
        assertSame(replacement,assignment.getAssignedVehicle()); assertSame(assignment,replacement.getCurrentAssignment());
        assertNull(vehicle.getCurrentAssignment()); assertEquals(4.0,replacement.getCurrentLoad()); assertEquals(0.0,vehicle.getCurrentLoad());
        assertEquals(java.math.BigDecimal.ONE,replacement.getCurrentLongitude());
        assertEquals(Vehicle.VehicleStatus.TRANSPORT_DRIVING,replacement.getCurrentStatus());
        assertEquals(TransportRandomEvent.EventStatus.RESOLVED,event.getStatus()); assertEquals("REPLACED",event.getReplacementOutcome());
        verify(progress).transferAndSettle(vehicle.getId(), replacement, assignment,event.getOriginalDrivingPhaseKey(),event.getReplacementReadyTime(), observed);
        service.tick(observed.plusMinutes(30),30,2);
        verify(progress,times(1)).transferAndSettle(anyLong(),same(replacement),same(assignment),nullable(String.class),any(),any());
    }

    @Test void invalidatedCandidateIsCancelledWithoutOverwritingChangedStateAndRetriesLater() {
        Vehicle replacement=candidate(21L,"T",10,10);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(replacement),List.of());
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(replacement));
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(21L,TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);e.setId(101L);return e;});
        var attempt=new VehicleReplacementAttempt();attempt.setStatus(VehicleReplacementAttempt.AttemptStatus.RESERVED);
        when(replacementAttemptRepository.findFirstByEventIdAndStatusOrderByIdDesc(101L,VehicleReplacementAttempt.AttemptStatus.RESERVED))
                .thenReturn(Optional.of(attempt));
        TransportRandomEvent event=service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,
                TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow);
        replacement.releaseReplacementReservation(simNow.plusMinutes(10));
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        service.tick(simNow.plusMinutes(10),10,1);
        assertEquals(Vehicle.VehicleStatus.IDLE,replacement.getCurrentStatus());
        assertEquals(VehicleReplacementAttempt.AttemptStatus.CANCELLED,attempt.getStatus());
        assertNull(event.getReplacementVehicleId());assertNull(event.getPlannedEndTime());
        assertEquals(TransportRandomEvent.BreakdownPhase.WAITING_REPLACEMENT,event.getBreakdownPhase());
    }

    @Test void guardFailureReleasesReservedCandidateAndResolvesWithSpecificOutcome() {
        Vehicle replacement=candidate(21L,"T",10,10);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(replacement));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(replacement));
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(21L,TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);e.setId(101L);return e;});
        TransportRandomEvent event=service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,
                TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow);
        assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of(event));
        service.tick(simNow.plusMinutes(60),60,1);
        assertEquals("ASSIGNMENT_STATUS_CHANGED",event.getReplacementOutcome());
        assertEquals(TransportRandomEvent.EventStatus.RESOLVED,event.getStatus());
        assertEquals(Vehicle.VehicleStatus.IDLE,replacement.getCurrentStatus());
    }

    @Test void twoReplacementEventsCannotReserveTheSameVehicle() {
        Vehicle replacement=candidate(21L,"T",10,10);
        Vehicle secondOriginal=candidate(13L,"O2",10,10);
        secondOriginal.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,simNow.minusMinutes(5),Duration.ofHours(1));
        Assignment secondAssignment=new Assignment();secondAssignment.setId(89L);secondAssignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        secondAssignment.setAssignedVehicle(secondOriginal);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenReturn(List.of(replacement));
        when(vehicleRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(secondOriginal));
        when(vehicleRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(replacement));
        when(assignmentRepository.findActiveAssignmentByVehicle(13L)).thenReturn(Optional.of(secondAssignment));
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(13L,TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(21L,TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(Optional.empty());
        java.util.concurrent.atomic.AtomicLong ids=new java.util.concurrent.atomic.AtomicLong(100);
        when(eventRepository.save(any(TransportRandomEvent.class))).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);if(e.getId()==null)e.setId(ids.incrementAndGet());return e;});
        TransportRandomEvent first=service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,
                TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow);
        TransportRandomEvent second=service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,13L,null,
                TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow);
        assertEquals(21L,first.getReplacementVehicleId());assertNull(second.getReplacementVehicleId());
        assertEquals(TransportRandomEvent.BreakdownPhase.WAITING_REPLACEMENT,second.getBreakdownPhase());
    }

    @Test void concurrentReplacementEventsUseAtomicReservationAgainstDetachedStaleCandidates() throws Exception {
        Vehicle original2=candidate(13L,"O2",10,10);original2.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,simNow,Duration.ofHours(1));
        Assignment assignment2=new Assignment();assignment2.setId(89L);assignment2.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);assignment2.setAssignedVehicle(original2);
        CyclicBarrier staleReadBarrier=new CyclicBarrier(2);AtomicLong owner=new AtomicLong(0);AtomicLong ids=new AtomicLong(100);
        when(vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE)).thenAnswer(i->{staleReadBarrier.await(5,TimeUnit.SECONDS);return List.of(candidate(21L,"T",10,10));});
        doAnswer(i->owner.compareAndSet(0,i.getArgument(1))?1:0).when(vehicleRepository).reserveReplacementIfIdle(eq(21L),anyLong(),any());
        doAnswer(i->{long id=i.getArgument(0);if(id==12)return Optional.of(vehicle);if(id==13)return Optional.of(original2);
            Vehicle fresh=candidate(21L,"T",10,10);if(owner.get()!=0)fresh.reserveAsReplacement(owner.get(),simNow);return Optional.of(fresh);})
                .when(vehicleRepository).findByIdForUpdate(anyLong());
        when(assignmentRepository.findActiveAssignmentByVehicle(13L)).thenReturn(Optional.of(assignment2));
        when(assignmentRepository.findActiveAssignmentByVehicle(21L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByVehicleIdAndStatus(anyLong(),eq(TransportRandomEvent.EventStatus.ACTIVE))).thenReturn(Optional.empty());
        when(eventRepository.findByStatus(TransportRandomEvent.EventStatus.ACTIVE)).thenReturn(List.of());
        when(eventRepository.save(any())).thenAnswer(i->{TransportRandomEvent e=i.getArgument(0);if(e.getId()==null)e.setId(ids.incrementAndGet());return e;});
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<TransportRandomEvent> a=pool.submit(()->service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow));
            Future<TransportRandomEvent> b=pool.submit(()->service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,13L,null,TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow));
            List<TransportRandomEvent> results=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(e->e.getReplacementVehicleId()!=null).count());
            assertEquals(1,results.stream().filter(e->e.getBreakdownPhase()==TransportRandomEvent.BreakdownPhase.WAITING_REPLACEMENT).count());
        } finally {pool.shutdownNow();}
    }

    @Test void replacementFieldsAreValidatedBeforeVehicleLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,60,TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow));
        assertThrows(IllegalArgumentException.class, () -> service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,TransportRandomEvent.BreakdownLevel.MINOR,0,60,60,simNow));
        assertThrows(IllegalArgumentException.class, () -> service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,12L,null,TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,29,simNow));
        verify(vehicleRepository,never()).findByIdForUpdate(anyLong());
    }

    @Test void replacementRequiresAnActiveAssignedOrInProgressAssignment() {
        assignment.setStatus(Assignment.AssignmentStatus.WAITING);
        assertThrows(IllegalStateException.class,()->service.triggerManually(TransportRandomEvent.EventType.VEHICLE_BREAKDOWN,
                12L,null,TransportRandomEvent.BreakdownLevel.REPLACEMENT_REQUIRED,null,null,60,simNow));
    }

    @Test void protectedReplacementStatusesIgnoreOrdinaryTransitionsButResetToIdle() {
        vehicle.markScrapped(simNow);vehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE,simNow.plusMinutes(1),Duration.ZERO);
        assertEquals(Vehicle.VehicleStatus.SCRAPPED,vehicle.getCurrentStatus());vehicle.resetToIdle(simNow.plusMinutes(2));
        assertEquals(Vehicle.VehicleStatus.IDLE,vehicle.getCurrentStatus());vehicle.reserveAsReplacement(simNow.plusMinutes(3));
        vehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING,simNow.plusMinutes(4),Duration.ZERO);
        assertEquals(Vehicle.VehicleStatus.RESERVED_REPLACEMENT,vehicle.getCurrentStatus());vehicle.resetToIdle(simNow.plusMinutes(5));
        assertEquals(Vehicle.VehicleStatus.IDLE,vehicle.getCurrentStatus());
    }

    private Vehicle candidate(long id,String plate,double load,double volume){Vehicle v=new Vehicle();v.setId(id);v.setLicensePlate(plate);v.setMaxLoadCapacity(load);v.setCargoVolume(volume);v.transitionToStatus(Vehicle.VehicleStatus.IDLE,simNow,Duration.ZERO);return v;}
}
