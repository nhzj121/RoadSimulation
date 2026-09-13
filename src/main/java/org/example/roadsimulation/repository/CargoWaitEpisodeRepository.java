package org.example.roadsimulation.repository;

import org.example.roadsimulation.evaluation.CargoWaitEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Phase 9A-1：货物首次运输等待事实的持久化入口。 */
@Repository
public interface CargoWaitEpisodeRepository extends JpaRepository<CargoWaitEpisode, Long> {
    List<CargoWaitEpisode> findAllBySimulationRunId(String simulationRunId);
}
