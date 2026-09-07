package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {
    Optional<ProductionBatch> findByBatchNo(String batchNo);
    boolean existsByBatchNo(String batchNo);
    List<ProductionBatch> findByPlanId(Long planId);
}
