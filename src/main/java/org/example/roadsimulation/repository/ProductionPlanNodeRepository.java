package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionPlanNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanNodeRepository extends JpaRepository<ProductionPlanNode, Long> {
    List<ProductionPlanNode> findByPlanIdOrderByStageOrderAsc(Long planId);
}
