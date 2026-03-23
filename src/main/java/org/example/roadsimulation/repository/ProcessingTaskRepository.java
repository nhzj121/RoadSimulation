package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingTaskRepository extends JpaRepository<ProcessingTask, Long> {
    
    List<ProcessingTask> findByProcessingOrderId(Long orderId);
    
    List<ProcessingTask> findByProcessingOrderIdOrderByStageStageOrderAsc(Long orderId);
    
    Optional<ProcessingTask> findFirstByProcessingOrderIdAndStatus(Long orderId, ProcessingTask.TaskStatus status);
    
    List<ProcessingTask> findByStatus(ProcessingTask.TaskStatus status);
    
    List<ProcessingTask> findByStageId(Long stageId);
}
