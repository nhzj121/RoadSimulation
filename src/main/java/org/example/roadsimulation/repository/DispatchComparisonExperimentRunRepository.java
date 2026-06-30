package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.DispatchComparisonExperimentRun;
import org.example.roadsimulation.entity.DispatchComparisonExperimentRun.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface DispatchComparisonExperimentRunRepository extends JpaRepository<DispatchComparisonExperimentRun, Long> {

    Optional<DispatchComparisonExperimentRun> findTopByOrderByCreatedAtDesc();

    Optional<DispatchComparisonExperimentRun> findTopByStatusInOrderByCreatedAtDesc(Collection<RunStatus> statuses);
}
