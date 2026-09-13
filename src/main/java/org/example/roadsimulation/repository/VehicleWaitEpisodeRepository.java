package org.example.roadsimulation.repository;

import org.example.roadsimulation.evaluation.VehicleWaitEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Phase 9A-1：车辆可用空闲等待事实的持久化入口。 */
@Repository
public interface VehicleWaitEpisodeRepository extends JpaRepository<VehicleWaitEpisode, Long> {
    List<VehicleWaitEpisode> findAllBySimulationRunId(String simulationRunId);
}
