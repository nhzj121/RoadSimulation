package org.example.roadsimulation.repository;

import org.example.roadsimulation.evaluation.DeliverySlaFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Phase 9B-2：确定性交付 SLA 评价事实的持久化入口。 */
@Repository
public interface DeliverySlaFactRepository extends JpaRepository<DeliverySlaFact, Long> {
    List<DeliverySlaFact> findAllBySimulationRunId(String simulationRunId);
}
