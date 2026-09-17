package org.example.roadsimulation.repository;

import org.example.roadsimulation.evaluation.NodeServiceEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Phase 7B：节点服务账本的唯一持久化入口。 */
@Repository
public interface NodeServiceEpisodeRepository extends JpaRepository<NodeServiceEpisode, Long> {

    Optional<NodeServiceEpisode> findByEventKey(String eventKey);

    Optional<NodeServiceEpisode> findFirstByAssignmentNodeIdOrderByIdDesc(Long assignmentNodeId);

    Optional<NodeServiceEpisode> findFirstByAssignmentIdAndActionTypeAndStatusOrderByServiceStartedAtDesc(
            Long assignmentId,
            NodeServiceEpisode.ActionType actionType,
            NodeServiceEpisode.Status status
    );
}
