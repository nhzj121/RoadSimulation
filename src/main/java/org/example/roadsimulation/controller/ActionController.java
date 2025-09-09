package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.service.ActionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/actions") // 基础路径设置为 /api/actions
public class ActionController {

    private final ActionService actionService;

    @Autowired
    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    // GET /api/actions - 获取所有行为定义
    @GetMapping
    public List<Action> getAllActions() {
        return actionService.getAllActions();
    }

    // GET /api/actions/{id} - 根据ID获取特定行为
    @GetMapping("/{id}")
    public Action getActionById(@PathVariable Long id) {
        return actionService.getActionById(id);
    }

    // GET /api/actions/type/{type} - 根据类型获取行为，例如 /api/actions/type/运输中
    @GetMapping("/type/{actionType}")
    public List<Action> getActionsByType(@PathVariable String actionType) {
        return actionService.getActionsByType(actionType);
    }

    // POST /api/actions - 创建一个新的行为定义
    @PostMapping
    public Action createAction(@RequestBody Action action) {
        return actionService.saveAction(action);
    }

    // PUT /api/actions/{id} - 更新一个已有的行为定义
    @PutMapping("/{id}")
    public Action updateAction(@PathVariable Long id, @RequestBody Action actionDetails) {
        Action action = actionService.getActionById(id);
        // 更新字段
        action.setActionName(actionDetails.getActionName());
        action.setActionType(actionDetails.getActionType());
        action.setDuration(actionDetails.getDuration());
        action.setAccidentRate(actionDetails.getAccidentRate());

        return actionService.saveAction(action);
    }

    // DELETE /api/actions/{id} - 删除一个行为定义
    @DeleteMapping("/{id}")
    public String deleteAction(@PathVariable Long id) {
        actionService.deleteAction(id);
        return "Action with id " + id + " has been deleted successfully.";
    }
}