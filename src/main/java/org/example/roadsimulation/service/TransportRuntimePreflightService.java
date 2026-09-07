package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.repository.AssignmentLegRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 4：仿真开始前的只读运输数据一致性门禁。
 *
 * <p>不再无条件删除上一次数据；只在发现 Phase 3 终态遗留或全新 loop
 * 无法安全继续的活动进度时拒绝启动，并明确提示现有 reset 入口。</p>
 */
@Service
public class TransportRuntimePreflightService {

    private final AssignmentLegRepository assignmentLegRepository;

    public TransportRuntimePreflightService(AssignmentLegRepository assignmentLegRepository) {
        this.assignmentLegRepository = assignmentLegRepository;
    }

    /**
     * Phase 4：检查当前 loop 是否可以不清库直接开始/继续仿真。
     */
    @Transactional(readOnly = true)
    public void assertReadyToStart(int currentLoopIndex) {
        long completedWithIncompleteLegs = assignmentLegRepository.countCompletedAssignmentsWithIncompleteLegs(
                Assignment.AssignmentStatus.COMPLETED,
                AssignmentLeg.ProgressStatus.COMPLETED
        );
        if (completedWithIncompleteLegs > 0L) {
            throw new IllegalStateException(
                    "Phase 4 preflight rejected " + completedWithIncompleteLegs
                            + " COMPLETED assignment(s) with unfinished legs; call /api/simulation/reset first"
            );
        }

        long activeAtOrAheadOfLoop = assignmentLegRepository.countActiveAssignmentsAtOrAheadOfLoop(
                Assignment.AssignmentStatus.IN_PROGRESS,
                currentLoopIndex
        );
        if (activeAtOrAheadOfLoop > 0L) {
            throw new IllegalStateException(
                    "Phase 4 preflight found " + activeAtOrAheadOfLoop
                            + " active assignment(s) whose saved loop is not older than current loop; "
                            + "this usually means an unclean process restart, call /api/simulation/reset first"
            );
        }
    }
}
