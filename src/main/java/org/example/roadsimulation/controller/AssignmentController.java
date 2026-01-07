package org.example.roadsimulation.controller;

import jakarta.validation.Valid;
import org.example.roadsimulation.dto.AssignmentBriefDTO;
import org.example.roadsimulation.dto.AssignmentDTO;
import org.example.roadsimulation.dto.AssignmentRequestDTO;
import org.example.roadsimulation.dto.AssignmentResponseDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.service.AssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * AssignmentController - 任务分配管理REST API控制器
 */
@Validated
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentController.class);

    private final AssignmentService assignmentService;

    @Autowired
    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    // ============================
    // 创建任务分配相关接口
    // ============================

    /**
     * 创建新任务分配
     */
    @PostMapping
    public ResponseEntity<AssignmentResponseDTO> createAssignment(@Valid @RequestBody AssignmentRequestDTO requestDTO) {
        logger.info("开始创建新任务分配");

        try {
            AssignmentResponseDTO createdAssignment = assignmentService.createAssignment(requestDTO);

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(createdAssignment.getId())
                    .toUri();

            logger.info("任务分配创建完成，ID: {}, 状态: {}", createdAssignment.getId(), createdAssignment.getStatus());
            return ResponseEntity.created(location).body(createdAssignment);
        } catch (IllegalArgumentException e) {
            logger.error("任务分配创建业务异常: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("任务分配创建系统异常: ", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 批量创建任务分配
     */
    @PostMapping("/batch")
    public ResponseEntity<List<AssignmentResponseDTO>> batchCreateAssignments(@Valid @RequestBody List<AssignmentRequestDTO> requestDTOs) {
        logger.info("开始批量创建任务分配，待创建数量: {}", requestDTOs.size());

        try {
            List<AssignmentResponseDTO> createdAssignments = assignmentService.batchCreateAssignments(requestDTOs);
            logger.info("批量创建任务分配执行完成，成功创建数量: {}", createdAssignments.size());
            return ResponseEntity.ok(createdAssignments);
        } catch (IllegalArgumentException e) {
            logger.error("批量创建任务分配业务异常: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("批量创建任务分配系统异常: ", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 查询任务分配相关接口
    // ============================

    /**
     * 根据ID查询任务分配详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponseDTO> getAssignmentById(@PathVariable Long id) {
        logger.debug("根据ID查询任务分配，目标ID: {}", id);

        try {
            AssignmentResponseDTO assignment = assignmentService.getAssignmentById(id);
            logger.debug("任务分配查询成功，ID: {}, 状态: {}", id, assignment.getStatus());
            return ResponseEntity.ok(assignment);
        } catch (RuntimeException e) {
            logger.warn("任务分配查询失败，ID不存在: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配查询系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 分页查询所有任务分配
     */
    @GetMapping("/page")
    public ResponseEntity<Page<AssignmentResponseDTO>> getAllAssignmentsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.debug("开始分页查询任务分配，参数: page={}, size={}, sort={}, direction={}",
                page, size, sortBy, direction);

        try {
            Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            Page<AssignmentResponseDTO> assignmentPage = assignmentService.getAllAssignments(pageable);
            logger.debug("分页查询任务分配完成，当前页: {}, 总记录: {}, 总页数: {}",
                    assignmentPage.getNumber(), assignmentPage.getTotalElements(), assignmentPage.getTotalPages());

            return ResponseEntity.ok(assignmentPage);
        } catch (Exception e) {
            logger.error("分页查询任务分配系统异常，参数: page={}, size={}: ", page, size, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 根据状态查询任务分配
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AssignmentResponseDTO>> getAssignmentsByStatus(@PathVariable Assignment.AssignmentStatus status) {
        logger.debug("开始按状态查询任务分配，目标状态: {}", status);

        try {
            List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsByStatus(status);
            logger.debug("按状态查询任务分配完成，状态: {}, 结果数量: {}", status, assignments.size());
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logger.error("按状态查询任务分配系统异常，状态: {}: ", status, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 根据车辆查询任务分配
     */
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<AssignmentResponseDTO>> getAssignmentsByVehicle(@PathVariable Long vehicleId) {
        logger.debug("开始按车辆查询任务分配，目标车辆ID: {}", vehicleId);

        try {
            List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsByVehicle(vehicleId);
            logger.debug("按车辆查询任务分配完成，车辆ID: {}, 结果数量: {}", vehicleId, assignments.size());
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logger.error("按车辆查询任务分配系统异常，车辆ID: {}: ", vehicleId, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 根据驾驶员查询任务分配
     */
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<List<AssignmentResponseDTO>> getAssignmentsByDriver(@PathVariable Long driverId) {
        logger.debug("开始按驾驶员查询任务分配，目标驾驶员ID: {}", driverId);

        try {
            List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsByDriver(driverId);
            logger.debug("按驾驶员查询任务分配完成，驾驶员ID: {}, 结果数量: {}", driverId, assignments.size());
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logger.error("按驾驶员查询任务分配系统异常，驾驶员ID: {}: ", driverId, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 根据路线查询任务分配
     */
    @GetMapping("/route/{routeId}")
    public ResponseEntity<List<AssignmentResponseDTO>> getAssignmentsByRoute(@PathVariable Long routeId) {
        logger.debug("开始按路线查询任务分配，目标路线ID: {}", routeId);

        try {
            List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsByRoute(routeId);
            logger.debug("按路线查询任务分配完成，路线ID: {}, 结果数量: {}", routeId, assignments.size());
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logger.error("按路线查询任务分配系统异常，路线ID: {}: ", routeId, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 更新任务分配相关接口
    // ============================

    /**
     * 更新任务分配信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<AssignmentResponseDTO> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequestDTO requestDTO) {

        logger.info("开始更新任务分配信息，目标ID: {}", id);

        try {
            AssignmentResponseDTO updatedAssignment = assignmentService.updateAssignment(id, requestDTO);
            logger.info("任务分配信息更新成功，ID: {}, 新状态: {}", id, updatedAssignment.getStatus());
            return ResponseEntity.ok(updatedAssignment);
        } catch (RuntimeException e) {
            logger.warn("任务分配更新失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配更新系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 删除任务分配相关接口
    // ============================

    /**
     * 删除任务分配
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        logger.info("开始删除任务分配，目标ID: {}", id);

        try {
            assignmentService.deleteAssignment(id);
            logger.info("任务分配删除操作完成，ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            logger.warn("任务分配删除失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配删除系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============================
    // 任务状态管理接口
    // ============================

    /**
     * 开始执行任务
     */
    @PatchMapping("/{id}/start")
    public ResponseEntity<AssignmentResponseDTO> startAssignment(@PathVariable Long id) {
        logger.info("开始执行任务分配，目标ID: {}", id);

        try {
            AssignmentResponseDTO startedAssignment = assignmentService.startAssignment(id);
            logger.info("任务分配开始执行成功，ID: {}, 当前状态: {}", id, startedAssignment.getStatus());
            return ResponseEntity.ok(startedAssignment);
        } catch (IllegalStateException e) {
            logger.warn("任务分配开始执行失败，状态不允许: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            logger.warn("任务分配开始执行失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配开始执行系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 完成任务
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<AssignmentResponseDTO> completeAssignment(@PathVariable Long id) {
        logger.info("开始完成任务分配，目标ID: {}", id);

        try {
            AssignmentResponseDTO completedAssignment = assignmentService.completeAssignment(id);
            logger.info("任务分配完成操作成功，ID: {}, 当前状态: {}", id, completedAssignment.getStatus());
            return ResponseEntity.ok(completedAssignment);
        } catch (IllegalStateException e) {
            logger.warn("任务分配完成失败，状态不允许: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            logger.warn("任务分配完成失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配完成系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 取消任务
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AssignmentResponseDTO> cancelAssignment(@PathVariable Long id) {
        logger.info("开始取消任务分配，目标ID: {}", id);

        try {
            AssignmentResponseDTO cancelledAssignment = assignmentService.cancelAssignment(id);
            logger.info("任务分配取消操作成功，ID: {}, 当前状态: {}", id, cancelledAssignment.getStatus());
            return ResponseEntity.ok(cancelledAssignment);
        } catch (IllegalStateException e) {
            logger.warn("任务分配取消失败，状态不允许: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            logger.warn("任务分配取消失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配取消系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 移动到下一个动作
     */
    @PatchMapping("/{id}/next-action")
    public ResponseEntity<AssignmentResponseDTO> moveToNextAction(@PathVariable Long id) {
        logger.info("开始移动到下一个动作，任务分配ID: {}", id);

        try {
            AssignmentResponseDTO updatedAssignment = assignmentService.moveToNextAction(id);
            logger.info("移动到下一个动作操作成功，ID: {}, 当前动作索引: {}", id, updatedAssignment.getCurrentActionIndex());
            return ResponseEntity.ok(updatedAssignment);
        } catch (IllegalStateException e) {
            logger.warn("移动到下一个动作失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            logger.warn("移动到下一个动作失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("移动到下一个动作系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 更新任务状态
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AssignmentResponseDTO> updateAssignmentStatus(
            @PathVariable Long id,
            @RequestParam Assignment.AssignmentStatus status) {

        logger.info("开始更新任务分配状态，目标ID: {}, 新状态: {}", id, status);

        try {
            AssignmentResponseDTO updatedAssignment = assignmentService.updateAssignmentStatus(id, status);
            logger.info("任务分配状态更新成功，ID: {}, 新状态: {}", id, updatedAssignment.getStatus());
            return ResponseEntity.ok(updatedAssignment);
        } catch (IllegalStateException e) {
            logger.warn("任务分配状态更新失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            logger.warn("任务分配状态更新失败，目标任务不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("任务分配状态更新系统异常，ID: {}: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 批量更新任务状态
     */
    @PatchMapping("/batch-status")
    public ResponseEntity<Void> batchUpdateStatus(
            @RequestParam List<Long> assignmentIds,
            @RequestParam Assignment.AssignmentStatus status) {

        logger.info("开始批量更新任务分配状态，任务数量: {}, 新状态: {}", assignmentIds.size(), status);

        try {
            assignmentService.batchUpdateStatus(assignmentIds, status);
            logger.info("批量更新任务分配状态操作完成，数量: {}", assignmentIds.size());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("批量更新任务分配状态系统异常: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取当前活跃的 Assignment
     */
    @GetMapping("/active")
    public List<AssignmentBriefDTO> getActiveAssignments() {
        return assignmentService.getActiveAssignments();
    }

    /**
     * 获取新增的 Assignment（尚未绘制的）
     */
    @GetMapping("/new")
    public List<AssignmentBriefDTO> getNewAssignments() {
        return assignmentService.getNewAssignments();
    }

    /**
     * 获取完整的 Assignment 信息
     */
    @GetMapping("/{assignmentId}")
    public AssignmentDTO getAssignmentDetail(@PathVariable Long assignmentId) {
        return assignmentService.getAssignmentDetail(assignmentId);
    }

    /**
     * 标记 Assignment 为已绘制
     */
    @PostMapping("/mark-drawn/{assignmentId}")
    public ResponseEntity<Void> markAssignmentAsDrawn(@PathVariable Long assignmentId) {
        assignmentService.markAssignmentAsDrawn(assignmentId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取需要清理的 Assignment ID 列表
     */
    @GetMapping("/to-cleanup")
    public List<Long> getAssignmentsToCleanup() {
        return assignmentService.getCompletedAssignments();
    }
}