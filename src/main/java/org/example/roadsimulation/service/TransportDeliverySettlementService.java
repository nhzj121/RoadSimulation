package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Phase 4：后端卸货完成后的唯一交付结算入口。
 *
 * <p>现有库存和 POI 内存映射仍集中在 {@link DataInitializer}，本阶段通过本服务
 * 收口调用，避免 Controller、状态机和前端各自触发结算。后续若拆分 DataInitializer，
 * 只需替换本服务内部依赖。</p>
 */
@Service
public class TransportDeliverySettlementService {

    private final DataInitializer dataInitializer;
    private final AssignmentRepository assignmentRepository;
    private final TransportLifecycleService transportLifecycleService;

    public TransportDeliverySettlementService(
            DataInitializer dataInitializer,
            AssignmentRepository assignmentRepository,
            TransportLifecycleService transportLifecycleService
    ) {
        this.dataInitializer = dataInitializer;
        this.assignmentRepository = assignmentRepository;
        this.transportLifecycleService = transportLifecycleService;
    }

    /**
     * Phase 4：按 Assignment 幂等结算库存、运单、车辆和任务终态。
     */
    @Transactional
    public void settleAfterBackendUnloading(
            Assignment assignment,
            Vehicle vehicle,
            POI endPOI,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignment == null || isClosed(assignment)) {
            // Phase 4：终态就是结算幂等门；重复 tick 不得再扣库存或释放车辆。
            return;
        }

        // Phase 4 修复：重取当前任务作为幂等依据；不再由 POI 映射猜测本次应完成的任务。
        Assignment refreshed = assignmentRepository.findById(assignment.getId()).orElse(assignment);
        if (isClosed(refreshed)) {
            return;
        }

        // Phase 4 修复：普通与 VRP 都按本任务货物项数量结算；共享展示映射缺失不再跳过库存。
        // 库存失败直接回滚，只有结算成功才允许核心生命周期进入 COMPLETED。
        dataInitializer.settleAssignmentInventory(refreshed);
        transportLifecycleService.completeDelivery(refreshed, vehicle, endPOI, simNow, actor);

        // Phase 4：生命周期完成后才清理运单共享缓存，多车同运单不会被首车提前清空。
        dataInitializer.completeAssignmentDeliveryCaches(refreshed);

        // Phase 4：兼容前端的简要任务缓存由后端结算同步，不再等 vehicle-arrived。
        dataInitializer.markAssignmentAsCompleted(assignment.getId());
    }

    // Phase 4：所有业务终态都共用同一幂等判断。
    private boolean isClosed(Assignment assignment) {
        return assignment.getStatus() == Assignment.AssignmentStatus.COMPLETED
                || assignment.getStatus() == Assignment.AssignmentStatus.CANCELLED
                || assignment.getStatus() == Assignment.AssignmentStatus.FAILED;
    }
}
