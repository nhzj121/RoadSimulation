package org.example.roadsimulation.controller;

import jakarta.validation.Valid;
import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.service.ActionService;
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

/**
 * ActionController - 动作管理REST API控制器
 */
@Validated
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private static final Logger logger = LoggerFactory.getLogger(ActionController.class);

    private final ActionService actionService;

    @Autowired
    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    // ============================
    // 创建动作相关接口
    // ============================

    @PostMapping
    public ResponseEntity<Action> createAction(@Valid @RequestBody Action action) {
        logger.info("开始创建新动作，动作名称: {}", action.getActionName());

        try {
            Action createdAction = actionService.createAction(action);

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(createdAction.getId())
                    .toUri();

            logger.info("动作创建完成，ID: {}, 名称: {}", createdAction.getId(), createdAction.getActionName());
            return ResponseEntity.created(location).body(createdAction);
        } catch (IllegalArgumentException e) {
            logger.error("动作创建业务异常: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("动作创建系统异常，动作名称: {}, 错误: {}", action.getActionName(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Action>> batchCreateActions(@Valid @RequestBody List<Action> actions) {
        logger.info("开始批量创建动作，待创建数量: {}", actions.size());

        try {
            List<Action> createdActions = actionService.batchCreateActions(actions);
            logger.info("批量创建动作执行完成，成功创建数量: {}", createdActions.size());
            return ResponseEntity.ok(createdActions);
        } catch (IllegalArgumentException e) {
            logger.error("批量创建动作业务异常: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("批量创建动作系统异常，数量: {}, 错误: {}", actions.size(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 查询动作相关接口
    // ============================

    @GetMapping("/{id}")
    public ResponseEntity<Action> getActionById(@PathVariable Long id) {
        logger.debug("根据ID查询动作，目标ID: {}", id);

        try {
            Action action = actionService.getActionById(id);
            logger.debug("动作查询成功，ID: {}, 名称: {}", id, action.getActionName());
            return ResponseEntity.ok(action);
        } catch (RuntimeException e) {
            logger.warn("动作查询失败，ID不存在: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("动作查询系统异常，ID: {}, 错误详情: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<Action>> getAllActions() {
        logger.debug("开始获取所有动作列表");

        try {
            List<Action> actions = actionService.getAllActions();
            logger.debug("所有动作列表获取完成，总计: {} 个动作", actions.size());
            return ResponseEntity.ok(actions);
        } catch (Exception e) {
            logger.error("获取所有动作列表系统异常: ", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/page")
    public ResponseEntity<Page<Action>> getAllActionsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.debug("开始分页查询动作，参数: page={}, size={}, sort={}, direction={}",
                page, size, sortBy, direction);

        try {
            Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            Page<Action> actionPage = actionService.getAllActions(pageable);
            logger.debug("分页查询动作完成，当前页: {}, 总记录: {}, 总页数: {}",
                    actionPage.getNumber(), actionPage.getTotalElements(), actionPage.getTotalPages());

            return ResponseEntity.ok(actionPage);
        } catch (Exception e) {
            logger.error("分页查询动作系统异常，参数: page={}, size={}, 错误: ", page, size, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Action>> searchActionsByName(@RequestParam String name) {
        logger.debug("开始按名称搜索动作，搜索关键词: {}", name);

        try {
            List<Action> actions = actionService.searchActionsByName(name);
            logger.debug("动作名称搜索完成，关键词: {}, 匹配数量: {}", name, actions.size());
            return ResponseEntity.ok(actions);
        } catch (Exception e) {
            logger.error("动作名称搜索系统异常，关键词: {}, 错误: ", name, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/type/{actionType}")
    public ResponseEntity<List<Action>> getActionsByType(@PathVariable Action.ActionType actionType) {
        logger.debug("开始按类型查询动作，目标类型: {}", actionType);

        try {
            List<Action> actions = actionService.getActionsByType(actionType);
            logger.debug("按类型查询动作完成，类型: {}, 结果数量: {}", actionType, actions.size());
            return ResponseEntity.ok(actions);
        } catch (Exception e) {
            logger.error("按类型查询动作系统异常，类型: {}, 错误: ", actionType, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 更新动作相关接口
    // ============================

    @PutMapping("/{id}")
    public ResponseEntity<Action> updateAction(
            @PathVariable Long id,
            @Valid @RequestBody Action actionDetails) {

        logger.info("开始更新动作信息，目标ID: {}", id);

        try {
            Action updatedAction = actionService.updateAction(id, actionDetails);
            logger.info("动作信息更新成功，ID: {}, 新名称: {}", id, updatedAction.getActionName());
            return ResponseEntity.ok(updatedAction);
        } catch (IllegalStateException e) {
            logger.warn("动作更新失败，动作已被锁定无法修改，ID: {}", id);
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            logger.warn("动作更新失败，目标动作不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("动作更新系统异常，ID: {}, 错误详情: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 删除动作相关接口
    // ============================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAction(@PathVariable Long id) {
        logger.info("开始删除动作，目标ID: {}", id);

        try {
            actionService.deleteAction(id);
            logger.info("动作删除操作完成，ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            logger.warn("动作删除失败，动作已被锁定无法删除，ID: {}", id);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            logger.warn("动作删除失败，目标动作不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("动作删除系统异常，ID: {}, 错误详情: ", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============================
    // 动作状态管理接口
    // ============================

    @PatchMapping("/{id}/lock")
    public ResponseEntity<Action> lockAction(@PathVariable Long id) {
        logger.info("开始锁定动作，目标ID: {}", id);

        try {
            Action lockedAction = actionService.lockAction(id);
            logger.info("动作锁定操作完成，ID: {}, 当前锁定状态: {}", id, lockedAction.getIsLocked());
            return ResponseEntity.ok(lockedAction);
        } catch (RuntimeException e) {
            logger.warn("动作锁定失败，目标动作不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("动作锁定系统异常，ID: {}, 错误详情: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @PatchMapping("/{id}/unlock")
    public ResponseEntity<Action> unlockAction(@PathVariable Long id) {
        logger.info("开始解锁动作，目标ID: {}", id);

        try {
            Action unlockedAction = actionService.unlockAction(id);
            logger.info("动作解锁操作完成，ID: {}, 当前锁定状态: {}", id, unlockedAction.getIsLocked());
            return ResponseEntity.ok(unlockedAction);
        } catch (RuntimeException e) {
            logger.warn("动作解锁失败，目标动作不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("动作解锁系统异常，ID: {}, 错误详情: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ============================
    // 工具和校验接口
    // ============================

    @PostMapping("/validate")
    public ResponseEntity<Boolean> validateAction(@Valid @RequestBody Action action) {
        logger.debug("开始验证动作数据，动作名称: {}", action.getActionName());

        try {
            boolean isValid = actionService.validateAction(action);
            logger.debug("动作数据验证完成，名称: {}, 验证结果: {}", action.getActionName(), isValid);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            logger.error("动作数据验证系统异常，动作名称: {}, 错误: ", action.getActionName(), e);
            return ResponseEntity.internalServerError().body(false);
        }
    }

    @GetMapping("/{id}/description")
    public ResponseEntity<String> getActionDescription(@PathVariable Long id) {
        logger.debug("开始获取动作描述信息，目标ID: {}", id);

        try {
            Action action = actionService.getActionById(id);
            String description = action.generateDescription();
            logger.debug("动作描述信息获取完成，ID: {}, 描述内容: {}", id, description);
            return ResponseEntity.ok(description);
        } catch (RuntimeException e) {
            logger.warn("获取动作描述失败，目标动作不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("获取动作描述系统异常，ID: {}, 错误详情: ", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/health")
    //http://localhost:8080/health/api/actions
    public ResponseEntity<String> healthCheck() {
        logger.debug("动作服务健康检查请求");
        return ResponseEntity.ok("Action Service is running properly");
    }
}