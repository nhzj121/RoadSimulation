package org.example.roadsimulation.repository;

import org.example.roadsimulation.evaluation.TaskWaitEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Phase 9A-1：任务响应和启动等待事实的持久化入口。 */
@Repository
public interface TaskWaitEpisodeRepository extends JpaRepository<TaskWaitEpisode, Long> {
    List<TaskWaitEpisode> findAllBySimulationRunId(String simulationRunId);
}
