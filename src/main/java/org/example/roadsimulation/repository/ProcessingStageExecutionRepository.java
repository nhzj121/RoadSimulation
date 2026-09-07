package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingStageExecutionRepository extends JpaRepository<ProcessingStageExecution, Long> {
    List<ProcessingStageExecution> findByBatchIdOrderByStageOrderAsc(Long batchId);
    List<ProcessingStageExecution> findByStatus(ProcessingStageExecution.ExecutionStatus status);
    Optional<ProcessingStageExecution> findByBatchIdAndStageOrder(Long batchId, Integer stageOrder);
    Optional<ProcessingStageExecution> findByInboundShipmentId(Long inboundShipmentId);
}
