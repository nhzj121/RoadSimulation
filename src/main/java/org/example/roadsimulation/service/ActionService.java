package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Action;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ActionService {

    // 创建动作
    Action createAction(Action action);

    // 根据ID获取动作
    Action getActionById(Long id);

    // 获取所有动作
    List<Action> getAllActions();

    // 分页查询所有动作
    Page<Action> getAllActions(Pageable pageable);

    // 更新动作
    Action updateAction(Long id, Action actionDetails);

    // 删除动作
    void deleteAction(Long id);

    // 根据名称搜索动作
    List<Action> searchActionsByName(String name);

    // 根据类型查询动作
    List<Action> getActionsByType(Action.ActionType actionType);

    // 锁定动作
    Action lockAction(Long id);

    // 解锁动作
    Action unlockAction(Long id);

    // 批量创建动作
    List<Action> batchCreateActions(List<Action> actions);

    // 验证动作数据
    boolean validateAction(Action action);
}