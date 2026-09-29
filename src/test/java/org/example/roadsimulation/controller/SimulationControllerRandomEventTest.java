package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ACK never replaces backend recovery, unloading or inventory delivery. */
class SimulationControllerRandomEventTest {
    private final AssignmentRepository assignments=mock(AssignmentRepository.class);
    private final SimulationController controller=new SimulationController();
    private final Vehicle vehicle=new Vehicle();
    private final Assignment assignment=new Assignment();

    SimulationControllerRandomEventTest(){
        ReflectionTestUtils.setField(controller,"assignmentRepository",assignments);
        vehicle.setId(21L);vehicle.setCurrentLoad(7.0);vehicle.setCurrentVolumn(3.0);
        assignment.setId(88L);assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        vehicle.addAssignment(assignment);
        when(assignments.findById(88L)).thenReturn(Optional.of(assignment));
    }
    private SimulationController.VehicleArrivedRequest arrival(){
        var request=new SimulationController.VehicleArrivedRequest();
        request.setAssignmentId(88L);request.setVehicleId(21L);request.setEndPOIId(20L);
        return request;
    }
    @ParameterizedTest
    @EnumSource(value=Vehicle.VehicleStatus.class,names={"ORDER_DRIVING","TRANSPORT_DRIVING","BREAKDOWN","UNLOADING","SCRAPPED"})
    void arrivalOnlyAcknowledgesWithoutCompletingOrRecovering(Vehicle.VehicleStatus status){
        vehicle.setCurrentStatus(status);
        var request=arrival();request.setReplacementEventId(101L);
        assertEquals(HttpStatus.OK,controller.handleVehicleArrived(request).getStatusCode());
        request.setReplacementEventId(999L);
        assertEquals(HttpStatus.OK,controller.handleVehicleArrived(request).getStatusCode());
        assertEquals(Assignment.AssignmentStatus.IN_PROGRESS,assignment.getStatus());
        assertEquals(status,vehicle.getCurrentStatus());assertEquals(7.0,vehicle.getCurrentLoad());
        verify(assignments,never()).save(any());
    }
    @Test void oldVehicleCannotAcknowledgeAfterOwnershipChanges(){
        var request=arrival();request.setVehicleId(12L);
        assertEquals(HttpStatus.BAD_REQUEST,controller.handleVehicleArrived(request).getStatusCode());
        assertEquals(21L,assignment.getAssignedVehicle().getId());
        verify(assignments,never()).save(any());
    }
    @Test void missingIdentityAndUnknownAssignmentAreRejected(){
        assertEquals(HttpStatus.BAD_REQUEST,controller.handleVehicleArrived(null).getStatusCode());
        var request=arrival();request.setEndPOIId(null);
        assertEquals(HttpStatus.BAD_REQUEST,controller.handleVehicleArrived(request).getStatusCode());
        request=arrival();request.setAssignmentId(999L);
        assertEquals(HttpStatus.BAD_REQUEST,controller.handleVehicleArrived(request).getStatusCode());
        verify(assignments,never()).save(any());
    }
    @Test void loadedAcknowledgementCannotChangeCargoOrVehicleStatus(){
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.LOADING);
        var request=new SimulationController.AssignmentLoadedRequest();
        request.setAssignmentId(88L);request.setVehicleId(21L);
        assertEquals(HttpStatus.OK,controller.handleAssignmentLoaded(request).getStatusCode());
        assertEquals(Vehicle.VehicleStatus.LOADING,vehicle.getCurrentStatus());
        assertEquals(7.0,vehicle.getCurrentLoad());assertEquals(3.0,vehicle.getCurrentVolumn());
        assertEquals(Assignment.AssignmentStatus.IN_PROGRESS,assignment.getStatus());
        verify(assignments,never()).save(any());
    }
}
