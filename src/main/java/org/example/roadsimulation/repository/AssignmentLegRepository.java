package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssignmentLegRepository extends JpaRepository<AssignmentLeg, Long> {
    // Phase 2：按稳定 sequenceIndex 返回完整路段快照，供详情投影和后续进度服务使用。
    List<AssignmentLeg> findByAssignmentIdOrderBySequenceIndexAsc(Long assignmentId);

    // Phase 2：currentLegIndex 必须通过任务 ID + 序号精确定位，禁止依赖数据库自增 ID 的偶然顺序。
    Optional<AssignmentLeg> findByAssignmentIdAndSequenceIndex(Long assignmentId, Integer sequenceIndex);

    // Phase 2：车辆聚合继续读取该车辆的单次模拟路段数据。
    List<AssignmentLeg> findByVehicleId(Long vehicleId);

    // Phase 2：整组删除仅允许在全局 reset 或 TransportMetricsService 的执行前保护检查之后调用。
    void deleteByAssignmentId(Long assignmentId);

    // Phase 4：只查找仍有未完成路段的权威推进候选；调用方仅传 IN_PROGRESS。
    @Query("""
            select distinct leg.assignment.id
            from AssignmentLeg leg
            where leg.progressStatus <> :completedProgress
              and leg.assignment.status in :eligibleAssignmentStatuses
            order by leg.assignment.id
            """)
    List<Long> findProgressCandidateAssignmentIds(
            @Param("completedProgress") AssignmentLeg.ProgressStatus completedProgress,
            @Param("eligibleAssignmentStatuses") Collection<Assignment.AssignmentStatus> eligibleAssignmentStatuses
    );

    // Phase 4：终态任务仍有未完成路段表示 Phase 3 遗留或异常中断，不允许带入新语义。
    @Query("""
            select count(distinct leg.assignment.id)
            from AssignmentLeg leg
            where leg.assignment.status = :completedAssignmentStatus
              and leg.progressStatus <> :completedProgress
            """)
    long countCompletedAssignmentsWithIncompleteLegs(
            @Param("completedAssignmentStatus") Assignment.AssignmentStatus completedAssignmentStatus,
            @Param("completedProgress") AssignmentLeg.ProgressStatus completedProgress
    );

    // Phase 4：全新 JVM 的 loop 会从 0 开始；若活动路段已记录当前/更大 loop，继续运行会倒序覆盖进度。
    @Query("""
            select count(distinct leg.assignment.id)
            from AssignmentLeg leg
            where leg.assignment.status = :activeStatus
              and leg.lastProcessedLoopIndex is not null
              and leg.lastProcessedLoopIndex >= :currentLoopIndex
            """)
    long countActiveAssignmentsAtOrAheadOfLoop(
            @Param("activeStatus") Assignment.AssignmentStatus activeStatus,
            @Param("currentLoopIndex") Integer currentLoopIndex
    );
}
