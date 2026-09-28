package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.AssignmentDriverHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentDriverHistoryRepository extends JpaRepository<AssignmentDriverHistory, Long> {
    List<AssignmentDriverHistory> findByAssignmentIdOrderByCreatedTimeAsc(Long assignmentId);
}
