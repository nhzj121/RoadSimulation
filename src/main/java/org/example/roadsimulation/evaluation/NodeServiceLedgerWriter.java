package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.repository.NodeServiceEpisodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Phase 7B：在独立事务中执行节点服务观察事件的幂等账本写入。 */
@Service
public class NodeServiceLedgerWriter {

    private static final Logger log = LoggerFactory.getLogger(NodeServiceLedgerWriter.class);

    private final NodeServiceEpisodeRepository repository;

    public NodeServiceLedgerWriter(NodeServiceEpisodeRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void project(NodeServiceObservation observation) {
        if (observation.type() == NodeServiceObservation.Type.SERVICE_STARTED) {
            projectStart(observation);
        } else {
            projectCompletion(observation);
        }
    }

    private void projectStart(NodeServiceObservation observation) {
        // Phase 7B：eventKey 同时承担应用级和数据库级幂等，重复路段完成通知不会重复计数。
        if (repository.findByEventKey(observation.eventKey()).isPresent()) {
            return;
        }
        repository.save(NodeServiceEpisode.start(observation));
    }

    private void projectCompletion(NodeServiceObservation observation) {
        Optional<NodeServiceEpisode> candidate = observation.assignmentNodeId() != null
                ? repository.findFirstByAssignmentNodeIdOrderByIdDesc(observation.assignmentNodeId())
                : repository.findFirstByAssignmentIdAndActionTypeAndStatusOrderByServiceStartedAtDesc(
                        observation.assignmentId(),
                        observation.actionType(),
                        NodeServiceEpisode.Status.IN_SERVICE
                );
        if (candidate.isEmpty()) {
            // Phase 7B：缺少开始事件时不伪造到达时间；该服务样本保持不可观测并留日志诊断。
            log.warn(
                    "[Phase7B NodeServiceLedger] completion has no matching start: assignmentId={}, nodeId={}, action={}",
                    observation.assignmentId(), observation.assignmentNodeId(), observation.actionType()
            );
            return;
        }

        NodeServiceEpisode episode = candidate.get();
        if (episode.getStatus() == NodeServiceEpisode.Status.COMPLETED) {
            return;
        }
        episode.complete(observation.occurredAt(), observation.processedTonnes());
        repository.save(episode);
    }
}
