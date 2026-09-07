package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {
    Optional<ProductionPlan> findByPlanNo(String planNo);
    boolean existsByPlanNo(String planNo);
}
