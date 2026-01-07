package org.example.roadsimulation;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionServiceImplTest {

    @Mock
    VehicleRepository vehicleRepository;

    @Mock
    AssignmentRepository assignmentRepository;

    @InjectMocks
    StateTransitionServiceImpl service;

    @BeforeEach
    void setUp() {
        // 单元测试不会自动触发 @PostConstruct，所以要手动初始化矩阵
        service.initTransitionMatrix();
    }

    @Test
    void whenAssignmentNull_shouldReturnNonNullState() {
        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.IDLE, null, new Vehicle()
        );
        assertNotNull(next);
    }

    @Test
    void whenAssignmentAssigned_shouldBeOrderDriving() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.ASSIGNED);

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.IDLE, a, new Vehicle()
        );

        assertEquals(VehicleStatus.ORDER_DRIVING, next);
    }

    @Test
    void whenAssignmentCompleted_shouldBeIdle() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.COMPLETED);

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.TRANSPORT_DRIVING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.IDLE, next);
    }

    @Test
    void whenAssignmentCancelled_shouldBeIdle() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.CANCELLED);

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.LOADING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.IDLE, next);
    }

    @Test
    void whenInProgress_actionIdLastDigit1_shouldBeLoading() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.IN_PROGRESS);
        a.setCurrentActionIndex(0);
        a.setActionLine(List.of(101L)); // 101 % 10 = 1

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.ORDER_DRIVING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.LOADING, next);
    }

    @Test
    void whenInProgress_actionIdLastDigit2_shouldBeTransportDriving() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.IN_PROGRESS);
        a.setCurrentActionIndex(0);
        a.setActionLine(List.of(102L)); // 2

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.LOADING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.TRANSPORT_DRIVING, next);
    }

    @Test
    void whenInProgress_actionIdLastDigit3_shouldBeUnloading() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.IN_PROGRESS);
        a.setCurrentActionIndex(0);
        a.setActionLine(List.of(103L)); // 3

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.TRANSPORT_DRIVING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.UNLOADING, next);
    }

    @Test
    void whenInProgress_actionIdLastDigit4_shouldBeWaiting() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.IN_PROGRESS);
        a.setCurrentActionIndex(0);
        a.setActionLine(List.of(104L)); // 4

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.UNLOADING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.WAITING, next);
    }

    @Test
    void whenInProgress_actionIdLastDigit5_shouldBeBreakdown() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.IN_PROGRESS);
        a.setCurrentActionIndex(0);
        a.setActionLine(List.of(105L)); // 5

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.WAITING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.BREAKDOWN, next);
    }

    @Test
    void whenInProgress_actionLineInvalid_shouldDefaultTransportDriving() {
        Assignment a = new Assignment();
        a.setStatus(AssignmentStatus.IN_PROGRESS);
        a.setCurrentActionIndex(0);
        a.setActionLine(List.of()); // 空

        VehicleStatus next = service.selectNextStateWithFullContext(
                VehicleStatus.LOADING, a, new Vehicle()
        );

        assertEquals(VehicleStatus.TRANSPORT_DRIVING, next);
    }
}
