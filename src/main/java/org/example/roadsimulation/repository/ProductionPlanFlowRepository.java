package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionPlanFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanFlowRepository extends JpaRepository<ProductionPlanFlow, Long> {
    List<ProductionPlanFlow> findByPlanId(Long planId);
    boolean existsByPlanId(Long planId);
}
