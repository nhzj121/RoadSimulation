package org.example.roadsimulation.repository;

import org.example.roadsimulation.evaluation.EvaluationSnapshotHistoryRecord;
import org.example.roadsimulation.evaluation.EvaluationRunKind;
import org.example.roadsimulation.evaluation.EvaluationSnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Phase 9C：只服务于评价历史，不参与运输业务查询或仿真 reset。 */
@Repository
public interface EvaluationSnapshotHistoryRepository
        extends JpaRepository<EvaluationSnapshotHistoryRecord, Long> {

    /** Phase 9C：运行列表不读取 LONGTEXT 快照，避免历史增长后每次打开看板都加载全部 JSON。 */
    @Query("""
            select r.contractVersion as contractVersion,
                   r.simulationRunId as simulationRunId,
                   r.runKind as runKind,
                   r.loopIndex as loopIndex,
                   r.snapshotRevision as snapshotRevision,
                   r.simTime as simTime,
                   r.snapshotStatus as snapshotStatus
              from EvaluationSnapshotHistoryRecord r
             order by r.simulationRunId asc, r.snapshotRevision asc
            """)
    List<MetadataProjection> findAllMetadataOrderByRunAndRevision();

    List<EvaluationSnapshotHistoryRecord>
    findAllBySimulationRunIdOrderBySnapshotRevisionAsc(String simulationRunId);

    interface MetadataProjection {
        String getContractVersion();
        String getSimulationRunId();
        EvaluationRunKind getRunKind();
        Integer getLoopIndex();
        Long getSnapshotRevision();
        LocalDateTime getSimTime();
        EvaluationSnapshotStatus getSnapshotStatus();
    }
}
