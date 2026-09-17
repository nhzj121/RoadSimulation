package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingExecutionFlowRepository extends JpaRepository<ProcessingExecutionFlow, Long> {
    List<ProcessingExecutionFlow> findByBatchId(Long batchId);
    List<ProcessingExecutionFlow> findByToExecutionId(Long toExecutionId);
    List<ProcessingExecutionFlow> findByFromExecutionId(Long fromExecutionId);
    List<ProcessingExecutionFlow> findByStatus(ProcessingExecutionFlow.FlowStatus status);
    Optional<ProcessingExecutionFlow> findByShipmentId(Long shipmentId);
}
