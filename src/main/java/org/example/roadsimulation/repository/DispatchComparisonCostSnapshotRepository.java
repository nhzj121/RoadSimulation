package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.DispatchComparisonCostSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchComparisonCostSnapshotRepository extends JpaRepository<DispatchComparisonCostSnapshot, Long> {

    List<DispatchComparisonCostSnapshot> findByStrategyRunIdOrderByLoopCountAsc(Long strategyRunId);
}
