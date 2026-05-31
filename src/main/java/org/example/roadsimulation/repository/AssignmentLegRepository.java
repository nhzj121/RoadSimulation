package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.AssignmentLeg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentLegRepository extends JpaRepository<AssignmentLeg, Long> {
    List<AssignmentLeg> findByAssignmentIdOrderBySequenceIndexAsc(Long assignmentId);
    List<AssignmentLeg> findByVehicleId(Long vehicleId);
    void deleteByAssignmentId(Long assignmentId);
}
