package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanFlowRepository extends JpaRepository<ProductionPlanFlow, Long> {
    List<ProductionPlanFlow> findByPlanId(Long planId);
    boolean existsByPlanId(Long planId);

    @Modifying
    @Query(value = "DELETE FROM production_plan_flow WHERE plan_id IN "
            + "(SELECT id FROM production_plan WHERE simulation_run_id IS NOT NULL)", nativeQuery = true)
    int deleteAutomaticPlanFlows();
}
