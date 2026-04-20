package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.AssignmentNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentNodeRepository extends JpaRepository<AssignmentNode, Long> {
}