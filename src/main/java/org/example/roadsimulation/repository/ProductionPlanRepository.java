package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {
    Optional<ProductionPlan> findByPlanNo(String planNo);
    boolean existsByChainId(Long chainId);
    boolean existsByPlanNo(String planNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProductionPlan p
            SET p.status = :nextStatus, p.updatedAt = CURRENT_TIMESTAMP
            WHERE p.id = :planId AND p.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("planId") Long planId,
            @Param("expectedStatus") ProductionPlan.PlanStatus expectedStatus,
            @Param("nextStatus") ProductionPlan.PlanStatus nextStatus
    );
}
