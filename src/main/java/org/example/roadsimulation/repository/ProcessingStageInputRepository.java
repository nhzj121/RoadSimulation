package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingStageInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingStageInputRepository extends JpaRepository<ProcessingStageInput, Long> {
    List<ProcessingStageInput> findByStageIdOrderByInputKeyAsc(Long stageId);
    boolean existsByStageId(Long stageId);
}
