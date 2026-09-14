package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingStageEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingStageEdgeRepository extends JpaRepository<ProcessingStageEdge, Long> {
    List<ProcessingStageEdge> findByChainId(Long chainId);
    boolean existsByChainId(Long chainId);
    boolean existsByToStageInputId(Long toStageInputId);
    boolean existsByFromStageId(Long fromStageId);
    boolean existsByToStageId(Long toStageId);
}
