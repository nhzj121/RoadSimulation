package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.DispatchComparisonStrategyRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispatchComparisonStrategyRunRepository extends JpaRepository<DispatchComparisonStrategyRun, Long> {

    List<DispatchComparisonStrategyRun> findByExperimentRunIdOrderByStartedAtAsc(Long experimentRunId);

    Optional<DispatchComparisonStrategyRun> findTopByExperimentRunIdAndStrategyOrderByStartedAtDesc(
            Long experimentRunId,
            String strategy
    );
}
