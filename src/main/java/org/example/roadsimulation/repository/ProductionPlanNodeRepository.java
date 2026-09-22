package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionPlanNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanNodeRepository extends JpaRepository<ProductionPlanNode, Long> {
    List<ProductionPlanNode> findByPlanIdOrderByStageOrderAsc(Long planId);
    boolean existsByStageId(Long stageId);

    @Modifying
    @Query(value = "DELETE FROM production_plan_node WHERE plan_id IN "
            + "(SELECT id FROM production_plan WHERE simulation_run_id IS NOT NULL)", nativeQuery = true)
    int deleteAutomaticPlanNodes();
}
